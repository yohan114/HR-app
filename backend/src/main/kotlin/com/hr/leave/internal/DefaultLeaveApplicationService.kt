package com.hr.leave.internal

import com.hr.employee.EmployeeLookupService
import com.hr.leave.ApplicationStatus
import com.hr.leave.DayPortion
import com.hr.leave.LeaveApplicationDayDto
import com.hr.leave.LeaveApplicationDto
import com.hr.leave.LeaveApplicationRequest
import com.hr.leave.LeaveApplicationService
import com.hr.leave.LeaveEligibilityResult
import com.hr.leave.LeaveYearStatus
import com.hr.leave.LedgerEntryType
import org.springframework.context.ApplicationEventPublisher
import com.hr.leave.LeaveAppliedEvent
import com.hr.leave.LeaveApprovedEvent
import com.hr.leave.LeaveRejectedEvent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
@Transactional
class DefaultLeaveApplicationService(
    private val employeeLookupService: EmployeeLookupService,
    private val leaveYearRepository: LeaveYearRepository,
    private val leaveTypeRepository: LeaveTypeRepository,
    private val entitlementRepository: EmployeeLeaveEntitlementRepository,
    private val ledgerRepository: LeaveLedgerRepository,
    private val applicationRepository: LeaveApplicationRepository,
    private val applicationDayRepository: LeaveApplicationDayRepository,
    private val publicHolidayRepository: PublicHolidayRepository,
    private val eventPublisher: ApplicationEventPublisher? = null,
) : LeaveApplicationService {

    override fun checkEligibility(request: LeaveApplicationRequest): LeaveEligibilityResult {
        val reasons = mutableListOf<String>()

        val employee = employeeLookupService.findById(request.employeeId)
        if (employee == null) {
            return LeaveEligibilityResult(
                eligible = false,
                workingDaysRequested = BigDecimal.ZERO,
                currentBalance = BigDecimal.ZERO,
                pendingDays = BigDecimal.ZERO,
                availableBalance = BigDecimal.ZERO,
                reasons = listOf("Employee not found: ${request.employeeId}"),
            )
        }

        if (employee.status != "ACTIVE") {
            reasons.add("Employee is not active (current status: ${employee.status})")
        }

        if (request.endDate.isBefore(request.startDate)) {
            reasons.add("End date (${request.endDate}) cannot precede start date (${request.startDate})")
        }

        val leaveType = leaveTypeRepository.findById(request.leaveTypeId).orElse(null)
        if (leaveType == null) {
            reasons.add("Leave type not found: ${request.leaveTypeId}")
        }

        // Find active leave year spanning the start date
        val activeYear = leaveYearRepository.findAll().find {
            it.status == LeaveYearStatus.ACTIVE &&
                !request.startDate.isBefore(it.startDate) &&
                !request.startDate.isAfter(it.endDate)
        }

        if (activeYear == null) {
            reasons.add("No active leave year covers start date ${request.startDate}")
        }

        // Expand calendar days and count working days
        val holidays = publicHolidayRepository.findAllByHolidayDateBetween(request.startDate, request.endDate)
            .map { it.holidayDate }
            .toSet()

        var workingDays = BigDecimal.ZERO
        var currentDate = request.startDate
        while (!currentDate.isAfter(request.endDate)) {
            val isWeekend = currentDate.dayOfWeek == DayOfWeek.SATURDAY || currentDate.dayOfWeek == DayOfWeek.SUNDAY
            val isHoliday = holidays.contains(currentDate)

            if (!isWeekend && !isHoliday) {
                val dayWeight = if (request.dayPortion == DayPortion.FULL_DAY) {
                    BigDecimal.ONE
                } else {
                    BigDecimal("0.50")
                }
                workingDays = workingDays.add(dayWeight)
            }
            currentDate = currentDate.plusDays(1)
        }

        if (workingDays <= BigDecimal.ZERO) {
            reasons.add("No working days in requested date range (all selected dates fall on weekends or public holidays)")
        }

        // Check for overlapping applications
        val overlapping = applicationRepository.findByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            employeeId = request.employeeId,
            statuses = listOf(ApplicationStatus.SUBMITTED, ApplicationStatus.APPROVED),
            endCheck = request.endDate,
            startCheck = request.startDate,
        )

        if (overlapping.isNotEmpty()) {
            val conflict = overlapping.first()
            reasons.add("Overlapping leave application (${conflict.status}) already exists from ${conflict.startDate} to ${conflict.endDate}")
        }

        // Check balance
        val entitlement = if (activeYear != null) {
            entitlementRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(
                request.employeeId, activeYear.id, request.leaveTypeId
            )
        } else null

        val currentBalance = entitlement?.balance ?: BigDecimal.ZERO
        val pendingApplications = applicationRepository.findAllByEmployeeIdAndLeaveTypeIdAndStatus(
            request.employeeId, request.leaveTypeId, ApplicationStatus.SUBMITTED
        )
        val pendingDays = pendingApplications.fold(BigDecimal.ZERO) { acc, app -> acc.add(app.totalDays) }
        val availableBalance = currentBalance.subtract(pendingDays)

        if (workingDays > BigDecimal.ZERO && availableBalance < workingDays) {
            reasons.add("Insufficient leave balance (available: $availableBalance days, requested: $workingDays days)")
        }

        return LeaveEligibilityResult(
            eligible = reasons.isEmpty(),
            workingDaysRequested = workingDays.setScale(2, RoundingMode.HALF_UP),
            currentBalance = currentBalance.setScale(2, RoundingMode.HALF_UP),
            pendingDays = pendingDays.setScale(2, RoundingMode.HALF_UP),
            availableBalance = availableBalance.setScale(2, RoundingMode.HALF_UP),
            reasons = reasons,
        )
    }

    override fun submitApplication(request: LeaveApplicationRequest): LeaveApplicationDto {
        val eligibility = checkEligibility(request)
        require(eligibility.eligible) {
            "Leave application ineligible: " + eligibility.reasons.joinToString("; ")
        }

        val employee = employeeLookupService.findById(request.employeeId)
            ?: throw IllegalArgumentException("Employee not found: ${request.employeeId}")

        val leaveType = leaveTypeRepository.findById(request.leaveTypeId)
            .orElseThrow { IllegalArgumentException("Leave type not found: ${request.leaveTypeId}") }

        val activeYear = leaveYearRepository.findAll().find {
            it.status == LeaveYearStatus.ACTIVE &&
                !request.startDate.isBefore(it.startDate) &&
                !request.startDate.isAfter(it.endDate)
        } ?: throw IllegalStateException("No active leave year found for ${request.startDate}")

        val application = LeaveApplication(
            employeeId = request.employeeId,
            leaveYearId = activeYear.id,
            leaveTypeId = request.leaveTypeId,
            startDate = request.startDate,
            endDate = request.endDate,
            totalDays = eligibility.workingDaysRequested,
            dayPortion = request.dayPortion,
            status = ApplicationStatus.SUBMITTED,
            reason = request.reason,
            submittedAt = Instant.now(),
        ).also { it.tenantId = employee.tenantId }

        val savedApp = applicationRepository.save(application)

        // Expand schedule breakdown
        val holidays = publicHolidayRepository.findAllByHolidayDateBetween(request.startDate, request.endDate)
            .map { it.holidayDate }
            .toSet()

        val dayDtos = mutableListOf<LeaveApplicationDayDto>()
        var currentDate = request.startDate
        while (!currentDate.isAfter(request.endDate)) {
            val isWeekend = currentDate.dayOfWeek == DayOfWeek.SATURDAY || currentDate.dayOfWeek == DayOfWeek.SUNDAY
            val isHoliday = holidays.contains(currentDate)
            val isWorking = !isWeekend && !isHoliday

            val hours = when {
                !isWorking -> BigDecimal.ZERO
                request.dayPortion == DayPortion.FULL_DAY -> BigDecimal("8.00")
                else -> BigDecimal("4.00")
            }

            val appDay = LeaveApplicationDay(
                leaveApplicationId = savedApp.id,
                dayDate = currentDate,
                dayPortion = request.dayPortion,
                isWorkingDay = isWorking,
                hours = hours,
            ).also { it.tenantId = employee.tenantId }

            val savedDay = applicationDayRepository.save(appDay)
            dayDtos.add(
                LeaveApplicationDayDto(
                    id = savedDay.id,
                    date = savedDay.dayDate,
                    dayPortion = savedDay.dayPortion,
                    isWorkingDay = savedDay.isWorkingDay,
                    hours = savedDay.hours,
                )
            )

            currentDate = currentDate.plusDays(1)
        }

        val resultDto = toDto(savedApp, leaveType.code, leaveType.name, dayDtos)
        eventPublisher?.publishEvent(
            LeaveAppliedEvent(
                tenantId = employee.tenantId,
                applicationId = savedApp.id,
                employeeId = savedApp.employeeId,
                leaveTypeName = leaveType.name,
                startDate = savedApp.startDate,
                endDate = savedApp.endDate,
                totalDays = savedApp.totalDays,
            ),
        )
        return resultDto
    }

    override fun approveApplication(
        applicationId: UUID,
        approverId: UUID,
        remarks: String?,
    ): LeaveApplicationDto {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { IllegalArgumentException("Application not found: $applicationId") }

        check(application.status == ApplicationStatus.SUBMITTED) {
            "Cannot approve application with status ${application.status}"
        }

        val entitlement = entitlementRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(
            application.employeeId, application.leaveYearId, application.leaveTypeId
        ) ?: throw IllegalStateException("No entitlement found for employee ${application.employeeId} and leave type ${application.leaveTypeId}")

        check(entitlement.balance >= application.totalDays) {
            "Insufficient balance at approval time: balance=${entitlement.balance}, requested=${application.totalDays}"
        }

        // 1. Update application state
        application.status = ApplicationStatus.APPROVED
        application.actionedAt = Instant.now()
        application.actionedBy = approverId
        application.actionReason = remarks
        applicationRepository.save(application)

        // 2. Update projection
        val newBalance = entitlement.balance.subtract(application.totalDays)
        entitlement.taken = entitlement.taken.add(application.totalDays)
        entitlement.balance = newBalance
        entitlement.updatedAt = Instant.now()
        entitlementRepository.save(entitlement)

        // 3. Post TAKEN debit entry to append-only leave ledger
        val ledgerEntry = LeaveLedgerEntry(
            employeeId = application.employeeId,
            leaveYearId = application.leaveYearId,
            leaveTypeId = application.leaveTypeId,
            entryType = LedgerEntryType.TAKEN,
            days = application.totalDays.negate(),
            referenceType = "LEAVE_APPLICATION",
            referenceId = application.id.toString(),
            effectiveDate = application.startDate,
            balanceAfter = newBalance,
            remarks = remarks ?: "Approved leave from ${application.startDate} to ${application.endDate}",
        ).also { it.tenantId = application.tenantId }

        ledgerRepository.save(ledgerEntry)

        val approvedDto = getApplication(applicationId)
        val leaveType = leaveTypeRepository.findById(application.leaveTypeId).orElse(null)
        val tenantId = application.tenantId
            ?: employeeLookupService.findById(application.employeeId)?.tenantId
            ?: UUID.randomUUID()
        eventPublisher?.publishEvent(
            LeaveApprovedEvent(
                tenantId = tenantId,
                applicationId = application.id,
                employeeId = application.employeeId,
                approverId = approverId,
                leaveTypeName = leaveType?.name ?: "Leave",
                startDate = application.startDate,
                endDate = application.endDate,
                totalDays = application.totalDays,
                remarks = remarks,
            ),
        )
        return approvedDto
    }

    override fun rejectApplication(
        applicationId: UUID,
        approverId: UUID,
        reason: String,
    ): LeaveApplicationDto {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { IllegalArgumentException("Application not found: $applicationId") }

        check(application.status == ApplicationStatus.SUBMITTED) {
            "Cannot reject application with status ${application.status}"
        }

        application.status = ApplicationStatus.REJECTED
        application.actionedAt = Instant.now()
        application.actionedBy = approverId
        application.actionReason = reason
        applicationRepository.save(application)

        val rejectedDto = getApplication(applicationId)
        val leaveType = leaveTypeRepository.findById(application.leaveTypeId).orElse(null)
        val tenantId = application.tenantId
            ?: employeeLookupService.findById(application.employeeId)?.tenantId
            ?: UUID.randomUUID()
        eventPublisher?.publishEvent(
            LeaveRejectedEvent(
                tenantId = tenantId,
                applicationId = application.id,
                employeeId = application.employeeId,
                approverId = approverId,
                leaveTypeName = leaveType?.name ?: "Leave",
                startDate = application.startDate,
                endDate = application.endDate,
                totalDays = application.totalDays,
                reason = reason,
            ),
        )
        return rejectedDto
    }

    override fun cancelApplication(
        applicationId: UUID,
        cancelledBy: UUID,
        reason: String,
    ): LeaveApplicationDto {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { IllegalArgumentException("Application not found: $applicationId") }

        return when (application.status) {
            ApplicationStatus.APPROVED -> {
                // Reversal flow
                val entitlement = entitlementRepository.findByEmployeeIdAndLeaveYearIdAndLeaveTypeId(
                    application.employeeId, application.leaveYearId, application.leaveTypeId
                ) ?: throw IllegalStateException("Entitlement projection not found for leave cancellation")

                val newBalance = entitlement.balance.add(application.totalDays)
                entitlement.taken = entitlement.taken.subtract(application.totalDays)
                entitlement.balance = newBalance
                entitlement.updatedAt = Instant.now()
                entitlementRepository.save(entitlement)

                val reversalEntry = LeaveLedgerEntry(
                    employeeId = application.employeeId,
                    leaveYearId = application.leaveYearId,
                    leaveTypeId = application.leaveTypeId,
                    entryType = LedgerEntryType.CANCELLED,
                    days = application.totalDays,
                    referenceType = "LEAVE_CANCELLATION",
                    referenceId = application.id.toString(),
                    effectiveDate = LocalDate.now(),
                    balanceAfter = newBalance,
                    remarks = "Reversal of cancelled leave application: $reason",
                ).also { it.tenantId = application.tenantId }

                ledgerRepository.save(reversalEntry)

                application.status = ApplicationStatus.CANCELLED
                application.actionedAt = Instant.now()
                application.actionedBy = cancelledBy
                application.actionReason = reason
                applicationRepository.save(application)

                getApplication(applicationId)
            }
            ApplicationStatus.SUBMITTED -> {
                withdrawApplication(applicationId, cancelledBy)
            }
            else -> {
                throw IllegalStateException("Cannot cancel application with status ${application.status}")
            }
        }
    }

    override fun withdrawApplication(
        applicationId: UUID,
        employeeId: UUID,
    ): LeaveApplicationDto {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { IllegalArgumentException("Application not found: $applicationId") }

        check(application.status == ApplicationStatus.SUBMITTED) {
            "Cannot withdraw application with status ${application.status}"
        }

        application.status = ApplicationStatus.WITHDRAWN
        application.actionedAt = Instant.now()
        application.actionedBy = employeeId
        application.actionReason = "Withdrawn by applicant"
        applicationRepository.save(application)

        return getApplication(applicationId)
    }

    override fun getApplication(applicationId: UUID): LeaveApplicationDto {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { IllegalArgumentException("Application not found: $applicationId") }

        val leaveType = leaveTypeRepository.findById(application.leaveTypeId).orElse(null)
        val days = applicationDayRepository.findAllByLeaveApplicationIdOrderByDayDateAsc(applicationId).map {
            LeaveApplicationDayDto(
                id = it.id,
                date = it.dayDate,
                dayPortion = it.dayPortion,
                isWorkingDay = it.isWorkingDay,
                hours = it.hours,
            )
        }

        return toDto(
            application,
            leaveType?.code ?: "UNKNOWN",
            leaveType?.name ?: "Unknown Leave",
            days,
        )
    }

    override fun getEmployeeApplications(employeeId: UUID): List<LeaveApplicationDto> {
        val apps = applicationRepository.findAllByEmployeeIdOrderBySubmittedAtDesc(employeeId)
        val leaveTypes = leaveTypeRepository.findAll().associateBy { it.id }

        return apps.map { app ->
            val days = applicationDayRepository.findAllByLeaveApplicationIdOrderByDayDateAsc(app.id).map {
                LeaveApplicationDayDto(
                    id = it.id,
                    date = it.dayDate,
                    dayPortion = it.dayPortion,
                    isWorkingDay = it.isWorkingDay,
                    hours = it.hours,
                )
            }
            val lt = leaveTypes[app.leaveTypeId]
            toDto(app, lt?.code ?: "UNKNOWN", lt?.name ?: "Unknown Leave", days)
        }
    }

    private fun toDto(
        app: LeaveApplication,
        typeCode: String,
        typeName: String,
        days: List<LeaveApplicationDayDto>,
    ) = LeaveApplicationDto(
        id = app.id,
        employeeId = app.employeeId,
        leaveYearId = app.leaveYearId,
        leaveTypeId = app.leaveTypeId,
        leaveTypeCode = typeCode,
        leaveTypeName = typeName,
        startDate = app.startDate,
        endDate = app.endDate,
        totalDays = app.totalDays,
        dayPortion = app.dayPortion,
        status = app.status,
        reason = app.reason,
        submittedAt = app.submittedAt,
        actionedAt = app.actionedAt,
        actionedBy = app.actionedBy,
        actionReason = app.actionReason,
        days = days,
    )
}
