package com.mvp.medarchive.ui.memberdetail

import android.content.Intent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvp.medarchive.billing.ProDialog
import com.mvp.medarchive.billing.ProManager
import com.mvp.medarchive.data.db.MedReminder
import com.mvp.medarchive.data.db.Member
import com.mvp.medarchive.data.db.Record
import com.mvp.medarchive.ui.ageFromBirthdate
import com.mvp.medarchive.ui.common.AppCard
import com.mvp.medarchive.ui.common.AppTopBar
import com.mvp.medarchive.ui.common.Avatar
import com.mvp.medarchive.ui.common.MonthHeader
import com.mvp.medarchive.ui.common.RecordCard
import com.mvp.medarchive.ui.member.MemberDialog
import com.mvp.medarchive.ui.common.staggeredAppear
import com.mvp.medarchive.ui.monthLabel
import com.mvp.medarchive.ui.reminder.ReminderDialog
import com.mvp.medarchive.ui.reminder.ReminderSection
import com.mvp.medarchive.ui.vitals.VitalsCard

private val RECORD_TYPES = listOf("病历", "检查单", "处方", "用药", "疫苗", "其他")

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun MemberDetailScreen(
    vm: MemberDetailViewModel,
    onBack: () -> Unit,
    onAddRecord: (Long) -> Unit,
    onOpenRecord: (Long) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val member by vm.member.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val shareUri by vm.shareUri.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var filterType by remember { mutableStateOf<String?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    var showPro by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<MedReminder?>(null) }

    val notificationsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    fun askNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationsLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val titleModifier = member?.let { m ->
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState("member_name_${m.id}"),
                animatedVisibilityScope
            )
        }
    } ?: Modifier

    val uri = shareUri
    if (uri != null) {
        LaunchedEffect(uri) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            com.mvp.medarchive.security.ExternalUi.launch {
                ctx.startActivity(Intent.createChooser(intent, "分享 PDF"))
            }
            vm.consumeShareUri()
        }
    }

    val shown = remember(records, filterType) {
        if (filterType == null) records else records.filter { it.type == filterType }
    }
    val grouped = remember(shown) {
        shown.groupBy { monthLabel(it.dateEpochDay) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppTopBar(
                title = member?.name ?: "档案",
                titleModifier = titleModifier,
                onBack = onBack,
                actions = {
                    TextButton(onClick = vm::export, enabled = !exporting) {
                        Text(if (exporting) "导出中…" else "导出 PDF")
                    }
                    IconButton(onClick = { showSheet = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!ProManager(ctx).isPro() && records.size >= ProManager.FREE_RECORD_LIMIT_PER_MEMBER) {
                        showPro = true
                    } else {
                        member?.let { onAddRecord(it.id) }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Text("+ 添加记录") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            member?.let { m ->
                item(key = "profile") {
                    ProfileCard(m)
                }
                if (m.allergies.isNotBlank()) {
                    item(key = "allergy") {
                        AllergyBanner(m.allergies)
                    }
                }
                val hasInfo = m.birthdate.isNotBlank() || m.bloodType.isNotBlank() || m.notes.isNotBlank()
                if (hasInfo) {
                    item(key = "info") {
                        InfoCard(m)
                    }
                }
            }

            if (records.isNotEmpty()) {
                item(key = "vitals") {
                    VitalsCard(records)
                }
            }

            item(key = "reminders") {
                ReminderSection(
                    reminders = reminders,
                    onAdd = {
                        askNotificationPermission()
                        editingReminder = null
                        showReminderDialog = true
                    },
                    onEdit = { reminder ->
                        editingReminder = reminder
                        showReminderDialog = true
                    },
                    onToggle = { reminder, enabled -> vm.setReminderEnabled(reminder, enabled) }
                )
            }

            item(key = "section") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "就诊记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "共 ${shown.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "filters") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterType == null,
                        onClick = { filterType = null },
                        label = { Text("全部") }
                    )
                    RECORD_TYPES.forEach { t ->
                        FilterChip(
                            selected = filterType == t,
                            onClick = { filterType = if (filterType == t) null else t },
                            label = { Text(t) }
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                item(key = "empty") {
                    EmptyHint(hasAny = records.isNotEmpty())
                }
            } else {
                grouped.forEach { (month, list) ->
                    item(key = "month_$month") {
                        MonthHeader(month, list.size)
                    }
                    items(list, key = { it.id }) { record ->
                        SwipeRecordRow(
                            record = record,
                            onClick = { onOpenRecord(record.id) },
                            onRequestDelete = { pendingDeleteId = record.id },
                            modifier = Modifier.staggeredAppear(0)
                        )
                    }
                }
            }
        }
    }

    if (showEdit) {
        MemberDialog(
            initial = member,
            onDismiss = { showEdit = false },
            onConfirm = { n, b, bt, al, notes, avatar ->
                vm.updateMember(n, b, bt, al, notes, avatar)
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
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "更多操作",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showSheet = false
                            showEdit = true
                        }
                        .height(52.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("编辑资料", style = MaterialTheme.typography.bodyLarge)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            showSheet = false
                            showDelete = true
                        }
                        .height(52.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "删除成员",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除记录") },
            text = { Text("确定删除这条记录？照片也会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteId?.let { vm.deleteRecord(it) }
                    pendingDeleteId = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("取消") } }
        )
    }
    if (showPro) {
        ProDialog(onDismiss = { showPro = false })
    }
    if (showReminderDialog) {
        val editing = editingReminder
        ReminderDialog(
            initial = editing,
            onDismiss = { showReminderDialog = false },
            onSave = { drug, dosage, times, enabled ->
                vm.saveReminder(editing, drug, dosage, times, enabled)
                showReminderDialog = false
            },
            onDelete = editing?.let { r ->
                {
                    vm.deleteReminder(r)
                    showReminderDialog = false
                }
            }
        )
    }
}

@Composable
private fun ProfileCard(m: Member) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.name, m.avatarPath, 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                val age = ageFromBirthdate(m.birthdate)
                val meta = listOfNotNull(
                    age?.let { "$it 岁" },
                    m.bloodType.takeIf { it.isNotBlank() }?.let { "血型 $it" }
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AllergyBanner(allergies: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = "过敏警示",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "过敏史",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                allergies,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun InfoCard(m: Member) {
    AppCard {
        if (m.birthdate.isNotBlank()) {
            val age = ageFromBirthdate(m.birthdate)
            InfoLine("生日", if (age != null) "${m.birthdate}（$age 岁）" else m.birthdate)
        }
        if (m.bloodType.isNotBlank()) InfoLine("血型", m.bloodType)
        if (m.notes.isNotBlank()) InfoLine("备注", m.notes)
    }
}

@Composable
private fun EmptyHint(hasAny: Boolean) {
    AppCard {
        Text(
            if (hasAny) "这个类型下还没有记录" else "还没有记录",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (hasAny) "换个类型看看，或点右下角添加" else "点右下角「+ 添加记录」，先把最近一次就诊存进来",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwipeRecordRow(
    record: Record,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) onRequestDelete()
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除记录",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        modifier = modifier
    ) {
        RecordCard(record = record, onClick = onClick)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
