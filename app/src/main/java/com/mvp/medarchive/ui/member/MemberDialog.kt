package com.mvp.medarchive.ui.member

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.data.db.Member

@Composable
fun MemberDialog(
    initial: Member? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, birthdate: String, bloodType: String, allergies: String, notes: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var birthdate by remember { mutableStateOf(initial?.birthdate ?: "") }
    var blood by remember { mutableStateOf(initial?.bloodType ?: "") }
    var allergies by remember { mutableStateOf(initial?.allergies ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加成员" else "编辑成员") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("姓名") })
                OutlinedTextField(value = birthdate, onValueChange = { birthdate = it }, label = { Text("生日（可空，如 1990-01-01）") })
                OutlinedTextField(value = blood, onValueChange = { blood = it }, label = { Text("血型（可空）") })
                OutlinedTextField(value = allergies, onValueChange = { allergies = it }, label = { Text("过敏史（可空）") })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("备注（可空）") })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, birthdate, blood, allergies, notes) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
