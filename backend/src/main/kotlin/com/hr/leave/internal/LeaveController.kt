package com.hr.leave.internal

import com.hr.employee.EmployeeLookupService
import com.hr.identity.Caller
import com.hr.leave.ApplicationStatus
import com.hr.leave.DayPortion
import com.hr.leave.LeaveApplicationDto
import com.hr.leave.LeaveApplicationRequest as ServiceLeaveApplicationRequest
import com.hr.leave.LeaveApplicationService
import com.hr.leave.LeaveBalanceService
import com.hr.leave.LeaveYearStatus
import com.hr.leave.LedgerEntryType
import com.hr.shared.api.NotFoundException
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

// ---------------------------------------------------------------------------
// DTOs matching spec/openapi.yaml
// ---------------------------------------------------------------------------

data class LeaveBalanceItemResponse(
    val leaveTypeId: String,
    val leaveTypeCode: String,
    val leaveTypeName: String,
    val color: String,
    val entitledDays: Double,
    val accruedDays: Double,
    val takenDays: Double,
    val pendingDays: Double,
    val availableDays: Double,
)

data class LeaveBalancesResponse(
    val leaveYear: String,
    val balances: List<LeaveBalanceItemResponse>,
)

data class LeaveApplicationDayItemResponse(
    val date: LocalDate,
    val dayOfWeek: String,
    val isWorkingDay: Boolean,
    val isPublicHoliday: Boolean,
    val holidayName: String? = null,
    val portion: String,
    val hours: Double,
)

data class LeaveApplicationItemResponse(
    val id: UUID,
    val employeeId: UUID,
    val employeeName: String,
    val leaveTypeId: String,
    val leaveTypeCode: String,
    val leaveTypeName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dayPortion: String,
    val totalDays: Double,
    val reason: String,
    val status: String,
    val submittedAt: OffsetDateTime,
    val approvedAt: OffsetDateTime? = null,
    val days: List<LeaveApplicationDayItemResponse> = emptyList(),
)

data class LeaveApplicationsResponse(
    val applications: List<LeaveApplicationItemResponse>,
)

data class LeaveEligibilityRequestPayload(
    val leaveTypeId: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dayPortion: String? = "FULL_DAY",
)

data class LeaveEligibilityResponsePayload(
    val eligible: Boolean,
    val workingDaysRequested: Double,
    val balanceAvailable: Double,
    val remainingAfter: Double,
    val reasons: List<String> = emptyList(),
    val days: List<LeaveApplicationDayItemResponse> = emptyList(),
)

data class LeaveApplicationRequestPayload(
    val leaveTypeId: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dayPortion: String? = "FULL_DAY",
    val reason: String,
)

data class CancelLeaveRequestPayload(
    val reason: String? = null,
)

data class LeaveLedgerEntryItemResponse(
    val id: String,
    val date: LocalDate,
    val leaveTypeId: String,
    val leaveTypeCode: String,
    val leaveTypeName: String,
    val eventType: String,
    val daysCredited: Double,
    val daysDebited: Double,
    val balanceAfter: Double,
    val referenceId: String? = null,
    val notes: String? = null,
)

data class LeaveLedgerResponse(
    val ledger: List<LeaveLedgerEntryItemResponse>,
)

data class TeamMemberLeaveDto(
    val employeeId: UUID,
    val employeeCode: String,
    val employeeName: String,
    val leaveTypeId: String,
    val leaveTypeName: String,
    val portion: String,
    val status: String,
)

data class TeamCalendarDayDto(
    val date: LocalDate,
    val dayOfWeek: Int,
    val isWeekend: Boolean,
    val isHoliday: Boolean,
    val holidayName: String? = null,
    val absences: List<TeamMemberLeaveDto> = emptyList(),
    val hasConflict: Boolean = false,
)

data class TeamCalendarResponseDto(
    val days: List<TeamCalendarDayDto>,
)

// ---------------------------------------------------------------------------
// Controller
// ---------------------------------------------------------------------------

