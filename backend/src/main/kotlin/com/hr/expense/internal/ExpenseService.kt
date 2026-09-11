package com.hr.expense.internal

import com.hr.expense.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional
class ExpenseService(
    private val categoryRepository: ExpenseCategoryRepository,
    private val claimRepository: ExpenseClaimRepository,
    private val lineRepository: ExpenseClaimLineRepository,
    private val ocrService: ExpenseOcrService,
) {

    @Transactional(readOnly = true)
    fun getExpenseCategories(): ExpenseCategoriesResponse {
        val categories = categoryRepository.findAllByIsActiveTrueOrderByCodeAsc()
        return ExpenseCategoriesResponse(
            categories = categories.map { it.toResponse() },
        )
    }

    @Transactional(readOnly = true)
    fun getMyExpenseClaims(employeeId: UUID, statusFilter: String?): ExpenseClaimsResponse {
        val parsedStatus = statusFilter?.let { runCatching { ExpenseClaimStatus.valueOf(it.uppercase()) }.getOrNull() }
        val allClaims = claimRepository.findAllByEmployeeIdOrderByClaimDateDesc(employeeId)

        val filtered = if (parsedStatus != null) {
            allClaims.filter { it.status == parsedStatus }
        } else {
            allClaims
        }

        var totalClaimed = BigDecimal.ZERO
        var totalApproved = BigDecimal.ZERO
        var totalReimbursed = BigDecimal.ZERO
        var pendingCount = 0

        for (claim in allClaims) {
            totalClaimed = totalClaimed.add(claim.totalAmount)
            if (claim.status == ExpenseClaimStatus.APPROVED || claim.status == ExpenseClaimStatus.REIMBURSED) {
                totalApproved = totalApproved.add(claim.approvedAmount ?: claim.totalAmount)
            }
            if (claim.status == ExpenseClaimStatus.REIMBURSED) {
                totalReimbursed = totalReimbursed.add(claim.approvedAmount ?: claim.totalAmount)
            }
            if (claim.status == ExpenseClaimStatus.SUBMITTED || claim.status == ExpenseClaimStatus.UNDER_REVIEW) {
                pendingCount++
            }
        }

        val claimItems = filtered.map { claim ->
            val count = lineRepository.countByClaimId(claim.id).toInt()
            claim.toItemResponse(count)
        }

        return ExpenseClaimsResponse(
            totalClaimedAmount = totalClaimed.setScale(2, RoundingMode.HALF_UP),
            totalApprovedAmount = totalApproved.setScale(2, RoundingMode.HALF_UP),
            totalReimbursedAmount = totalReimbursed.setScale(2, RoundingMode.HALF_UP),
            pendingCount = pendingCount,
            claims = claimItems,
        )
    }

    @Transactional(readOnly = true)
    fun getMyExpenseClaimDetails(employeeId: UUID, claimId: UUID): ExpenseClaimDetailResponse {
        val claim = claimRepository.findByIdAndEmployeeId(claimId, employeeId)
            ?: error("Expense claim not found: $claimId")

        val lines = lineRepository.findAllByClaimIdOrderByExpenseDateAsc(claim.id)
        val categoriesMap = categoryRepository.findAll().associateBy { it.id }

        val lineResponses = lines.map { line ->
            val cat = categoriesMap[line.categoryId]
            line.toResponse(
                categoryCode = cat?.code ?: "UNKNOWN",
                categoryName = cat?.name ?: "Unknown Category",
            )
        }

        return ExpenseClaimDetailResponse(
            claim = claim.toItemResponse(lineResponses.size),
            lines = lineResponses,
        )
    }

    fun submitExpenseClaim(
        employeeId: UUID,
        request: ExpenseClaimSubmitRequest,
    ): ExpenseClaimDetailResponse {
        require(request.lines.isNotEmpty()) { "Expense claim must contain at least one line item" }

        val categoriesMap = categoryRepository.findAll().associateBy { it.id }
        var computedTotal = BigDecimal.ZERO

        val validatedLines = request.lines.map { lineInput ->
            val category = categoriesMap[lineInput.categoryId]
                ?: error("Invalid expense category: ${lineInput.categoryId}")

            require(category.isActive) { "Expense category '${category.name}' is inactive" }

            // Validate policy per-claim limit
            if (category.maxAmountPerClaim != null && lineInput.amount > category.maxAmountPerClaim!!) {
                error("Amount ${lineInput.amount} exceeds maximum allowed of ${category.maxAmountPerClaim} for category '${category.name}'")
            }

            // Calculate amount if per-unit rate
            val finalAmount = if (category.ratePerUnit != null && lineInput.unitQuantity != null) {
                lineInput.unitQuantity.multiply(category.ratePerUnit).setScale(2, RoundingMode.HALF_UP)
            } else {
                lineInput.amount.setScale(2, RoundingMode.HALF_UP)
            }

            computedTotal = computedTotal.add(finalAmount)
            lineInput to category
        }

        val claimNumber = "EXP-${LocalDate.now().year}-${UUID.randomUUID().toString().take(6).uppercase()}"

        val claim = ExpenseClaim(
            employeeId = employeeId,
            claimNumber = claimNumber,
            title = request.title.ifBlank { "Expense Claim $claimNumber" },
            claimDate = request.claimDate,
            currency = request.currency.uppercase(),
            totalAmount = computedTotal,
            approvedAmount = null,
            status = ExpenseClaimStatus.SUBMITTED,
            rejectionReason = null,
            remarks = request.remarks,
        )

        val savedClaim = claimRepository.save(claim)

        val savedLines = validatedLines.map { (lineInput, category) ->
            val line = ExpenseClaimLine(
                claimId = savedClaim.id,
                categoryId = category.id,
                expenseDate = lineInput.expenseDate,
                description = lineInput.description,
                merchantName = lineInput.merchantName,
                unitQuantity = lineInput.unitQuantity,
                amount = lineInput.amount.setScale(2, RoundingMode.HALF_UP),
                receiptKey = lineInput.receiptKey,
                receiptUrl = lineInput.receiptUrl,
                status = ExpenseLineStatus.PENDING,
            )
            lineRepository.save(line)
        }

        return ExpenseClaimDetailResponse(
            claim = savedClaim.toItemResponse(savedLines.size),
            lines = savedLines.map { line ->
                val cat = categoriesMap[line.categoryId]
                line.toResponse(cat?.code ?: "UNKNOWN", cat?.name ?: "Unknown Category")
            },
        )
    }

    fun cancelExpenseClaim(
        employeeId: UUID,
        claimId: UUID,
        reason: String?,
    ): ExpenseClaimItemResponse {
        val claim = claimRepository.findByIdAndEmployeeId(claimId, employeeId)
            ?: error("Expense claim not found: $claimId")

        require(claim.status == ExpenseClaimStatus.SUBMITTED || claim.status == ExpenseClaimStatus.DRAFT || claim.status == ExpenseClaimStatus.UNDER_REVIEW) {
            "Cannot cancel expense claim in '${claim.status}' state"
        }

        claim.status = ExpenseClaimStatus.CANCELLED
        if (!reason.isNullOrBlank()) {
            claim.remarks = (claim.remarks?.let { "$it | " } ?: "") + "Cancelled: $reason"
        }

        val saved = claimRepository.save(claim)
        val lineCount = lineRepository.countByClaimId(saved.id).toInt()
        return saved.toItemResponse(lineCount)
    }

    fun scanReceiptOcr(request: ReceiptOcrRequest): ReceiptOcrResponse {
        return ocrService.parseReceipt(request)
    }

    private fun ExpenseCategory.toResponse() = ExpenseCategoryItemResponse(
        id = id,
        code = code,
        name = name,
        description = description,
        glCode = glCode,
        requiresReceipt = requiresReceipt,
        maxAmountPerClaim = maxAmountPerClaim,
        ratePerUnit = ratePerUnit,
        unitName = unitName,
        isActive = isActive,
    )

    private fun ExpenseClaim.toItemResponse(lineCount: Int) = ExpenseClaimItemResponse(
        id = id,
        claimNumber = claimNumber,
        title = title,
        claimDate = claimDate,
        currency = currency,
        totalAmount = totalAmount,
        approvedAmount = approvedAmount,
        status = status,
        lineCount = lineCount,
        rejectionReason = rejectionReason,
        approvedAt = approvedAt,
        reimbursedAt = reimbursedAt,
        remarks = remarks,
    )

    private fun ExpenseClaimLine.toResponse(categoryCode: String, categoryName: String) = ExpenseClaimLineItemResponse(
        id = id,
        categoryId = categoryId,
        categoryCode = categoryCode,
        categoryName = categoryName,
        expenseDate = expenseDate,
        description = description,
        merchantName = merchantName,
        unitQuantity = unitQuantity,
        amount = amount,
        receiptKey = receiptKey,
        receiptUrl = receiptUrl,
        status = status,
        approvedAmount = approvedAmount,
        rejectionReason = lineRejectionReason,
        ocrExtractedSummary = ocrExtracted,
    )
}
