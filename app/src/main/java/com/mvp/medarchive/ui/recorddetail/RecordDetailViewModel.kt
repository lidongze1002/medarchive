package com.mvp.medarchive.ui.recorddetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mvp.medarchive.data.MedRepository
import com.mvp.medarchive.data.db.Record
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RecordDetailViewModel(
    private val repository: MedRepository,
    private val recordId: Long
) : ViewModel() {

    private val _record = MutableStateFlow<Record?>(null)
    val record: StateFlow<Record?> = _record

    private val _imagePaths = MutableStateFlow<List<String>>(emptyList())
    val imagePaths: StateFlow<List<String>> = _imagePaths

    init {
        viewModelScope.launch {
            _record.value = repository.record(recordId)
            _imagePaths.value = repository.imagePaths(recordId)
                .map { repository.imageStore.file(it).absolutePath }
        }
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        repository.deleteRecord(recordId)
        onDeleted()
    }

    companion object {
        fun factory(repository: MedRepository, recordId: Long): ViewModelProvider.Factory =
            viewModelFactory { initializer { RecordDetailViewModel(repository, recordId) } }
    }
}
