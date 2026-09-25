// Supabase Edge Function: 评论区 AI 小助手
//
// 为什么放在服务端：
//   豆包(火山方舟) 与 DeepSeek 的密钥原本通过 buildConfigField 编译进 APK，
//   项目未开混淆，反编译即可直接盗用额度。搬到服务端后密钥只存在于 Secret 里。
//
// 双保险降级链原样保留（行为与迁移前一致）：
//   帖子有图片/视频 → 豆包视觉优先，失败降级 DeepSeek 文本
//   纯文字帖子     → DeepSeek 文本优先，失败降级豆包
//
// 部署：supabase functions deploy ai-assistant --no-verify-jwt
//   （函数内自行校验用户 JWT，不依赖网关对 sb_publishable_ 这类新式 key 的 verify_jwt 行为）
// 需要设置：DEEPSEEK_API_KEY、VISION_API_KEY
// 可选设置：VISION_BASE_URL、VISION_MODEL（代码内有默认值）
// 自动注入：SUPABASE_URL、SUPABASE_SERVICE_ROLE_KEY

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const DEEPSEEK_API_KEY = Deno.env.get("DEEPSEEK_API_KEY") ?? "";
const VISION_API_KEY = Deno.env.get("VISION_API_KEY") ?? "";
const VISION_BASE_URL = Deno.env.get("VISION_BASE_URL") ||
  "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
const VISION_MODEL = Deno.env.get("VISION_MODEL") ?? "";

const DEEPSEEK_BASE_URL = "https://api.deepseek.com/chat/completions";
const DEEPSEEK_MODEL = "deepseek-chat";

/** 小助手固定身份，与客户端 AiAssistant.UID 保持一致 */
const AI_UID = "ai_assistant";
const AI_NAME = "小助手";

const MAX_QUESTION_LEN = 300;
const MAX_ANSWER_LEN = 500;
/** 单个模型调用的上限；两个都试也不会顶到 Edge Function 的墙钟限制 */
const PER_MODEL_TIMEOUT_MS = 45_000;

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

function svcHeaders(extra: Record<string, string> = {}): Record<string, string> {
  return {
    apikey: SERVICE_ROLE,
    Authorization: `Bearer ${SERVICE_ROLE}`,
    "Content-Type": "application/json",
    ...extra,
  };
}

/** 用调用方带来的用户 JWT 向 GoTrue 换取用户信息；失败即未登录 */
async function requireUser(req: Request): Promise<string | null> {
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return null;
  const res = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: SERVICE_ROLE, Authorization: auth },
  });
  if (!res.ok) return null;
  const user = await res.json().catch(() => null);
  const id = user?.id;
  return typeof id === "string" && id ? id : null;
}

function buildSystemPrompt(postTitle: string, postContent: string, hasMedia: boolean): string {
  let prompt = "你叫「小助手」，是小红书 App 评论区里的 AI 助手。";
  if (hasMedia) prompt += "用户已附上帖子的图片/视频内容，请基于画面内容分析帖子。";
  if (postTitle.trim()) prompt += `\n帖子标题：「${postTitle.trim()}」`;
  const content = postContent.trim();
  if (content) {
    const brief = content.length > 300 ? `${content.slice(0, 300)}…` : content;
    prompt += `\n帖子内容：「${brief}」`;
  }
  prompt += "\n请结合以上帖子内容回答用户对帖子的提问。用简体中文、口语化、简洁地回答问题，不要超过 200 字，不要使用 Markdown/表情符号。";
  return prompt;
}

function normalizeAnswer(raw: string): string {
  return raw.trim().replace(/^[\n\s]+/gm, "").slice(0, MAX_ANSWER_LEN);
}

async function chatCompletion(
  url: string,
  apiKey: string,
  body: Record<string, unknown>,
): Promise<string | null> {
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json; charset=utf-8",
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(PER_MODEL_TIMEOUT_MS),
    });
    if (!res.ok) {
      console.warn("model http error", url, res.status, (await res.text()).slice(0, 200));
      return null;
    }
    const data = await res.json();
    const content = data?.choices?.[0]?.message?.content;
    return typeof content === "string" && content.trim() ? content : null;
  } catch (e) {
    console.warn("model call failed", url, String(e));
    return null;
  }
}

