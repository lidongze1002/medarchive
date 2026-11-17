package com.mvp.medarchive.ui.reminder

import com.mvp.medarchive.ui.i18n.T

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.data.db.MedReminder
import com.mvp.medarchive.reminder.ReminderScheduler
import java.time.LocalTime

/**
 * 新建/编辑用药提醒：
 * 药品名 + 剂量（可空）+ 每天多个时间点 + 启用开关。
 */
@Composable
fun ReminderDialog(
    initial: MedReminder? = null,
    onDismiss: () -> Unit,
    onSave: (drug: String, dosage: String, times: List<LocalTime>, enabled: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    var drug by remember { mutableStateOf(initial?.drug ?: "") }
    var dosage by remember { mutableStateOf(initial?.dosage ?: "") }
    var times by remember {
        mutableStateOf(
            ReminderScheduler.parseTimes(initial?.times ?: "08:00")
                .ifEmpty { listOf(LocalTime.of(8, 0)) }
        )
    }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }

    fun addTime() {
        val now = LocalTime.now()
        TimePickerDialog(
            ctx,
            { _, hour, minute ->
                val t = LocalTime.of(hour, minute)
                times = (times + t).distinct().sorted()
            },
            now.hour,
            now.minute,
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) T("添加用药提醒", "Add reminder") else T("编辑用药提醒", "Edit reminder")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = drug,
                    onValueChange = { drug = it },
                    label = { Text(T("药品名，如 缬沙坦", "Drug name, e.g. Valsartan")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text(T("剂量（可空，如 1 片 / 80mg）", "Dose (optional, e.g. 1 tablet / 80mg)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(T("每天提醒时间", "Daily reminder times"), style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    times.forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimeChip(text = formatTime(t))
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                times = times.filterNot { it == t }.ifEmpty { times }
                            }) { Text(T("移除", "Remove")) }
                        }
                    }
                    TextButton(onClick = { addTime() }) { Text(T("+ 添加时间", "+ Add time")) }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(T("启用提醒", "Enable reminder"), style = MaterialTheme.typography.bodyLarge)
                        Text(T("关闭后保留设置但不再通知", "Keeps settings but stops alerts"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(drug, dosage, times, enabled) },
                enabled = drug.isNotBlank() && times.isNotEmpty()
            ) { Text(T("保存", "Save")) }
        },
        dismissButton = {
            Row {
                if (initial != null && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(T("删除", "Delete"), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(T("取消", "Cancel")) }
            }
        }
    )
}

@Composable
private fun TimeChip(text: String) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

internal fun formatTime(t: LocalTime): String =
    "%02d:%02d".format(t.hour, t.minute)
