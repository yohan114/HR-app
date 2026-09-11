package com.hr.loan.internal

import com.hr.loan.LoanStatus
import com.hr.loan.ScheduleStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LoanTypeRepository : JpaRepository<LoanType, UUID> {
    fun findAllByIsActiveTrueOrderByCodeAsc(): List<LoanType>
    fun findByCode(code: String): LoanType?
}

@Repository
interface EmployeeLoanRepository : JpaRepository<EmployeeLoan, UUID> {
    fun findAllByEmployeeIdOrderByCreatedAtDesc(employeeId: UUID): List<EmployeeLoan>
    fun findByIdAndEmployeeId(id: UUID, employeeId: UUID): EmployeeLoan?
    fun countByEmployeeIdAndStatusIn(employeeId: UUID, statuses: List<LoanStatus>): Long
}

@Repository
interface LoanRepaymentScheduleRepository : JpaRepository<LoanRepaymentSchedule, UUID> {
    fun findAllByLoanIdOrderByInstallmentNumberAsc(loanId: UUID): List<LoanRepaymentSchedule>
    fun findAllByLoanIdAndStatus(loanId: UUID, status: ScheduleStatus): List<LoanRepaymentSchedule>
}
