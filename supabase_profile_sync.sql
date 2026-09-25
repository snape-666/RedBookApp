-- ============================================================================
-- 资料改名 / 换头像后，自动把历史内容的冗余署名同步为最新值
--
-- 背景
--   posts / comments / notifications 在写入时各自冗余了一份作者署名快照
--   （posts.author_name、posts.author_avatar；comments.author_name、
--     comments.author_avatar；notifications.actor_name、actor_avatar），
--   目的是避免读时 JOIN。改了 users.nickname / users.avatar_url 后，这些旧快照
--   不会自己变，于是出现"前后发布的帖子头像用户名不统一"。
--
-- 做法
--   在 users 表上挂一个 AFTER UPDATE 触发器，昵称或头像变化时，把最新值回填到
--   该用户自己的 posts / comments / notifications。
--   触发器以 SECURITY DEFINER 运行：notifications 的 RLS 只允许"收件人"更新，
--   作者本人（actor）改不了自己的署名，只有服务端能顺带修好，这条只能靠触发器。
--
-- 使用
--   Supabase Dashboard → SQL Editor → 粘贴执行。可重复执行（幂等）。
--   执行后无需改客户端：任何一次改资料都会即时刷新全部历史内容。
-- ============================================================================

create or replace function public.sync_user_author_snapshot()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    -- 昵称和头像都没变就什么都不做（避免无谓的全表扫描）
    if new.nickname is not distinct from old.nickname
       and new.avatar_url is not distinct from old.avatar_url then
        return new;
    end if;

    -- 空值不覆盖，避免把已有的署名抹成空字符串
    update public.posts
       set author_name   = coalesce(nullif(new.nickname, ''), author_name),
           author_avatar = coalesce(nullif(new.avatar_url, ''), author_avatar)
     where author_uid = new.uid;

    update public.comments
       set author_name   = coalesce(nullif(new.nickname, ''), author_name),
           author_avatar = coalesce(nullif(new.avatar_url, ''), author_avatar)
     where author_uid = new.uid;

    update public.notifications
       set actor_name   = coalesce(nullif(new.nickname, ''), actor_name),
           actor_avatar = coalesce(nullif(new.avatar_url, ''), actor_avatar)
     where actor_uid = new.uid;

    return new;
end;
$$;

drop trigger if exists trg_sync_user_author_snapshot on public.users;
create trigger trg_sync_user_author_snapshot
after update of nickname, avatar_url on public.users
for each row
execute function public.sync_user_author_snapshot();
