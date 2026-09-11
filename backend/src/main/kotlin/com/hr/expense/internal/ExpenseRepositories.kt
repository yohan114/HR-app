package com.hr.expense.internal

import com.hr.expense.ExpenseClaimStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ExpenseCategoryRepository : JpaRepository<ExpenseCategory, UUID> {
    fun findAllByIsActiveTrueOrderByCodeAsc(): List<ExpenseCategory>
    fun findByCode(code: String): ExpenseCategory?
}

@Repository
interface ExpenseClaimRepository : JpaRepository<ExpenseClaim, UUID> {
    fun findAllByEmployeeIdOrderByClaimDateDesc(employeeId: UUID): List<ExpenseClaim>
    fun findAllByEmployeeIdAndStatusOrderByClaimDateDesc(employeeId: UUID, status: ExpenseClaimStatus): List<ExpenseClaim>
    fun findByIdAndEmployeeId(id: UUID, employeeId: UUID): ExpenseClaim?
    fun countByEmployeeIdAndStatus(employeeId: UUID, status: ExpenseClaimStatus): Long
}

@Repository
interface ExpenseClaimLineRepository : JpaRepository<ExpenseClaimLine, UUID> {
    fun findAllByClaimIdOrderByExpenseDateAsc(claimId: UUID): List<ExpenseClaimLine>
    fun countByClaimId(claimId: UUID): Long
    fun deleteAllByClaimId(claimId: UUID)
}
