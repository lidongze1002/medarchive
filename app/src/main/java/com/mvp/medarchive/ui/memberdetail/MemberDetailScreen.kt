package com.mvp.medarchive.ui.memberdetail

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.ui.formatEpochDay
import com.mvp.medarchive.ui.member.MemberDialog

@Composable
fun MemberDetailScreen(
    vm: MemberDetailViewModel,
    onBack: () -> Unit,
    onAddRecord: (Long) -> Unit,
    onOpenRecord: (Long) -> Unit
) {
    val member by vm.member.collectAsState()
    val records by vm.records.collectAsState()
    val exporting by vm.exporting.collectAsState()
    val shareUri by vm.shareUri.collectAsState()
    val ctx = LocalContext.current
    var filterType by remember { mutableStateOf<String?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val uri = shareUri
    if (uri != null) {
        LaunchedEffect(uri) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "分享 PDF"))
            vm.consumeShareUri()
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text("返回") }
                    Text(member?.name ?: "档案", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = vm::export, enabled = !exporting) {
                        Text(if (exporting) "导出中…" else "导出 PDF")
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { member?.let { onAddRecord(it.id) } }) { Text("+ 添加记录") }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            member?.let { m ->
                if (m.birthdate.isNotBlank()) Text("生日：${m.birthdate}", style = MaterialTheme.typography.bodyMedium)
                if (m.bloodType.isNotBlank()) Text("血型：${m.bloodType}", style = MaterialTheme.typography.bodyMedium)
                if (m.allergies.isNotBlank()) {
                    Text("过敏史：${m.allergies}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showEdit = true }) { Text("编辑") }
                TextButton(onClick = { showDelete = true }) { Text("删除") }
            }
            Spacer(Modifier.height(8.dp))
            Text("记录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filterType == null, onClick = { filterType = null }, label = { Text("全部") })
                val types = listOf("病历", "检查单", "处方", "用药", "疫苗", "其他")
                types.forEach { t ->
                    FilterChip(selected = filterType == t, onClick = { filterType = t }, label = { Text(t) })
                }
            }
            Spacer(Modifier.height(8.dp))
            val shown = if (filterType == null) records else records.filter { it.type == filterType }
            if (shown.isEmpty()) {
                Text("还没有记录，点右下角添加", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { r ->
                        Card(Modifier.fillMaxWidth().clickable { onOpenRecord(r.id) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(r.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${r.type} · ${formatEpochDay(r.dateEpochDay)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEdit) {
        MemberDialog(
            initial = member,
            onDismiss = { showEdit = false },
            onConfirm = { n, b, bt, al, notes ->
                vm.updateMember(n, b, bt, al, notes)
                showEdit = false
            }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除成员") },
            text = { Text("确定删除「${member?.name}」？其所有记录与照片也会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.deleteMember(onBack)
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}
