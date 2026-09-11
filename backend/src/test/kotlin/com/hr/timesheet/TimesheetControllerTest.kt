package com.hr.timesheet

import com.hr.timesheet.internal.TimesheetController
import com.hr.timesheet.internal.TimesheetService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.UUID

@DisplayName("Timesheet Controller Unit Tests")
class TimesheetControllerTest {

    private val timesheetService = mockk<TimesheetService>()
    private lateinit var timesheetController: TimesheetController

    private val employeeId = UUID.randomUUID()
    private val timesheetId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        timesheetController = TimesheetController(timesheetService)
    }

    @Test
    fun `listClients returns HTTP 200 with clients list`() {
        val client = TimesheetClientItem(
            id = clientId,
            clientCode = "CLI-ACME",
            clientName = "Acme Corp",
            status = "ACTIVE",
            billingCurrency = "USD",
        )
        every { timesheetService.listClients() } returns TimesheetClientListResponse(listOf(client))

        val response = timesheetController.listClients()

        assertEquals(1, response.clients.size)
        assertEquals("Acme Corp", response.clients[0].clientName)
    }

    @Test
    fun `createProject returns HTTP 201 Created`() {
        val req = TimesheetProjectCreateRequest(
            clientId = clientId,
            projectCode = "PRJ-MOBILE",
            projectName = "Mobile App",
            billable = true,
        )
        val project = TimesheetProjectItem(
            id = UUID.randomUUID(),
            clientId = clientId,
            clientName = "Acme Corp",
            projectCode = "PRJ-MOBILE",
            projectName = "Mobile App",
            billable = true,
            status = "ACTIVE",
        )
        every { timesheetService.createProject(req) } returns project

        val response = timesheetController.createProject(req)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("PRJ-MOBILE", response.body?.projectCode)
    }

    @Test
    fun `listTimesheets returns list of timesheet items`() {
        val item = TimesheetListItem(
            id = timesheetId,
            employeeId = employeeId,
            employeeName = "Kasun Perera",
            weekStartDate = LocalDate.of(2026, 9, 7),
            weekEndDate = LocalDate.of(2026, 9, 13),
            status = "APPROVED",
            totalHours = 40.0,
            billableHours = 35.0,
            nonBillableHours = 5.0,
        )
        every { timesheetService.listTimesheets(employeeId, "APPROVED") } returns TimesheetListResponse(listOf(item))

        val response = timesheetController.listTimesheets(employeeId, "APPROVED")

        assertEquals(1, response.timesheets.size)
        assertEquals("Kasun Perera", response.timesheets[0].employeeName)
    }

    @Test
    fun `submitTimesheet triggers service and returns detail response`() {
        val detail = TimesheetDetailResponse(
            id = timesheetId,
            employeeId = employeeId,
            employeeName = "Kasun Perera",
            weekStartDate = LocalDate.of(2026, 9, 7),
            weekEndDate = LocalDate.of(2026, 9, 13),
            status = "SUBMITTED",
            totalHours = 40.0,
            billableHours = 40.0,
            nonBillableHours = 0.0,
        )
        every { timesheetService.submitTimesheet(timesheetId) } returns detail

        val response = timesheetController.submitTimesheet(timesheetId)

        assertEquals("SUBMITTED", response.status)
    }

    @Test
    fun `reconcileWithAttendance returns reconciliation breakdown`() {
        val recon = TimesheetReconciliationResponse(
            employeeId = employeeId,
            weekStartDate = LocalDate.of(2026, 9, 7),
            weekEndDate = LocalDate.of(2026, 9, 13),
            totalLoggedHours = 40.0,
            totalAttendanceHours = 40.0,
            totalVarianceHours = 0.0,
            dailyBreakdown = emptyList(),
        )
        every { timesheetService.reconcileWithAttendance(employeeId, LocalDate.of(2026, 9, 7)) } returns recon

        val response = timesheetController.reconcileWithAttendance(employeeId, LocalDate.of(2026, 9, 7))

        assertEquals(40.0, response.totalLoggedHours)
        assertEquals(0.0, response.totalVarianceHours)
    }
}
