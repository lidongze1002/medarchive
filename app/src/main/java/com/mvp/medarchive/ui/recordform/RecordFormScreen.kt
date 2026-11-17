package com.mvp.medarchive.ui.recordform

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.mvp.medarchive.billing.ProDialog
import com.mvp.medarchive.billing.ProManager
import com.mvp.medarchive.scan.ScanCropScreen
import com.mvp.medarchive.ui.common.AppTopBar
import java.io.File

private val TYPES = listOf("病历", "检查单", "处方", "用药", "疫苗", "其他")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFormScreen(
    vm: RecordFormViewModel,
    isEdit: Boolean,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingScan by remember { mutableStateOf(false) }
    var scanPath by remember { mutableStateOf<String?>(null) }
    var showContinueScan by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showPro by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) vm.addImageUris(uris) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = pendingCameraUri
        val file = pendingCameraFile
        if (ok && uri != null) {
            if (pendingScan && file != null) {
                scanPath = file.absolutePath
            } else if (file != null) {
                vm.addImageFromCamera(file)
            } else {
                vm.addImageUris(listOf(uri))
            }
        } else {
            file?.delete()
        }
        pendingScan = false
    }

    fun launchCamera(scan: Boolean = false) {
        val f = File(ctx.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        pendingCameraUri = uri
        pendingCameraFile = f
        pendingScan = scan
        try {
            com.mvp.medarchive.security.ExternalUi.launch { takePicture.launch(uri) }
        } catch (e: Exception) {
            f.delete()
            android.widget.Toast.makeText(ctx, "没有可用的相机应用", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 扫描校正页（拍照后进入）
    val cropPath = scanPath
    if (cropPath != null) {
        ScanCropScreen(
            imagePath = cropPath,
            onCancel = {
                scanPath = null
                pendingCameraFile?.delete()
                pendingCameraFile = null
            },
            onDone = { bytes ->
                val auto = ProManager(ctx).isPro()
                vm.addScannedImage(bytes, autoOcr = auto)
                scanPath = null
                pendingCameraFile?.delete()
                pendingCameraFile = null
                if (auto) {
                    android.widget.Toast.makeText(
                        ctx,
                        "扫描完成，正在识别报告内容…",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                showContinueScan = true
            }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = if (isEdit) "编辑记录" else "添加记录", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("类型", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TYPES.forEach { t ->
                    FilterChip(
                        selected = state.type == t,
                        onClick = { vm.onTypeChange(t) },
                        label = { Text(t) }
                    )
                }
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = vm::onTitleChange,
                label = { Text("标题") },
                isError = state.error != null,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("日期：${com.mvp.medarchive.ui.formatEpochDay(state.dateEpochDay)}")
            }
            OutlinedTextField(
                value = state.hospital,
                onValueChange = vm::onHospitalChange,
                label = { Text("医院（可空）") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = vm::onNotesChange,
                label = { Text("备注（可空）") },
                modifier = Modifier.fillMaxWidth()
            )
            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text("报告图片", style = MaterialTheme.typography.titleSmall)
            if (state.images.isEmpty()) {
                Text(
                    "扫描会自动找边、拉正并增强，纸质报告拍出来更清楚。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.images.forEachIndexed { i, path ->
                        Thumb(path) { vm.removeImage(i) }
                    }
                }
                Text(
                    "共 ${state.images.size} 张，点缩略图可移除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { launchCamera(scan = true) },
                    modifier = Modifier.weight(1f)
                ) { Text("扫描") }
                OutlinedButton(
                    onClick = { launchCamera() },
                    modifier = Modifier.weight(1f)
                ) { Text("拍照") }
                OutlinedButton(
                    onClick = {
                        com.mvp.medarchive.security.ExternalUi.launch { pickImages.launch("image/*") }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("相册") }
            }
            if (state.images.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { vm.enhanceImages() },
                        enabled = !state.ocrBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text("增强") }
                    OutlinedButton(
                        onClick = {
                            if (ProManager(ctx).isPro()) vm.ocrImages() else showPro = true
                        },
                        enabled = !state.ocrBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (state.ocrBusy) "识别中…" else "识别文字") }
                }
                val msg = state.ocrMessage
                if (msg != null) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Button(onClick = { vm.save(onBack) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.saving) "保存中…" else "保存")
            }
        }
    }

    if (showPro) {
        ProDialog(onDismiss = { showPro = false })
    }
    if (showContinueScan) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showContinueScan = false },
            title = { Text("要接着扫描下一页吗？") },
            text = { Text("这一页已加入记录。多页报告可以连续扫描，最后一起保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showContinueScan = false
                    launchCamera(scan = true)
                }) { Text("继续扫描") }
            },
            dismissButton = {
                TextButton(onClick = { showContinueScan = false }) { Text("完成") }
            }
        )
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.dateEpochDay * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { vm.onDateChange(it / 86_400_000L) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun Thumb(path: String, onClick: () -> Unit) {
    AsyncImage(
        model = File(path),
        contentDescription = "移除",
        modifier = Modifier.size(80.dp).clickable { onClick() }
    )
}
