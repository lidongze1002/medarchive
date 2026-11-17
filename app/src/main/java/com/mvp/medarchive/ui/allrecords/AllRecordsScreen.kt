package com.mvp.medarchive.ui.allrecords

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvp.medarchive.ui.common.AppTopBar
import com.mvp.medarchive.ui.common.MonthHeader
import com.mvp.medarchive.ui.common.RecordCard
import com.mvp.medarchive.ui.common.SearchBar
import com.mvp.medarchive.ui.common.staggeredAppear
import com.mvp.medarchive.ui.monthLabel
import kotlinx.coroutines.delay

private val FILTER_TYPES = listOf("病历", "检查单", "处方", "用药", "疫苗")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllRecordsScreen(
    vm: AllRecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (recordId: Long, memberId: Long) -> Unit
) {
    val all by vm.items.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        delay(200)
        debounced = query
    }

    val shown = remember(all, debounced, filterType) {
        all.filter { item ->
            (filterType == null || item.record.type == filterType) &&
                (debounced.isBlank() ||
                    item.record.title.contains(debounced, ignoreCase = true) ||
                    item.record.hospital.contains(debounced, ignoreCase = true) ||
                    item.record.notes.contains(debounced, ignoreCase = true) ||
                    item.memberName.contains(debounced, ignoreCase = true))
        }
    }
    val grouped = remember(shown) { shown.groupBy { monthLabel(it.record.dateEpochDay) } }

    Scaffold(
        containerColor = Color.Transparent,
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
                    FILTER_TYPES.forEach { t ->
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (month, list) ->
                        item(key = "month_$month") {
                            MonthHeader(month, list.size)
                        }
                        items(list, key = { it.record.id }) { item ->
                            RecordCard(
                                record = item.record,
                                memberName = item.memberName,
                                onClick = { onOpenRecord(item.record.id, item.record.memberId) },
                                modifier = Modifier.staggeredAppear(0)
                            )
                        }
                    }
                }
            }
        }
    }
}
