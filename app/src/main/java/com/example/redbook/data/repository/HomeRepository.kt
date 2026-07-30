package com.example.redbook.data.repository

import com.example.redbook.R
import com.example.redbook.data.model.Note

class HomeRepository {
    // 模拟数据：给你 20 条假笔记
    fun getNotes(): List<Note> {
        return List(20) { index ->
            Note(
                id = index,
                imageRes = if (index % 2 == 0) R.drawable.test else R.drawable.test2, // 暂用测试图
                title = "这是第 ${index + 1} 篇超级好看的小红书风格笔记，内容非常精彩！",
                avatarRes = R.drawable.test,
                userName = "用户${index + 1}",
                likeCount = (100..9999).random()
            )
        }
    }
}