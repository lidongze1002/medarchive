package com.mvp.medarchive.ui.memberlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MemberListScreen(
    vm: MemberListViewModel,
    onOpen: (Long) -> Unit
) {
    val items by vm.items.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showDialog = true }) { Text("+ 添加成员") }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有成员，点右下角添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.member.id }) { item ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(item.member.id) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(item.member.name, style = MaterialTheme.typography.titleMedium)
                            if (item.member.birthdate.isNotBlank()) {
                                Text("生日：${item.member.birthdate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("记录 ${item.recordCount} 条", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.member.allergies.isNotBlank()) {
                                Text("过敏史：${item.member.allergies}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddMemberDialog(
            onDismiss = { showDialog = false },
            onConfirm = { n, b, bt, al, notes ->
                vm.add(n, b, bt, al, notes)
                showDialog = false
            }
        )
    }
}

@Composable
private fun AddMemberDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var birthdate by remember { mutableStateOf("") }
    var blood by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加成员") },
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
