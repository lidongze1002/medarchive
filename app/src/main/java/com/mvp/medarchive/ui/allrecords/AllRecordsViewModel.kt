package com.mvp.medarchive.ui.allrecords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mvp.medarchive.data.MedRepository
import com.mvp.medarchive.data.db.Member
import com.mvp.medarchive.data.db.Record
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AllRecordItem(val record: Record, val memberName: String)

class AllRecordsViewModel(repository: MedRepository) : ViewModel() {

    val items: StateFlow<List<AllRecordItem>> = combine(
        repository.allRecords(),
        repository.members()
    ) { records, members ->
        val names: Map<Long, String> = members.associate { it.id to it.name }
        records.map { AllRecordItem(it, names[it.memberId] ?: "未知成员") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        fun factory(repository: MedRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AllRecordsViewModel(repository) }
        }
    }
}
