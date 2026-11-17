package com.mvp.medarchive.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mvp.medarchive.MedArchiveApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 重启、改时间/时区后，闹钟会被系统清空，这里重新排一遍。 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        val app = context.applicationContext as? MedArchiveApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                ReminderScheduler.scheduleAll(context, app.container.repository.allEnabledReminders())
            }
            pending.finish()
        }
    }
}
