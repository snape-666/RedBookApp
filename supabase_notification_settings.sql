-- ============================================================
-- 通知设置列(在 Supabase SQL Editor 中执行一次)
-- users 表新增 通知设置 5 个布尔列 + version,用于本地/云端双存
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS notif_receive_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS notif_like_fav BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS notif_follow BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS notif_comment BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS notif_dm BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS notif_version BIGINT NOT NULL DEFAULT 0;

-- 若 users 启用了 RLS,需保证 anon key 可读可更新这些列(项目其它表已 DISABLE RLS)
ALTER TABLE users DISABLE ROW LEVEL SECURITY;
GRANT ALL ON users TO anon, authenticated;
