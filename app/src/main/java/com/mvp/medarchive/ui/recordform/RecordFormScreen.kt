package com.mvp.medarchive.ui.recordform

import com.mvp.medarchive.ui.i18n.T

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
            Text(T("类型", "Type"), style = MaterialTheme.typography.titleSmall)
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
                label = { Text(T("标题", "Title")) },
                isError = state.error != null,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    T(
                        "日期：${com.mvp.medarchive.ui.formatEpochDay(state.dateEpochDay)}",
                        "Date: ${com.mvp.medarchive.ui.formatEpochDay(state.dateEpochDay)}"
                    )
                )
            }
            OutlinedTextField(
                value = state.hospital,
                onValueChange = vm::onHospitalChange,
                label = { Text(T("医院（可空）", "Hospital (optional)")) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = vm::onNotesChange,
                label = { Text(T("备注（可空）", "Notes (optional)")) },
                modifier = Modifier.fillMaxWidth()
            )
            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text(T("报告图片", "Report images"), style = MaterialTheme.typography.titleSmall)
            if (state.images.isEmpty()) {
                Text(T("扫描会自动找边、拉正并增强，纸质报告拍出来更清楚。", "Scan finds edges, straightens and enhances paper reports."),
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
                    T(
                        "共 ${state.images.size} 张，点缩略图可移除",
                        "${state.images.size} images · tap a thumbnail to remove"
                    ),
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
                ) { Text(T("扫描", "Scan")) }
                OutlinedButton(
                    onClick = { launchCamera() },
                    modifier = Modifier.weight(1f)
                ) { Text(T("拍照", "Camera")) }
                OutlinedButton(
                    onClick = {
                        com.mvp.medarchive.security.ExternalUi.launch { pickImages.launch("image/*") }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(T("相册", "Gallery")) }
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
                    ) { Text(T("增强", "Enhance")) }
                    OutlinedButton(
                        onClick = {
                            if (ProManager(ctx).isPro()) vm.ocrImages() else showPro = true
                        },
                        enabled = !state.ocrBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (state.ocrBusy) T("识别中…", "Recognizing…") else T("识别文字", "Recognize text")) }
                }
                val msg = state.ocrMessage
                if (msg != null) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Button(onClick = { vm.save(onBack) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.saving) T("保存中…", "Saving…") else T("保存", "Save"))
            }
        }
    }

    if (showPro) {
        ProDialog(onDismiss = { showPro = false })
    }
    if (showContinueScan) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showContinueScan = false },
            title = { Text(T("要接着扫描下一页吗？", "Scan another page?")) },
            text = { Text(T("这一页已加入记录。多页报告可以连续扫描，最后一起保存。", "Page added. Scan more pages, then save together.")) },
            confirmButton = {
                TextButton(onClick = {
                    showContinueScan = false
                    launchCamera(scan = true)
                }) { Text(T("继续扫描", "Scan next page")) }
            },
            dismissButton = {
                TextButton(onClick = { showContinueScan = false }) { Text(T("完成", "Done")) }
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
                }) { Text(T("确定", "OK")) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(T("取消", "Cancel")) } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun Thumb(path: String, onClick: () -> Unit) {
    AsyncImage(
        model = File(path),
        contentDescription = T("移除", "Remove"),
        modifier = Modifier.size(80.dp).clickable { onClick() }
    )
}
