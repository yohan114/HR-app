package com.hr.app.ui.lifecycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.lifecycle.LifecycleRepository
import com.hr.client.model.CareerMovementItem
import com.hr.client.model.CareerTimelineResponse
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
class LifecycleViewModel
    @Inject
    constructor(
        private val repository: LifecycleRepository,
    ) : ViewModel() {

        val timeline: StateFlow<CareerTimelineResponse?> = repository.timeline
        val movements: StateFlow<List<CareerMovementItem>> = repository.movements
        val selectedMovement: StateFlow<CareerMovementItem?> = repository.selectedMovement
        val totalSalaryGrowthPercentage: StateFlow<BigDecimal> = repository.totalSalaryGrowthPercentage

        private val _uiState = MutableStateFlow(LifecycleUiState())
        val uiState: StateFlow<LifecycleUiState> = _uiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh(employeeId: UUID? = null) {
            _uiState.update { it.copy(isLoading = true, errorBanner = null) }
            viewModelScope.launch {
                repository.refreshTimeline(employeeId)
                repository.refreshMovements(
                    employeeId = employeeId,
                    status = _uiState.value.movementStatusFilter,
                    type = _uiState.value.movementTypeFilter,
                )
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        fun selectTab(tab: LifecycleTab) {
            _uiState.update { it.copy(activeTab = tab, errorBanner = null, successBanner = null) }
        }

        fun filterByType(type: CareerMovementItem.MovementType?) {
            _uiState.update { it.copy(movementTypeFilter = type) }
            viewModelScope.launch {
                repository.refreshMovements(
                    status = _uiState.value.movementStatusFilter,
                    type = type,
                )
            }
        }

        fun filterByStatus(status: CareerMovementItem.Status?) {
            _uiState.update { it.copy(movementStatusFilter = status) }
            viewModelScope.launch {
                repository.refreshMovements(
                    status = status,
                    type = _uiState.value.movementTypeFilter,
                )
            }
        }

        fun selectMovement(id: UUID) {
            _uiState.update { it.copy(selectedMovementId = id) }
            viewModelScope.launch {
                repository.getMovementById(id)
            }
        }

        fun openProposalDialog() {
            val current = timeline.value
            _uiState.update {
                it.copy(
                    showProposalDialog = true,
                    proposalMovementType = CareerMovementItem.MovementType.PROMOTION,
                    proposalEffectiveDate = LocalDate.now().plusMonths(1),
                    proposalJustification = "",
                    proposalDepartmentName = current?.currentDepartment ?: "Engineering",
                    proposalDesignationName = "",
                    proposalSalaryGradeCode = "M1",
                    proposalBaseSalary = "",
                    proposalRemarks = "",
                    errorBanner = null,
                )
            }
        }

        fun closeProposalDialog() {
            _uiState.update { it.copy(showProposalDialog = false) }
        }

        fun updateProposalMovementType(type: CareerMovementItem.MovementType) {
            _uiState.update { it.copy(proposalMovementType = type) }
        }

        fun updateProposalEffectiveDate(date: LocalDate) {
            _uiState.update { it.copy(proposalEffectiveDate = date) }
        }

        fun updateProposalJustification(text: String) {
            _uiState.update { it.copy(proposalJustification = text) }
        }

        fun updateProposalDepartment(dept: String) {
            _uiState.update { it.copy(proposalDepartmentName = dept) }
        }

        fun updateProposalDesignation(title: String) {
            _uiState.update { it.copy(proposalDesignationName = title) }
        }

        fun updateProposalGrade(grade: String) {
            _uiState.update { it.copy(proposalSalaryGradeCode = grade) }
        }

        fun updateProposalSalary(salary: String) {
            _uiState.update { it.copy(proposalBaseSalary = salary) }
        }

        fun updateProposalRemarks(remarks: String) {
            _uiState.update { it.copy(proposalRemarks = remarks) }
        }

        fun submitProposal(employeeId: UUID) {
            val s = _uiState.value
            if (s.proposalJustification.isBlank()) {
                _uiState.update { it.copy(errorBanner = "Please enter justification for this career movement.") }
                return
            }
            if (s.proposalDesignationName.isBlank()) {
                _uiState.update { it.copy(errorBanner = "Please specify the proposed designation title.") }
                return
            }

            val salary = s.proposalBaseSalary.takeIf { it.isNotBlank() }?.let { runCatching { BigDecimal(it) }.getOrNull() }
            _uiState.update { it.copy(isSubmittingProposal = true, errorBanner = null) }

            viewModelScope.launch {
                val result = repository.proposeMovement(
                    employeeId = employeeId,
                    movementType = s.proposalMovementType,
                    effectiveDate = s.proposalEffectiveDate,
                    justification = s.proposalJustification.trim(),
                    newDepartmentName = s.proposalDepartmentName.trim(),
                    newDesignationName = s.proposalDesignationName.trim(),
                    newSalaryGradeCode = s.proposalSalaryGradeCode.trim(),
                    newBaseSalary = salary,
                    newCurrency = s.proposalCurrency,
                    remarks = s.proposalRemarks.takeIf { it.isNotBlank() },
                )

                result.onSuccess { created ->
                    _uiState.update {
                        it.copy(
                            isSubmittingProposal = false,
                            showProposalDialog = false,
                            successBanner = "Movement proposal ${created.movementNumber} submitted for approval.",
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSubmittingProposal = false,
                            errorBanner = error.message ?: "Failed to propose career movement.",
                        )
                    }
                }
            }
        }

        fun approveMovement(id: UUID) {
            _uiState.update { it.copy(isLoading = true, errorBanner = null) }
            viewModelScope.launch {
                val result = repository.approveMovement(id)
                result.onSuccess { approved ->
                    val actionLabel = if (approved.cascadeApplied) "applied immediately to employee master" else "scheduled for effective date"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successBanner = "Movement ${approved.movementNumber} approved and $actionLabel.",
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorBanner = error.message ?: "Failed to approve career movement.",
                        )
                    }
                }
            }
        }

        fun openRevertDialog(id: UUID) {
            _uiState.update {
                it.copy(
                    showRevertDialog = true,
                    revertingMovementId = id,
                    revertReasonText = "",
                    errorBanner = null,
                )
            }
        }

        fun closeRevertDialog() {
            _uiState.update { it.copy(showRevertDialog = false, revertingMovementId = null) }
        }

        fun updateRevertReason(reason: String) {
            _uiState.update { it.copy(revertReasonText = reason) }
        }

        fun submitRevert() {
            val id = _uiState.value.revertingMovementId ?: return
            val reason = _uiState.value.revertReasonText
            if (reason.isBlank()) {
                _uiState.update { it.copy(errorBanner = "Please specify reason for reverting this movement.") }
                return
            }

            _uiState.update { it.copy(isSubmittingRevert = true, errorBanner = null) }
            viewModelScope.launch {
                val result = repository.revertMovement(id, reason.trim())
                result.onSuccess { reverted ->
                    _uiState.update {
                        it.copy(
                            isSubmittingRevert = false,
                            showRevertDialog = false,
                            revertingMovementId = null,
                            successBanner = "Movement ${reverted.movementNumber} reverted. Master attributes rolled back.",
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSubmittingRevert = false,
                            errorBanner = error.message ?: "Failed to revert career movement.",
                        )
                    }
                }
            }
        }

        fun dismissBanner() {
            _uiState.update { it.copy(errorBanner = null, successBanner = null) }
        }
    }
