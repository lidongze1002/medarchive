package com.mvp.medarchive.ui.memberdetail

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mvp.medarchive.data.MedRepository
import com.mvp.medarchive.data.db.Member
import com.mvp.medarchive.data.db.Record
import com.mvp.medarchive.data.db.MedDose
import com.mvp.medarchive.data.db.MedReminder
import com.mvp.medarchive.export.PdfContentBuilder
import com.mvp.medarchive.reminder.ReminderScheduler
import com.mvp.medarchive.billing.ProManager
import com.mvp.medarchive.export.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemberDetailViewModel(
    application: Application,
    private val repository: MedRepository,
    private val memberId: Long
) : AndroidViewModel(application) {

    private val _member = MutableStateFlow<Member?>(null)
    val member: StateFlow<Member?> = _member

    val records: StateFlow<List<Record>> = repository.recordsOf(memberId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<MedReminder>> = repository.remindersOf(memberId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 最近 7 天的打卡记录（含今天） */
    val doses: StateFlow<List<MedDose>> = repository
        .dosesSince(memberId, java.time.LocalDate.now().minusDays(6).toEpochDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting

    private val _shareUri = MutableStateFlow<Uri?>(null)
    val shareUri: StateFlow<Uri?> = _shareUri

    private val exporter = PdfExporter(getApplication())

    init {
        viewModelScope.launch { _member.value = repository.member(memberId) }
    }

    fun export() = viewModelScope.launch(Dispatchers.IO) {
        _exporting.value = true
        val m = repository.member(memberId)
        if (m != null) {
            val rs = repository.recordsList(memberId)
            val content = PdfContentBuilder.build(m, rs)
            val imgs = rs.associate { r ->
                r.id to repository.imagePaths(r.id).mapNotNull { p ->
                    runCatching {
                        BitmapFactory.decodeFile(repository.imageStore.file(p).absolutePath)
                    }.getOrNull()
                }
            }
            _shareUri.value = exporter.export(content, ProManager(getApplication()).isPro()) { rid -> imgs[rid] ?: emptyList() }
        }
        _exporting.value = false
    }

    fun consumeShareUri() { _shareUri.value = null }

    fun deleteRecord(id: Long) = viewModelScope.launch { repository.deleteRecord(id) }

    fun updateMember(
        name: String,
        birthdate: String,
        bloodType: String,
        allergies: String,
        notes: String,
        avatarPath: String
    ) =
        viewModelScope.launch {
            val m = _member.value ?: return@launch
            if (m.avatarPath.isNotBlank() && m.avatarPath != avatarPath) {
                repository.imageStore.delete(m.avatarPath)
            }
            repository.updateMember(
                m.copy(
                    name = name.trim(),
                    birthdate = birthdate.trim(),
                    bloodType = bloodType.trim(),
                    allergies = allergies.trim(),
                    notes = notes.trim(),
                    avatarPath = avatarPath
                )
            )
            _member.value = repository.member(memberId)
        }

    fun deleteMember(onDeleted: () -> Unit) = viewModelScope.launch {
        val app = getApplication<Application>()
        repository.reminders(memberId).forEach { ReminderScheduler.cancel(app, it) }
        repository.deleteMember(memberId)
        onDeleted()
    }

    fun saveReminder(
        existing: MedReminder?,
        drug: String,
        dosage: String,
        times: List<java.time.LocalTime>,
        enabled: Boolean
    ) = viewModelScope.launch {
        if (drug.isBlank() || times.isEmpty()) return@launch
        val app = getApplication<Application>()
        val csv = times.sorted()
            .joinToString(",") { String.format("%02d:%02d", it.hour, it.minute) }
        if (existing == null) {
            val reminder = MedReminder(
                memberId = memberId,
                drug = drug.trim(),
                dosage = dosage.trim(),
                times = csv,
                enabled = enabled
            )
            val id = repository.addReminder(reminder)
            ReminderScheduler.schedule(app, reminder.copy(id = id))
        } else {
            val updated = existing.copy(
                drug = drug.trim(),
                dosage = dosage.trim(),
                times = csv,
                enabled = enabled
            )
            repository.updateReminder(updated)
            ReminderScheduler.schedule(app, updated)
        }
    }

    fun deleteReminder(reminder: MedReminder) = viewModelScope.launch {
        ReminderScheduler.cancel(getApplication(), reminder)
        repository.deleteReminder(reminder.id)
    }

    fun setReminderEnabled(reminder: MedReminder, enabled: Boolean) = viewModelScope.launch {
        val updated = reminder.copy(enabled = enabled)
        repository.updateReminder(updated)
        ReminderScheduler.schedule(getApplication(), updated)
    }

    /** 点时间胶囊打卡 / 取消打卡（今天）。 */
    fun toggleDose(reminder: MedReminder, time: String) = viewModelScope.launch {
        val day = java.time.LocalDate.now().toEpochDay()
        val taken = repository.todayDoses(memberId, day)
            .any { it.reminderId == reminder.id && it.time == time && it.status == "taken" }
        if (taken) {
            repository.unmarkDose(reminder.id, day, time)
        } else {
            repository.markDoseTaken(reminder.id, memberId, time, day)
        }
    }

    companion object {
        fun factory(app: Application, repository: MedRepository, memberId: Long): ViewModelProvider.Factory =
            viewModelFactory { initializer { MemberDetailViewModel(app, repository, memberId) } }
    }
}
