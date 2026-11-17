package com.mvp.medarchive.ui.recordform

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.mvp.medarchive.data.MedRepository
import com.mvp.medarchive.data.db.Record
import com.mvp.medarchive.scan.ImageEnhancer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

data class RecordFormState(
    val type: String = "病历",
    val title: String = "",
    val hospital: String = "",
    val notes: String = "",
    val dateEpochDay: Long = LocalDate.now().toEpochDay(),
    val images: List<String> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
    val ocrBusy: Boolean = false,
    val ocrMessage: String? = null
)

class RecordFormViewModel(
    application: Application,
    private val repository: MedRepository,
    private val memberId: Long,
    private val recordId: Long? = null
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(RecordFormState())
    val state: StateFlow<RecordFormState> = _state

    fun load() {
        if (recordId != null) {
            viewModelScope.launch {
                val r = repository.record(recordId)
                _state.value = if (r != null) {
                    RecordFormState(
                        type = r.type,
                        title = r.title,
                        hospital = r.hospital,
                        notes = r.notes,
                        dateEpochDay = r.dateEpochDay
                    )
                } else {
                    RecordFormState()
                }
            }
        } else {
            _state.value = RecordFormState()
        }
    }

    fun onTypeChange(v: String) { _state.value = _state.value.copy(type = v) }
    fun onTitleChange(v: String) { _state.value = _state.value.copy(title = v, error = null) }
    fun onHospitalChange(v: String) { _state.value = _state.value.copy(hospital = v) }
    fun onNotesChange(v: String) { _state.value = _state.value.copy(notes = v) }
    fun onDateChange(epochDay: Long) { _state.value = _state.value.copy(dateEpochDay = epochDay) }

    fun addImageUris(uris: List<Uri>) = viewModelScope.launch(Dispatchers.IO) {
        val paths = uris.mapNotNull { uri ->
            runCatching {
                val name = getApplication<Application>().contentResolver
                    .openInputStream(uri)?.use { repository.imageStore.save(it.readBytes()) }
                name?.let { repository.imageStore.file(it).absolutePath }
            }.getOrNull()
        }
        _state.value = _state.value.copy(images = _state.value.images + paths)
    }

    fun removeImage(index: Int) {
        val imgs = _state.value.images.toMutableList()
        if (index in imgs.indices) imgs.removeAt(index)
        _state.value = _state.value.copy(images = imgs)
    }

    fun ocrImages(auto: Boolean = false) = viewModelScope.launch { runOcr(auto) }

    private suspend fun runOcr(auto: Boolean) {
        val paths = _state.value.images
        if (paths.isEmpty()) {
            _state.value = _state.value.copy(ocrMessage = "请先添加图片")
            return
        }
        _state.value = _state.value.copy(ocrBusy = true, ocrMessage = if (auto) "正在自动识别…" else null)
        val text = withContext(Dispatchers.IO) {
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            val sb = StringBuilder()
            try {
                for (p in paths) {
                    val bmp = decodeSampled(p) ?: continue
                    val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
                    if (result.text.isNotBlank()) sb.append(result.text).append("\n")
                    bmp.recycle()
                }
            } catch (e: Exception) {
                // handled below
            } finally {
                recognizer.close()
            }
            sb.toString().trim()
        }
        if (text.isBlank()) {
            _state.value = _state.value.copy(ocrBusy = false, ocrMessage = "未识别到文字，可手动填写")
            return
        }
        val parsed = OcrParser.parse(text)
        val s = _state.value
        val filled = buildList {
            if (parsed.dateEpochDay != null) add("日期")
            if (s.hospital.isBlank() && parsed.hospital != null) add("医院")
            if (s.title.isBlank() && parsed.title != null) add("标题")
        }
        _state.value = s.copy(
            ocrBusy = false,
            ocrMessage = if (filled.isEmpty()) {
                "已识别 ${text.length} 字，已填入备注"
            } else {
                "${if (auto) "扫描完成，" else ""}已识别 ${text.length} 字，自动填入：${filled.joinToString("、")}"
            },
            notes = (s.notes + "\n" + text).trim(),
            title = if (s.title.isBlank()) parsed.title ?: s.title else s.title,
            dateEpochDay = parsed.dateEpochDay ?: s.dateEpochDay,
            hospital = if (s.hospital.isBlank()) parsed.hospital ?: s.hospital else s.hospital
        )
    }

    /** 扫描完成的图片：入库，并按需自动识别填表（Pro）。 */
    fun addScannedImage(bytes: ByteArray, autoOcr: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        val name = repository.imageStore.save(bytes)
        val path = repository.imageStore.file(name).absolutePath
        _state.value = _state.value.copy(images = _state.value.images + path)
        if (autoOcr) runOcr(auto = true)
    }

    /** 已拍/已导入的照片一键转成扫描件（去色 + 提对比度）。 */
    fun enhanceImages() = viewModelScope.launch(Dispatchers.IO) {
        val paths = _state.value.images
        if (paths.isEmpty()) return@launch
        _state.value = _state.value.copy(ocrBusy = true, ocrMessage = "正在增强成扫描件…")
        val result = paths.map { p ->
            val old = File(p)
            val bytes = runCatching { ImageEnhancer.enhanceFile(old) }.getOrNull()
            if (bytes == null) {
                p
            } else {
                runCatching {
                    val name = repository.imageStore.save(bytes)
                    val newFile = repository.imageStore.file(name)
                    if (newFile.absolutePath != old.absolutePath) old.delete()
                    newFile.absolutePath
                }.getOrDefault(p)
            }
        }
        _state.value = _state.value.copy(
            images = result,
            ocrBusy = false,
            ocrMessage = "已增强 ${result.size} 张，可点缩略图查看"
        )
    }

    /** 大图按 2000px 采样解码，避免 OCR 时 OOM。 */
    private fun decodeSampled(path: String, maxSide: Int = 2000): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.value = s.copy(error = "标题不能为空")
            return@launch
        }
        _state.value = s.copy(saving = true, error = null)
        val relativeImages = s.images.map { File(it).name }
        if (recordId == null) {
            repository.addRecord(
                Record(
                    memberId = memberId,
                    type = s.type,
                    title = s.title.trim(),
                    dateEpochDay = s.dateEpochDay,
                    hospital = s.hospital.trim(),
                    notes = s.notes.trim()
                ),
                relativeImages
            )
        } else {
            val existing = repository.record(recordId)
            if (existing != null) {
                repository.updateRecord(
                    existing.copy(
                        type = s.type,
                        title = s.title.trim(),
                        dateEpochDay = s.dateEpochDay,
                        hospital = s.hospital.trim(),
                        notes = s.notes.trim()
                    )
                )
                repository.appendRecordImages(recordId, relativeImages)
            }
        }
        _state.value = RecordFormState()
        onSaved()
    }

    companion object {
        fun factory(
            app: Application,
            repository: MedRepository,
            memberId: Long,
            recordId: Long? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecordFormViewModel(app, repository, memberId, recordId) }
        }
    }
}
