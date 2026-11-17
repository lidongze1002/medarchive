package com.mvp.medarchive.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 显示偏好：目前只有「大字号」，用来方便长辈阅读。
 * 用 Compose State 保存，切换后整个 App 立即重排。
 */
object DisplayPrefs {
    private const val PREF = "medarchive_display"
    private const val KEY_LARGE_TEXT = "large_text"

    var largeText: Boolean by mutableStateOf(false)
        private set

    fun load(context: Context) {
        largeText = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_LARGE_TEXT, false)
    }

    fun setLargeText(context: Context, value: Boolean) {
        largeText = value
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LARGE_TEXT, value)
            .apply()
    }

    fun fontScale(): Float = if (largeText) 1.16f else 1f
}
