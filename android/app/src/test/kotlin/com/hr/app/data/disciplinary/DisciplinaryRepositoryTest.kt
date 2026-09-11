package com.hr.app.data.disciplinary

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.DisciplinaryApi
import com.hr.client.model.CorrectiveActionItem
import com.hr.client.model.DisciplinaryAppealItem
import com.hr.client.model.DisciplinaryIncidentItem
import com.hr.client.model.DisciplinaryIncidentListResponse
import com.hr.client.model.DisciplinaryIncidentReportRequest
import com.hr.client.model.IncidentTypeItem
import com.hr.client.model.IncidentTypesResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class DisciplinaryRepositoryTest {

    private val disciplinaryApi = mockk<DisciplinaryApi>()
    private val outbox = mockk<Outbox>(relaxed = true)
    private val clock = Clock { 1772870000000L }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val pendingCountFlow = MutableStateFlow(0)
    private lateinit var repository: DisciplinaryRepository

    private val sampleType = IncidentTypeItem(
        id = UUID.randomUUID(),
        code = "ATTENDANCE",
        name = "Attendance Breach",
        description = "Unauthorized absence",
        severity = IncidentTypeItem.Severity.MEDIUM,
        subtypes = emptyList(),
    )

    private val sampleIncident = DisciplinaryIncidentItem(
        id = UUID.randomUUID(),
        incidentNumber = "DISC-2026-0001",
        employeeId = UUID.randomUUID(),
        employeeName = "Kasun Mendis",
        reportedByEmployeeId = UUID.randomUUID(),
        reportedByEmployeeName = "Nimal Perera",
        typeCode = "ATTENDANCE",
        typeName = "Attendance Breach",
        subtypeName = "AWOL",
        incidentDate = LocalDate.of(2026, 3, 2),
        location = "Engineering Floor",
        description = "Unexcused absence",
        severity = DisciplinaryIncidentItem.Severity.MEDIUM,
        status = DisciplinaryIncidentItem.Status.REPORTED,
        createdAt = OffsetDateTime.now(),
    )

    @Before
    fun setUp() {
        coEvery { outbox.pendingCount } returns pendingCountFlow
        repository = DisciplinaryRepository(
            disciplinaryApi = disciplinaryApi,
            outbox = outbox,
            json = json,
            clock = clock,
        )
    }

    @Test
    fun `refreshTypes updates types StateFlow on successful API response`() = runTest {
        coEvery { disciplinaryApi.getIncidentTypes() } returns Response.success(
            IncidentTypesResponse(types = listOf(sampleType)),
        )

        val result = repository.refreshTypes()

        assertTrue(result.isSuccess)
        val types = repository.types.value
        assertEquals(1, types.size)
        assertEquals("ATTENDANCE", types[0].code)
    }

    @Test
    fun `refreshIncidents updates incident list and openCount`() = runTest {
        val listResponse = DisciplinaryIncidentListResponse(
            incidents = listOf(sampleIncident),
            totalCount = 1,
            openCount = 1,
        )
        coEvery { disciplinaryApi.getDisciplinaryIncidents(any(), any()) } returns Response.success(listResponse)

        val result = repository.refreshIncidents()

        assertTrue(result.isSuccess)
        assertEquals(1, repository.incidents.value.size)
        assertEquals(1, repository.openCount.value)
        assertEquals("DISC-2026-0001", repository.incidents.value.first().incidentNumber)
    }

    @Test
    fun `reportIncident adds new incident to StateFlow and increments openCount`() = runTest {
        val request = DisciplinaryIncidentReportRequest(
            employeeId = UUID.randomUUID(),
            incidentTypeId = sampleType.id,
            incidentDate = LocalDate.of(2026, 3, 2),
            description = "Unexcused absence on Monday",
            location = "Floor 2",
        )

        coEvery {
            disciplinaryApi.reportDisciplinaryIncident(any(), any())
        } returns Response.success(sampleIncident)

        val result = repository.reportIncident(request)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.incidents.value.size)
        assertEquals(1, repository.openCount.value)
        assertEquals("DISC-2026-0001", repository.incidents.value.first().incidentNumber)
    }

    @Test
    fun `respondToCorrectiveAction submits response and updates in-memory detail`() = runTest {
        val actionId = UUID.randomUUID()
        val updatedAction = CorrectiveActionItem(
            id = actionId,
            incidentId = sampleIncident.id,
            actionType = CorrectiveActionItem.ActionType.SHOW_CAUSE,
            issuedAt = OffsetDateTime.now(),
            status = CorrectiveActionItem.Status.RESPONDED,
            issuedBy = UUID.randomUUID(),
            issuedByName = "Nimal Perera",
            title = "Show Cause Notice",
            details = "Explain absence",
            responseDueDate = LocalDate.now(),
            employeeResponse = "Hospitalized with acute illness",
            respondedAt = OffsetDateTime.now(),
        )

        coEvery {
            disciplinaryApi.respondToCorrectiveAction(any(), any(), any())
        } returns Response.success(updatedAction)

        val result = repository.respondToCorrectiveAction(actionId, "Hospitalized with acute illness")

        assertTrue(result.isSuccess)
        assertEquals(CorrectiveActionItem.Status.RESPONDED, result.getOrNull()?.status)
        assertEquals("Hospitalized with acute illness", result.getOrNull()?.employeeResponse)
    }
}
