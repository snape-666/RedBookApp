-- 评论点赞表（评论与回复共用 comments 表，按 user_uid + comment_id 记录点赞关系）
CREATE TABLE IF NOT EXISTS comment_likes (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  like_id TEXT UNIQUE NOT NULL,
  user_uid TEXT NOT NULL,
  user_xhs_id TEXT NOT NULL DEFAULT '',
  comment_id TEXT NOT NULL,
  created_at BIGINT NOT NULL
);
ALTER TABLE comment_likes DISABLE ROW LEVEL SECURITY;
GRANT ALL ON comment_likes TO anon, authenticated;

-- 评论点赞计数列（若不存在则补上，保证 updateCommentLike 能落库）
ALTER TABLE comments ADD COLUMN IF NOT EXISTS like_count INT DEFAULT 0;
