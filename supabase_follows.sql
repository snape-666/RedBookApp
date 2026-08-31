-- 关注表
CREATE TABLE IF NOT EXISTS follows (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  follower_uid TEXT NOT NULL,
  followed_uid TEXT NOT NULL,
  created_at BIGINT NOT NULL
);
ALTER TABLE follows DISABLE ROW LEVEL SECURITY;
GRANT ALL ON follows TO anon, authenticated;

-- 备注名表（我(viewer_uid)对某用户(target_uid)的备注，独立于关注关系）
CREATE TABLE IF NOT EXISTS remarks (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  viewer_uid TEXT NOT NULL,
  target_uid TEXT NOT NULL,
  remark TEXT NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL DEFAULT 0,
  UNIQUE (viewer_uid, target_uid)
);
ALTER TABLE remarks DISABLE ROW LEVEL SECURITY;
GRANT ALL ON remarks TO anon, authenticated;
