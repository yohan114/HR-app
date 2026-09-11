package com.hr.disciplinary

import com.hr.disciplinary.internal.DisciplinaryController
import com.hr.disciplinary.internal.DisciplinaryService
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@DisplayName("Disciplinary Controller Unit Tests")
class DisciplinaryControllerTest {

    private val service = mockk<DisciplinaryService>()
    private lateinit var controller: DisciplinaryController

    private val sampleIncident = DisciplinaryIncidentItem(
        id = UUID.randomUUID(),
        incidentNumber = "DISC-2026-001",
        employeeId = UUID.randomUUID(),
        employeeName = "Kasun Mendis",
        reportedByEmployeeId = UUID.randomUUID(),
        reportedByEmployeeName = "Nimal Perera",
        typeCode = "ATTENDANCE_BREACH",
        typeName = "Attendance Breach",
        subtypeName = "AWOL",
        incidentDate = LocalDate.now(),
        location = "HQ",
        description = "Test description",
        severity = IncidentSeverity.MEDIUM,
        status = IncidentStatus.REPORTED,
        createdAt = Instant.now(),
    )

    private val sampleDetail = DisciplinaryIncidentDetailResponse(
        incident = sampleIncident,
        correctiveActions = emptyList(),
        journalEntries = emptyList(),
        appeals = emptyList(),
    )

    private val sampleAction = CorrectiveActionItem(
        id = UUID.randomUUID(),
        incidentId = sampleIncident.id,
        actionType = CorrectiveActionType.SHOW_CAUSE,
        issuedAt = Instant.now(),
        issuedBy = UUID.randomUUID(),
        issuedByName = "HR Authority",
        title = "Show Cause",
        details = "Details",
        responseDueDate = null,
        employeeResponse = null,
        respondedAt = null,
        outcome = null,
        effectiveFrom = null,
        effectiveTo = null,
        status = CorrectiveActionStatus.ISSUED,
    )

    private val sampleJournal = IncidentJournalEntryItem(
        id = UUID.randomUUID(),
        incidentId = sampleIncident.id,
        entry = "Journal note",
        enteredBy = UUID.randomUUID(),
        enteredByName = "Investigator",
        enteredAt = Instant.now(),
    )

    private val sampleAppeal = DisciplinaryAppealItem(
        id = UUID.randomUUID(),
        incidentId = sampleIncident.id,
        correctiveActionId = sampleAction.id,
        reason = "Appeal reason",
        appealedAt = Instant.now(),
        outcome = null,
        reviewedAt = null,
        status = DisciplinaryAppealStatus.SUBMITTED,
    )

    @BeforeEach
    fun setUp() {
        controller = DisciplinaryController(service)
    }

    @Test
    fun `getIncidentTypes delegates to service`() {
        every { service.getIncidentTypes() } returns IncidentTypesResponse(emptyList())
        val res = controller.getIncidentTypes()
        assertEquals(0, res.types.size)
    }

    @Test
    fun `getDisciplinaryIncidents delegates to service`() {
        every { service.getIncidents(employeeId = any(), status = null) } returns DisciplinaryIncidentListResponse(1, 1, listOf(sampleIncident))
        val res = controller.getDisciplinaryIncidents(null, null, null)
        assertEquals(1, res.totalCount)
    }

    @Test
    fun `getDisciplinaryIncidentById delegates to service`() {
        val id = UUID.randomUUID()
        every { service.getIncidentById(id = id) } returns sampleDetail
        val res = controller.getDisciplinaryIncidentById(id)
        assertEquals("DISC-2026-001", res.incident.incidentNumber)
    }

    @Test
    fun `reportDisciplinaryIncident delegates to service`() {
        val req = DisciplinaryIncidentReportRequest(
            employeeId = sampleIncident.employeeId,
            incidentTypeId = UUID.randomUUID(),
            incidentDate = LocalDate.now(),
            description = "Test desc",
        )
        every { service.reportIncident(reportedByEmployeeId = any(), request = req) } returns sampleIncident
        val res = controller.reportDisciplinaryIncident(req, null, null)
        assertEquals("DISC-2026-001", res.incidentNumber)
    }

    @Test
    fun `issueCorrectiveAction delegates to service`() {
        val id = sampleIncident.id
        val req = IssueCorrectiveActionRequest(
            actionType = CorrectiveActionType.SHOW_CAUSE,
            title = "Show Cause",
            details = "Details",
        )
        every { service.issueCorrectiveAction(incidentId = id, issuedBy = any(), request = req) } returns sampleAction
        val res = controller.issueCorrectiveAction(id, req, null, null)
        assertEquals(CorrectiveActionType.SHOW_CAUSE, res.actionType)
    }

    @Test
    fun `addIncidentJournalEntry delegates to service`() {
        val id = sampleIncident.id
        val req = AddJournalEntryRequest(entry = "Note")
        every { service.addJournalEntry(incidentId = id, enteredBy = any(), request = req) } returns sampleJournal
        val res = controller.addIncidentJournalEntry(id, req, null)
        assertEquals("Journal note", res.entry)
    }

    @Test
    fun `respondToCorrectiveAction delegates to service`() {
        val actionId = sampleAction.id
        val req = CorrectiveActionResponseRequest(employeeResponse = "My explanation")
        every { service.respondToCorrectiveAction(actionId = actionId, employeeId = any(), request = req) } returns sampleAction
        val res = controller.respondToCorrectiveAction(actionId, req, null, null)
        assertEquals(CorrectiveActionType.SHOW_CAUSE, res.actionType)
    }

    @Test
    fun `appealCorrectiveAction delegates to service`() {
        val actionId = sampleAction.id
        val req = DisciplinaryAppealRequest(reason = "Appeal reason")
        every { service.appealCorrectiveAction(actionId = actionId, employeeId = any(), request = req) } returns sampleAppeal
        val res = controller.appealCorrectiveAction(actionId, req, null, null)
        assertEquals(DisciplinaryAppealStatus.SUBMITTED, res.status)
    }
}
