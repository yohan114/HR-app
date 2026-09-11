package com.hr.app.ui.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.leave.LeaveRepository
import com.hr.client.model.LeaveApplicationItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LeaveViewModel
    @Inject
    constructor(
        private val repository: LeaveRepository,
    ) : ViewModel() {

        private val _state = MutableStateFlow(LeaveState(loading = true))
        val state: StateFlow<LeaveState> = _state.asStateFlow()

        init {
            observeRepository()
            refreshAll()
        }

        private fun observeRepository() {
            viewModelScope.launch {
                repository.balances.collect { balances ->
                    _state.update { current ->
                        val defaultSelected = current.selectedBalanceTypeId
                            ?: balances.firstOrNull()?.leaveTypeId
                        current.copy(
                            balances = balances,
                            selectedBalanceTypeId = defaultSelected,
                            applyLeaveTypeId = current.applyLeaveTypeId.ifBlank {
                                balances.firstOrNull()?.leaveTypeId ?: ""
                            },
                        )
                    }
                }
            }

            viewModelScope.launch {
                repository.leaveYear.collect { year ->
                    _state.update { it.copy(leaveYear = year) }
                }
            }

            viewModelScope.launch {
                repository.applications.collect { applications ->
                    _state.update { it.copy(applications = applications, applicationsLoading = false) }
                }
            }

            viewModelScope.launch {
                repository.ledger.collect { ledger ->
                    _state.update { it.copy(ledger = ledger, ledgerLoading = false) }
                }
            }

            viewModelScope.launch {
                repository.pendingOutboxCount.collect { count ->
                    _state.update { it.copy(pendingOutboxCount = count) }
                }
            }
        }

        fun refreshAll() {
            viewModelScope.launch {
                _state.update { it.copy(refreshing = true, error = null) }
                repository.refreshBalances()
                repository.refreshApplications(_state.value.applicationsFilter)
                repository.refreshLedger(_state.value.ledgerFilterTypeId)
                _state.update { it.copy(refreshing = false, loading = false) }
            }
        }

        fun selectTab(tab: LeaveTab) {
            _state.update { it.copy(selectedTab = tab) }
            when (tab) {
                LeaveTab.BALANCES_APPLY -> {
                    viewModelScope.launch { repository.refreshBalances() }
                }
                LeaveTab.MY_APPLICATIONS -> {
                    viewModelScope.launch {
                        _state.update { it.copy(applicationsLoading = true) }
                        repository.refreshApplications(_state.value.applicationsFilter)
                        _state.update { it.copy(applicationsLoading = false) }
                    }
                }
                LeaveTab.STATEMENT_LEDGER -> {
                    viewModelScope.launch {
                        _state.update { it.copy(ledgerLoading = true) }
                        repository.refreshLedger(_state.value.ledgerFilterTypeId)
                        _state.update { it.copy(ledgerLoading = false) }
                    }
                }
            }
        }

        fun selectBalanceCard(leaveTypeId: String) {
            _state.update { it.copy(selectedBalanceTypeId = leaveTypeId) }
        }

        fun filterApplications(status: String?) {
            _state.update { it.copy(applicationsFilter = status, applicationsLoading = true) }
            viewModelScope.launch {
                repository.refreshApplications(status)
                _state.update { it.copy(applicationsLoading = false) }
            }
        }

        fun filterLedger(leaveTypeId: String?) {
            _state.update { it.copy(ledgerFilterTypeId = leaveTypeId, ledgerLoading = true) }
            viewModelScope.launch {
                repository.refreshLedger(leaveTypeId)
                _state.update { it.copy(ledgerLoading = false) }
            }
        }

        fun openApplyDialog(preselectedTypeId: String? = null) {
            val typeId = preselectedTypeId
                ?: _state.value.selectedBalanceTypeId
                ?: _state.value.balances.firstOrNull()?.leaveTypeId
                ?: ""

            val nextWorkDay = getNextWorkingDay(LocalDate.now())

            _state.update {
                it.copy(
                    isApplyDialogOpen = true,
                    applyLeaveTypeId = typeId,
                    applyStartDate = nextWorkDay,
                    applyEndDate = nextWorkDay,
                    applyDayPortion = "FULL_DAY",
                    applyReason = "",
                    eligibility = null,
                    error = null,
                )
            }
            recalculateEligibility()
        }

        fun dismissApplyDialog() {
            _state.update { it.copy(isApplyDialogOpen = false, eligibility = null) }
        }

        fun updateApplyLeaveType(typeId: String) {
            _state.update { it.copy(applyLeaveTypeId = typeId) }
            recalculateEligibility()
        }

        fun updateApplyDates(start: LocalDate, end: LocalDate) {
            _state.update { it.copy(applyStartDate = start, applyEndDate = end) }
            recalculateEligibility()
        }

        fun updateApplyDayPortion(portion: String) {
            _state.update { it.copy(applyDayPortion = portion) }
            recalculateEligibility()
        }

        fun updateApplyReason(reason: String) {
            _state.update { it.copy(applyReason = reason) }
        }

        fun recalculateEligibility() {
            val current = _state.value
            if (current.applyLeaveTypeId.isBlank()) return

            viewModelScope.launch {
                _state.update { it.copy(eligibilityLoading = true) }
                val result = repository.checkEligibility(
                    leaveTypeId = current.applyLeaveTypeId,
                    startDate = current.applyStartDate,
                    endDate = current.applyEndDate,
                    dayPortion = current.applyDayPortion,
                )
                _state.update {
                    it.copy(
                        eligibility = result.getOrNull(),
                        eligibilityLoading = false,
                    )
                }
            }
        }

        fun submitApplication() {
            val current = _state.value
            if (current.applyLeaveTypeId.isBlank()) {
                _state.update { it.copy(error = "Please select a leave category") }
                return
            }
            if (current.applyReason.isBlank()) {
                _state.update { it.copy(error = "Please provide a reason for leave") }
                return
            }
            if (current.eligibility?.eligible == false) {
                val reason = current.eligibility.reasons.firstOrNull() ?: "Leave request is ineligible"
                _state.update { it.copy(error = reason) }
                return
            }

            viewModelScope.launch {
                _state.update { it.copy(isSubmittingApplication = true, error = null) }
                val result = repository.submitApplication(
                    leaveTypeId = current.applyLeaveTypeId,
                    startDate = current.applyStartDate,
                    endDate = current.applyEndDate,
                    dayPortion = current.applyDayPortion,
                    reason = current.applyReason,
                )
                result.fold(
                    onSuccess = { app ->
                        _state.update {
                            it.copy(
                                isSubmittingApplication = false,
                                isApplyDialogOpen = false,
                                actionMessage = "Leave application submitted (${app.totalDays} days)",
                                selectedTab = LeaveTab.MY_APPLICATIONS,
                            )
                        }
                    },
                    onFailure = { err ->
                        _state.update {
                            it.copy(
                                isSubmittingApplication = false,
                                error = err.message ?: "Failed to submit leave application",
                            )
                        }
                    }
                )
            }
        }

        fun openCancelDialog(application: LeaveApplicationItem) {
            _state.update {
                it.copy(
                    applicationToCancel = application,
                    cancelReason = "",
                    error = null,
                )
            }
        }

        fun dismissCancelDialog() {
            _state.update { it.copy(applicationToCancel = null, cancelReason = "") }
        }

        fun updateCancelReason(reason: String) {
            _state.update { it.copy(cancelReason = reason) }
        }

        fun confirmCancelApplication() {
            val app = _state.value.applicationToCancel ?: return
            val reason = _state.value.cancelReason

            viewModelScope.launch {
                _state.update { it.copy(isCancellingApplication = true, error = null) }
                val result = repository.cancelApplication(
                    applicationId = app.id,
                    reason = reason.ifBlank { null },
                )
                result.fold(
                    onSuccess = {
                        _state.update {
                            it.copy(
                                isCancellingApplication = false,
                                applicationToCancel = null,
                                cancelReason = "",
                                actionMessage = "Leave application cancelled successfully",
                            )
                        }
                    },
                    onFailure = { err ->
                        _state.update {
                            it.copy(
                                isCancellingApplication = false,
                                error = err.message ?: "Failed to cancel leave application",
                            )
                        }
                    }
                )
            }
        }

        fun clearActionMessage() {
            _state.update { it.copy(actionMessage = null, error = null) }
        }

        private fun getNextWorkingDay(from: LocalDate): LocalDate {
            var date = from.plusDays(1)
            while (date.dayOfWeek == java.time.DayOfWeek.SATURDAY || date.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                date = date.plusDays(1)
            }
            return date
        }
    }
