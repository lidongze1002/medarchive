package com.mvp.medarchive.ui.reminder

import com.mvp.medarchive.ui.i18n.T

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.data.db.MedDose
import com.mvp.medarchive.data.db.MedReminder
import com.mvp.medarchive.reminder.ReminderScheduler
import com.mvp.medarchive.ui.common.AppCard
import java.time.LocalDate

@Composable
fun ReminderSection(
    reminders: List<MedReminder>,
    doses: List<MedDose>,
    onAdd: () -> Unit,
    onEdit: (MedReminder) -> Unit,
    onToggle: (MedReminder, Boolean) -> Unit,
    onToggleDose: (MedReminder, String) -> Unit
) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val takenToday = remember(doses, today) {
        doses.filter { it.status == "taken" && it.dateEpochDay == today.toEpochDay() }
            .map { it.reminderId to it.time }
            .toSet()
    }

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(T("用药提醒", "Medication reminders"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text(T("添加", "Add")) }
        }

        if (reminders.isEmpty()) {
            Text(T("添加后每天按设定的时间提醒吃药；点时间胶囊可以打卡。", "Daily reminders at your set times; tap a time chip to check in."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(4.dp))
            reminders.forEachIndexed { index, reminder ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onEdit(reminder) }
                        .padding(vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = reminder.enabled,
                            onCheckedChange = { onToggle(reminder, it) }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderScheduler.parseTimes(reminder.times).forEach { t ->
                            val key = formatTime(t)
                            DoseChip(
                                time = key,
                                taken = takenToday.contains(reminder.id to key),
                                onClick = { onToggleDose(reminder, key) }
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        adherenceFor(reminder, doses, today).text + " · 点时间打卡",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun DoseChip(time: String, taken: Boolean, onClick: () -> Unit) {
    val container = if (taken) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (taken) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics {
                contentDescription = if (taken) "已服用 $time，点按取消" else "标记 $time 已服用"
            }
    ) {
        Text(
            if (taken) "✓ $time" else time,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (taken) FontWeight.SemiBold else FontWeight.Medium,
            color = content
        )
    }
}

@Composable
private fun ExactAlarmHint(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val manager = ctx.getSystemService(AlarmManager::class.java) ?: return
    if (manager.canScheduleExactAlarms()) return
    Spacer(Modifier.height(8.dp))
    Text(T("提醒可能延迟：点这里允许「闹钟和提醒」", "Reminders may be late: tap to allow alarms & reminders"),
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
