package com.mvp.medarchive.ui.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.data.db.MedReminder
import com.mvp.medarchive.reminder.ReminderScheduler
import com.mvp.medarchive.ui.common.AppCard

@Composable
fun ReminderSection(
    reminders: List<MedReminder>,
    onAdd: () -> Unit,
    onEdit: (MedReminder) -> Unit,
    onToggle: (MedReminder, Boolean) -> Unit
) {
    val ctx = LocalContext.current
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "用药提醒",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text("添加") }
        }

        if (reminders.isEmpty()) {
            Text(
                "添加后每天按设定的时间提醒吃药，重启手机也会保留。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(4.dp))
            reminders.forEachIndexed { index, reminder ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onEdit(reminder) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            reminder.drug,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (reminder.dosage.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                reminder.dosage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ReminderScheduler.parseTimes(reminder.times).forEach { t ->
                                TimeBadge(formatTime(t))
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = reminder.enabled,
                        onCheckedChange = { onToggle(reminder, it) }
                    )
                }
                if (index != reminders.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        if (reminders.isNotEmpty()) {
            ExactAlarmHint(ctx)
        }
    }
}

@Composable
private fun TimeBadge(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ExactAlarmHint(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val manager = ctx.getSystemService(AlarmManager::class.java) ?: return
    if (manager.canScheduleExactAlarms()) return
    Spacer(Modifier.height(8.dp))
    Text(
        "提醒可能延迟：点这里允许「闹钟和提醒」",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.clickable {
            runCatching {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${ctx.packageName}")
                    )
                )
            }
        }
    )
}
