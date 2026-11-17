package com.mvp.medarchive.security

import android.os.SystemClock

/**
 * 标记「即将跳转到系统界面」（相册、相机、文件选择器、分享面板、Play 购买页）。
 * 这类跳转会触发 ON_STOP，但不应该把用户重新锁在密码页外。
 *
 * 只在发起后的短窗口内有效，避免标记残留导致下一次切后台漏锁。
 */
object ExternalUi {
    private const val WINDOW_MS = 3_000L

    @Volatile
    private var until: Long = 0L

    fun <T> launch(block: () -> T): T {
        until = SystemClock.elapsedRealtime() + WINDOW_MS
        return block()
    }

    fun isRecent(): Boolean = SystemClock.elapsedRealtime() < until

    fun clear() {
        until = 0L
    }
}
