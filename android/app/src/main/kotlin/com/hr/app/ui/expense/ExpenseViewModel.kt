package com.hr.app.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.expense.ExpenseRepository
import com.hr.client.model.ExpenseClaimItem
import com.hr.client.model.ExpenseClaimLineInput
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
class ExpenseViewModel
    @Inject
    constructor(
        private val repository: ExpenseRepository,
    ) : ViewModel() {

        private val _state = MutableStateFlow(ExpenseState())
        val state: StateFlow<ExpenseState> = _state.asStateFlow()

        init {
            observeRepository()
            refresh()
        }

        private fun observeRepository() {
            viewModelScope.launch {
                repository.categories.collect { categories ->
                    _state.update { it.copy(categories = categories) }
                }
            }

            viewModelScope.launch {
                repository.claims.collect { claims ->
                    _state.update { it.copy(claims = claims) }
                }
            }

            viewModelScope.launch {
                repository.totalClaimedAmount.collect { total ->
                    _state.update { it.copy(totalClaimedAmount = total) }
                }
            }

            viewModelScope.launch {
                repository.totalApprovedAmount.collect { total ->
                    _state.update { it.copy(totalApprovedAmount = total) }
                }
            }

            viewModelScope.launch {
                repository.totalReimbursedAmount.collect { total ->
                    _state.update { it.copy(totalReimbursedAmount = total) }
                }
            }

            viewModelScope.launch {
                repository.pendingCount.collect { count ->
                    _state.update { it.copy(pendingCount = count) }
                }
            }

            viewModelScope.launch {
                repository.selectedClaimDetail.collect { detail ->
                    _state.update { it.copy(selectedClaimDetail = detail) }
                }
            }
        }

        fun refresh() {
            _state.update { it.copy(isLoading = true, error = null) }
            viewModelScope.launch {
                repository.refreshCategories()
                repository.refreshClaims(_state.value.selectedStatusFilter)
                _state.update { it.copy(isLoading = false) }
            }
        }

        fun setTab(tab: ExpenseTab) {
            _state.update { it.copy(selectedTab = tab, error = null) }
        }

        fun filterByStatus(status: String?) {
            _state.update { it.copy(selectedStatusFilter = status) }
            viewModelScope.launch {
                repository.refreshClaims(status)
            }
        }

        fun openClaimDetails(claim: ExpenseClaimItem) {
            _state.update { it.copy(isDetailModalOpen = true) }
            viewModelScope.launch {
                repository.fetchClaimDetails(claim.id.toString())
            }
        }

        fun closeClaimDetails() {
            _state.update { it.copy(isDetailModalOpen = false) }
            repository.clearSelectedClaimDetail()
        }

        fun setDraftTitle(title: String) {
            _state.update { it.copy(draftTitle = title) }
        }

        fun setDraftRemarks(remarks: String) {
            _state.update { it.copy(draftRemarks = remarks) }
        }

        fun addLine() {
            _state.update {
                val defaultCatId = it.categories.firstOrNull()?.id
                it.copy(draftLines = it.draftLines + DraftLineItem(categoryId = defaultCatId))
            }
        }

        fun removeLine(id: UUID) {
            _state.update {
                if (it.draftLines.size <= 1) {
                    it
                } else {
                    it.copy(draftLines = it.draftLines.filterNot { line -> line.id == id })
                }
            }
        }

        fun updateLine(id: UUID, block: (DraftLineItem) -> DraftLineItem) {
            _state.update { current ->
                current.copy(
                    draftLines = current.draftLines.map { line ->
                        if (line.id == id) block(line) else line
                    },
                )
            }
        }

        fun simulateReceiptScan(lineId: UUID, sampleKind: String) {
            _state.update { current ->
                current.copy(
                    draftLines = current.draftLines.map { line ->
                        if (line.id == lineId) line.copy(isScanningOcr = true) else line
                    },
                )
            }

            viewModelScope.launch {
                val mockText = when (sampleKind) {
                    "HOTEL" -> "Hilton Colombo Invoice 2026-03-05 Total: LKR 32,500.00 Deluxe Stay"
                    "TRANSIT" -> "Uber Technologies Ride Date: 2026-03-06 Total: LKR 1,850.00 Fort to Port City"
                    else -> "Keells Supermarket Date: 2026-03-07 Total Due: LKR 2,450.00 Lunch & Refreshments"
                }

                val result = repository.scanReceiptOcr(
                    receiptText = mockText,
                    fileName = "${sampleKind.lowercase()}_receipt.jpg",
                )

                result.fold(
                    onSuccess = { ocr ->
                        val matchedCat = _state.value.categories.find { it.code == ocr.suggestedCategoryCode }
                            ?: _state.value.categories.firstOrNull()

                        updateLine(lineId) { line ->
                            line.copy(
                                isScanningOcr = false,
                                merchantName = ocr.merchantName ?: line.merchantName,
                                amount = ocr.amount?.toPlainString() ?: line.amount,
                                expenseDate = ocr.expenseDate ?: line.expenseDate,
                                categoryId = matchedCat?.id ?: line.categoryId,
                                description = line.description.ifBlank { "Expense at ${ocr.merchantName ?: "Merchant"}" },
                                receiptFileName = "${sampleKind.lowercase()}_receipt.jpg",
                            )
                        }
                    },
                    onFailure = {
                        updateLine(lineId) { line -> line.copy(isScanningOcr = false) }
                    },
                )
            }
        }

        fun submitClaim() {
            val s = _state.value
            if (s.draftTitle.isBlank()) {
                _state.update { it.copy(error = "Please provide a title for the expense claim.") }
                return
            }

            val validLines = mutableListOf<ExpenseClaimLineInput>()
            for ((index, line) in s.draftLines.withIndex()) {
                val catId = line.categoryId ?: s.categories.firstOrNull()?.id
                if (catId == null) {
                    _state.update { it.copy(error = "Line ${index + 1}: Select an expense category.") }
                    return
                }
                val amount = line.amount.trim().toBigDecimalOrNull()
                if (amount == null || amount <= BigDecimal.ZERO) {
                    _state.update { it.copy(error = "Line ${index + 1}: Enter a valid amount greater than zero.") }
                    return
                }
                validLines.add(
                    ExpenseClaimLineInput(
                        categoryId = catId,
                        expenseDate = line.expenseDate,
                        description = line.description.ifBlank { "Expense line ${index + 1}" },
                        merchantName = line.merchantName.ifBlank { null },
                        unitQuantity = line.unitQuantity.trim().toBigDecimalOrNull(),
                        amount = amount,
                        receiptKey = line.receiptFileName,
                        receiptUrl = line.receiptUrl,
                    ),
                )
            }

            _state.update { it.copy(isSubmitting = true, error = null) }

            viewModelScope.launch {
                val result = repository.submitClaim(
                    title = s.draftTitle,
                    claimDate = s.draftClaimDate,
                    currency = s.draftCurrency,
                    remarks = s.draftRemarks.ifBlank { null },
                    lines = validLines,
                )

                result.fold(
                    onSuccess = { detail ->
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                selectedTab = ExpenseTab.MY_CLAIMS,
                                actionMessage = "Expense claim ${detail.claim.claimNumber} submitted for approval!",
                                draftTitle = "",
                                draftRemarks = "",
                                draftLines = listOf(DraftLineItem()),
                            )
                        }
                    },
                    onFailure = { err ->
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                error = err.message ?: "Failed to submit expense claim.",
                            )
                        }
                    },
                )
            }
        }

        fun openCancelDialog(claim: ExpenseClaimItem) {
            _state.update {
                it.copy(
                    claimToCancel = claim,
                    cancelReason = "",
                    isCancelDialogOpen = true,
                )
            }
        }

        fun closeCancelDialog() {
            _state.update {
                it.copy(
                    claimToCancel = null,
                    cancelReason = "",
                    isCancelDialogOpen = false,
                )
            }
        }

        fun setCancelReason(reason: String) {
            _state.update { it.copy(cancelReason = reason) }
        }

        fun confirmCancelClaim() {
            val claim = _state.value.claimToCancel ?: return
            _state.update { it.copy(isLoading = true) }

            viewModelScope.launch {
                val result = repository.cancelClaim(claim.id.toString(), _state.value.cancelReason)
                result.fold(
                    onSuccess = { cancelled ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isCancelDialogOpen = false,
                                claimToCancel = null,
                                actionMessage = "Claim ${cancelled.claimNumber} successfully cancelled.",
                            )
                        }
                    },
                    onFailure = { err ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = err.message ?: "Failed to cancel expense claim.",
                            )
                        }
                    },
                )
            }
        }

        fun clearActionMessage() {
            _state.update { it.copy(actionMessage = null) }
        }

        fun clearError() {
            _state.update { it.copy(error = null) }
        }
    }
