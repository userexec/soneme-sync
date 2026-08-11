package com.userexec.soneme.sync

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.DocumentsContract
import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import java.text.DateFormat as JavaDateFormat

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

fun formatRunTime(time: Long?): String {
    if (time == null) return "Never run"
    return JavaDateFormat.getDateTimeInstance(JavaDateFormat.SHORT, JavaDateFormat.SHORT).format(time)
}

fun runStatusGlyph(status: RunStatus): Pair<String, Int> = when (status) {
    RunStatus.NEVER -> "" to Color.TRANSPARENT
    RunStatus.RUNNING -> "…" to Color.rgb(79, 111, 143)
    RunStatus.SUCCESS -> "✓" to Color.rgb(30, 125, 54)
    RunStatus.ERROR -> "!" to Color.rgb(180, 35, 35)
    RunStatus.CANCELED -> "×" to Color.rgb(110, 110, 110)
}

fun humanTreePath(context: Context, uri: android.net.Uri): String {
    return try {
        val id = DocumentsContract.getTreeDocumentId(uri)
        val split = id.split(':', limit = 2)
        if (split.size == 2) {
            val root = when (split[0].lowercase()) {
                "primary" -> "/storage/emulated/0"
                "home" -> "/storage/emulated/0/Documents"
                else -> "/storage/${split[0]}"
            }
            if (split[1].isBlank()) root else "$root/${split[1]}"
        } else id
    } catch (_: Exception) {
        uri.toString()
    }
}

fun setTabAppearance(context: Context, view: TextView, active: Boolean, stripFocused: Boolean) {
    view.background = GradientDrawable().apply {
        setColor(if (active) Color.rgb(79, 111, 143) else Color.rgb(217, 222, 227))
        if (active && stripFocused) setStroke(context.dp(2), Color.WHITE)
    }
    view.setTextColor(if (active) Color.WHITE else Color.rgb(26, 26, 26))
}
