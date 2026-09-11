package com.hr.disciplinary

import com.hr.disciplinary.internal.*
import com.hr.employee.EmployeeLeaveProfile
import com.hr.employee.EmployeeLookupService
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Disciplinary Service Unit Tests")
class DisciplinaryServiceTest {

    private val incidentTypeRepository = mockk<IncidentTypeRepository>()
    private val incidentSubtypeRepository = mockk<IncidentSubtypeRepository>()
    private val incidentRepository = mockk<DisciplinaryIncidentRepository>()
    private val correctiveActionRepository = mockk<CorrectiveActionRepository>()
    private val journalRepository = mockk<IncidentJournalRepository>()
    private val appealRepository = mockk<DisciplinaryAppealRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)

    private lateinit var service: DisciplinaryService

    private val tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val sampleType = IncidentType(
        code = "ATTENDANCE_BREACH",
        name = "Attendance Breach",
        description = "Absence and lateness issues",
        severity = IncidentSeverity.MEDIUM,
    ).apply {
        this.tenantId = this@DisciplinaryServiceTest.tenantId
    }
    private val typeId get() = sampleType.id

    private val sampleSubtype = IncidentSubtype(
        incidentTypeId = sampleType.id,
        code = "AWOL",
        name = "Unauthorized Absence",
        description = "Unexcused absence",
    ).apply {
        this.tenantId = this@DisciplinaryServiceTest.tenantId
    }
    private val subtypeId get() = sampleSubtype.id

    private val sampleEmployee = EmployeeLeaveProfile(
        id = UUID.randomUUID(),
        tenantId = tenantId,
        employeeCode = "LK010",
        firstName = "Kasun",
        lastName = "Mendis",
        displayName = "Kasun Mendis",
        joinDate = LocalDate.of(2024, 1, 1),
        status = "ACTIVE",
    )
    private val employeeId get() = sampleEmployee.id

    private val sampleReporter = EmployeeLeaveProfile(
        id = UUID.randomUUID(),
        tenantId = tenantId,
        employeeCode = "LK002",
        firstName = "Nimal",
        lastName = "Perera",
        displayName = "Nimal Perera",
        joinDate = LocalDate.of(2023, 1, 1),
        status = "ACTIVE",
    )
    private val reporterId get() = sampleReporter.id

    @BeforeEach
    fun setUp() {
        every { employeeLookupService.findDisplayName(employeeId) } returns "Kasun Mendis"
        every { employeeLookupService.findDisplayName(reporterId) } returns "Nimal Perera"
        every { employeeLookupService.findAll() } returns listOf(sampleEmployee, sampleReporter)
        every { employeeLookupService.findById(employeeId) } returns sampleEmployee
        every { employeeLookupService.findById(reporterId) } returns sampleReporter

        service = DisciplinaryService(
            incidentTypeRepository = incidentTypeRepository,
            incidentSubtypeRepository = incidentSubtypeRepository,
            incidentRepository = incidentRepository,
            correctiveActionRepository = correctiveActionRepository,
            journalRepository = journalRepository,
            appealRepository = appealRepository,
            employeeLookupService = employeeLookupService,
        )
    }

    @Test
    fun `getIncidentTypes returns types and subtypes`() {
        every { incidentTypeRepository.findByTenantIdOrderByCodeAsc(tenantId) } returns listOf(sampleType)
        every { incidentSubtypeRepository.findByTenantIdOrderByCodeAsc(tenantId) } returns listOf(sampleSubtype)

        val response = service.getIncidentTypes(tenantId)

        assertEquals(1, response.types.size)
        assertEquals("ATTENDANCE_BREACH", response.types[0].code)
        assertEquals(1, response.types[0].subtypes.size)
        assertEquals("AWOL", response.types[0].subtypes[0].code)
    }

    @Test
    fun `reportIncident creates incident with initial journal entry and status REPORTED`() {
        every { incidentTypeRepository.findById(typeId) } returns Optional.of(sampleType)
        every { incidentSubtypeRepository.findById(subtypeId) } returns Optional.of(sampleSubtype)

        val incidentSlot = slot<DisciplinaryIncident>()
        every { incidentRepository.save(capture(incidentSlot)) } answers { incidentSlot.captured }
        every { journalRepository.save(any()) } answers { firstArg() }

        val request = DisciplinaryIncidentReportRequest(
            employeeId = employeeId,
            incidentTypeId = typeId,
            subtypeId = subtypeId,
            incidentDate = LocalDate.of(2026, 3, 2),
            location = "Engineering Floor 2",
            description = "Unexcused absence on release day",
        )

        val item = service.reportIncident(tenantId, reporterId, request)

        assertNotNull(item)
        assertTrue(item.incidentNumber.startsWith("DISC-"))
        assertEquals(IncidentStatus.REPORTED, item.status)
        assertEquals("Kasun Mendis", item.employeeName)
        assertEquals("Nimal Perera", item.reportedByEmployeeName)
        assertEquals("ATTENDANCE_BREACH", item.typeCode)

        verify(exactly = 1) { journalRepository.save(any()) }
    }

    @Test
    fun `issueCorrectiveAction advances incident status to ACTION_ISSUED and logs journal`() {
        val incident = DisciplinaryIncident(
            incidentNumber = "DISC-2026-001",
            employeeId = employeeId,
            reportedByEmployeeId = reporterId,
            incidentTypeId = typeId,
            subtypeId = subtypeId,
            incidentDate = LocalDate.of(2026, 3, 2),
            description = "Unexcused absence",
            status = IncidentStatus.REPORTED,
        ).apply {
            this.tenantId = this@DisciplinaryServiceTest.tenantId
        }

        every { incidentRepository.findByTenantIdAndId(tenantId, incident.id) } returns incident
        every { correctiveActionRepository.save(any()) } answers { firstArg() }
        every { incidentRepository.save(any()) } answers { firstArg() }
        every { journalRepository.save(any()) } answers { firstArg() }

        val request = IssueCorrectiveActionRequest(
            actionType = CorrectiveActionType.SHOW_CAUSE,
            title = "Show Cause Notice: Absence on March 2nd",
            details = "Please explain the reason for unexcused absence within 3 business days.",
            responseDueDate = LocalDate.of(2026, 3, 8),
        )

        val actionItem = service.issueCorrectiveAction(tenantId, incident.id, reporterId, request)

        assertEquals(CorrectiveActionType.SHOW_CAUSE, actionItem.actionType)
        assertEquals(CorrectiveActionStatus.ISSUED, actionItem.status)
        assertEquals(IncidentStatus.ACTION_ISSUED, incident.status)

        verify(exactly = 1) { incidentRepository.save(match { it.status == IncidentStatus.ACTION_ISSUED }) }
        verify(exactly = 1) { journalRepository.save(any()) }
    }

    @Test
    fun `respondToCorrectiveAction records employee response and updates status to RESPONDED`() {
        val incidentId = UUID.randomUUID()
        val action = CorrectiveAction(
            incidentId = incidentId,
            actionType = CorrectiveActionType.SHOW_CAUSE,
            issuedBy = reporterId,
            title = "Show Cause Notice",
            details = "Explain absence",
            status = CorrectiveActionStatus.ISSUED,
        ).apply {
            this.tenantId = this@DisciplinaryServiceTest.tenantId
        }
        val actionId = action.id

        every { correctiveActionRepository.findByTenantIdAndId(tenantId, actionId) } returns action
        every { correctiveActionRepository.save(any()) } answers { firstArg() }
        every { journalRepository.save(any()) } answers { firstArg() }

        val request = CorrectiveActionResponseRequest(
            employeeResponse = "I was hospitalized due to acute gastroenteritis and have medical certificates attached.",
        )

        val updated = service.respondToCorrectiveAction(tenantId, actionId, employeeId, request)

        assertEquals(CorrectiveActionStatus.RESPONDED, updated.status)
        assertNotNull(updated.employeeResponse)
        assertNotNull(updated.respondedAt)
        verify(exactly = 1) { journalRepository.save(any()) }
    }

    @Test
    fun `appealCorrectiveAction creates appeal and transitions status to APPEALED`() {
        val incident = DisciplinaryIncident(
            incidentNumber = "DISC-2026-001",
            employeeId = employeeId,
            reportedByEmployeeId = reporterId,
            incidentTypeId = typeId,
            incidentDate = LocalDate.of(2026, 3, 2),
            description = "Some incident",
            status = IncidentStatus.ACTION_ISSUED,
        ).apply {
            this.tenantId = this@DisciplinaryServiceTest.tenantId
        }
        val incidentId = incident.id

        val action = CorrectiveAction(
            incidentId = incidentId,
            actionType = CorrectiveActionType.WRITTEN_WARNING,
            issuedBy = reporterId,
            title = "Written Warning",
            details = "Warning details",
            status = CorrectiveActionStatus.CONFIRMED,
        ).apply {
            this.tenantId = this@DisciplinaryServiceTest.tenantId
        }
        val actionId = action.id

        every { correctiveActionRepository.findByTenantIdAndId(tenantId, actionId) } returns action
        every { appealRepository.findByTenantIdAndCorrectiveActionId(tenantId, actionId) } returns null
        every { appealRepository.save(any()) } answers { firstArg() }
        every { correctiveActionRepository.save(any()) } answers { firstArg() }
        every { incidentRepository.findByTenantIdAndId(tenantId, incidentId) } returns incident
        every { incidentRepository.save(any()) } answers { firstArg() }
        every { journalRepository.save(any()) } answers { firstArg() }

        val appealItem = service.appealCorrectiveAction(
            tenantId = tenantId,
            actionId = actionId,
            employeeId = employeeId,
            request = DisciplinaryAppealRequest(reason = "Procedural unfairness in domestic inquiry"),
        )

        assertEquals(DisciplinaryAppealStatus.SUBMITTED, appealItem.status)
        assertEquals("Procedural unfairness in domestic inquiry", appealItem.reason)
        assertEquals(CorrectiveActionStatus.APPEALED, action.status)
        assertEquals(IncidentStatus.APPEALED, incident.status)
    }
}
