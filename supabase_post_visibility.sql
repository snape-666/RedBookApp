-- ============================================================
-- 帖子可见性列(在 Supabase SQL Editor 中执行一次)
-- posts 表新增 visibility: 公开可见 / 仅自己可见
--   'public'  -> 所有人可见(默认,老帖回填)
--   'private' -> 仅作者自己可见
-- 使用 TEXT 而非 BOOLEAN,便于未来扩展(如 'followers' 粉丝可见)
-- ============================================================

ALTER TABLE posts ADD COLUMN IF NOT EXISTS visibility TEXT NOT NULL DEFAULT 'public';
UPDATE posts SET visibility = 'public' WHERE visibility IS NULL OR visibility = '';
