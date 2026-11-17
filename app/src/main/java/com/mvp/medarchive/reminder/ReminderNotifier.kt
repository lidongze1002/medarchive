package com.mvp.medarchive.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mvp.medarchive.MainActivity
import com.mvp.medarchive.R

object ReminderNotifier {
    const val CHANNEL_ID = "med_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "用药提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "每天按设定时间提醒吃药"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        id: Long,
        memberId: Long,
        drug: String,
        dosage: String,
        time: String
    ) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val takenAction = actionPending(
            context, ReminderActionReceiver.ACTION_TAKEN, id, memberId, drug, dosage, time,
            (id * 10 + 1).toInt()
        )
        val snoozeAction = actionPending(
            context, ReminderActionReceiver.ACTION_SNOOZE, id, memberId, drug, dosage, time,
            (id * 10 + 2).toInt()
        )
        val line = listOfNotNull(
            dosage.takeIf { it.isNotBlank() },
            "该吃药了 · $time"
        ).joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("用药提醒：$drug")
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(0, "已服用", takenAction)
            .addAction(0, "10 分钟后再提醒", snoozeAction)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(id.toInt(), notification)
        }
    }

    private fun actionPending(
        context: Context,
        action: String,
        id: Long,
        memberId: Long,
        drug: String,
        dosage: String,
        time: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderReceiver.EXTRA_ID, id)
            putExtra(ReminderReceiver.EXTRA_MEMBER_ID, memberId)
            putExtra(ReminderReceiver.EXTRA_DRUG, drug)
            putExtra(ReminderReceiver.EXTRA_DOSAGE, dosage)
            putExtra(ReminderReceiver.EXTRA_TIME, time)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
