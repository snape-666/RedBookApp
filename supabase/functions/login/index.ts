// Supabase Edge Function: 账号登录代理
//
// 为什么需要它：
//   GoTrue 只支持「邮箱 + 密码」登录，而这个 App 支持「账号名登录」，
//   所以原先客户端要先调一个 resolve_login_email RPC 把账号名换成邮箱。
//   那个函数匿名可调、且把邮箱地址原样返回 —— 任何人报上一个账号名
//   就能换出对应用户的邮箱（PII 泄露，可用于钓鱼与撞库）。
//
//   现在把「账号名 → 邮箱」这一步收进服务端，邮箱只在函数内部使用，
//   不再离开服务端。客户端只拿到登录成功后的会话。
//
// 部署：supabase functions deploy login --no-verify-jwt
//   （登录本身发生在未登录状态，网关层不能要求用户 JWT）
// 自动注入：SUPABASE_URL、SUPABASE_SERVICE_ROLE_KEY（无需额外 Secret）
//
// 说明：账号名「是否存在」这件事本来就是公开的（注册页的 account_exists 查重
// 就是标准做法），所以本函数保留 account_not_found 与 invalid_credentials 两种
// 提示以维持原交互；真正被修掉的是「邮箱可以被任意换出」。

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

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

const isEmail = (v: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);

/** 输入是邮箱就直接用；是账号名则回服务端查一次，邮箱不出本函数 */
async function resolveEmail(input: string): Promise<string | null> {
  if (isEmail(input)) return input;
  const url = `${SUPABASE_URL}/rest/v1/users` +
    `?select=email&account=eq.${encodeURIComponent(input)}&limit=1`;
  const res = await fetch(url, { headers: svcHeaders() });
  if (!res.ok) {
    console.error("resolve account failed", res.status, await res.text());
    return null;
  }
  const rows = await res.json().catch(() => []);
  const email = Array.isArray(rows) && rows[0]?.email ? String(rows[0].email) : "";
  return email || null;
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

  const input = String(payload.input ?? "").trim();
  const password = String(payload.password ?? "");
  if (!input || !password) return json({ ok: false, reason: "bad_request" }, 400);

  const email = await resolveEmail(input);
  if (!email) return json({ ok: false, reason: "account_not_found" });

  const res = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: svcHeaders(),
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) {
    const detail = (await res.text()).slice(0, 200);
    console.warn("gotrue token failed", res.status, detail);
    // GoTrue 对密码错误返回 400 invalid login credentials；429 是它自带的频控
    const reason = res.status === 429 ? "too_many_requests" : "invalid_credentials";
    return json({ ok: false, reason });
  }

  // 会话里已经带了 user.email，客户端不需要额外的邮箱字段
  const session = await res.json();
  return json({ ok: true, session });
});
