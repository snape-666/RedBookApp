-- 用户表加 xhs_id 列
ALTER TABLE users ADD COLUMN IF NOT EXISTS xhs_id TEXT;

-- 点赞表加 xhs_id
ALTER TABLE likes ADD COLUMN IF NOT EXISTS user_xhs_id TEXT;

-- 收藏表加 xhs_id
ALTER TABLE favorites ADD COLUMN IF NOT EXISTS user_xhs_id TEXT;

-- 评论表加 xhs_id
ALTER TABLE comments ADD COLUMN IF NOT EXISTS author_xhs_id TEXT;

-- 帖子表确认有 author_xhs_id
ALTER TABLE posts ADD COLUMN IF NOT EXISTS author_xhs_id TEXT DEFAULT '';
