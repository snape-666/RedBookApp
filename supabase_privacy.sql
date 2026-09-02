-- ============================================================
-- 隐私设置列(在 Supabase SQL Editor 中执行一次)
-- users 表新增 4 个展示开关 + version，控制主页 笔记/评论/收藏/赞过 对他人可见性
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS privacy_posts BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS privacy_comments BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS privacy_favorites BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS privacy_likes BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS privacy_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users DISABLE ROW LEVEL SECURITY;
GRANT ALL ON users TO anon, authenticated;
