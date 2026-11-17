package com.mvp.medarchive.ui.recorddetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mvp.medarchive.ui.common.AppCard
import com.mvp.medarchive.ui.common.AppTopBar
import com.mvp.medarchive.ui.common.StatusChip
import com.mvp.medarchive.ui.formatEpochDay
import java.io.File

@Composable
fun RecordDetailScreen(
    vm: RecordDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit
) {
    val record by vm.record.collectAsState()
    val imagePaths by vm.imagePaths.collectAsState()

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = "记录详情",
            onBack = onBack,
            actions = {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                }
            }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            record?.let { r ->
                AppCard {
                    StatusChip(
                        text = r.type,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        r.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        formatEpochDay(r.dateEpochDay),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (r.hospital.isNotBlank() || r.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    AppCard {
                        if (r.hospital.isNotBlank()) LabeledText("医院", r.hospital)
                        if (r.notes.isNotBlank()) LabeledText("备注", r.notes)
                    }
                }
            }
            if (imagePaths.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "图片",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                imagePaths.forEach { path ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.delete { onDeleted() } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text("删除记录") }
        }
    }
}

@Composable
private fun LabeledText(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(2.dp))
    Text(value, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(10.dp))
}
