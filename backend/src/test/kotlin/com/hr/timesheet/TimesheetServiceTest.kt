package com.hr.timesheet

import com.hr.attendance.AttendanceProcessorService
import com.hr.attendance.DailyAttendanceRecord
import com.hr.attendance.DayStatus
import com.hr.employee.EmployeeLookupService
import com.hr.timesheet.internal.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@DisplayName("Timesheet Service Unit Tests")
class TimesheetServiceTest {

    private val clientRepository = mockk<TimesheetClientRepository>()
    private val projectRepository = mockk<TimesheetProjectRepository>()
    private val activityRepository = mockk<TimesheetActivityRepository>()
    private val timesheetRepository = mockk<TimesheetRepository>()
    private val entryRepository = mockk<TimesheetEntryRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)
    private val attendanceProcessorService = mockk<AttendanceProcessorService>(relaxed = true)

    private lateinit var timesheetService: TimesheetService

    private val employeeId = UUID.randomUUID()
    private val approverId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val activityId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        timesheetService = TimesheetService(
            clientRepository = clientRepository,
            projectRepository = projectRepository,
            activityRepository = activityRepository,
            timesheetRepository = timesheetRepository,
            entryRepository = entryRepository,
            employeeLookupService = employeeLookupService,
            attendanceProcessorService = attendanceProcessorService,
        )
    }

    @Test
    fun `listClients returns all clients sorted`() {
        val client = TimesheetClientEntity(
            clientCode = "CLI-ACME",
            clientName = "Acme Corp",
            billingCurrency = "USD",
        )
        every { clientRepository.findAllByOrderByClientNameAsc() } returns listOf(client)

        val response = timesheetService.listClients()

        assertEquals(1, response.clients.size)
        assertEquals("CLI-ACME", response.clients.first().clientCode)
    }

    @Test
    fun `createProject persists project and generates code if not supplied`() {
        val client = TimesheetClientEntity(
            clientCode = "CLI-ACME",
            clientName = "Acme Corp",
        )
        val project = TimesheetProjectEntity(
            clientId = client.id,
            projectCode = "PRJ-MOBILE",
            projectName = "HR Mobile App",
        )

        every { clientRepository.findById(client.id) } returns Optional.of(client)
        every { projectRepository.findByProjectCode("PRJ-MOBILE") } returns null
        every { projectRepository.save(any()) } returns project

        val request = TimesheetProjectCreateRequest(
            clientId = client.id,
            projectCode = "PRJ-MOBILE",
            projectName = "HR Mobile App",
        )

        val result = timesheetService.createProject(request)

        assertNotNull(result)
        assertEquals("PRJ-MOBILE", result.projectCode)
        assertEquals("Acme Corp", result.clientName)
        verify { projectRepository.save(any()) }
    }

    @Test
    fun `saveTimesheet persists entries and calculates totals accurately`() {
        val weekStart = LocalDate.of(2026, 9, 7) // Monday

        val timesheet = TimesheetEntity(
            employeeId = employeeId,
            weekStartDate = weekStart,
            weekEndDate = weekStart.plusDays(6),
            totalHours = BigDecimal("16.0"),
            billableHours = BigDecimal("12.0"),
            status = TimesheetStatus.DRAFT.name,
        )

        val entries = listOf(
            TimesheetEntryInput(
                projectId = projectId,
                activityId = activityId,
                entryDate = weekStart,
                hours = 8.0,
                billable = true,
                notes = "Feature delivery",
            ),
            TimesheetEntryInput(
                projectId = projectId,
                activityId = activityId,
                entryDate = weekStart.plusDays(1),
                hours = 4.0,
                billable = true,
                notes = "Code review",
            ),
            TimesheetEntryInput(
                projectId = projectId,
                activityId = activityId,
                entryDate = weekStart.plusDays(1),
                hours = 4.0,
                billable = false,
                notes = "All-hands meeting",
            ),
        )

        val request = TimesheetSaveRequest(
            employeeId = employeeId,
            weekStartDate = weekStart,
            entries = entries,
        )

        every { timesheetRepository.findByEmployeeIdAndWeekStartDate(employeeId, weekStart) } returns null
        every { timesheetRepository.save(any()) } returns timesheet
        every { entryRepository.deleteByTimesheetId(any()) } returns Unit
        every { entryRepository.saveAll(any<List<TimesheetEntryEntity>>()) } answers { firstArg() }
        every { entryRepository.findAllByTimesheetIdOrderByEntryDateAsc(any()) } returns emptyList()
        every { projectRepository.findAll() } returns emptyList()
        every { activityRepository.findAll() } returns emptyList()

        val response = timesheetService.saveTimesheet(request)

        assertNotNull(response)
        assertEquals("DRAFT", response.status)
        assertEquals(weekStart, response.weekStartDate)
        verify { entryRepository.saveAll(any<List<TimesheetEntryEntity>>()) }
    }

    @Test
    fun `saveTimesheet throws exception when day hours exceed 24`() {
        val weekStart = LocalDate.of(2026, 9, 7)

        val entries = listOf(
            TimesheetEntryInput(
                projectId = projectId,
                activityId = activityId,
                entryDate = weekStart,
                hours = 16.0,
                billable = true,
            ),
            TimesheetEntryInput(
                projectId = projectId,
                activityId = activityId,
                entryDate = weekStart,
                hours = 9.0,
                billable = false,
            ),
        )

        val request = TimesheetSaveRequest(
            employeeId = employeeId,
            weekStartDate = weekStart,
            entries = entries,
        )

        val ex = assertThrows<IllegalArgumentException> {
            timesheetService.saveTimesheet(request)
        }
        assertTrue(ex.message!!.contains("cannot exceed 24.0"))
    }

    @Test
    fun `submitTimesheet transitions status to SUBMITTED`() {
        val weekStart = LocalDate.of(2026, 9, 7)
        val timesheet = TimesheetEntity(
            employeeId = employeeId,
            weekStartDate = weekStart,
            weekEndDate = weekStart.plusDays(6),
            status = TimesheetStatus.DRAFT.name,
        )

        val entry = TimesheetEntryEntity(
            timesheetId = timesheet.id,
            projectId = projectId,
            activityId = activityId,
            entryDate = weekStart,
            hours = BigDecimal("8.0"),
            billable = true,
        )

        every { timesheetRepository.findById(timesheet.id) } returns Optional.of(timesheet)
        every { entryRepository.findAllByTimesheetIdOrderByEntryDateAsc(timesheet.id) } returns listOf(entry)
        every { timesheetRepository.save(any()) } answers { firstArg() }
        every { projectRepository.findAll() } returns emptyList()
        every { activityRepository.findAll() } returns emptyList()

        val response = timesheetService.submitTimesheet(timesheet.id)

        assertEquals("SUBMITTED", response.status)
        assertNotNull(response.submittedAt)
    }

    @Test
    fun `approveTimesheet transitions status to APPROVED and records approver`() {
        val weekStart = LocalDate.of(2026, 9, 7)
        val timesheet = TimesheetEntity(
            employeeId = employeeId,
            weekStartDate = weekStart,
            weekEndDate = weekStart.plusDays(6),
            status = TimesheetStatus.SUBMITTED.name,
        )

        every { timesheetRepository.findById(timesheet.id) } returns Optional.of(timesheet)
        every { timesheetRepository.save(any()) } answers { firstArg() }
        every { entryRepository.findAllByTimesheetIdOrderByEntryDateAsc(timesheet.id) } returns emptyList()
        every { projectRepository.findAll() } returns emptyList()
        every { activityRepository.findAll() } returns emptyList()

        val response = timesheetService.approveTimesheet(timesheet.id, approverId, "Looks great")

        assertEquals("APPROVED", response.status)
        assertNotNull(response.approvedAt)
        assertEquals(approverId, response.approverId)
        assertTrue(timesheet.comments!!.contains("Looks great"))
    }

    @Test
    fun `rejectTimesheet transitions status to REJECTED and records reason`() {
        val weekStart = LocalDate.of(2026, 9, 7)
        val timesheet = TimesheetEntity(
            employeeId = employeeId,
            weekStartDate = weekStart,
            weekEndDate = weekStart.plusDays(6),
            status = TimesheetStatus.SUBMITTED.name,
        )

        every { timesheetRepository.findById(timesheet.id) } returns Optional.of(timesheet)
        every { timesheetRepository.save(any()) } answers { firstArg() }
        every { entryRepository.findAllByTimesheetIdOrderByEntryDateAsc(timesheet.id) } returns emptyList()
        every { projectRepository.findAll() } returns emptyList()
        every { activityRepository.findAll() } returns emptyList()

        val response = timesheetService.rejectTimesheet(timesheet.id, approverId, "Missing Friday activity")

        assertEquals("REJECTED", response.status)
        assertEquals("Missing Friday activity", response.rejectionReason)
    }

    @Test
    fun `copyPreviousTimesheet copies previous week's entries to new week in DRAFT status`() {
        val targetWeek = LocalDate.of(2026, 9, 14)
        val prevWeek = LocalDate.of(2026, 9, 7)

        val prevTimesheet = TimesheetEntity(
            employeeId = employeeId,
            weekStartDate = prevWeek,
            weekEndDate = prevWeek.plusDays(6),
            status = TimesheetStatus.APPROVED.name,
        )

        val prevEntry = TimesheetEntryEntity(
            timesheetId = prevTimesheet.id,
            projectId = projectId,
            activityId = activityId,
            entryDate = prevWeek,
            hours = BigDecimal("7.5"),
            billable = true,
            notes = "Design sprint",
        )

        every { timesheetRepository.findByEmployeeIdAndWeekStartDate(employeeId, prevWeek) } returns prevTimesheet
        every { entryRepository.findAllByTimesheetIdOrderByEntryDateAsc(prevTimesheet.id) } returns listOf(prevEntry)
        every { timesheetRepository.findByEmployeeIdAndWeekStartDate(employeeId, targetWeek) } returns null
        every { timesheetRepository.save(any()) } answers { firstArg() }
        every { entryRepository.deleteByTimesheetId(any()) } returns Unit
        every { entryRepository.saveAll(any<List<TimesheetEntryEntity>>()) } answers { firstArg() }
        every { entryRepository.findAllByTimesheetIdOrderByEntryDateAsc(not(eq(prevTimesheet.id))) } returns emptyList()
        every { projectRepository.findAll() } returns emptyList()
        every { activityRepository.findAll() } returns emptyList()

        val request = TimesheetCopyPreviousRequest(
            employeeId = employeeId,
            targetWeekStartDate = targetWeek,
        )

        val response = timesheetService.copyPreviousTimesheet(request)

        assertEquals("DRAFT", response.status)
        assertEquals(targetWeek, response.weekStartDate)
        verify { entryRepository.saveAll(any<List<TimesheetEntryEntity>>()) }
    }

    @Test
    fun `reconcileWithAttendance calculates daily variances and statuses correctly`() {
        val weekStart = LocalDate.of(2026, 9, 7)
        val timesheet = TimesheetEntity(
            employeeId = employeeId,
            weekStartDate = weekStart,
            weekEndDate = weekStart.plusDays(6),
            status = TimesheetStatus.APPROVED.name,
        )

        val entries = listOf(
            TimesheetEntryEntity(
                timesheetId = timesheet.id,
                projectId = projectId,
                activityId = activityId,
                entryDate = weekStart,
                hours = BigDecimal("8.0"),
                billable = true,
            ),
            TimesheetEntryEntity(
                timesheetId = timesheet.id,
                projectId = projectId,
                activityId = activityId,
                entryDate = weekStart.plusDays(1),
                hours = BigDecimal("6.0"),
                billable = true,
            ),
        )

        val attendances = listOf(
            DailyAttendanceRecord(
                id = UUID.randomUUID(),
                employeeId = employeeId,
                workDate = weekStart,
                dayStatus = DayStatus.PRESENT,
                netWorkedMinutes = 480, // 8.0 hours
            ),
            DailyAttendanceRecord(
                id = UUID.randomUUID(),
                employeeId = employeeId,
                workDate = weekStart.plusDays(1),
                dayStatus = DayStatus.PRESENT,
                netWorkedMinutes = 480, // 8.0 hours (logged only 6.0 => UNDER_LOGGED)
            ),
        )

        every { timesheetRepository.findByEmployeeIdAndWeekStartDate(employeeId, weekStart) } returns timesheet
        every { entryRepository.findAllByTimesheetIdOrderByEntryDateAsc(timesheet.id) } returns entries
        every {
            attendanceProcessorService.getAttendanceRecords(
                employeeId, weekStart, weekStart.plusDays(6)
            )
        } returns attendances

        val result = timesheetService.reconcileWithAttendance(employeeId, weekStart)

        assertEquals(14.0, result.totalLoggedHours)
        assertEquals(16.0, result.totalAttendanceHours)
        assertEquals(-2.0, result.totalVarianceHours)
        assertEquals(7, result.dailyBreakdown.size)

        // Day 0: Monday (8.0 vs 8.0) -> MATCHED
        assertEquals(ReconciliationStatus.MATCHED.name, result.dailyBreakdown[0].status)
        assertEquals(0.0, result.dailyBreakdown[0].varianceHours)

        // Day 1: Tuesday (6.0 vs 8.0) -> UNDER_LOGGED
        assertEquals(ReconciliationStatus.UNDER_LOGGED.name, result.dailyBreakdown[1].status)
        assertEquals(-2.0, result.dailyBreakdown[1].varianceHours)

        // Day 2: Wednesday (0.0 vs 0.0) -> NO_ATTENDANCE
        assertEquals(ReconciliationStatus.NO_ATTENDANCE.name, result.dailyBreakdown[2].status)
    }
}
