-- ============================================================
-- RedBookApp RLS 安全加固迁移
-- 在 Supabase SQL Editor 中整段执行一次；脚本幂等，可重复执行。
--
-- 解决的问题：
--   App 之前所有请求只带 apikey、不带用户 JWT，一旦开启 RLS，
--   读会被策略过滤成空数组(帖子列表空白)、写会被直接拒绝。
--
-- 本脚本配合客户端改动一起生效：
--   1. 各业务表开启 RLS，策略全部基于 auth.uid()，客户端自己传的 uid 不再被信任
--   2. Storage(post-images) 保持公开读，写入收紧为「登录用户 + 只能动自己 uid 目录」
--   3. 需要跨用户 / 未登录访问的写操作改为 SECURITY DEFINER 函数
--   4. App 端不再需要 service_role 密钥（APK 里不再内置万能钥匙）
--
-- 执行后请务必跑一遍文末的「验证」部分。
-- ============================================================

create extension if not exists pgcrypto;

-- 策略与计数器函数会用到这些列，若历史库里缺失则补上（幂等）
alter table public.posts    add column if not exists visibility     text not null default 'public';
alter table public.posts    add column if not exists like_count     int default 0;
alter table public.posts    add column if not exists favorite_count int default 0;
alter table public.posts    add column if not exists view_count     int default 0;
alter table public.comments add column if not exists like_count     int default 0;

-- 桶保持公开读：帖子图片本身是公开内容；
-- 若改为私有就得全 App 换成会过期的签名 URL，收益远小于代价。
-- 真正需要收紧的是写入权限，见下方 storage 策略。
update storage.buckets set public = true where id = 'post-images';


-- ------------------------------------------------------------
-- 0. 先清掉这些表与 storage.objects 上的既有策略
--    （覆盖之前手工改过的那批，避免新旧策略互相冲突）
-- ------------------------------------------------------------
do $$
declare
  t text;
  p record;
begin
  foreach t in array array[
    'users', 'posts', 'comments', 'likes', 'favorites', 'comment_likes',
    'follows', 'remarks', 'browsing_history', 'drafts',
    'notifications', 'conversations', 'messages'
  ]
  loop
    if exists (
      select 1 from information_schema.tables
      where table_schema = 'public' and table_name = t
    ) then
      for p in select policyname from pg_policies where schemaname = 'public' and tablename = t
      loop
        execute format('drop policy if exists %I on public.%I', p.policyname, t);
      end loop;
      execute format('alter table public.%I enable row level security', t);
    end if;
  end loop;

  for p in select policyname from pg_policies where schemaname = 'storage' and tablename = 'objects'
  loop
    execute format('drop policy if exists %I on storage.objects', p.policyname);
  end loop;
end $$;


-- ------------------------------------------------------------
-- 1. 表策略：全部授予 authenticated，全部基于 auth.uid()
-- ------------------------------------------------------------

-- 用户表：资料所有人可读（头像/昵称/隐私开关都要被别人看到），只能改自己那一行
alter table public.users enable row level security;
create policy users_select on public.users
  for select to authenticated using (true);
create policy users_insert_self on public.users
  for insert to authenticated with check (uid = auth.uid()::text);
create policy users_update_self on public.users
  for update to authenticated
  using (uid = auth.uid()::text) with check (uid = auth.uid()::text);

-- 帖子：公开帖所有人可见，"仅自己可见"由服务端强制（不再是客户端过滤）
alter table public.posts enable row level security;
create policy posts_select on public.posts
  for select to authenticated
  using (coalesce(visibility, 'public') <> 'private' or author_uid = auth.uid()::text);
create policy posts_insert_own on public.posts
  for insert to authenticated with check (author_uid = auth.uid()::text);
create policy posts_update_own on public.posts
  for update to authenticated
  using (author_uid = auth.uid()::text) with check (author_uid = auth.uid()::text);
create policy posts_delete_own on public.posts
  for delete to authenticated using (author_uid = auth.uid()::text);

-- 评论/回复：所有人可读，但只能写自己名下。
-- 小助手的回复改由 Edge Function 用 service_role 写入，因此这里不再需要
-- 为 'ai_assistant' 留白名单 —— 客户端已不具备冒充小助手的能力。
alter table public.comments enable row level security;
create policy comments_select on public.comments
  for select to authenticated using (true);
create policy comments_insert on public.comments
  for insert to authenticated
  with check (author_uid = auth.uid()::text);
create policy comments_update_own on public.comments
  for update to authenticated
  using (author_uid = auth.uid()::text) with check (author_uid = auth.uid()::text);
create policy comments_delete_own on public.comments
  for delete to authenticated using (author_uid = auth.uid()::text);

