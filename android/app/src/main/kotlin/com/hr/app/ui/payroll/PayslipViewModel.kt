package com.hr.app.ui.payroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.payroll.PayslipRepository
import com.hr.client.model.PayslipLineItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PayslipViewModel
    @Inject
    constructor(
        private val repository: PayslipRepository,
    ) : ViewModel() {

        private val _state = MutableStateFlow(PayslipState())
        val state: StateFlow<PayslipState> = _state.asStateFlow()

        init {
            loadPayslips()
        }

        fun loadPayslips(initialPayslipId: UUID? = null) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, error = null) }
                repository.refreshPayslips()
                val payslips = repository.payslips.value

                val targetId = initialPayslipId
                    ?: payslips.firstOrNull()?.id

                _state.update {
                    it.copy(
                        payslips = payslips,
                        selectedPayslipId = targetId,
                        isLoading = false,
                    )
                }

                if (targetId != null) {
                    loadPayslipDetailsAndComparison(targetId)
                }
            }
        }

        fun selectPayslip(id: UUID) {
            _state.update { it.copy(selectedPayslipId = id) }
            loadPayslipDetailsAndComparison(id)
        }

        private fun loadPayslipDetailsAndComparison(id: UUID) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                repository.loadPayslipDetail(id)
                repository.loadPayslipComparison(id)

                _state.update {
                    it.copy(
                        selectedPayslipDetail = repository.activePayslip.value,
                        comparison = repository.comparison.value,
                        isLoading = false,
                    )
                }
            }
        }

        fun openExplainer(line: PayslipLineItem) {
            _state.update {
                it.copy(
                    selectedExplainerLine = line,
                    isExplainerOpen = true,
                )
            }
        }

        fun closeExplainer() {
            _state.update {
                it.copy(
                    isExplainerOpen = false,
                    selectedExplainerLine = null,
                )
            }
        }

        fun openComparison() {
            _state.update { it.copy(isComparisonOpen = true) }
        }

        fun closeComparison() {
            _state.update { it.copy(isComparisonOpen = false) }
        }

        fun toggleMask() {
            _state.update { it.copy(isMasked = !it.isMasked) }
        }
    }
