package com.hr.app.ui.benefit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.benefit.BenefitRepository
import com.hr.client.model.*
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
class BenefitViewModel
    @Inject
    constructor(
        private val repository: BenefitRepository,
    ) : ViewModel() {

        val catalogue: StateFlow<BenefitCatalogueResponse?> = repository.catalogue
        val myBenefits: StateFlow<MyBenefitsResponse?> = repository.myBenefits
        val claims: StateFlow<List<BenefitClaimItem>> = repository.claims
        val totalAnnualEntitlement: StateFlow<BigDecimal> = repository.totalAnnualEntitlement
        val totalUsedAmount: StateFlow<BigDecimal> = repository.totalUsedAmount
        val totalPendingAmount: StateFlow<BigDecimal> = repository.totalPendingAmount
        val totalRemainingBalance: StateFlow<BigDecimal> = repository.totalRemainingBalance
        val pendingClaimsCount: StateFlow<Int> = repository.pendingClaimsCount

        private val _uiState = MutableStateFlow(BenefitUiState())
        val uiState: StateFlow<BenefitUiState> = _uiState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            _uiState.update { it.copy(isLoading = true, errorBanner = null) }
            viewModelScope.launch {
                repository.refreshCatalogue()
                repository.refreshMyBenefits()
                repository.refreshClaims(_uiState.value.statusFilter)
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        fun selectTab(tab: BenefitTab) {
            _uiState.update { it.copy(activeTab = tab, errorBanner = null, successBanner = null) }
        }

        fun filterByStatus(status: String?) {
            _uiState.update { it.copy(statusFilter = status) }
            viewModelScope.launch {
                repository.refreshClaims(status)
            }
        }

        fun filterByCategory(categoryCode: String?) {
            _uiState.update { it.copy(selectedCategoryFilter = categoryCode) }
        }

        fun updateSearchQuery(query: String) {
            _uiState.update { it.copy(searchQuery = query) }
        }

        fun openClaimDialog(presetEnrollmentId: UUID? = null) {
            val enrollments = myBenefits.value?.enrollments.orEmpty()
            val enrollmentId = presetEnrollmentId ?: enrollments.firstOrNull()?.id
            _uiState.update {
                it.copy(
                    showClaimDialog = true,
                    selectedEnrollmentId = enrollmentId,
                    selectedDependentId = null,
                    claimDate = LocalDate.now(),
                    serviceProvider = "",
                    diagnosisOrReason = "",
                    invoiceNumber = "",
                    claimedAmount = "",
                    remarks = "",
                    receiptUrl = null,
                    receiptKey = null,
                    simulatedReceiptName = null,
                    errorBanner = null,
                )
            }
        }

        fun closeClaimDialog() {
            _uiState.update { it.copy(showClaimDialog = false) }
        }

        fun updateEnrollmentSelection(id: UUID) {
            _uiState.update { it.copy(selectedEnrollmentId = id, selectedDependentId = null) }
        }

        fun updateDependentSelection(id: UUID?) {
            _uiState.update { it.copy(selectedDependentId = id) }
        }

        fun updateServiceProvider(text: String) {
            _uiState.update { it.copy(serviceProvider = text) }
        }

        fun updateDiagnosisOrReason(text: String) {
            _uiState.update { it.copy(diagnosisOrReason = text) }
        }

        fun updateInvoiceNumber(text: String) {
            _uiState.update { it.copy(invoiceNumber = text) }
        }

        fun updateClaimedAmount(text: String) {
            _uiState.update { it.copy(claimedAmount = text) }
        }

        fun updateRemarks(text: String) {
            _uiState.update { it.copy(remarks = text) }
        }

        fun attachMockReceipt() {
            val mockFileName = "medical_invoice_${System.currentTimeMillis() % 10000}.pdf"
            _uiState.update {
                it.copy(
                    simulatedReceiptName = mockFileName,
                    receiptUrl = "https://storage.hrapp.io/receipts/$mockFileName",
                    receiptKey = "receipts/$mockFileName",
                )
            }
        }

        fun submitClaim() {
            val state = _uiState.value
            val enrollmentId = state.selectedEnrollmentId
            if (enrollmentId == null) {
                _uiState.update { it.copy(errorBanner = "Please select an active benefit policy") }
                return
            }
            if (state.serviceProvider.isBlank()) {
                _uiState.update { it.copy(errorBanner = "Please enter the healthcare provider or clinic name") }
                return
            }
            if (state.diagnosisOrReason.isBlank()) {
                _uiState.update { it.copy(errorBanner = "Please describe the diagnosis, prescription, or procedure") }
                return
            }
            val amount = state.claimedAmount.toBigDecimalOrNull()
            if (amount == null || amount <= BigDecimal.ZERO) {
                _uiState.update { it.copy(errorBanner = "Please enter a valid claim amount greater than zero") }
                return
            }

            _uiState.update { it.copy(isSubmitting = true, errorBanner = null) }
            viewModelScope.launch {
                repository.submitClaim(
                    enrollmentId = enrollmentId,
                    claimDate = state.claimDate,
                    dependentId = state.selectedDependentId,
                    serviceProvider = state.serviceProvider.trim(),
                    diagnosisOrReason = state.diagnosisOrReason.trim(),
                    invoiceNumber = state.invoiceNumber.trim().ifBlank { null },
                    claimedAmount = amount,
                    currency = "LKR",
                    receiptUrl = state.receiptUrl,
                    receiptKey = state.receiptKey,
                    remarks = state.remarks.trim().ifBlank { null },
                ).onSuccess { claim ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            showClaimDialog = false,
                            activeTab = BenefitTab.CLAIMS,
                            successBanner = "Claim ${claim.claimNumber} submitted successfully! Your balance has been updated.",
                        )
                    }
                    repository.refreshMyBenefits()
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorBanner = err.message ?: "Failed to submit claim. Please try again.",
                        )
                    }
                }
            }
        }

        fun cancelClaim(claimId: String, reason: String? = null) {
            _uiState.update { it.copy(isCancelling = true, errorBanner = null) }
            viewModelScope.launch {
                repository.cancelClaim(claimId, reason).onSuccess { claim ->
                    _uiState.update {
                        it.copy(
                            isCancelling = false,
                            successBanner = "Claim ${claim.claimNumber} was cancelled and funds restored to your policy.",
                        )
                    }
                    repository.refreshMyBenefits()
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isCancelling = false,
                            errorBanner = err.message ?: "Failed to cancel claim.",
                        )
                    }
                }
            }
        }

        fun clearBanners() {
            _uiState.update { it.copy(errorBanner = null, successBanner = null) }
        }
    }
