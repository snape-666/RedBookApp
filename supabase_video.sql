CREATE TABLE video_notes (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  video_id TEXT UNIQUE NOT NULL,
  title TEXT NOT NULL DEFAULT '',
  video_url TEXT NOT NULL DEFAULT '',
  author_uid TEXT NOT NULL,
  author_xhs_id TEXT NOT NULL,
  author_name TEXT NOT NULL,
  author_avatar TEXT DEFAULT '',
  like_count INT DEFAULT 0,
  favorite_count INT DEFAULT 0,
  comment_count INT DEFAULT 0,
  view_count INT DEFAULT 0,
  ip_location TEXT DEFAULT '',
  created_at BIGINT NOT NULL
);
ALTER TABLE video_notes DISABLE ROW LEVEL SECURITY;
GRANT ALL ON video_notes TO anon, authenticated;

CREATE TABLE video_comments (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  comment_id TEXT UNIQUE NOT NULL,
  video_id TEXT NOT NULL,
  parent_id TEXT DEFAULT '',
  content TEXT NOT NULL,
  author_uid TEXT NOT NULL,
  author_name TEXT NOT NULL,
  author_avatar TEXT DEFAULT '',
  like_count INT DEFAULT 0,
  ip_location TEXT DEFAULT '',
  created_at BIGINT NOT NULL
);
ALTER TABLE video_comments DISABLE ROW LEVEL SECURITY;
GRANT ALL ON video_comments TO anon, authenticated;
