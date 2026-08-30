package com.example.redbook.data.repository

import com.example.redbook.R
import com.example.redbook.data.model.Note
import org.json.JSONArray

class HomeRepository(val supabase: SupabaseAuthRepository) {

    suspend fun getNotes(userUid: String = ""): List<Note> {
        return try {
            val posts = supabase.getPosts()
            val likedIds = try { supabase.getLikedPostIds(userUid) } catch (e: Exception) { emptySet() }
            if (posts.length() == 0) {
                seedMockPosts()
                return try { parsePosts(supabase.getPosts(), likedIds) } catch (e: Exception) { mockNotes() }
            }
            parsePosts(posts, likedIds)
        } catch (e: Exception) {
            mockNotes()
        }
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
                isLiked = likedIds.contains(postId)
            )
        }
    }

    private suspend fun seedMockPosts() {
        try {
            for (i in 0 until 20) {
                supabase.insertPost(
                    postId = "post_$i",
                    title = "这是第 ${i + 1} 篇超级好看的小红书风格笔记",
                    content = "这是第 ${i + 1} 篇帖子的详细内容，非常精彩值得阅读。",
                    authorUid = "user_${i % 5}",
                    authorName = "用户${i + 1}",
                    authorAvatar = ""
                )
            }
        } catch (_: Exception) { }
    }

    private fun mockNotes(): List<Note> {
        return List(20) { index ->
            Note(
                id = "post_$index",
                imageRes = if (index % 2 == 0) R.drawable.test else R.drawable.test2,
                title = "这是第 ${index + 1} 篇超级好看的小红书风格笔记，内容非常精彩！",
                avatarRes = R.drawable.test,
                userName = "用户${index + 1}",
                likeCount = (100..9999).random()
            )
        }
    }

    suspend fun getVideoNotes(): List<Note> {
        return try {
            val videos = supabase.getVideos()
            (0 until videos.length()).map { i ->
                val v = videos.getJSONObject(i)
                Note(
                    id = v.optString("video_id", ""),
                    title = v.optString("title", ""),
                    imageRes = R.drawable.test,
                    imageUrl = "video:${v.optString("video_url", "")}",
                    avatarRes = R.drawable.test,
                    userName = v.optString("author_name", ""),
                    likeCount = 0
                )
            }
        } catch (e: Exception) { emptyList() }
    }
}
