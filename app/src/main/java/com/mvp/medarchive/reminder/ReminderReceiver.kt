package com.mvp.medarchive.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mvp.medarchive.MedArchiveApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 到点后发通知，并把同一个提醒排到明天。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val drug = intent.getStringExtra(EXTRA_DRUG) ?: "用药"
        val dosage = intent.getStringExtra(EXTRA_DOSAGE).orEmpty()
        val time = intent.getStringExtra(EXTRA_TIME) ?: ""

        ReminderNotifier.show(context, id, drug, dosage, time)

        if (id <= 0) return
        val app = context.applicationContext as? MedArchiveApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                app.container.repository.findReminder(id)?.let { reminder ->
                    if (reminder.enabled) ReminderScheduler.schedule(context, reminder)
                }
            }
            pending.finish()
        }
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_DRUG = "drug"
        const val EXTRA_DOSAGE = "dosage"
        const val EXTRA_TIME = "time"
    }
}
