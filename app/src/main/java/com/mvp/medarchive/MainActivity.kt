package com.mvp.medarchive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mvp.medarchive.ui.memberdetail.MemberDetailScreen
import com.mvp.medarchive.ui.memberdetail.MemberDetailViewModel
import com.mvp.medarchive.ui.memberlist.MemberListScreen
import com.mvp.medarchive.ui.memberlist.MemberListViewModel
import com.mvp.medarchive.ui.recorddetail.RecordDetailScreen
import com.mvp.medarchive.ui.recorddetail.RecordDetailViewModel
import com.mvp.medarchive.ui.recordform.RecordFormScreen
import com.mvp.medarchive.ui.recordform.RecordFormViewModel
import com.mvp.medarchive.ui.theme.MedArchiveTheme

sealed interface Screen {
    data object List : Screen
    data class Detail(val memberId: Long) : Screen
    data class RecordForm(val memberId: Long) : Screen
    data class RecordDetail(val recordId: Long) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MedArchiveTheme {
                val container = (application as MedArchiveApp).container
                var stack by remember { mutableStateOf(listOf<Screen>(Screen.List)) }
                val current = stack.last()

                BackHandler { if (stack.size > 1) stack = stack.dropLast(1) }

                when (current) {
                    Screen.List -> MemberListScreen(
                        vm = viewModel(factory = MemberListViewModel.factory(container.repository)),
                        onOpen = { id -> stack = stack + Screen.Detail(id) }
                    )
                    is Screen.Detail -> MemberDetailScreen(
                        vm = viewModel(
                            key = "member_${current.memberId}",
                            factory = MemberDetailViewModel.factory(application, container.repository, current.memberId)
                        ),
                        onBack = { stack = stack.dropLast(1) },
                        onAddRecord = { id -> stack = stack + Screen.RecordForm(id) },
                        onOpenRecord = { id -> stack = stack + Screen.RecordDetail(id) }
                    )
                    is Screen.RecordForm -> RecordFormScreen(
                        vm = viewModel(
                            key = "recordform_${current.memberId}",
                            factory = RecordFormViewModel.factory(application, container.repository, current.memberId)
                        ),
                        onBack = { stack = stack.dropLast(1) }
                    )
                    is Screen.RecordDetail -> RecordDetailScreen(
                        vm = viewModel(
                            key = "record_${current.recordId}",
                            factory = RecordDetailViewModel.factory(container.repository, current.recordId)
                        ),
                        onBack = { stack = stack.dropLast(1) },
                        onDeleted = { stack = stack.dropLast(1) }
                    )
                }
            }
        }
    }
}
