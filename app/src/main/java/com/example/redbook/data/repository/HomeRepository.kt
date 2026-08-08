package com.example.redbook.data.repository

import com.example.redbook.R
import com.example.redbook.data.model.Note
import org.json.JSONArray

class HomeRepository(private val supabase: SupabaseAuthRepository) {

    suspend fun getNotes(): List<Note> {
        return try {
            val posts = supabase.getPosts()
            if (posts.length() == 0) {
                seedMockPosts()
                return try { parsePosts(supabase.getPosts()) } catch (e: Exception) { mockNotes() }
            }
            parsePosts(posts)
        } catch (e: Exception) {
            mockNotes()
        }
    }

    private fun parsePosts(posts: JSONArray): List<Note> {
        return (0 until posts.length()).map { i ->
            val p = posts.getJSONObject(i)
                    Note(
                        id = p.optString("post_id", ""),
                        title = p.optString("title", ""),
                        imageRes = R.drawable.test,
                        imageUrl = p.optString("image_url", ""),
                avatarRes = R.drawable.test,
                userName = p.optString("author_name", ""),
                likeCount = p.optInt("like_count", 0),
                isLiked = false
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
}
