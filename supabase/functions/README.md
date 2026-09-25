# Edge Functions 部署说明

这三个函数存在的目的是：**让第三方密钥与敏感数据不再离开服务端**。

| 函数 | 作用 | 需要的 Secret |
|---|---|---|
| `login` | 账号名 → 邮箱在服务端解析，邮箱不回传；再转发 GoTrue 密码登录 | 无（用自动注入的 key） |
| `reset-password` | 触发 Supabase Auth 生成验证码并发信；校验通过后改密 | 无（用 Dashboard 里配的自定义 SMTP 发信） |
| `ai-assistant` | 豆包视觉 + DeepSeek 文本双保险降级；以小助手身份写入回复 | `DEEPSEEK_API_KEY`、`VISION_API_KEY` |

`SUPABASE_URL`、`SUPABASE_SERVICE_ROLE_KEY` 由 Supabase 自动注入，**不需要手动设置**，也绝不要写进 App。

---

## 前置

1. 装 Supabase CLI：`npm i -g supabase`（或 `scoop install supabase`）。
2. 网络能访问 `api.supabase.com`。若 Dashboard 报 `Failed to fetch`，先按项目根目录说明修好本机 IPv6。

## 部署

```powershell
supabase login
supabase link --project-ref wsxygiskzjkezoakejri

# reset-password 的发信不走 Secret：验证码由 Supabase Auth 发，
# 所以要在 Dashboard 里配 SMTP 与邮件模板，见文末「常见问题」第 1 条
supabase secrets set `
  DEEPSEEK_API_KEY=sk-xxx `
  VISION_API_KEY=ark-xxx `
  VISION_BASE_URL=https://ark.cn-beijing.volces.com/api/v3/chat/completions `
  VISION_MODEL=ep-xxx

supabase functions deploy login --no-verify-jwt
supabase functions deploy reset-password --no-verify-jwt
supabase functions deploy ai-assistant --no-verify-jwt
```

三个函数都部署为 `--no-verify-jwt`，各自在函数体内做自己的把关：

- `login` / `reset-password` 本身发生在未登录状态，不需要用户身份。
  - `login` 不限制调用频率，但转发的是 GoTrue 自己的 token 接口，沿用 GoTrue 内置的登录频控。
  - `reset-password` 靠「同一邮箱 60 秒内只能发一次」控制；验证码本身的有效期与试错次数由 Supabase Auth 管（`/verify` 端点自带限制）。
- `ai-assistant` 用调用方带来的用户 JWT 请求 `/auth/v1/user` 换取用户信息，非 200 直接 401。

> 不用 CLI 也行：Dashboard → Edge Functions → 新建同名函数 → 粘贴 `index.ts` 内容 → 在 Secrets 里逐条填。效果一样，只是不可重复执行。

## 验证

```powershell
$anon = "sb_publishable_WedwYJNF5dqYrX5ERlSXTA_VTJkWkJL"
$base = "https://wsxygiskzjkezoakejri.supabase.co/functions/v1"

# ⓪ 账号名登录：应返回 {"ok":true,"session":{...}}，且响应里没有 email 字段之外的账号信息
#    关键：用账号名登录能成功，说明服务端解析生效，而客户端全程拿不到"账号名→邮箱"的映射
curl.exe -s -X POST "$base/login" -H "apikey: $anon" -H "Authorization: Bearer $anon" `
  -H "Content-Type: application/json" -d '{\"input\":\"你的账号名\",\"password\":\"你的密码\"}'

# ① 发验证码：应返回 {"ok":true}，且响应里不含验证码
curl.exe -s -X POST "$base/reset-password" -H "apikey: $anon" -H "Authorization: Bearer $anon" `
  -H "Content-Type: application/json" -d '{\"action\":\"request\",\"email\":\"你的测试邮箱\"}'

# ② 用错误的验证码确认改密：必须返回 {"ok":false,"reason":"bad_code"}
#    —— 这一步是账号接管漏洞是否真的堵上的验收点
curl.exe -s -X POST "$base/reset-password" -H "apikey: $anon" -H "Authorization: Bearer $anon" `
  -H "Content-Type: application/json" `
  -d '{\"action\":\"confirm\",\"email\":\"你的测试邮箱\",\"code\":\"000000\",\"newPassword\":\"newpass123\"}'

# ③ 未带用户 JWT 调 AI 函数：必须返回 401
curl.exe -s -i -X POST "$base/ai-assistant" -H "apikey: $anon" -H "Authorization: Bearer $anon" `
  -H "Content-Type: application/json" -d '{}'
```

## 排查

```powershell
supabase functions logs reset-password --tail
supabase functions logs ai-assistant --tail
```

常见问题：

- `server_misconfigured` → 没 link 到项目，或 Secret 没设置成功。
- 改密码时 **收不到验证码** → 发信现在由 Supabase Auth 负责，按顺序查这三处：
  1. Authentication → **SMTP Settings**：自定义 SMTP 是不是 enabled（本项目用 Gmail 账号）；
  2. Authentication → **Email Templates → Reset Password**：模板里有没有 `{{ .Token }}` —— 只有 `{{ .ConfirmationURL }}` 的话邮件里没有验证码，客户端自然永远填不对；
  3. 邮箱其实收到了，但在**垃圾邮件**里（Gmail 当发信人容易这样）。
  - App 会把失败原因显示出来：`email_failed` = Supabase 发信失败（多为 SMTP 没配好、或 Gmail 账号被限流），原话在函数日志与响应 `detail` 里；
    验证码填错是 `bad_code`，超期是 `expired`。
  - 只改 Dashboard 配置不用重新部署函数；改了 `index.ts` 必须重新 `supabase functions deploy reset-password --no-verify-jwt`。
- AI 一直走 DeepSeek 兜底 → Edge Function 访问 `ark.cn-beijing.volces.com` 不稳，属预期内；可换 `VISION_BASE_URL` 为可达地址。
- AI 返回 `no_provider` → 两个 key 都没配。
