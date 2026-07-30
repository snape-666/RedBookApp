package com.example.redbook.ui.utils

import java.util.Locale

fun formatCount(count: Int): String {
    return when {
        count < 10000 -> count.toString()
        count < 100000000 -> {
            val wan = count / 10000.0
            formatWithUnit(wan, "万")
        }
        else -> {
            val yi = count / 100000000.0
            formatWithUnit(yi, "亿")
        }
    }
}

private fun formatWithUnit(value: Double, unit: String): String {
    val rounded = (value * 10).toInt() / 10.0
    return if (rounded % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", rounded) + " " + unit
    } else {
        String.format(Locale.US, "%.1f", rounded) + " " + unit
    }
}