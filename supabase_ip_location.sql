-- ============================================================
-- IP 归属地(省份)展示功能：users 表补充 ip_location 列
-- posts / comments 表已有 ip_location 列(代码已读写)，无需改动
-- 在 Supabase SQL Editor 中执行一次即可
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS ip_location TEXT NOT NULL DEFAULT '';
