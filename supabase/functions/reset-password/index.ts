// Supabase Edge Function: 密码重置
//
// 为什么必须放在服务端：
//   验证码一旦由客户端提供，服务端就无法分辨它是否真的送达了账号所有者 ——
//   任何人都能「自己指定一个码 → 立刻用它改密」，等于任意邮箱账号可被接管。
//
// 现在的分工：
//   验证码由 Supabase Auth(GoTrue) 生成并保存，邮件也由它按模板发出；
//   本函数只做两件事：触发发送，以及「校验通过之后才改密」。
//   客户端既拿不到码，也没有直接改密的能力。
//
// 部署：supabase functions deploy reset-password --no-verify-jwt
//   （重置密码发生在未登录状态，网关层不能要求用户 JWT）
// 需要先在 Dashboard 里配置（都不是 Secret，改完不用重新部署函数）：
//   1) Authentication → SMTP Settings：启用自定义 SMTP，否则信发不出去；
//   2) Authentication → Email Templates → Reset Password：模板里要有 {{ .Token }}，
//      邮件里才会带 6 位验证码；只放 {{ .ConfirmationURL }} 的话客户端拿不到码。
// 自动注入：SUPABASE_URL、SUPABASE_SERVICE_ROLE_KEY

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
/**
 * 可公开的 publishable key：它同样硬编码在 App 里(SupabaseConfig.anonKey)，
 * 设计上就允许出现在客户端。这里用它调用 GoTrue 的两个公开端点(/recover、/verify)，
 * 走和客户端一模一样的标准调用路径；service_role 只用于查表和改密。
 */
const PUBLIC_KEY = "sb_publishable_WedwYJNF5dqYrX5ERlSXTA_VTJkWkJL";

/** 同一邮箱两次发送的最小间隔，防止有人狂点把发信账号点到被 Google 限流 */
const SEND_COOLDOWN_MS = 60 * 1000;

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

/** GoTrue 公开端点(/recover、/verify)专用：只带 publishable key，不带 service_role */
function publicHeaders(): Record<string, string> {
  return { apikey: PUBLIC_KEY, "Content-Type": "application/json" };
}

const isEmail = (v: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);

/** 按邮箱查业务用户表（uid 即 auth.users.id）；查不到就没必要发信 */
async function findUser(email: string): Promise<string | null> {
  const url = `${SUPABASE_URL}/rest/v1/users?select=uid&email=eq.${encodeURIComponent(email)}&limit=1`;
  const res = await fetch(url, { headers: svcHeaders() });
  if (!res.ok) return null;
  const rows = await res.json().catch(() => []);
  if (!Array.isArray(rows) || rows.length === 0) return null;
  const uid = String(rows[0]?.uid ?? "");
  return uid || null;
}

/**
 * 这张表(password_resets)现在已经不存验证码了 —— 验证码与校验都归 Supabase Auth 管。
 * 这里只借用 email + updated_at 两列记「同一邮箱最近一次发送时间」做 60 秒冷却，
 * 免得有人狂点把发信账号点到被限流；其余列填占位值仅为满足 NOT NULL。
 */
async function getLastSentAt(email: string): Promise<number> {
  const url = `${SUPABASE_URL}/rest/v1/password_resets` +
    `?select=updated_at&email=eq.${encodeURIComponent(email)}&limit=1`;
  const res = await fetch(url, { headers: svcHeaders() });
  if (!res.ok) return 0;
  const rows = await res.json().catch(() => []);
  if (!Array.isArray(rows) || rows.length === 0) return 0;
  return Number(rows[0]?.updated_at ?? 0);
}

type SendResult =
  | { ok: true }
  | { ok: false; reason: string; status?: number; detail?: string };

/**
 * 让 Supabase Auth 发「重置密码」邮件。
 *
 * 验证码不在本函数生成、也不落我们的库：GoTrue 自己生成 6 位 recovery token、
 * 存进 auth 内部表、按模板发信（走项目里配好的自定义 SMTP，本项目是 Gmail 账号），
 * 并在 /verify 时自己校验有效期与尝试次数 —— 这些比赛在自己这边管更可靠。
 */
async function sendRecoveryEmail(email: string): Promise<SendResult> {
  try {
    const res = await fetch(`${SUPABASE_URL}/auth/v1/recover`, {
      method: "POST",
      headers: publicHeaders(),
      body: JSON.stringify({ email }),
      signal: AbortSignal.timeout(15_000),
    });
    if (res.ok) return { ok: true };
    const detail = (await res.text().catch(() => "")).slice(0, 300);
    console.error("recover failed", res.status, detail);
    // Supabase 自己的频控（每小时发信上限等）
    if (res.status === 429) return { ok: false, reason: "too_soon", status: res.status, detail };
    return { ok: false, reason: "email_failed", status: res.status, detail };
  } catch (e) {
    console.error("recover call failed", String(e));
    return { ok: false, reason: "email_failed", detail: String(e).slice(0, 300) };
  }
}

