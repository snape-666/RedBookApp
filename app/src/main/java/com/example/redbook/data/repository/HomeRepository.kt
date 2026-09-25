package com.example.redbook.data.repository

import com.example.redbook.R
import com.example.redbook.data.model.Note
import org.json.JSONArray

/**
 * 冷启动预热缓存。
 *
 * 冷启动时第一条请求要在全新连接上做 DNS+TCP+TLS，海外链路抖动大，容易超时或被重置，
 * 表现为"第一次进 App 首页报网络异常，手动刷新才出来"。启动页先按 uid 拉一次首页数据
 * 放进这里，进入首页直接取用，省掉那次很可能失败的首请求。只消费一次，切账号即失效。
 */
object HomeFeedCache {
    @Volatile private var uid: String = ""
    @Volatile private var notes: List<Note>? = null

    fun put(uid: String, notes: List<Note>) {
        this.uid = uid
        this.notes = notes
    }

    /** 取出并清空；uid 与当前账号不一致(切换账号)时返回 null */
    fun take(uid: String): List<Note>? {
        if (this.uid != uid) return null
        val cached = notes
        notes = null
        return cached
    }
}

class HomeRepository(val supabase: SupabaseAuthRepository) {

    suspend fun getNotes(userUid: String = ""): List<Note> {
        val posts = supabase.filterVisiblePosts(supabase.getPosts(), userUid)
        val likedIds = try { supabase.getLikedPostIds(userUid) } catch (e: Exception) { emptySet() }
        return applyRemarks(parsePosts(posts, likedIds), userUid)
    }

    /** 只返回我关注的人发布的帖子 */
    suspend fun getFollowingNotes(userUid: String): List<Note> {
        if (userUid.isBlank()) return emptyList()
        val followingUids = supabase.getFollowingUids(userUid)
        val posts = supabase.filterVisiblePosts(supabase.getPosts(), userUid)
        val likedIds = try { supabase.getLikedPostIds(userUid) } catch (e: Exception) { emptySet() }
        val notes = parsePosts(posts, likedIds).filter { it.authorUid in followingUids }
        return applyRemarks(notes, userUid)
    }

    /** 将作者名替换为我对该作者的备注（有备注优先），authorUid 为空的 mock 数据跳过 */
    private suspend fun applyRemarks(notes: List<Note>, userUid: String): List<Note> {
        if (userUid.isBlank() || notes.isEmpty()) return notes
        return try {
            val uids = notes.map { it.authorUid }.filter { it.isNotBlank() }.distinct()
            if (uids.isEmpty()) return notes
            val remarks = supabase.getRemarks(userUid, uids)
            if (remarks.isEmpty()) return notes
            notes.map { note ->
                val remark = remarks[note.authorUid]
                if (!remark.isNullOrBlank()) note.copy(userName = remark) else note
            }
        } catch (e: Exception) { notes }
    }

    private fun parsePosts(posts: JSONArray, likedIds: Set<String> = emptySet()): List<Note> {
        return (0 until posts.length()).map { i ->
            val p = posts.getJSONObject(i)
            val postId = p.optString("post_id", "")
                    Note(
                        id = postId,
                        title = p.optString("title", ""),
                        imageRes = R.drawable.test,
                        imageUrl = p.optString("image_url", ""),
                avatarRes = R.drawable.test,
                avatarUrl = p.optString("author_avatar", ""),
                userName = p.optString("author_name", ""),
                likeCount = p.optInt("like_count", 0),
                isLiked = likedIds.contains(postId),
                authorUid = p.optString("author_uid", "")
            )
        }
    }

}
