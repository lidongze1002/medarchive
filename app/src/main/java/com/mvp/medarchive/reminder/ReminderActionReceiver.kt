package com.mvp.medarchive.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.mvp.medarchive.MedArchiveApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 通知上的「已服用 / 稍后提醒」按钮。 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderReceiver.EXTRA_ID, -1L)
        val memberId = intent.getLongExtra(ReminderReceiver.EXTRA_MEMBER_ID, -1L)
        val time = intent.getStringExtra(ReminderReceiver.EXTRA_TIME).orEmpty()
        val drug = intent.getStringExtra(ReminderReceiver.EXTRA_DRUG).orEmpty()
        val dosage = intent.getStringExtra(ReminderReceiver.EXTRA_DOSAGE).orEmpty()
        if (reminderId <= 0) return

        when (intent.action) {
            ACTION_TAKEN -> {
                val app = context.applicationContext as? MedArchiveApp
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        if (app != null && memberId > 0) {
                            app.container.repository.markDoseTaken(
                                reminderId = reminderId,
                                memberId = memberId,
                                time = time,
                                day = LocalDate.now().toEpochDay()
                            )
                        }
                    }
                    runCatching {
                        NotificationManagerCompat.from(context).cancel(reminderId.toInt())
                    }
                    pending.finish()
                }
            }

            ACTION_SNOOZE -> {
                ReminderScheduler.snooze(context, reminderId, memberId, drug, dosage, time, minutes = 10)
                runCatching {
                    NotificationManagerCompat.from(context).cancel(reminderId.toInt())
                }
            }
        }
    }

    companion object {
        const val ACTION_TAKEN = "com.mvp.medarchive.action.DOSE_TAKEN"
        const val ACTION_SNOOZE = "com.mvp.medarchive.action.DOSE_SNOOZE"
    }
}
