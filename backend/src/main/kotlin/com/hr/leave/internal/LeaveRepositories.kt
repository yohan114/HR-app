package com.hr.leave.internal

import com.hr.leave.LeaveYearStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface LeaveYearRepository : JpaRepository<LeaveYear, UUID> {
    fun findByCode(code: String): LeaveYear?

    fun findByStatus(status: LeaveYearStatus): List<LeaveYear>

    @Query("SELECT y FROM LeaveYear y WHERE :asOfDate BETWEEN y.startDate AND y.endDate AND y.status = 'ACTIVE'")
    fun findActiveYearAsOf(asOfDate: LocalDate): LeaveYear?
}

@Repository
interface LeaveTypeRepository : JpaRepository<LeaveType, UUID> {
    fun findByCode(code: String): LeaveType?

    fun findAllByOrderBySequenceAsc(): List<LeaveType>
}

@Repository
interface LeaveEntitlementRuleRepository : JpaRepository<LeaveEntitlementRule, UUID> {
    fun findByLeaveTypeId(leaveTypeId: UUID): List<LeaveEntitlementRule>
}

@Repository
interface EmployeeLeaveEntitlementRepository : JpaRepository<EmployeeLeaveEntitlement, EmployeeLeaveEntitlementId> {
    fun findByEmployeeIdAndLeaveYearId(
        employeeId: UUID,
        leaveYearId: UUID,
    ): List<EmployeeLeaveEntitlement>

    fun findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(
        employeeId: UUID,
        leaveYearId: UUID,
        leaveTypeId: UUID,
    ): EmployeeLeaveEntitlement?
}

@Repository
interface LeaveLedgerRepository : JpaRepository<LeaveLedgerEntry, UUID> {
    fun findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(
        employeeId: UUID,
    ): List<LeaveLedgerEntry>

    fun findByEmployeeIdAndLeaveYearIdOrderByEffectiveDateDescCreatedAtDesc(
        employeeId: UUID,
        leaveYearId: UUID,
    ): List<LeaveLedgerEntry>

    fun findByEmployeeIdAndLeaveYearIdAndLeaveTypeIdOrderByEffectiveDateAscCreatedAtAsc(
        employeeId: UUID,
        leaveYearId: UUID,
        leaveTypeId: UUID,
    ): List<LeaveLedgerEntry>

    fun findByEmployeeIdAndLeaveYearIdAndLeaveTypeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscCreatedAtAsc(
        employeeId: UUID,
        leaveYearId: UUID,
        leaveTypeId: UUID,
        effectiveDate: LocalDate,
    ): List<LeaveLedgerEntry>

    fun existsByEmployeeIdAndLeaveYearIdAndLeaveTypeIdAndReferenceTypeAndReferenceId(
        employeeId: UUID,
        leaveYearId: UUID,
        leaveTypeId: UUID,
        referenceType: String,
        referenceId: String,
    ): Boolean
}

@Repository
interface PublicHolidayRepository : JpaRepository<PublicHoliday, UUID> {
    fun findAllByHolidayDateBetween(startDate: LocalDate, endDate: LocalDate): List<PublicHoliday>
    fun findByCountryCodeAndHolidayDate(countryCode: String, holidayDate: LocalDate): PublicHoliday?
}

@Repository
interface LeaveApplicationRepository : JpaRepository<LeaveApplication, UUID> {
    fun findAllByEmployeeIdOrderBySubmittedAtDesc(employeeId: UUID): List<LeaveApplication>

    fun findByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
        employeeId: UUID,
        statuses: List<com.hr.leave.ApplicationStatus>,
        endCheck: LocalDate,
        startCheck: LocalDate,
    ): List<LeaveApplication>

    fun findAllByEmployeeIdAndLeaveTypeIdAndStatus(
        employeeId: UUID,
        leaveTypeId: UUID,
        status: com.hr.leave.ApplicationStatus,
    ): List<LeaveApplication>

    fun findByStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
        statuses: List<com.hr.leave.ApplicationStatus>,
        endCheck: LocalDate,
        startCheck: LocalDate,
    ): List<LeaveApplication>
}

@Repository
interface LeaveApplicationDayRepository : JpaRepository<LeaveApplicationDay, UUID> {
    fun findAllByLeaveApplicationIdOrderByDayDateAsc(leaveApplicationId: UUID): List<LeaveApplicationDay>
}