-- 点赞/收藏：需要读别人的（他人主页「赞过」Tab），但只能写自己的
alter table public.likes enable row level security;
create policy likes_select on public.likes
  for select to authenticated using (true);
create policy likes_insert_own on public.likes
  for insert to authenticated with check (user_uid = auth.uid()::text);
create policy likes_delete_own on public.likes
  for delete to authenticated using (user_uid = auth.uid()::text);

alter table public.favorites enable row level security;
create policy favorites_select on public.favorites
  for select to authenticated using (true);
create policy favorites_insert_own on public.favorites
  for insert to authenticated with check (user_uid = auth.uid()::text);
create policy favorites_delete_own on public.favorites
  for delete to authenticated using (user_uid = auth.uid()::text);

-- 评论点赞：只涉及自己的态，完全私有
alter table public.comment_likes enable row level security;
create policy comment_likes_own on public.comment_likes
  for all to authenticated
  using (user_uid = auth.uid()::text) with check (user_uid = auth.uid()::text);

-- 关注关系：关注/粉丝列表是公开的，但只能增删自己的关注
alter table public.follows enable row level security;
create policy follows_select on public.follows
  for select to authenticated using (true);
create policy follows_insert_own on public.follows
  for insert to authenticated with check (follower_uid = auth.uid()::text);
create policy follows_delete_own on public.follows
  for delete to authenticated using (follower_uid = auth.uid()::text);

-- 备注名：纯私有，只有自己能看能改
alter table public.remarks enable row level security;
create policy remarks_own on public.remarks
  for all to authenticated
  using (viewer_uid = auth.uid()::text) with check (viewer_uid = auth.uid()::text);

-- 浏览记录：纯私有
alter table public.browsing_history enable row level security;
create policy browsing_history_own on public.browsing_history
  for all to authenticated
  using (user_uid = auth.uid()::text) with check (user_uid = auth.uid()::text);

-- 草稿箱：纯私有
alter table public.drafts enable row level security;
create policy drafts_own on public.drafts
  for all to authenticated
  using (author_uid = auth.uid()::text) with check (author_uid = auth.uid()::text);

-- 通知：只有接收者能看和标记已读；插入时必须是"我"触发的互动
alter table public.notifications enable row level security;
create policy notifications_select_recipient on public.notifications
  for select to authenticated using (recipient_uid = auth.uid()::text);
create policy notifications_insert_actor on public.notifications
  for insert to authenticated with check (actor_uid = auth.uid()::text);
create policy notifications_update_recipient on public.notifications
  for update to authenticated
  using (recipient_uid = auth.uid()::text) with check (recipient_uid = auth.uid()::text);

-- 会话：只有参与者可见可改
alter table public.conversations enable row level security;
create policy conversations_select_participant on public.conversations
  for select to authenticated
  using (user_a_uid = auth.uid()::text or user_b_uid = auth.uid()::text);
create policy conversations_insert_participant on public.conversations
  for insert to authenticated
  with check (user_a_uid = auth.uid()::text or user_b_uid = auth.uid()::text);
create policy conversations_update_participant on public.conversations
  for update to authenticated
  using (user_a_uid = auth.uid()::text or user_b_uid = auth.uid()::text)
  with check (user_a_uid = auth.uid()::text or user_b_uid = auth.uid()::text);

-- 私信：收发双方可见；只能以自己身份发，只能标记发给自己的为已读
alter table public.messages enable row level security;
create policy messages_select_participant on public.messages
  for select to authenticated
  using (sender_uid = auth.uid()::text or receiver_uid = auth.uid()::text);
create policy messages_insert_sender on public.messages
  for insert to authenticated with check (sender_uid = auth.uid()::text);
create policy messages_update_receiver on public.messages
  for update to authenticated
  using (receiver_uid = auth.uid()::text) with check (receiver_uid = auth.uid()::text);


-- 旧版视频表（如果项目里还在用）：同样规则
do $$
begin
  if exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'video_notes') then
    execute 'alter table public.video_notes enable row level security';
    execute $p$create policy video_notes_select on public.video_notes for select to authenticated using (true)$p$;
    execute $p$create policy video_notes_insert_own on public.video_notes for insert to authenticated with check (author_uid = auth.uid()::text)$p$;
    execute $p$create policy video_notes_update_own on public.video_notes for update to authenticated using (author_uid = auth.uid()::text) with check (author_uid = auth.uid()::text)$p$;
    execute $p$create policy video_notes_delete_own on public.video_notes for delete to authenticated using (author_uid = auth.uid()::text)$p$;
  end if;

  if exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = 'video_comments') then
    execute 'alter table public.video_comments enable row level security';
    execute $p$create policy video_comments_select on public.video_comments for select to authenticated using (true)$p$;
    execute $p$create policy video_comments_insert_own on public.video_comments for insert to authenticated with check (author_uid = auth.uid()::text)$p$;
    execute $p$create policy video_comments_update_own on public.video_comments for update to authenticated using (author_uid = auth.uid()::text) with check (author_uid = auth.uid()::text)$p$;
    execute $p$create policy video_comments_delete_own on public.video_comments for delete to authenticated using (author_uid = auth.uid()::text)$p$;
  end if;