/** 豆包多模态：文本 + 图片 + 视频直传分析画面 */
function requestVision(
  question: string,
  postTitle: string,
  postContent: string,
  images: string[],
  video: string,
): Promise<string | null> {
  if (!VISION_API_KEY || !VISION_MODEL) return Promise.resolve(null);
  const userContent: Record<string, unknown>[] = [{ type: "text", text: question }];
  if (video) userContent.push({ type: "video_url", video_url: { url: video } });
  for (const img of images) userContent.push({ type: "image_url", image_url: { url: img } });

  return chatCompletion(VISION_BASE_URL, VISION_API_KEY, {
    model: VISION_MODEL,
    messages: [
      { role: "system", content: buildSystemPrompt(postTitle, postContent, !!video || images.length > 0) },
      { role: "user", content: userContent },
    ],
    max_tokens: 800,
    temperature: 0.8,
    stream: false,
  });
}

/** DeepSeek 纯文本 */
function requestText(question: string, postTitle: string, postContent: string): Promise<string | null> {
  if (!DEEPSEEK_API_KEY) return Promise.resolve(null);
  return chatCompletion(DEEPSEEK_BASE_URL, DEEPSEEK_API_KEY, {
    model: DEEPSEEK_MODEL,
    messages: [
      { role: "system", content: buildSystemPrompt(postTitle, postContent, false) },
      { role: "user", content: question },
    ],
    max_tokens: 700,
    temperature: 0.8,
    stream: false,
  });
}

/** 以小助手身份写入 comments 表（服务端写入，客户端不再具备冒充能力） */
async function insertReply(
  replyId: string,
  postId: string,
  parentCommentId: string,
  answer: string,
): Promise<boolean> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/comments`, {
    method: "POST",
    headers: svcHeaders({ Prefer: "return=minimal" }),
    body: JSON.stringify({
      comment_id: replyId,
      post_id: postId,
      parent_id: parentCommentId,
      content: answer,
      author_uid: AI_UID,
      author_name: AI_NAME,
      author_avatar: "",
      author_xhs_id: "",
      post_title: "",
      image_url: "",
      ip_location: "",
      created_at: Date.now(),
    }),
  });
  if (!res.ok) {
    console.error("insert ai reply failed", res.status, (await res.text()).slice(0, 200));
    return false;
  }
  return true;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false, reason: "method_not_allowed" }, 405);
  if (!SUPABASE_URL || !SERVICE_ROLE) {
    return json({ ok: false, reason: "server_misconfigured" }, 500);
  }

  const userId = await requireUser(req);
  if (!userId) return json({ ok: false, reason: "unauthorized" }, 401);

  let payload: Record<string, unknown>;
  try {
    payload = await req.json();
  } catch {
    return json({ ok: false, reason: "bad_request" }, 400);
  }

  const question = String(payload.question ?? "").trim();
  const postId = String(payload.postId ?? "");
  const parentCommentId = String(payload.parentCommentId ?? "");
  const postTitle = String(payload.postTitle ?? "");
  const postContent = String(payload.postContent ?? "");

  if (!question) return json({ ok: false, reason: "empty_question" }, 400);
  if (question.length > MAX_QUESTION_LEN) return json({ ok: false, reason: "question_too_long" }, 400);
  if (!postId || !parentCommentId) return json({ ok: false, reason: "bad_request" }, 400);

  const isHttp = (u: string) => u.startsWith("http://") || u.startsWith("https://");
  const rawImages = Array.isArray(payload.images) ? payload.images : [];
  const images = rawImages
    .flatMap((it) => String(it).split(","))
    .map((it) => it.trim())
    .filter(isHttp);
  const video = String(payload.video ?? "").trim().replace(/^video:/, "");
  const media = video && isHttp(video) ? video : "";

  // 双保险：按优先级尝试，任一成功即用
  const attempts: Array<() => Promise<string | null>> = [];
  const hasMedia = images.length > 0 || !!media;
  const visionReady = !!VISION_API_KEY && !!VISION_MODEL;
  const textReady = !!DEEPSEEK_API_KEY;

  if (hasMedia && visionReady) {
    attempts.push(() => requestVision(question, postTitle, postContent, images, media));
  }
  if (textReady) {
    attempts.push(() => requestText(question, postTitle, postContent));
  }
  if (!hasMedia && visionReady) {
    attempts.push(() => requestVision(question, postTitle, postContent, [], ""));
  }
  if (attempts.length === 0) return json({ ok: false, reason: "no_provider" }, 503);

  let answer: string | null = null;
  for (const attempt of attempts) {
    const raw = await attempt();
    if (raw && raw.trim()) {
      answer = normalizeAnswer(raw);
      break;
    }
  }
  if (!answer) return json({ ok: false, reason: "no_answer" }, 502);

  const replyId = `ai_${Date.now()}`;
  const inserted = await insertReply(replyId, postId, parentCommentId, answer);
  if (!inserted) return json({ ok: false, reason: "insert_failed" }, 500);

  return json({ ok: true, replyId, answer });
});
