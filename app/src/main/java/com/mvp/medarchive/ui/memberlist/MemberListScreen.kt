package com.mvp.medarchive.ui.memberlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.ui.common.AppTopBar
import com.mvp.medarchive.ui.member.MemberDialog

@Composable
fun MemberListScreen(
    vm: MemberListViewModel,
    onOpen: (Long) -> Unit
) {
    val items by vm.items.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { AppTopBar(title = "家庭病历本") },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showDialog = true }) { Text("+ 添加成员") }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("还没有成员", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点右下角「+ 添加成员」开始",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
        MemberDialog(
            onDismiss = { showDialog = false },
            onConfirm = { n, b, bt, al, notes ->
                vm.add(n, b, bt, al, notes)
                showDialog = false
            }
        )
    }
}
