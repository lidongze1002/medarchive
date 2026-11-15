package com.mvp.medarchive.ui.recorddetail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.ui.formatEpochDay

@Composable
fun RecordDetailScreen(
    vm: RecordDetailViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val record by vm.record.collectAsState()
    val imagePaths by vm.imagePaths.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                TextButton(onClick = onBack) { Text("返回") }
                Text("记录详情", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(8.dp))
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            record?.let { r ->
                Text(r.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${r.type} · ${formatEpochDay(r.dateEpochDay)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (r.hospital.isNotBlank()) Text("医院：${r.hospital}", style = MaterialTheme.typography.bodyMedium)
                if (r.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(r.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (imagePaths.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("图片", style = MaterialTheme.typography.titleSmall)
                imagePaths.forEach { path -> FullImage(path) }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = { vm.delete { onDeleted() } }, modifier = Modifier.fillMaxWidth()) {
                Text("删除记录")
            }
        }
    }
}

@Composable
private fun FullImage(path: String) {
    val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
    }
}
