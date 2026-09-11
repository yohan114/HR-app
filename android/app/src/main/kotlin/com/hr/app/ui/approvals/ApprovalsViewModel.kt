package com.hr.app.ui.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.approval.ApprovalsRepository
import com.hr.client.model.ApprovalItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Manager Approvals inbox screen.
 */
@HiltViewModel
class ApprovalsViewModel
    @Inject
    constructor(
        private val repository: ApprovalsRepository,
    ) : ViewModel() {

    private val _state = MutableStateFlow(ApprovalsState(loading = true))
    val state: StateFlow<ApprovalsState> = _state.asStateFlow()

    init {
        observeRepository()
        refresh(initial = true)
    }

    private fun observeRepository() {
        viewModelScope.launch {
            repository.pendingApprovals.collect { items ->
                _state.update { it.copy(items = items) }
            }
        }
        viewModelScope.launch {
            repository.pendingOutboxCount.collect { count ->
                _state.update { it.copy(pendingOutboxCount = count) }
            }
        }
    }

    fun selectTab(tab: ApprovalFilterTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = initial && it.items.isEmpty(),
                    refreshing = !initial,
                    error = null,
                )
            }
            repository.refresh()
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = err.message ?: "Failed to refresh approvals",
                        )
                    }
                }
                .onSuccess {
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                        )
                    }
                }
        }
    }

    fun openDetail(item: ApprovalItem) {
        _state.update { it.copy(selectedItemForDetail = item) }
    }

    fun closeDetail() {
        _state.update { it.copy(selectedItemForDetail = null) }
    }

    fun promptReject(item: ApprovalItem) {
        _state.update {
            it.copy(
                rejectionTargetItem = item,
                rejectionRemarks = "",
            )
        }
    }

    fun setRejectionRemarks(remarks: String) {
        _state.update { it.copy(rejectionRemarks = remarks) }
    }

    fun cancelReject() {
        _state.update {
            it.copy(
                rejectionTargetItem = null,
                rejectionRemarks = "",
            )
        }
    }

    fun approve(item: ApprovalItem) {
        viewModelScope.launch {
            _state.update { it.copy(actionInProgressId = item.id) }
            runCatching {
                repository.submitDecision(
                    itemId = item.id,
                    itemType = item.type,
                    decision = "APPROVE",
                    remarks = null,
                )
            }
            _state.update {
                it.copy(
                    actionInProgressId = null,
                    selectedItemForDetail = null,
                    successSnackbarMessage = "Approved request for ${item.requesterName}",
                )
            }
        }
    }

    fun confirmReject() {
        val target = _state.value.rejectionTargetItem ?: return
        val remarks = _state.value.rejectionRemarks.trim().ifEmpty { "Rejected by manager" }
        viewModelScope.launch {
            _state.update { it.copy(actionInProgressId = target.id) }
            runCatching {
                repository.submitDecision(
                    itemId = target.id,
                    itemType = target.type,
                    decision = "REJECT",
                    remarks = remarks,
                )
            }
            _state.update {
                it.copy(
                    actionInProgressId = null,
                    rejectionTargetItem = null,
                    selectedItemForDetail = null,
                    rejectionRemarks = "",
                    successSnackbarMessage = "Rejected request for ${target.requesterName}",
                )
            }
        }
    }

    fun clearSnackbarMessage() {
        _state.update { it.copy(successSnackbarMessage = null) }
    }
}
