package com.mvp.medarchive.ui.memberlist

import com.mvp.medarchive.ui.i18n.T

import android.content.Intent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.mvp.medarchive.R
import com.mvp.medarchive.ui.ageFromBirthdate
import com.mvp.medarchive.ui.common.AppCard
import com.mvp.medarchive.ui.common.Avatar
import com.mvp.medarchive.ui.common.CountUpText
import com.mvp.medarchive.ui.common.SkeletonCard
import com.mvp.medarchive.ui.common.SearchBar
import com.mvp.medarchive.ui.common.StatusChip
import com.mvp.medarchive.ui.common.staggeredAppear
import com.mvp.medarchive.billing.ProDialog
import com.mvp.medarchive.billing.ProManager
import com.mvp.medarchive.security.LockSettingsDialog
import com.mvp.medarchive.ui.i18n.LanguageDialog
import com.mvp.medarchive.ui.i18n.Lang
import com.mvp.medarchive.ui.member.MemberDialog
import com.mvp.medarchive.ui.theme.DisplayPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MemberListScreen(
    vm: MemberListViewModel,
    onOpen: (Long) -> Unit,
    onOpenAllRecords: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val items by vm.items.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var showLock by remember { mutableStateOf(false) }
    var showPro by remember { mutableStateOf(false) }
    var showDataSheet by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val totalRecords = items.sumOf { it.recordCount }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val n = runCatching { vm.importBackup(uri) }.getOrElse { -1 }
            android.widget.Toast.makeText(
                ctx,
                if (n >= 0) T("已导入 $n 条记录", "Imported $n records")
                else T("导入失败：文件格式不正确", "Import failed: invalid file"),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    val doExport: () -> Unit = {
        scope.launch {
            val uri = vm.exportBackup()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            com.mvp.medarchive.security.ExternalUi.launch {
                ctx.startActivity(Intent.createChooser(intent, "导出备份"))
            }
        }
    }
    var query by remember { mutableStateOf("") }
    var showLanguage by remember { mutableStateOf(false) }
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(200)
        debouncedQuery = query
    }
    val filtered = if (debouncedQuery.isBlank()) items else items.filter {
        it.member.name.contains(debouncedQuery, ignoreCase = true) ||
            it.member.allergies.contains(debouncedQuery, ignoreCase = true)
    }

    LaunchedEffect(Unit) {
        delay(420)
        loading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!ProManager(ctx).isPro() && items.size >= ProManager.FREE_MEMBER_LIMIT) {
                        showPro = true
                    } else {
                        showDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(T("添加成员", "Add member"))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                        )
                    )
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(T("家庭病历本", "Family Health Records"),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            T(
                                "${items.size} 位成员 · ${totalRecords} 条记录",
                                "${items.size} member${if (items.size == 1) "" else "s"} · " +
                                    "$totalRecords record${if (totalRecords == 1) "" else "s"}"
                            ),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(T("查看全部记录 ›", "See all records ›"),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.18f))
                                .clickable(onClick = onOpenAllRecords)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    IconButton(onClick = { showLock = true }) {
                        Icon(Icons.Filled.Lock, contentDescription = T("应用锁", "App lock"), tint = Color.White)
                    }
                    IconButton(onClick = { showDataSheet = true }) {
                        Icon(Icons.Filled.Share, contentDescription = T("导出数据", "Export data"), tint = Color.White)
                    }
                }
            }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = T("搜索成员姓名或过敏史", "Search member name or allergies")
                )
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch {
                        delay(700)
                        refreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    loading -> Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        repeat(3) { SkeletonCard() }
                    }

                    items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.empty_pulse))
                            LottieAnimation(
                                composition = composition,
                                iterations = LottieConstants.IterateForever,
                                modifier = Modifier.size(120.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(T("还没有成员", "No members yet"), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(T("点右下角「添加成员」开始", "Tap “Add member” at the bottom right"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "没有匹配「$debouncedQuery」的成员",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(filtered, key = { _, item -> item.member.id }) { index, item ->
                            AppCard(
                                modifier = Modifier.staggeredAppear(index),
                                onClick = { onOpen(item.member.id) }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(item.member.name, item.member.avatarPath)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        val nameModifier = with(sharedTransitionScope) {
                                            Modifier.sharedElement(
                                                rememberSharedContentState("member_name_${item.member.id}"),
                                                animatedVisibilityScope
                                            )
                                        }
                                        Text(
                                            item.member.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = nameModifier,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val age = ageFromBirthdate(item.member.birthdate)
                                        val meta = listOfNotNull(
                                            age?.let { "$it 岁" },
                                            item.member.bloodType.takeIf { it.isNotBlank() }?.let { "血型 $it" },
                                            item.member.birthdate.takeIf { age == null && it.isNotBlank() }
                                                ?.let { "生日 $it" }
                                        ).joinToString(" · ")
                                        if (meta.isNotBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                meta,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (item.member.allergies.isNotBlank()) {
                                            Spacer(Modifier.height(3.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Filled.Warning,
                                                    contentDescription = T("过敏", "Allergy"),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    "过敏：${item.member.allergies}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.error,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CountUpText(
                                            target = item.recordCount,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(T("条", ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
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
            onConfirm = { n, b, bt, al, notes, avatar ->
                vm.add(n, b, bt, al, notes, avatar)
                showDialog = false
            }
        )
    }
    if (showLock) {
        LockSettingsDialog(onDismiss = { showLock = false })
    }
    if (showPro) {
        ProDialog(onDismiss = { showPro = false })
    }
    if (showLanguage) {
        LanguageDialog(onDismiss = { showLanguage = false })
    }
    if (showDataSheet) {
        val largeText = DisplayPrefs.largeText
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showDataSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(T("设置与备份", "Settings & backup"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showLanguage = true }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(T("语言 / Language", "Language / 语言"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when (Lang.preference) {
                                "zh" -> "中文"
                                "en" -> "English"
                                else -> T("跟随系统", "System")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            DisplayPrefs.setLargeText(ctx, !largeText)
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(T("大字号", "Large text"), style = MaterialTheme.typography.bodyLarge)
                        Text(T("字更大、行距更宽，方便长辈看", "Larger text and line spacing for older users"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = largeText,
                        onCheckedChange = { DisplayPrefs.setLargeText(ctx, it) }
                    )
                }
                Text(T("导出备份（含照片）", "Export backup (with photos)"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showDataSheet = false
                            doExport()
                        }
                        .padding(20.dp)
                )
                Text(T("导入备份（Pro）", "Import backup (Pro)"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showDataSheet = false
                            if (ProManager(ctx).isPro()) {
                                com.mvp.medarchive.security.ExternalUi.launch {
                                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                                }
                            } else {
                                showPro = true
                            }
                        }
                        .padding(20.dp)
                )
            }
        }
    }
}
