package com.mvp.medarchive.ui.member

import com.mvp.medarchive.ui.i18n.T

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.data.ImageStore
import com.mvp.medarchive.data.db.Member
import com.mvp.medarchive.security.ExternalUi
import com.mvp.medarchive.ui.common.Avatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MemberDialog(
    initial: Member? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        birthdate: String,
        bloodType: String,
        allergies: String,
        notes: String,
        avatarPath: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var birthdate by remember { mutableStateOf(initial?.birthdate ?: "") }
    var blood by remember { mutableStateOf(initial?.bloodType ?: "") }
    var allergies by remember { mutableStateOf(initial?.allergies ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var avatarPath by remember { mutableStateOf(initial?.avatarPath ?: "") }
    var uploading by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploading = true
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            ImageStore(ctx).save(input.readBytes())
                        }
                    }.getOrNull()
                }
                if (saved != null) avatarPath = saved
                uploading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) T("添加成员", "Add member") else T("编辑成员", "Edit member")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.clickable {
                        ExternalUi.launch { pickAvatar.launch("image/*") }
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(name = name, path = avatarPath, size = 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            when {
                                uploading -> "正在读取…"
                                avatarPath.isBlank() -> "选择头像"
                                else -> "更换头像"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(T("不设则显示姓名首字", "Uses the first letter if empty"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(T("姓名", "Name")) })
                OutlinedTextField(value = birthdate, onValueChange = { birthdate = it }, label = { Text(T("生日（可空，如 1990-01-01）", "Birthday (optional, e.g. 1990-01-01)")) })
                OutlinedTextField(value = blood, onValueChange = { blood = it }, label = { Text(T("血型（可空）", "Blood type (optional)")) })
                OutlinedTextField(value = allergies, onValueChange = { allergies = it }, label = { Text(T("过敏史（可空）", "Allergies (optional)")) })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(T("备注（可空）", "Notes (optional)")) })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, birthdate, blood, allergies, notes, avatarPath) }) {
                Text(T("保存", "Save"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(T("取消", "Cancel")) } }
    )
}
