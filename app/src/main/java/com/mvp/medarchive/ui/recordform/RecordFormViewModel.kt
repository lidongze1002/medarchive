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

    fun ocrImages() = viewModelScope.launch {
        val paths = _state.value.images
        if (paths.isEmpty()) {
            _state.value = _state.value.copy(ocrMessage = "请先添加图片")
            return@launch
        }
        _state.value = _state.value.copy(ocrBusy = true, ocrMessage = null)
        val text = withContext(Dispatchers.IO) {
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            val sb = StringBuilder()
            try {
                for (p in paths) {
                    val bmp = BitmapFactory.decodeFile(p) ?: continue
                    val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
                    if (result.text.isNotBlank()) sb.append(result.text).append("\n")
                }
            } catch (e: Exception) {
                // handled below
            } finally {
                recognizer.close()
            }
            sb.toString().trim()
        }
        if (text.isBlank()) {
            _state.value = _state.value.copy(ocrBusy = false, ocrMessage = "未识别到文字")
            return@launch
        }
        val parsed = parseStructured(text)
        val s = _state.value
        val newDate = parsed.dateEpochDay ?: s.dateEpochDay
        val newHospital = if (s.hospital.isBlank() && parsed.hospital != null) parsed.hospital else s.hospital
        val filled = buildList {
            if (parsed.dateEpochDay != null) add("日期")
            if (s.hospital.isBlank() && parsed.hospital != null) add("医院")
        }
        _state.value = s.copy(
            ocrBusy = false,
            ocrMessage = if (filled.isEmpty()) "已识别 ${text.length} 字，已填入备注"
            else "已识别 ${text.length} 字，并自动填入：${filled.joinToString("、")}",
            notes = (s.notes + "\n" + text).trim(),
            dateEpochDay = newDate,
            hospital = newHospital
        )
    }

    private data class ParsedOcr(val dateEpochDay: Long?, val hospital: String?)

    private fun parseStructured(text: String): ParsedOcr {
        val datePattern = Regex("""(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})""")
        val date = datePattern.find(text)?.let {
            runCatching {
                LocalDate.of(
                    it.groupValues[1].toInt(),
                    it.groupValues[2].toInt(),
                    it.groupValues[3].toInt()
                ).toEpochDay()
            }.getOrNull()
        }
        val hospitalLine = text.lines()
            .map { it.trim() }
            .firstOrNull { it.contains("医院") || it.contains("卫生院") || it.contains("诊所") }
        val hospital = hospitalLine
            ?.substringBefore("医院")?.let { it + "医院" }
            ?.takeIf { it.length in 3..30 }
            ?: hospitalLine?.takeIf { it.length in 3..30 }
        return ParsedOcr(date, hospital)
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
