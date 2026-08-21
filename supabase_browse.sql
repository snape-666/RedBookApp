-- 浏览记录表
CREATE TABLE IF NOT EXISTS browsing_history (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_uid TEXT NOT NULL,
  post_id TEXT NOT NULL,
  created_at BIGINT NOT NULL
);
ALTER TABLE browsing_history DISABLE ROW LEVEL SECURITY;
GRANT ALL ON browsing_history TO anon, authenticated;