// ---------------------------------------------------------------- request
async function handleRequest(email: string) {
  const uid = await findUser(email);
  // 兼容原交互：明确告诉用户这个邮箱没注册过，而不是静默成功
  if (!uid) return json({ ok: false, reason: "not_registered" });

  const now = Date.now();
  if (now - await getLastSentAt(email) < SEND_COOLDOWN_MS) {
    return json({ ok: false, reason: "too_soon" });
  }

  // 先发信、成功才记冷却：发信失败(比如 SMTP 没配好)不该白占掉一次 60 秒
  const sent = await sendRecoveryEmail(email);
  if (!sent.ok) {
    // 用 200 + ok:false 返回，和本函数其它业务失败(bad_code/too_soon…)保持一致：
    // 客户端只有拿到 2xx 才会去读 body 里的 reason；非 2xx 会被网络层当 HTTP 错误抛出，
    // 界面上只剩一坨原始 JSON("502: {...}")，反而看不到真正的原因。
    return json({ ok: false, reason: sent.reason, status: sent.status, detail: sent.detail });
  }

  const upsert = await fetch(`${SUPABASE_URL}/rest/v1/password_resets?on_conflict=email`, {
    method: "POST",
    headers: svcHeaders({ Prefer: "resolution=merge-duplicates,return=minimal" }),
    body: JSON.stringify([{
      email,
      code_hash: "cooldown-only",
      expires_at: now,
      attempts: 0,
      updated_at: now,
    }]),
  });
  if (!upsert.ok) {
    // 冷却没记上不算致命：信已经发出去了，别让用户以为没发成功
    console.error("upsert password_resets failed", upsert.status, await upsert.text());
  }

  // 注意：响应里绝不能出现验证码 —— 它只存在于那封邮件里
  return json({ ok: true });
}

// ---------------------------------------------------------------- confirm
async function handleConfirm(email: string, code: string, newPassword: string) {
  if (!code) return json({ ok: false, reason: "bad_code" });
  if (newPassword.length < 6) return json({ ok: false, reason: "weak_password" });

  // 由 GoTrue 校验这枚 6 位 recovery token；通过时直接返回该用户的会话
  const verify = await fetch(`${SUPABASE_URL}/auth/v1/verify`, {
    method: "POST",
    headers: publicHeaders(),
    body: JSON.stringify({ type: "recovery", email, token: code }),
  });
  if (!verify.ok) {
    const detail = (await verify.text().catch(() => "")).slice(0, 300);
    console.warn("verify recovery failed", verify.status, detail);
    // 过期和「码不对」GoTrue 都是 4xx，靠正文区分一下，好给用户准确的提示
    const expired = /expire/i.test(detail);
    return json({ ok: false, reason: expired ? "expired" : "bad_code" });
  }

  const session = await verify.json().catch(() => null);
  const uid = String(session?.user?.id ?? "");
  if (!uid) return json({ ok: false, reason: "bad_code" });

  // 验证码确实通过校验了，才用管理接口改密（不直接写 auth.users）
  const update = await fetch(
    `${SUPABASE_URL}/auth/v1/admin/users/${encodeURIComponent(uid)}`,
    {
      method: "PUT",
      headers: svcHeaders(),
      body: JSON.stringify({ password: newPassword }),
    },
  );
  if (!update.ok) {
    console.error("admin update user failed", update.status, await update.text());
    return json({ ok: false, reason: "server_error" }, 500);
  }

  // 冷却记录顺手清掉：这次已经用完了，用户可以马上再发起一次
  await fetch(
    `${SUPABASE_URL}/rest/v1/password_resets?email=eq.${encodeURIComponent(email)}`,
    { method: "DELETE", headers: svcHeaders({ Prefer: "return=minimal" }) },
  ).catch(() => {});

  return json({ ok: true });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false, reason: "method_not_allowed" }, 405);
  if (!SUPABASE_URL || !SERVICE_ROLE) {
    return json({ ok: false, reason: "server_misconfigured" }, 500);
  }

  let payload: Record<string, unknown>;
  try {
    payload = await req.json();
  } catch {
    return json({ ok: false, reason: "bad_request" }, 400);
  }

  const action = String(payload.action ?? "");
  const email = String(payload.email ?? "").trim().toLowerCase();
  if (!isEmail(email)) return json({ ok: false, reason: "bad_email" }, 400);

  if (action === "request") return handleRequest(email);
  if (action === "confirm") {
    return handleConfirm(
      email,
      String(payload.code ?? "").trim(),
      String(payload.newPassword ?? ""),
    );
  }
  return json({ ok: false, reason: "bad_action" }, 400);
});