end $$;


-- ------------------------------------------------------------
-- 2. Storage 策略
--    读：桶内对象公开（图片链接仍可直接访问，无需签名）
--    写：必须是登录用户，且对象路径首段等于 auth.uid()
--        App 端上传路径已改为 "<uid>/img_xxx.jpg"
-- ------------------------------------------------------------
create policy post_images_read on storage.objects
  for select to public
  using (bucket_id = 'post-images');

create policy post_images_insert_own on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'post-images'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

create policy post_images_update_own on storage.objects
  for update to authenticated
  using (
    bucket_id = 'post-images'
    and (storage.foldername(name))[1] = auth.uid()::text
  )
  with check (
    bucket_id = 'post-images'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

create policy post_images_delete_own on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'post-images'
    and (storage.foldername(name))[1] = auth.uid()::text
  );


-- ------------------------------------------------------------
-- 3. 密码重置用的私有表
--    开启 RLS 且不建任何 policy：客户端(包括登录用户)完全接触不到。
--    读写它的只有 reset-password 这个 Edge Function，用 service_role(绕过 RLS)。
--    注意：验证码现在由 Supabase Auth 生成并校验(存在 auth 内部表)，本表不再存哈希，
--    只借用 email + updated_at 记「同一邮箱最近一次发送时间」做 60 秒冷却；
--    其余列属历史遗留，为满足 NOT NULL 填占位值。
-- ------------------------------------------------------------
create table if not exists public.password_resets (
  email      text primary key,
  code_hash  text   not null,
  expires_at bigint not null,
  attempts   int    not null default 0,
  updated_at bigint not null
);
alter table public.password_resets enable row level security;
revoke all on public.password_resets from anon, authenticated;
grant all on public.password_resets to service_role;

-- 旧的验证码列放在 users 表里是匿名可读的，等于账号接管入口，一并移除
alter table public.users drop column if exists reset_code;
alter table public.users drop column if exists reset_code_expiry;


-- ------------------------------------------------------------
-- 4. 登录前需要的查询：注册查重与小红书号生成必须绕过 RLS，
--    用 SECURITY DEFINER 函数暴露最少信息。
--
--    ⚠️ 曾经的 resolve_login_email(账号名 → 邮箱) 已删除：
--       它匿名可调且回传邮箱地址，等于任何人都能拿账号名换出别人的邮箱。
--       「账号名 → 邮箱」这一步现在收在 Edge Function(login) 内部，邮箱不再外泄。
-- ------------------------------------------------------------
drop function if exists public.resolve_login_email(text);

