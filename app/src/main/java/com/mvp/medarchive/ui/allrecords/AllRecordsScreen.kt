package com.mvp.medarchive.ui.allrecords

import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.ui.common.AppCard
import com.mvp.medarchive.ui.common.AppTopBar
import com.mvp.medarchive.ui.common.Avatar
import com.mvp.medarchive.ui.common.SearchBar
import com.mvp.medarchive.ui.common.StatusChip
import com.mvp.medarchive.ui.common.staggeredAppear
import java.time.LocalDate

@Composable
fun AllRecordsScreen(
    vm: AllRecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (recordId: Long, memberId: Long) -> Unit
) {
    val all by vm.items.collectAsState()
    var query by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<String?>(null) }

    val shown = all.filter { item ->
        (filterType == null || item.record.type == filterType) &&
            (query.isBlank() ||
                item.record.title.contains(query, ignoreCase = true) ||
                item.record.hospital.contains(query, ignoreCase = true) ||
                item.record.notes.contains(query, ignoreCase = true) ||
                item.memberName.contains(query, ignoreCase = true))
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { AppTopBar(title = "全部记录", onBack = onBack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "搜索标题、医院、备注或成员"
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterType == null,
                        onClick = { filterType = null },
                        label = { Text("全部") }
                    )
                    listOf("病历", "检查单", "处方", "用药", "疫苗").forEach { t ->
                        FilterChip(
                            selected = filterType == t,
                            onClick = { filterType = if (filterType == t) null else t },
                            label = { Text(t) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "共 ${shown.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (all.isEmpty()) "还没有任何记录" else "没有匹配的记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(shown, key = { _, item -> item.record.id }) { index, item ->
                        AppCard(
                            modifier = Modifier.staggeredAppear(index),
                            onClick = { onOpenRecord(item.record.id, item.record.memberId) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(item.memberName, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.record.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${item.memberName} · ${formatEpochDay(item.record.dateEpochDay)}" +
                                            if (item.record.hospital.isNotBlank()) " · ${item.record.hospital}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                StatusChip(
                                    text = item.record.type,
                                    container = MaterialTheme.colorScheme.secondaryContainer,
                                    content = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatEpochDay(epochDay: Long): String =
    runCatching { LocalDate.ofEpochDay(epochDay).toString() }.getOrDefault("—")
