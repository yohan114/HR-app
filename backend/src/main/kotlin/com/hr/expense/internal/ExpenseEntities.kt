package com.hr.expense.internal

import com.hr.expense.ExpenseClaimStatus
import com.hr.expense.ExpenseLineStatus
import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "expense_category")
class ExpenseCategory(
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "description")
    var description: String? = null,
    @Column(name = "gl_code", length = 32)
    var glCode: String? = null,
    @Column(name = "requires_receipt", nullable = false)
    var requiresReceipt: Boolean = true,
    @Column(name = "max_amount_per_claim", precision = 12, scale = 2)
    var maxAmountPerClaim: BigDecimal? = null,
    @Column(name = "rate_per_unit", precision = 10, scale = 2)
    var ratePerUnit: BigDecimal? = null,
    @Column(name = "unit_name", length = 32)
    var unitName: String? = null,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "expense_claim")
class ExpenseClaim(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "claim_number", nullable = false, length = 32)
    var claimNumber: String,
    @Column(name = "title", nullable = false, length = 128)
    var title: String,
    @Column(name = "claim_date", nullable = false)
    var claimDate: LocalDate,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "LKR",
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    var totalAmount: BigDecimal,
    @Column(name = "approved_amount", precision = 12, scale = 2)
    var approvedAmount: BigDecimal? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: ExpenseClaimStatus = ExpenseClaimStatus.SUBMITTED,
    @Column(name = "rejection_reason")
    var rejectionReason: String? = null,
    @Column(name = "approved_at")
    var approvedAt: Instant? = null,
    @Column(name = "approved_by")
    var approvedBy: UUID? = null,
    @Column(name = "reimbursed_at")
    var reimbursedAt: Instant? = null,
    @Column(name = "reimbursed_payroll_run_id")
    var reimbursedPayrollRunId: UUID? = null,
    @Column(name = "remarks")
    var remarks: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "expense_claim_line")
class ExpenseClaimLine(
    @Column(name = "claim_id", nullable = false)
    var claimId: UUID,
    @Column(name = "category_id", nullable = false)
    var categoryId: UUID,
    @Column(name = "expense_date", nullable = false)
    var expenseDate: LocalDate,
    @Column(name = "description", nullable = false, length = 255)
    var description: String,
    @Column(name = "merchant_name", length = 128)
    var merchantName: String? = null,
    @Column(name = "unit_quantity", precision = 10, scale = 2)
    var unitQuantity: BigDecimal? = null,
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    var amount: BigDecimal,
    @Column(name = "receipt_key", length = 255)
    var receiptKey: String? = null,
    @Column(name = "receipt_url")
    var receiptUrl: String? = null,
    @Column(name = "receipt_mime_type", length = 64)
    var receiptMimeType: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ocr_extracted", columnDefinition = "jsonb")
    var ocrExtracted: String? = null,
    @Column(name = "approved_amount", precision = 12, scale = 2)
    var approvedAmount: BigDecimal? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: ExpenseLineStatus = ExpenseLineStatus.PENDING,
    @Column(name = "line_rejection_reason")
    var lineRejectionReason: String? = null,
) : TenantScopedEntity()
