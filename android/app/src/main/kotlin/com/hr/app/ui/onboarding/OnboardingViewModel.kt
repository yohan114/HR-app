package com.hr.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.onboarding.OnboardingRepository
import com.hr.client.model.ClearanceTaskItem
import com.hr.client.model.ClearanceTaskStatusUpdateRequest
import com.hr.client.model.ExitNoticeCreateRequest
import com.hr.client.model.OnboardingTaskItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.myOnboarding.collect { detail ->
                _state.update { it.copy(myOnboarding = detail) }
            }
        }
        viewModelScope.launch {
            repository.teamInstances.collect { instances ->
                _state.update { it.copy(teamInstances = instances) }
            }
        }
        viewModelScope.launch {
            repository.exitTypes.collect { types ->
                _state.update { it.copy(exitTypes = types) }
            }
        }
        viewModelScope.launch {
            repository.exitNotices.collect { notices ->
                _state.update { it.copy(exitNotices = notices) }
                if (notices.isNotEmpty() && _state.value.selectedClearance == null) {
                    repository.refreshClearance(notices.first().id)
                }
            }
        }
        viewModelScope.launch {
            repository.selectedClearance.collect { clearance ->
                _state.update { it.copy(selectedClearance = clearance) }
            }
        }

        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.refreshMyOnboarding()
            repository.refreshTeamOnboarding()
            repository.refreshExitTypes()
            repository.refreshExitNotices()
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun selectTab(tab: OnboardingTab) {
        _state.update { it.copy(activeTab = tab, userMessage = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(userMessage = null) }
    }

    // -------------------------------------------------------------------------
    // Task Completion
    // -------------------------------------------------------------------------

    fun openTaskCompleteDialog(task: OnboardingTaskItem) {
        _state.update { it.copy(isTaskCompleteDialogOpen = true, selectedTaskForCompletion = task) }
    }

    fun dismissTaskCompleteDialog() {
        _state.update { it.copy(isTaskCompleteDialogOpen = false, selectedTaskForCompletion = null) }
    }

    fun submitTaskComplete(notes: String) {
        val task = _state.value.selectedTaskForCompletion ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.completeTask(task.id, notes = notes)
            _state.update {
                it.copy(
                    isLoading = false,
                    isTaskCompleteDialogOpen = false,
                    selectedTaskForCompletion = null,
                    userMessage = if (result.isSuccess) "Task marked as completed!" else "Failed to complete task: ${result.exceptionOrNull()?.message}",
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Resignation Exit Notice
    // -------------------------------------------------------------------------

    fun openExitNoticeDialog() {
        _state.update { it.copy(isExitNoticeDialogOpen = true) }
    }

    fun dismissExitNoticeDialog() {
        _state.update { it.copy(isExitNoticeDialogOpen = false) }
    }

    fun submitExitNotice(
        exitTypeId: UUID,
        exitReasonId: UUID?,
        requestedLastWorkingDate: LocalDate,
        remarks: String?,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val req = ExitNoticeCreateRequest(
                exitTypeId = exitTypeId,
                exitReasonId = exitReasonId,
                requestedLastWorkingDate = requestedLastWorkingDate,
                remarks = remarks,
            )
            val result = repository.submitExitNotice(req)
            _state.update {
                it.copy(
                    isLoading = false,
                    isExitNoticeDialogOpen = false,
                    userMessage = if (result.isSuccess) "Resignation notice submitted successfully!" else "Failed to submit exit notice: ${result.exceptionOrNull()?.message}",
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Clearance Sign-Off
    // -------------------------------------------------------------------------

    fun openClearanceSignOffDialog(task: ClearanceTaskItem) {
        _state.update { it.copy(isClearanceSignOffDialogOpen = true, selectedClearanceTask = task) }
    }

    fun dismissClearanceSignOffDialog() {
        _state.update { it.copy(isClearanceSignOffDialogOpen = false, selectedClearanceTask = null) }
    }

    fun submitClearanceSignOff(
        status: ClearanceTaskStatusUpdateRequest.Status,
        remarks: String?,
        recoverableAmount: BigDecimal?,
    ) {
        val task = _state.value.selectedClearanceTask ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val req = ClearanceTaskStatusUpdateRequest(
                status = status,
                remarks = remarks,
                recoverableAmount = recoverableAmount,
            )
            val result = repository.updateClearanceTask(task.id, req)
            _state.update {
                it.copy(
                    isLoading = false,
                    isClearanceSignOffDialogOpen = false,
                    selectedClearanceTask = null,
                    userMessage = if (result.isSuccess) "Clearance sign-off recorded!" else "Failed to record sign-off: ${result.exceptionOrNull()?.message}",
                )
            }
        }
    }
}