create or replace function public.account_exists(p_account text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
begin
  return jsonb_build_object(
    'exists', exists(select 1 from public.users where account = p_account)
  );
end $$;

create or replace function public.next_xhs_id()
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
  v_id text;
begin
  loop
    v_id := lpad((floor(random() * 1000000000))::bigint::text, 9, '0');
    exit when not exists (select 1 from public.users where xhs_id = v_id);
  end loop;
  return jsonb_build_object('xhs_id', v_id);
end $$;

revoke all on function public.account_exists(text) from public;
revoke all on function public.next_xhs_id() from public;
grant execute on function public.account_exists(text) to anon, authenticated;
grant execute on function public.next_xhs_id() to anon, authenticated;


-- ------------------------------------------------------------
-- 5. 密码重置：函数已迁到 Edge Function(reset-password)，这里只做清理
--
-- ⚠️ 历史提醒：曾经存在过 request_password_reset / reset_password_with_code
--    两个 anon 可执行的 RPC，它们让「验证码由客户端提供」，任何人都能
--    自己指定一个码再立刻用它改掉任意已知邮箱的密码 —— 完整的账号接管漏洞。
--    下面显式 drop 掉，确保无论你是否执行过旧版本脚本，这两个入口都不复存在。
-- ------------------------------------------------------------
drop function if exists public.request_password_reset(text, text);
drop function if exists public.reset_password_with_code(text, text, text);


-- ------------------------------------------------------------
-- 6. 计数器 RPC
--
--    ⚠️ 曾经的 bump_post_stats / bump_comment_like 接受客户端传入的 delta，
--       登录用户可以一次调用把任意帖子的赞数改成 999999。
--
--    现在改为「按关系表重算」：计数完全由 likes / favorites / comment_likes
--    的行数推导，客户端只能通过增删自己的那一条关系来影响它 ——
--    而那张表有 RLS 与唯一约束保护，一个人对一个帖子只能点一次赞。
--
--    重算是幂等的，所以即使历史上计数与关系表存在偏差，下一次点赞即自动纠正。
--    浏览量没有对应的关系表，只能保留 +1 语义（不再接受客户端指定增量）。
-- ------------------------------------------------------------

drop function if exists public.bump_post_stats(text, int, int, int);
drop function if exists public.bump_comment_like(text, int);

-- 点赞数 + 收藏数重算：客户端在 记录/取消 点赞或收藏后调用
create or replace function public.sync_post_counts(p_post_id text)
returns void
language plpgsql security definer set search_path = public
as $$
begin
  update public.posts p
     set like_count     = (select count(*) from public.likes l where l.post_id = p.post_id),
         favorite_count = (select count(*) from public.favorites f where f.post_id = p.post_id)
   where p.post_id = p_post_id;
end $$;

-- 评论点赞数重算
create or replace function public.sync_comment_like_count(p_comment_id text)
returns void
language plpgsql security definer set search_path = public
as $$
begin
  update public.comments
     set like_count = (select count(*) from public.comment_likes c where c.comment_id = p_comment_id)
   where comment_id = p_comment_id;
end $$;

-- 浏览量 +1：没有关系表可依据，只能固定加一
create or replace function public.increment_post_view(p_post_id text)
returns void
language plpgsql security definer set search_path = public
as $$
begin
  update public.posts
     set view_count = coalesce(view_count, 0) + 1
   where post_id = p_post_id;
end $$;

revoke all on function public.sync_post_counts(text) from public;
revoke all on function public.sync_comment_like_count(text) from public;
revoke all on function public.increment_post_view(text) from public;
grant execute on function public.sync_post_counts(text) to authenticated;
grant execute on function public.sync_comment_like_count(text) to authenticated;
grant execute on function public.increment_post_view(text) to authenticated;


-- ============================================================
-- 验证（执行完请逐条跑一遍）
-- ============================================================

-- ① 策略是否都建上了（应能看到各表的 select/insert/update/delete 以及 4 条 storage 策略）
-- select schemaname, tablename, policyname, cmd, roles
--   from pg_policies
--  where schemaname in ('public', 'storage')
--  order by schemaname, tablename, cmd;

-- ② 登录前查询函数是否可用
-- select public.next_xhs_id();                                -- 应返回 {"xhs_id": "........."}
-- select public.account_exists('不存在的账号');                 -- 应返回 {"exists": false}

-- ③ 确认五个「已被废弃的危险入口」都不存在了（本脚本的核心验收点之一）
--    select proname from pg_proc where proname in
--      ('request_password_reset',      -- 账号接管
--       'reset_password_with_code',    -- 账号接管
--       'resolve_login_email',         -- 匿名换出邮箱
--       'bump_post_stats',             -- 客户端指定计数增量
--       'bump_comment_like');
--    期望：0 行

-- ④ 确认密码重置表对客户端不可见、且已授权给 service_role
-- select grantee, privilege_type from information_schema.role_table_grants
--  where table_schema='public' and table_name='password_resets';
--    期望：只有 service_role 的权限；不含 anon / authenticated

-- ⑤ 计数器函数是否为「重算」语义（不能出现任何 delta 参数）
-- select proname, pg_get_function_arguments(oid) from pg_proc
--  where proname in ('sync_post_counts','sync_comment_like_count','increment_post_view');
--    期望：参数里只有 *id，没有任何 delta/增量字样

-- ⑥ 计数重算是否与关系表一致（对某个帖子抽查，两边数字应相同）
-- select p.post_id, p.like_count,
--        (select count(*) from public.likes l where l.post_id = p.post_id) as actual_likes
--   from public.posts p order by p.created_at desc limit 5;

-- ⑦ 确认旧的验证码列已移除
-- select column_name from information_schema.columns
--  where table_schema='public' and table_name='users' and column_name like 'reset_code%';
--    期望：0 行

-- ⑧ 确认 comments 不再放行 ai_assistant（客户端不能冒充小助手）
-- select pg_get_expr(polwithcheck, polrelid) from pg_policy
--  where polname = 'comments_insert';
--    期望：表达式里不含 ai_assistant

-- 登录、密码重置与 AI 的端到端验证不在 SQL 里做，改为对 Edge Function 发请求，
-- 具体命令见 supabase/functions/README.md
