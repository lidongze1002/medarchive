package com.mvp.medarchive.ui.memberdetail

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.ui.common.AppCard
import com.mvp.medarchive.ui.common.AppTopBar
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
            AppTopBar(
                title = member?.name ?: "档案",
                onBack = onBack,
                actions = {
                    TextButton(onClick = vm::export, enabled = !exporting) {
                        Text(if (exporting) "导出中…" else "导出 PDF")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { member?.let { onAddRecord(it.id) } },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Text("+ 添加记录") }
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
                val hasInfo = m.birthdate.isNotBlank() || m.bloodType.isNotBlank() || m.allergies.isNotBlank()
                AppCard {
                    if (hasInfo) {
                        if (m.birthdate.isNotBlank()) InfoLine("生日", m.birthdate)
                        if (m.bloodType.isNotBlank()) InfoLine("血型", m.bloodType)
                        if (m.allergies.isNotBlank()) InfoLine("过敏史", m.allergies, isError = true)
                    } else {
                        Text(
                            "暂无更多信息，点「编辑」补充",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showEdit = true }) { Text("编辑") }
                    TextButton(onClick = { showDelete = true }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filterType == null, onClick = { filterType = null }, label = { Text("全部") })
                val types = listOf("病历", "检查单", "处方", "用药", "疫苗", "其他")
                types.forEach { t ->
                    FilterChip(selected = filterType == t, onClick = { filterType = t }, label = { Text(t) })
                }
            }
            Spacer(Modifier.height(10.dp))
            val shown = if (filterType == null) records else records.filter { it.type == filterType }
            if (shown.isEmpty()) {
                Text(
                    "还没有记录，点右下角添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { r ->
                        AppCard(onClick = { onOpenRecord(r.id) }) {
                            Text(
                                r.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
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

@Composable
private fun InfoLine(label: String, value: String, isError: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
