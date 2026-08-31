package com.example.redbook.ui.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 会话列表/消息列表通用时间显示:
 * 今天 -> HH:mm
 * 昨天 -> 昨天
 * 前天 -> 前天
 * 3~7 天前 -> N天前
 * 1~4 周前 -> N周前
 * 1 月前 ~ 1 年前 -> N月前
 * 超过 1 年 -> yyyy-MM-dd
 */
fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()

    // 当天
    if (isSameDay(cal, now)) {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
    }
    // 昨天
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (isSameDay(cal, yesterday)) return "昨天"
    // 前天
    val dayBefore = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }
    if (isSameDay(cal, dayBefore)) return "前天"

    // 按天/周/月跨度
    val days = daysBetween(cal, now)
    if (days < 7) return "${days}天前"
    val weeks = days / 7
    if (weeks < 5) return "${weeks}周前"
    val months = monthsBetween(cal, now)
    if (months < 12) return "${months}月前"

    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
}

/** 聊天页时间条:同一时刻内只显示一次;今天显示 HH:mm,更早走 formatRelativeTime 的完整逻辑 */
fun formatChatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    if (isSameDay(cal, now)) {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
    }
    return formatRelativeTime(timestamp)
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun daysBetween(a: Calendar, b: Calendar): Long {
    val aDay = a.clone() as Calendar
    aDay.set(Calendar.HOUR_OF_DAY, 0); aDay.set(Calendar.MINUTE, 0)
    aDay.set(Calendar.SECOND, 0); aDay.set(Calendar.MILLISECOND, 0)
    val bDay = b.clone() as Calendar
    bDay.set(Calendar.HOUR_OF_DAY, 0); bDay.set(Calendar.MINUTE, 0)
    bDay.set(Calendar.SECOND, 0); bDay.set(Calendar.MILLISECOND, 0)
    return (bDay.timeInMillis - aDay.timeInMillis) / 86400000L
}

private fun monthsBetween(a: Calendar, b: Calendar): Long {
    var months = (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 12L +
        (b.get(Calendar.MONTH) - a.get(Calendar.MONTH))
    return if (months < 1) 1 else months
}
