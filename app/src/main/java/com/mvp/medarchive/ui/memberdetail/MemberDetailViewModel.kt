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
import com.mvp.medarchive.export.PdfContentBuilder
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

    fun updateMember(name: String, birthdate: String, bloodType: String, allergies: String, notes: String) =
        viewModelScope.launch {
            val m = _member.value ?: return@launch
            repository.updateMember(
                m.copy(
                    name = name.trim(),
                    birthdate = birthdate.trim(),
                    bloodType = bloodType.trim(),
                    allergies = allergies.trim(),
                    notes = notes.trim()
                )
            )
            _member.value = repository.member(memberId)
        }

    fun deleteMember(onDeleted: () -> Unit) = viewModelScope.launch {
        repository.deleteMember(memberId)
        onDeleted()
    }

    companion object {
        fun factory(app: Application, repository: MedRepository, memberId: Long): ViewModelProvider.Factory =
            viewModelFactory { initializer { MemberDetailViewModel(app, repository, memberId) } }
    }
}
