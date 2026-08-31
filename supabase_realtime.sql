-- ============================================================
-- 实时通知 + 私信表结构（在 Supabase SQL Editor 中执行）
-- ============================================================

-- 通知表（赞/收藏/评论/回复/关注 事件，App 写互动时同步插入）
CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  notif_id TEXT UNIQUE NOT NULL,
  recipient_uid TEXT NOT NULL,      -- 接收者（被赞/被评论/被关注的人）
  actor_uid TEXT NOT NULL,          -- 触发者
  actor_name TEXT NOT NULL DEFAULT '',
  actor_avatar TEXT NOT NULL DEFAULT '',
  type TEXT NOT NULL,               -- like | favorite | comment | reply | follow
  post_id TEXT NOT NULL DEFAULT '',
  post_title TEXT NOT NULL DEFAULT '',
  comment_id TEXT NOT NULL DEFAULT '',
  comment_content TEXT NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE
);
ALTER TABLE notifications DISABLE ROW LEVEL SECURITY;
GRANT ALL ON notifications TO anon, authenticated;

CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications (recipient_uid, created_at DESC);

-- 私信会话（user_a_uid 与 user_b_uid 约定按字典序小者在前）
CREATE TABLE IF NOT EXISTS conversations (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  conversation_id TEXT UNIQUE NOT NULL,
  user_a_uid TEXT NOT NULL,
  user_b_uid TEXT NOT NULL,
  last_message TEXT NOT NULL DEFAULT '',
  last_time BIGINT NOT NULL DEFAULT 0,
  UNIQUE (user_a_uid, user_b_uid)
);
ALTER TABLE conversations DISABLE ROW LEVEL SECURITY;
GRANT ALL ON conversations TO anon, authenticated;

-- 私信消息
CREATE TABLE IF NOT EXISTS messages (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  message_id TEXT UNIQUE NOT NULL,
  conversation_id TEXT NOT NULL,
  sender_uid TEXT NOT NULL,
  receiver_uid TEXT NOT NULL,
  content TEXT NOT NULL,
  media_url TEXT NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE
);
ALTER TABLE messages DISABLE ROW LEVEL SECURITY;
GRANT ALL ON messages TO anon, authenticated;

-- 为已存在的表补充 media_url 列（幂等：列已存在时会报错，可忽略）
ALTER TABLE messages ADD COLUMN IF NOT EXISTS media_url TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages (conversation_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_messages_receiver_unread ON messages (receiver_uid, is_read);

-- 启用 Realtime 发布（让 App 能实时收到 INSERT 事件）
ALTER PUBLICATION supabase_realtime ADD TABLE notifications;
ALTER PUBLICATION supabase_realtime ADD TABLE conversations;
ALTER PUBLICATION supabase_realtime ADD TABLE messages;
