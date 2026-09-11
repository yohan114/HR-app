package com.hr.timesheet.internal

import com.hr.attendance.AttendanceProcessorService
import com.hr.employee.EmployeeLookupService
import com.hr.timesheet.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.UUID

@Service
@Transactional
class TimesheetService(
    private val clientRepository: TimesheetClientRepository,
    private val projectRepository: TimesheetProjectRepository,
    private val activityRepository: TimesheetActivityRepository,
    private val timesheetRepository: TimesheetRepository,
    private val entryRepository: TimesheetEntryRepository,
    private val employeeLookupService: EmployeeLookupService,
    private val attendanceProcessorService: AttendanceProcessorService,
) {

    @Transactional
    fun listClients(): TimesheetClientListResponse {
        var clients = clientRepository.findAllByOrderByClientNameAsc()
        if (clients.isEmpty()) {
            val defaultClient = TimesheetClientEntity(
                clientCode = "ACME",
                clientName = "Acme Global Solutions",
                isActive = true,
                billingCurrency = "USD",
                contactEmail = "billing@acmeglobal.example",
            )
            val savedClient = clientRepository.save(defaultClient)

            val project1 = TimesheetProjectEntity(
                clientId = savedClient.id,
                projectCode = "PRJ-001",
                projectName = "Enterprise HR Mobile Platform 2.0",
                description = "Core HR platform enhancement and microservices",
                billable = true,
                status = "ACTIVE",
                budgetHours = BigDecimal.valueOf(800),
            )
            val project2 = TimesheetProjectEntity(
                clientId = savedClient.id,
                projectCode = "PRJ-002",
                projectName = "Internal Developer Tools & CI/CD Pipelines",
                description = "Automation pipelines and developer workflows",
                billable = false,
                status = "ACTIVE",
                budgetHours = BigDecimal.valueOf(400),
            )
            val savedP1 = projectRepository.save(project1)
            val savedP2 = projectRepository.save(project2)

            activityRepository.save(
                TimesheetActivityEntity(
                    projectId = savedP1.id,
                    activityCode = "DEV",
                    activityName = "Architecture & Core Feature Development",
                    billable = true,
                    isActive = true,
                )
            )
            activityRepository.save(
                TimesheetActivityEntity(
                    projectId = savedP1.id,
                    activityCode = "QA",
                    activityName = "Quality Assurance & Automated Testing",
                    billable = true,
                    isActive = true,
                )
            )
            activityRepository.save(
                TimesheetActivityEntity(
                    projectId = savedP2.id,
                    activityCode = "INFRA",
                    activityName = "Environment Setup & Gradle Optimization",
                    billable = false,
                    isActive = true,
                )
            )
            clients = listOf(savedClient)
        }
        return TimesheetClientListResponse(clients.map { it.toItem() })
    }

    @Transactional(readOnly = true)
    fun listProjects(clientId: UUID?): TimesheetProjectListResponse {
        val projects = if (clientId != null) {
            projectRepository.findAllByClientIdOrderByProjectNameAsc(clientId)
        } else {
            projectRepository.findAllByOrderByProjectNameAsc()
        }

        val clientMap = clientRepository.findAll().associateBy({ it.id }, { it.clientName })
        val items = projects.map { it.toItem(clientMap[it.clientId]) }
        return TimesheetProjectListResponse(items)
    }

    fun createProject(request: TimesheetProjectCreateRequest): TimesheetProjectItem {
        val client = clientRepository.findById(request.clientId)
            .orElseThrow { IllegalArgumentException("Client not found with id: ${request.clientId}") }

        val project = TimesheetProjectEntity(
            clientId = request.clientId,
            projectCode = request.projectCode,
            projectName = request.projectName,
            description = request.description,
            billable = request.billable,
            status = "ACTIVE",
            budgetHours = request.budgetHours?.let { BigDecimal.valueOf(it) },
        )
        val saved = projectRepository.save(project)
        return saved.toItem(client.clientName)
    }

    @Transactional(readOnly = true)
    fun listActivities(projectId: UUID?): TimesheetActivityListResponse {
        val activities = if (projectId != null) {
            activityRepository.findAllByProjectIdOrderByActivityNameAsc(projectId)
        } else {
            activityRepository.findAllByOrderByActivityNameAsc()
        }
        return TimesheetActivityListResponse(activities.map { it.toItem() })
    }

    @Transactional(readOnly = true)
    fun listTimesheets(employeeId: UUID?, status: String?): TimesheetListResponse {
        val normalizedStatus = status?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
        val list = timesheetRepository.filterTimesheets(employeeId, normalizedStatus)
        val employeeMap = employeeLookupService.findAll().associateBy({ it.id }, { it.displayName })
        val items = list.map { ts ->
            ts.toListItem(employeeMap[ts.employeeId] ?: "Employee ${ts.employeeId.toString().take(8)}")
        }
        return TimesheetListResponse(items)
    }

    @Transactional(readOnly = true)
    fun getTimesheet(id: UUID): TimesheetDetailResponse {
        val ts = timesheetRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Timesheet not found with id: $id") }
        return toDetailResponse(ts)
    }

    fun saveTimesheet(request: TimesheetSaveRequest, currentUserId: UUID? = null): TimesheetDetailResponse {
        val empId = request.employeeId ?: currentUserId
            ?: throw IllegalArgumentException("Employee ID is required")

        // Align to Monday start
        val weekStart = request.weekStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)

        // Validate max 24 hours per day
        val daySumMap = mutableMapOf<LocalDate, Double>()
        request.entries.forEach { entry ->
            if (entry.hours < 0.0 || entry.hours > 24.0) {
                throw IllegalArgumentException("Entry hours must be between 0 and 24")
            }
            val currentDaySum = (daySumMap[entry.entryDate] ?: 0.0) + entry.hours
            if (currentDaySum > 24.0) {
                throw IllegalArgumentException("Total hours for ${entry.entryDate} cannot exceed 24.0 (current sum: $currentDaySum)")
            }
            daySumMap[entry.entryDate] = currentDaySum
        }

        val timesheet = if (request.id != null) {
            val existing = timesheetRepository.findById(request.id)
                .orElseThrow { IllegalArgumentException("Timesheet not found with id: ${request.id}") }
            if (existing.status != TimesheetStatus.DRAFT.name && existing.status != TimesheetStatus.REJECTED.name) {
                throw IllegalStateException("Only DRAFT or REJECTED timesheets can be edited. Current status: ${existing.status}")
            }
            existing.comments = request.comments
            existing
        } else {
            timesheetRepository.findByEmployeeIdAndWeekStartDate(empId, weekStart) ?: TimesheetEntity(
                employeeId = empId,
                weekStartDate = weekStart,
                weekEndDate = weekEnd,
                status = TimesheetStatus.DRAFT.name,
            ).apply {
                comments = request.comments
            }
        }

        var totalHours = BigDecimal.ZERO
        var billableHours = BigDecimal.ZERO

        request.entries.forEach { e ->
            val h = BigDecimal.valueOf(e.hours).setScale(2, RoundingMode.HALF_UP)
            totalHours = totalHours.add(h)
            if (e.billable) {
                billableHours = billableHours.add(h)
            }
        }

        timesheet.totalHours = totalHours
        timesheet.billableHours = billableHours
        timesheet.comments = request.comments
        if (timesheet.status == TimesheetStatus.REJECTED.name) {
            timesheet.status = TimesheetStatus.DRAFT.name
        }

        val savedTs = timesheetRepository.save(timesheet)

        // Replace entries
        entryRepository.deleteByTimesheetId(savedTs.id)
        val entriesToSave = request.entries.map { input ->
            TimesheetEntryEntity(
                timesheetId = savedTs.id,
                projectId = input.projectId,
                activityId = input.activityId,
                entryDate = input.entryDate,
                hours = BigDecimal.valueOf(input.hours).setScale(2, RoundingMode.HALF_UP),
                billable = input.billable,
                notes = input.notes,
            )
        }
        entryRepository.saveAll(entriesToSave)

        return toDetailResponse(savedTs)
    }

    fun submitTimesheet(id: UUID): TimesheetDetailResponse {
        val ts = timesheetRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Timesheet not found with id: $id") }

        if (ts.status != TimesheetStatus.DRAFT.name && ts.status != TimesheetStatus.REJECTED.name) {
            throw IllegalStateException("Only DRAFT or REJECTED timesheets can be submitted. Current: ${ts.status}")
        }

        val entries = entryRepository.findAllByTimesheetIdOrderByEntryDateAsc(ts.id)
        if (entries.isEmpty()) {
            throw IllegalStateException("Cannot submit timesheet with zero entries")
        }

        ts.status = TimesheetStatus.SUBMITTED.name
        ts.submittedAt = Instant.now()
        ts.rejectionReason = null
        val saved = timesheetRepository.save(ts)
        return toDetailResponse(saved)
    }

    fun approveTimesheet(id: UUID, approverId: UUID?, comments: String?): TimesheetDetailResponse {
        val ts = timesheetRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Timesheet not found with id: $id") }

        if (ts.status != TimesheetStatus.SUBMITTED.name) {
            throw IllegalStateException("Only SUBMITTED timesheets can be approved. Current: ${ts.status}")
        }

        ts.status = TimesheetStatus.APPROVED.name
        ts.approvedAt = Instant.now()
        ts.approverId = approverId
        if (!comments.isNullOrBlank()) {
            ts.comments = if (ts.comments.isNullOrBlank()) comments else "${ts.comments}\n[Approver]: $comments"
        }
        val saved = timesheetRepository.save(ts)
        return toDetailResponse(saved)
    }

    fun rejectTimesheet(id: UUID, approverId: UUID?, reason: String): TimesheetDetailResponse {
        val ts = timesheetRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Timesheet not found with id: $id") }

        if (ts.status != TimesheetStatus.SUBMITTED.name) {
            throw IllegalStateException("Only SUBMITTED timesheets can be rejected. Current: ${ts.status}")
        }

        ts.status = TimesheetStatus.REJECTED.name
        ts.approverId = approverId
        ts.rejectionReason = reason
        val saved = timesheetRepository.save(ts)
        return toDetailResponse(saved)
    }

    fun copyPreviousTimesheet(request: TimesheetCopyPreviousRequest, currentUserId: UUID? = null): TimesheetDetailResponse {
        val empId = request.employeeId ?: currentUserId
            ?: throw IllegalArgumentException("Employee ID is required")

        val targetStart = request.targetWeekStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val targetEnd = targetStart.plusDays(6)
        val prevStart = targetStart.minusWeeks(1)

        val prevTs = timesheetRepository.findByEmployeeIdAndWeekStartDate(empId, prevStart)
        val prevEntries = if (prevTs != null) {
            entryRepository.findAllByTimesheetIdOrderByEntryDateAsc(prevTs.id)
        } else {
            emptyList()
        }

        val targetTs = timesheetRepository.findByEmployeeIdAndWeekStartDate(empId, targetStart)
            ?: TimesheetEntity(
                employeeId = empId,
                weekStartDate = targetStart,
                weekEndDate = targetEnd,
                status = TimesheetStatus.DRAFT.name,
            )

        if (targetTs.status != TimesheetStatus.DRAFT.name && targetTs.status != TimesheetStatus.REJECTED.name) {
            throw IllegalStateException("Target timesheet exists and is not in editable state (${targetTs.status})")
        }

        val savedTarget = timesheetRepository.save(targetTs)
        entryRepository.deleteByTimesheetId(savedTarget.id)

        var totalHours = BigDecimal.ZERO
        var billableHours = BigDecimal.ZERO

        val newEntries = prevEntries.map { prev ->
            // Shift date forward by days between previous and target week
            val dayDiff = java.time.temporal.ChronoUnit.DAYS.between(prevStart, prev.entryDate)
            val newDate = targetStart.plusDays(dayDiff)

            totalHours = totalHours.add(prev.hours)
            if (prev.billable) {
                billableHours = billableHours.add(prev.hours)
            }

            TimesheetEntryEntity(
                timesheetId = savedTarget.id,
                projectId = prev.projectId,
                activityId = prev.activityId,
                entryDate = newDate,
                hours = prev.hours,
                billable = prev.billable,
                notes = prev.notes,
            )
        }

        savedTarget.totalHours = totalHours
        savedTarget.billableHours = billableHours
        timesheetRepository.save(savedTarget)
        entryRepository.saveAll(newEntries)

        return toDetailResponse(savedTarget)
    }

    @Transactional(readOnly = true)
    fun reconcileWithAttendance(employeeId: UUID, weekStartDate: LocalDate): TimesheetReconciliationResponse {
        val weekStart = weekStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)

        val timesheet = timesheetRepository.findByEmployeeIdAndWeekStartDate(employeeId, weekStart)
        val entries = if (timesheet != null) {
            entryRepository.findAllByTimesheetIdOrderByEntryDateAsc(timesheet.id)
        } else {
            emptyList()
        }

        val loggedByDate = entries.groupBy { it.entryDate }
            .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.hours.toDouble() } }

        val attendanceList = attendanceProcessorService.getAttendanceRecords(
            employeeId, weekStart, weekEnd
        )
        val attendanceByDate = attendanceList.associateBy({ it.workDate }, { it.netWorkedMinutes / 60.0 })

        var totalLogged = 0.0
        var totalAttendance = 0.0

        val dailyList = (0..6).map { dayOffset ->
            val date = weekStart.plusDays(dayOffset.toLong())
            val logged = loggedByDate[date] ?: 0.0
            val attendance = attendanceByDate[date] ?: 0.0
            val variance = BigDecimal.valueOf(logged - attendance).setScale(2, RoundingMode.HALF_UP).toDouble()

            totalLogged += logged
            totalAttendance += attendance

            val status = when {
                logged == 0.0 && attendance == 0.0 -> ReconciliationStatus.NO_ATTENDANCE.name
                kotlin.math.abs(variance) <= 0.1 -> ReconciliationStatus.MATCHED.name
                variance < -0.1 -> ReconciliationStatus.UNDER_LOGGED.name
                else -> ReconciliationStatus.OVER_LOGGED.name
            }

            DailyReconciliationItem(
                date = date,
                loggedHours = logged,
                attendanceHours = attendance,
                varianceHours = variance,
                status = status,
            )
        }

        val totalVariance = BigDecimal.valueOf(totalLogged - totalAttendance).setScale(2, RoundingMode.HALF_UP).toDouble()

        return TimesheetReconciliationResponse(
            employeeId = employeeId,
            weekStartDate = weekStart,
            weekEndDate = weekEnd,
            totalLoggedHours = BigDecimal.valueOf(totalLogged).setScale(2, RoundingMode.HALF_UP).toDouble(),
            totalAttendanceHours = BigDecimal.valueOf(totalAttendance).setScale(2, RoundingMode.HALF_UP).toDouble(),
            totalVarianceHours = totalVariance,
            dailyBreakdown = dailyList,
        )
    }

    private fun toDetailResponse(ts: TimesheetEntity): TimesheetDetailResponse {
        val emp = employeeLookupService.findById(ts.employeeId)
        val approver = ts.approverId?.let { employeeLookupService.findById(it) }
        val entries = entryRepository.findAllByTimesheetIdOrderByEntryDateAsc(ts.id)

        val projectMap = projectRepository.findAll().associateBy { it.id }
        val activityMap = activityRepository.findAll().associateBy { it.id }

        val entryItems = entries.map { e ->
            val p = projectMap[e.projectId]
            val a = activityMap[e.activityId]
            TimesheetEntryItem(
                id = e.id,
                projectId = e.projectId,
                projectCode = p?.projectCode ?: "PRJ",
                projectName = p?.projectName ?: "Project",
                activityId = e.activityId,
                activityCode = a?.activityCode ?: "ACT",
                activityName = a?.activityName ?: "Activity",
                entryDate = e.entryDate,
                hours = e.hours.toDouble(),
                billable = e.billable,
                notes = e.notes,
            )
        }

        return TimesheetDetailResponse(
            id = ts.id,
            employeeId = ts.employeeId,
            employeeName = emp?.displayName ?: "Employee ${ts.employeeId.toString().take(8)}",
            weekStartDate = ts.weekStartDate,
            weekEndDate = ts.weekEndDate,
            status = ts.status,
            totalHours = ts.totalHours.toDouble(),
            billableHours = ts.billableHours.toDouble(),
            nonBillableHours = ts.nonBillableHours.toDouble(),
            submittedAt = ts.submittedAt?.atOffset(ZoneOffset.UTC),
            approvedAt = ts.approvedAt?.atOffset(ZoneOffset.UTC),
            approverId = ts.approverId,
            approverName = approver?.displayName,
            rejectionReason = ts.rejectionReason,
            comments = ts.comments,
            entries = entryItems,
        )
    }

    private fun TimesheetClientEntity.toItem(): TimesheetClientItem =
        TimesheetClientItem(
            id = id,
            clientCode = clientCode,
            clientName = clientName,
            status = status,
            billingCurrency = billingCurrency,
            contactEmail = contactEmail,
        )

    private fun TimesheetProjectEntity.toItem(clientName: String?): TimesheetProjectItem =
        TimesheetProjectItem(
            id = id,
            clientId = clientId,
            clientName = clientName,
            projectCode = projectCode,
            projectName = projectName,
            description = description,
            billable = billable,
            status = status,
            budgetHours = budgetHours?.toDouble(),
        )

    private fun TimesheetActivityEntity.toItem(): TimesheetActivityItem =
        TimesheetActivityItem(
            id = id,
            projectId = projectId,
            activityCode = activityCode,
            activityName = activityName,
            billable = billable,
            status = status,
        )

    private fun TimesheetEntity.toListItem(employeeName: String): TimesheetListItem =
        TimesheetListItem(
            id = id,
            employeeId = employeeId,
            employeeName = employeeName,
            weekStartDate = weekStartDate,
            weekEndDate = weekEndDate,
            status = status,
            totalHours = totalHours.toDouble(),
            billableHours = billableHours.toDouble(),
            nonBillableHours = nonBillableHours.toDouble(),
            submittedAt = submittedAt?.atOffset(ZoneOffset.UTC),
            approvedAt = approvedAt?.atOffset(ZoneOffset.UTC),
        )
}
