package com.hr.app.data.timesheet

import com.hr.client.api.TimesheetsApi
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class TimesheetRepositoryTest {

    private val timesheetsApi = mockk<TimesheetsApi>()
    private lateinit var repository: TimesheetRepository

    private val employeeId = UUID.randomUUID()
    private val timesheetId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

    @Before
    fun setUp() {
        repository = TimesheetRepository(timesheetsApi)
    }

    @Test
    fun `loadClients updates clients flow on success`() = runTest {
        val client = TimesheetClientItem(
            id = UUID.randomUUID(),
            clientCode = "CLI-ACME",
            clientName = "Acme Corp",
            status = "ACTIVE",
        )
        coEvery { timesheetsApi.listTimesheetClients() } returns Response.success(
            TimesheetClientListResponse(listOf(client))
        )

        val result = repository.loadClients()

        assertTrue(result.isSuccess)
        assertEquals(1, repository.clients.value.size)
        assertEquals("Acme Corp", repository.clients.value[0].clientName)
    }

    @Test
    fun `loadProjects updates projects flow on success`() = runTest {
        val project = TimesheetProjectItem(
            id = projectId,
            clientId = UUID.randomUUID(),
            projectCode = "PRJ-MOBILE",
            projectName = "Mobile App",
            clientName = "Internal",
            billable = true,
            status = "ACTIVE",
        )
        coEvery { timesheetsApi.listTimesheetProjects(null) } returns Response.success(
            TimesheetProjectListResponse(listOf(project))
        )

        val result = repository.loadProjects()

        assertTrue(result.isSuccess)
        assertEquals(1, repository.projects.value.size)
        assertEquals("Mobile App", repository.projects.value[0].projectName)
    }

    @Test
    fun `loadActivities updates activities flow on success`() = runTest {
        val activity = TimesheetActivityItem(
            id = UUID.randomUUID(),
            projectId = projectId,
            activityCode = "ACT-DEV",
            activityName = "Feature Development",
            billable = true,
            status = "ACTIVE",
        )
        coEvery { timesheetsApi.listTimesheetActivities(projectId) } returns Response.success(
            TimesheetActivityListResponse(listOf(activity))
        )

        val result = repository.loadActivities(projectId)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.activities.value.size)
        assertEquals("Feature Development", repository.activities.value[0].activityName)
    }

    @Test
    fun `loadMyTimesheets updates timesheets flow on success`() = runTest {
        val timesheet = TimesheetListItem(
            id = timesheetId,
            employeeId = employeeId,
            employeeName = "Alex Rivera",
            weekStartDate = LocalDate.of(2026, 9, 7),
            weekEndDate = LocalDate.of(2026, 9, 13),
            status = TimesheetListItem.Status.DRAFT,
            totalHours = 40.0,
            billableHours = 40.0,
            nonBillableHours = 0.0,
            submittedAt = null,
            approvedAt = null,
        )
        coEvery { timesheetsApi.listMyTimesheets(employeeId) } returns Response.success(
            TimesheetListResponse(listOf(timesheet))
        )

        val result = repository.loadMyTimesheets(employeeId = employeeId)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.timesheets.value.size)
        assertEquals(40.0, repository.timesheets.value[0].totalHours, 0.01)
    }

    @Test
    fun `submitTimesheet calls API and returns success`() = runTest {
        val submitted = TimesheetDetailResponse(
            id = timesheetId,
            employeeId = employeeId,
            employeeName = "Alex Rivera",
            weekStartDate = LocalDate.of(2026, 9, 7),
            weekEndDate = LocalDate.of(2026, 9, 13),
            status = TimesheetDetailResponse.Status.SUBMITTED,
            totalHours = 40.0,
            billableHours = 40.0,
            nonBillableHours = 0.0,
            propertyEntries = emptyList(),
            submittedAt = OffsetDateTime.now(),
        )
        coEvery { timesheetsApi.submitTimesheet(timesheetId) } returns Response.success(submitted)

        val result = repository.submitTimesheet(timesheetId)

        assertTrue(result.isSuccess)
        assertEquals(TimesheetDetailResponse.Status.SUBMITTED, result.getOrNull()?.status)
    }
}
