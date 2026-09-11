package com.hr.payroll.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface PayGroupRepository : JpaRepository<PayGroup, UUID> {
    fun findByCode(code: String): Optional<PayGroup>
    fun findAllByCountryCode(countryCode: String): List<PayGroup>
}

@Repository
interface PayPeriodRepository : JpaRepository<PayPeriod, UUID> {
    fun findByPayGroupIdAndCode(payGroupId: UUID, code: String): Optional<PayPeriod>
    fun findAllByPayGroupIdOrderByStartDateAsc(payGroupId: UUID): List<PayPeriod>
}

@Repository
interface PayrollRunRepository : JpaRepository<PayrollRun, UUID> {
    fun findByPayPeriodId(payPeriodId: UUID): List<PayrollRun>
    fun findByPayGroupIdAndPayPeriodIdAndRunNumber(
        payGroupId: UUID,
        payPeriodId: UUID,
        runNumber: Int,
    ): Optional<PayrollRun>
}

@Repository
interface PayrollResultRepository : JpaRepository<PayrollResult, UUID> {
    fun findAllByPayrollRunId(payrollRunId: UUID): List<PayrollResult>
    fun findByPayrollRunIdAndEmployeeId(payrollRunId: UUID, employeeId: UUID): Optional<PayrollResult>
    fun findAllByEmployeeIdOrderByCreatedAtDesc(employeeId: UUID): List<PayrollResult>
    fun findByIdAndEmployeeId(id: UUID, employeeId: UUID): Optional<PayrollResult>
}

@Repository
interface PayrollResultLineRepository : JpaRepository<PayrollResultLine, UUID> {
    fun findAllByPayrollResultId(payrollResultId: UUID): List<PayrollResultLine>
}