@RestController
@RequestMapping("/v1/leave")
class LeaveController(
    private val leaveBalanceService: LeaveBalanceService,
    private val leaveApplicationService: LeaveApplicationService,
    private val leaveYearRepository: LeaveYearRepository,
    private val leaveTypeRepository: LeaveTypeRepository,
    private val leaveLedgerRepository: LeaveLedgerRepository,
    private val applicationRepository: LeaveApplicationRepository,
    private val publicHolidayRepository: PublicHolidayRepository,
    private val employeeLookupService: EmployeeLookupService,
) {

    @GetMapping("/balances")
    fun getMyLeaveBalances(
        @AuthenticationPrincipal jwt: Jwt,
    ): LeaveBalancesResponse {
        val caller = Caller.from(jwt)
        val employeeId = caller.employeeId ?: throw NotFoundException("Employee record not linked to user")
        val today = LocalDate.now()

        val activeYear = leaveYearRepository.findActiveYearAsOf(today)
            ?: leaveYearRepository.findByStatus(LeaveYearStatus.ACTIVE).firstOrNull()
            ?: leaveYearRepository.findAll().firstOrNull()
            ?: return LeaveBalancesResponse(leaveYear = today.year.toString(), balances = emptyList())

        val balances = leaveBalanceService.getAllBalancesForEmployee(employeeId, activeYear.id)

        val balanceItems = balances.map { b ->
            val pendingApps = applicationRepository.findAllByEmployeeIdAndLeaveTypeIdAndStatus(
                employeeId,
                b.leaveTypeId,
                ApplicationStatus.SUBMITTED,
            )
            val pendingDays = pendingApps.fold(BigDecimal.ZERO) { acc, a -> acc.add(a.totalDays) }
            val available = b.balance.subtract(pendingDays).max(BigDecimal.ZERO)

            val color = resolveColor(b.leaveTypeCode)

            LeaveBalanceItemResponse(
                leaveTypeId = b.leaveTypeId.toString(),
                leaveTypeCode = b.leaveTypeCode,
                leaveTypeName = b.leaveTypeName,
                color = color,
                entitledDays = b.openingBalance.add(b.accrued).toDouble(),
                accruedDays = b.accrued.toDouble(),
                takenDays = b.taken.toDouble(),
                pendingDays = pendingDays.toDouble(),
                availableDays = available.toDouble(),
            )
        }

        return LeaveBalancesResponse(
            leaveYear = activeYear.code,
            balances = balanceItems,
        )
    }

    @GetMapping("/applications")
    fun getMyLeaveApplications(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) status: String?,
    ): LeaveApplicationsResponse {
        val caller = Caller.from(jwt)
        val employeeId = caller.employeeId ?: throw NotFoundException("Employee record not linked to user")
        val employee = employeeLookupService.findById(employeeId)
        val empName = employee?.displayName ?: "Employee"

        val apps = leaveApplicationService.getEmployeeApplications(employeeId)
        val filtered = if (!status.isNullOrBlank()) {
            apps.filter { it.status.name.equals(status, ignoreCase = true) }
        } else {
            apps
        }

        val items = filtered.map { app ->
            toItemResponse(app, empName)
        }

        return LeaveApplicationsResponse(applications = items)
    }

    @PostMapping("/eligibility")
    fun checkLeaveEligibility(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody payload: LeaveEligibilityRequestPayload,
    ): LeaveEligibilityResponsePayload {
        val caller = Caller.from(jwt)
        val employeeId = caller.employeeId ?: throw NotFoundException("Employee record not linked to user")

        val leaveTypeUuid = try {
            UUID.fromString(payload.leaveTypeId)
        } catch (_: Exception) {
            val byCode = leaveTypeRepository.findByCode(payload.leaveTypeId)
                ?: throw NotFoundException("Leave type ${payload.leaveTypeId} not found")
            byCode.id
        }

        val portion = try {
            DayPortion.valueOf(payload.dayPortion ?: "FULL_DAY")
        } catch (_: Exception) {
            DayPortion.FULL_DAY
        }

        val serviceRequest = ServiceLeaveApplicationRequest(
            employeeId = employeeId,
            leaveTypeId = leaveTypeUuid,
            startDate = payload.startDate,
            endDate = payload.endDate,
            dayPortion = portion,
            reason = "Eligibility check",
        )

        val result = leaveApplicationService.checkEligibility(serviceRequest)

        // Expand calendar breakdown
        val holidays = publicHolidayRepository.findAllByHolidayDateBetween(payload.startDate, payload.endDate)
            .associateBy { it.holidayDate }

        val daysList = mutableListOf<LeaveApplicationDayItemResponse>()
        var curr = payload.startDate
        val portionHours = if (portion == DayPortion.FULL_DAY) 8.0 else 4.0

        while (!curr.isAfter(payload.endDate)) {
            val isWeekend = curr.dayOfWeek == DayOfWeek.SATURDAY || curr.dayOfWeek == DayOfWeek.SUNDAY
            val hol = holidays[curr]
            val isHoliday = hol != null
            val isWorkingDay = !isWeekend && !isHoliday

            daysList.add(
                LeaveApplicationDayItemResponse(
                    date = curr,
                    dayOfWeek = curr.dayOfWeek.name,
                    isWorkingDay = isWorkingDay,
                    isPublicHoliday = isHoliday,
                    holidayName = hol?.name,
                    portion = portion.name,
                    hours = if (isWorkingDay) portionHours else 0.0,
                )
            )
            curr = curr.plusDays(1)
        }

        val balanceAvailable = result.availableBalance.toDouble()
        val workingDays = result.workingDaysRequested.toDouble()
        val remainingAfter = (balanceAvailable - workingDays).coerceAtLeast(-999.0)

        return LeaveEligibilityResponsePayload(
            eligible = result.eligible,
            workingDaysRequested = workingDays,
            balanceAvailable = balanceAvailable,
            remainingAfter = remainingAfter,
            reasons = result.reasons,
            days = daysList,
        )
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    fun submitLeaveApplication(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody payload: LeaveApplicationRequestPayload,
    ): LeaveApplicationItemResponse {
        val caller = Caller.from(jwt)
        val employeeId = caller.employeeId ?: throw NotFoundException("Employee record not linked to user")
        val employee = employeeLookupService.findById(employeeId)
        val empName = employee?.displayName ?: "Employee"

        val leaveTypeUuid = try {
            UUID.fromString(payload.leaveTypeId)
        } catch (_: Exception) {
            val byCode = leaveTypeRepository.findByCode(payload.leaveTypeId)
                ?: throw NotFoundException("Leave type ${payload.leaveTypeId} not found")
            byCode.id
        }

        val portion = try {
            DayPortion.valueOf(payload.dayPortion ?: "FULL_DAY")
        } catch (_: Exception) {
            DayPortion.FULL_DAY
        }

        val serviceRequest = ServiceLeaveApplicationRequest(
            employeeId = employeeId,
            leaveTypeId = leaveTypeUuid,
            startDate = payload.startDate,
            endDate = payload.endDate,
            dayPortion = portion,
            reason = payload.reason,
        )

        val appDto = leaveApplicationService.submitApplication(serviceRequest)
        return toItemResponse(appDto, empName)
    }

    @PostMapping("/applications/{id}/cancel")
    fun cancelLeaveApplication(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: UUID,
        @RequestBody(required = false) payload: CancelLeaveRequestPayload?,
    ): LeaveApplicationItemResponse {
        val caller = Caller.from(jwt)
        val employeeId = caller.employeeId ?: throw NotFoundException("Employee record not linked to user")
        val employee = employeeLookupService.findById(employeeId)
        val empName = employee?.displayName ?: "Employee"

        val existing = leaveApplicationService.getApplication(id)
        if (existing.employeeId != employeeId) {
            throw NotFoundException("Leave application not found for employee")
        }

        val resultDto = when (existing.status) {
            ApplicationStatus.SUBMITTED -> {
                leaveApplicationService.withdrawApplication(id, employeeId)
            }
            ApplicationStatus.APPROVED -> {
                val reason = payload?.reason ?: "Cancelled by employee"
                leaveApplicationService.cancelApplication(id, employeeId, reason)
            }
            else -> existing
        }

        return toItemResponse(resultDto, empName)
    }

    @GetMapping("/ledger")
    fun getMyLeaveLedger(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam(required = false) leaveTypeId: String?,
    ): LeaveLedgerResponse {
        val caller = Caller.from(jwt)
        val employeeId = caller.employeeId ?: throw NotFoundException("Employee record not linked to user")

        val leaveTypeMap = leaveTypeRepository.findAll().associateBy { it.id }

        val entries = if (!leaveTypeId.isNullOrBlank()) {
            val typeUuid = try {
                UUID.fromString(leaveTypeId)
            } catch (_: Exception) {
                leaveTypeRepository.findByCode(leaveTypeId)?.id
            }

            if (typeUuid != null) {
                leaveLedgerRepository.findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(employeeId)
                    .filter { it.leaveTypeId == typeUuid }
            } else {
                emptyList()
            }
        } else {
            leaveLedgerRepository.findByEmployeeIdOrderByEffectiveDateDescCreatedAtDesc(employeeId)
        }

        val items = entries.map { entry ->
            val type = leaveTypeMap[entry.leaveTypeId]
            val eventType = mapEventType(entry.entryType)

            val days = entry.days
            val (credited, debited) = when (entry.entryType) {
                LedgerEntryType.OPENING,
                LedgerEntryType.ACCRUAL,
                LedgerEntryType.CARRY_FORWARD,
                LedgerEntryType.CANCELLED -> Pair(days.abs().toDouble(), 0.0)

                LedgerEntryType.TAKEN,
                LedgerEntryType.ENCASHMENT,
                LedgerEntryType.EXPIRY -> Pair(0.0, days.abs().toDouble())

                LedgerEntryType.ADJUSTMENT -> {
                    if (days >= BigDecimal.ZERO) Pair(days.toDouble(), 0.0)
                    else Pair(0.0, days.abs().toDouble())
                }
            }

            LeaveLedgerEntryItemResponse(
                id = entry.id.toString(),
                date = entry.effectiveDate,
                leaveTypeId = entry.leaveTypeId.toString(),
                leaveTypeCode = type?.code ?: "UNKNOWN",
                leaveTypeName = type?.name ?: "Unknown",
                eventType = eventType,
                daysCredited = credited,
                daysDebited = debited,
                balanceAfter = entry.balanceAfter.toDouble(),
                referenceId = entry.referenceId,
                notes = entry.remarks,
            )
        }

        return LeaveLedgerResponse(ledger = items)
    }

    @GetMapping("/team-calendar")
    fun getTeamCalendar(
        @RequestParam year: Int,
        @RequestParam month: Int,
    ): TeamCalendarResponseDto {
        val start = LocalDate.of(year, month, 1)
        val end = start.withDayOfMonth(start.lengthOfMonth())

        val holidays = publicHolidayRepository.findAllByHolidayDateBetween(start, end)
            .associateBy { it.holidayDate }

        val activeStatuses = listOf(ApplicationStatus.SUBMITTED, ApplicationStatus.APPROVED)
        val applications = applicationRepository.findByStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            activeStatuses,
            end,
            start,
        )

        val leaveTypeMap = leaveTypeRepository.findAll().associateBy { it.id }

        val days = mutableListOf<TeamCalendarDayDto>()
        var curr = start
        while (!curr.isAfter(end)) {
            val isWeekend = curr.dayOfWeek.value >= 6
            val hol = holidays[curr]
            val isHoliday = hol != null

            val dayApps = applications.filter { app ->
                !app.startDate.isAfter(curr) && !app.endDate.isBefore(curr)
            }

            val absences = dayApps.map { app ->
                val emp = employeeLookupService.findById(app.employeeId)
                val type = leaveTypeMap[app.leaveTypeId]
                TeamMemberLeaveDto(
                    employeeId = app.employeeId,
                    employeeCode = emp?.employeeCode ?: "EMP",
                    employeeName = emp?.displayName ?: "Employee",
                    leaveTypeId = app.leaveTypeId.toString(),
                    leaveTypeName = type?.name ?: "Leave",
                    portion = app.dayPortion.name,
                    status = app.status.name,
                )
            }

            days.add(
                TeamCalendarDayDto(
                    date = curr,
                    dayOfWeek = curr.dayOfWeek.value,
                    isWeekend = isWeekend,
                    isHoliday = isHoliday,
                    holidayName = hol?.name,
                    absences = absences,
                    hasConflict = absences.size > 1,
                )
            )
            curr = curr.plusDays(1)
        }

        return TeamCalendarResponseDto(days = days)
    }

    private fun toItemResponse(app: LeaveApplicationDto, employeeName: String): LeaveApplicationItemResponse {
        return LeaveApplicationItemResponse(
            id = app.id,
            employeeId = app.employeeId,
            employeeName = employeeName,
            leaveTypeId = app.leaveTypeId.toString(),
            leaveTypeCode = app.leaveTypeCode,
            leaveTypeName = app.leaveTypeName,
            startDate = app.startDate,
            endDate = app.endDate,
            dayPortion = app.dayPortion.name,
            totalDays = app.totalDays.toDouble(),
            reason = app.reason ?: "",
            status = app.status.name,
            submittedAt = app.submittedAt.atOffset(ZoneOffset.UTC),
            approvedAt = app.actionedAt?.atOffset(ZoneOffset.UTC),
            days = app.days.map { d ->
                LeaveApplicationDayItemResponse(
                    date = d.date,
                    dayOfWeek = d.date.dayOfWeek.name,
                    isWorkingDay = d.isWorkingDay,
                    isPublicHoliday = false,
                    holidayName = null,
                    portion = d.dayPortion.name,
                    hours = d.hours.toDouble(),
                )
            },
        )
    }

    private fun resolveColor(code: String): String {
        return when (code.uppercase()) {
            "ANNUAL" -> "#0284c7"
            "CASUAL" -> "#16a34a"
            "MEDICAL" -> "#d97706"
            "MATERNITY" -> "#8b5cf6"
            "PATERNITY" -> "#6366f1"
            "STUDY" -> "#ec4899"
            "UNPAID" -> "#64748b"
            else -> "#0ea5e9"
        }
    }

    private fun mapEventType(type: LedgerEntryType): String {
        return when (type) {
            LedgerEntryType.OPENING -> "OPENING"
            LedgerEntryType.CARRY_FORWARD -> "OPENING"
            LedgerEntryType.ACCRUAL -> "ACCRUAL"
            LedgerEntryType.TAKEN -> "TAKEN"
            LedgerEntryType.CANCELLED -> "CANCELLATION"
            LedgerEntryType.ADJUSTMENT -> "ADJUSTMENT"
            LedgerEntryType.ENCASHMENT -> "FORFEITURE"
            LedgerEntryType.EXPIRY -> "FORFEITURE"
        }
    }
}
