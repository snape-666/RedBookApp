-- 用户表加 xhs_id 列
ALTER TABLE users ADD COLUMN IF NOT EXISTS xhs_id TEXT;

-- 点赞表加 xhs_id
ALTER TABLE likes ADD COLUMN IF NOT EXISTS user_xhs_id TEXT;

-- 收藏表加 xhs_id
ALTER TABLE favorites ADD COLUMN IF NOT EXISTS user_xhs_id TEXT;

-- 评论表加 xhs_id
ALTER TABLE comments ADD COLUMN IF NOT EXISTS author_xhs_id TEXT;

-- 评论表加所评论帖子的标题
ALTER TABLE comments ADD COLUMN IF NOT EXISTS post_title TEXT DEFAULT '';

-- 帖子表确认有 author_xhs_id
ALTER TABLE posts ADD COLUMN IF NOT EXISTS author_xhs_id TEXT DEFAULT '';

-- 草稿表加图片字段
ALTER TABLE drafts ADD COLUMN IF NOT EXISTS image_url TEXT DEFAULT '';

-- 用户表加昵称/背景图/性别/生日字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname TEXT DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS background_url TEXT DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url TEXT DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS gender TEXT DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS birthday TEXT DEFAULT '';
