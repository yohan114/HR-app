package com.hr.disciplinary.internal

import com.hr.disciplinary.*
import com.hr.employee.EmployeeLookupService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.Year
import java.util.UUID

@Service
class DisciplinaryService(
    private val incidentTypeRepository: IncidentTypeRepository,
    private val incidentSubtypeRepository: IncidentSubtypeRepository,
    private val incidentRepository: DisciplinaryIncidentRepository,
    private val correctiveActionRepository: CorrectiveActionRepository,
    private val journalRepository: IncidentJournalRepository,
    private val appealRepository: DisciplinaryAppealRepository,
    private val employeeLookupService: EmployeeLookupService,
) {

    private val defaultTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    /**
     * Lists incident types and their configured subtypes.
     */
    @Transactional(readOnly = true)
    fun getIncidentTypes(tenantId: UUID = defaultTenantId): IncidentTypesResponse {
        val types = incidentTypeRepository.findByTenantIdOrderByCodeAsc(tenantId)
        val subtypes = incidentSubtypeRepository.findByTenantIdOrderByCodeAsc(tenantId)
        val subtypeMap = subtypes.groupBy { it.incidentTypeId }

        return IncidentTypesResponse(
            types = types.map { t ->
                IncidentTypeItem(
                    id = t.id ?: UUID.randomUUID(),
                    code = t.code,
                    name = t.name,
                    description = t.description,
                    severity = t.severity,
                    subtypes = (subtypeMap[t.id] ?: emptyList()).map { st ->
                        IncidentSubtypeItem(
                            id = st.id ?: UUID.randomUUID(),
                            typeId = st.incidentTypeId,
                            code = st.code,
                            name = st.name,
                            description = st.description,
                        )
                    },
                )
            },
        )
    }

    /**
     * Lists disciplinary incidents with optional filtering by employee and status.
     */
    @Transactional(readOnly = true)
    fun getIncidents(
        tenantId: UUID = defaultTenantId,
        employeeId: UUID? = null,
        status: IncidentStatus? = null,
    ): DisciplinaryIncidentListResponse {
        val all = if (employeeId != null) {
            incidentRepository.findByTenantIdAndEmployeeIdOrderByIncidentDateDesc(tenantId, employeeId)
        } else {
            incidentRepository.findByTenantIdOrderByIncidentDateDesc(tenantId)
        }

        val filtered = if (status != null) all.filter { it.status == status } else all

        val types = incidentTypeRepository.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }
        val subtypes = incidentSubtypeRepository.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }
        val employees = employeeLookupService.findAll().associateBy { it.id }

        val openCount = all.count { it.status != IncidentStatus.CONCLUDED }

        return DisciplinaryIncidentListResponse(
            totalCount = filtered.size,
            openCount = openCount,
            incidents = filtered.map { inc ->
                val emp = employees[inc.employeeId]
                val reporter = employees[inc.reportedByEmployeeId]
                val type = types[inc.incidentTypeId]
                val subtype = subtypes[inc.subtypeId]

                DisciplinaryIncidentItem(
                    id = inc.id ?: UUID.randomUUID(),
                    incidentNumber = inc.incidentNumber,
                    employeeId = inc.employeeId,
                    employeeName = emp?.displayName ?: "Employee ${inc.employeeId}",
                    reportedByEmployeeId = inc.reportedByEmployeeId,
                    reportedByEmployeeName = reporter?.displayName ?: "Reporter ${inc.reportedByEmployeeId}",
                    typeCode = type?.code ?: "MISCONDUCT",
                    typeName = type?.name ?: "General Misconduct",
                    subtypeName = subtype?.name,
                    incidentDate = inc.incidentDate,
                    location = inc.location,
                    description = inc.description,
                    severity = inc.severity,
                    status = inc.status,
                    createdAt = inc.createdAt ?: Instant.now(),
                )
            },
        )
    }

    /**
     * Retrieves full incident case details including progressive actions, investigation journal, and appeals.
     */
    @Transactional(readOnly = true)
    fun getIncidentById(tenantId: UUID = defaultTenantId, id: UUID): DisciplinaryIncidentDetailResponse {
        val inc = incidentRepository.findByTenantIdAndId(tenantId, id)
            ?: error("Disciplinary incident not found: $id")

        val employees = employeeLookupService.findAll().associateBy { it.id }
        val type = incidentTypeRepository.findById(inc.incidentTypeId).orElse(null)
        val subtype = inc.subtypeId?.let { incidentSubtypeRepository.findById(it).orElse(null) }

        val actions = correctiveActionRepository.findByTenantIdAndIncidentIdOrderByIssuedAtAsc(tenantId, id)
        val journals = journalRepository.findByTenantIdAndIncidentIdOrderByEnteredAtAsc(tenantId, id)
        val appeals = appealRepository.findByTenantIdAndIncidentId(tenantId, id)

        val incidentItem = DisciplinaryIncidentItem(
            id = inc.id ?: id,
            incidentNumber = inc.incidentNumber,
            employeeId = inc.employeeId,
            employeeName = employees[inc.employeeId]?.displayName ?: "Employee ${inc.employeeId}",
            reportedByEmployeeId = inc.reportedByEmployeeId,
            reportedByEmployeeName = employees[inc.reportedByEmployeeId]?.displayName ?: "Reporter ${inc.reportedByEmployeeId}",
            typeCode = type?.code ?: "MISCONDUCT",
            typeName = type?.name ?: "General Misconduct",
            subtypeName = subtype?.name,
            incidentDate = inc.incidentDate,
            location = inc.location,
            description = inc.description,
            severity = inc.severity,
            status = inc.status,
            createdAt = inc.createdAt ?: Instant.now(),
        )

        return DisciplinaryIncidentDetailResponse(
            incident = incidentItem,
            correctiveActions = actions.map { act ->
                CorrectiveActionItem(
                    id = act.id ?: UUID.randomUUID(),
                    incidentId = act.incidentId,
                    actionType = act.actionType,
                    issuedAt = act.issuedAt,
                    issuedBy = act.issuedBy,
                    issuedByName = employees[act.issuedBy]?.displayName ?: "HR Authority",
                    title = act.title,
                    details = act.details,
                    responseDueDate = act.responseDueDate,
                    employeeResponse = act.employeeResponse,
                    respondedAt = act.respondedAt,
                    outcome = act.outcome,
                    effectiveFrom = act.effectiveFrom,
                    effectiveTo = act.effectiveTo,
                    status = act.status,
                )
            },
            journalEntries = journals.map { j ->
                IncidentJournalEntryItem(
                    id = j.id ?: UUID.randomUUID(),
                    incidentId = j.incidentId,
                    entry = j.entry,
                    enteredBy = j.enteredBy,
                    enteredByName = employees[j.enteredBy]?.displayName ?: "Investigator",
                    enteredAt = j.enteredAt,
                )
            },
            appeals = appeals.map { a ->
                DisciplinaryAppealItem(
                    id = a.id ?: UUID.randomUUID(),
                    incidentId = a.incidentId,
                    correctiveActionId = a.correctiveActionId,
                    reason = a.reason,
                    appealedAt = a.appealedAt,
                    outcome = a.outcome,
                    reviewedAt = a.reviewedAt,
                    status = a.status,
                )
            },
        )
    }

    /**
     * Reports a new workplace incident.
     */
    @Transactional
    fun reportIncident(
        tenantId: UUID = defaultTenantId,
        reportedByEmployeeId: UUID,
        request: DisciplinaryIncidentReportRequest,
    ): DisciplinaryIncidentItem {
        require(request.description.isNotBlank()) { "Incident description cannot be blank" }

        val type = incidentTypeRepository.findById(request.incidentTypeId).orElse(null)
            ?: error("Invalid incident type ID: ${request.incidentTypeId}")

        val year = Year.now().value
        val sequenceHex = UUID.randomUUID().toString().substring(0, 6).uppercase()
        val incidentNumber = "DISC-$year-$sequenceHex"

        val incident = DisciplinaryIncident(
            incidentNumber = incidentNumber,
            employeeId = request.employeeId,
            reportedByEmployeeId = reportedByEmployeeId,
            incidentTypeId = request.incidentTypeId,
            subtypeId = request.subtypeId,
            incidentDate = request.incidentDate,
            location = request.location,
            description = request.description,
            witnesses = request.witnesses?.toMutableList() ?: mutableListOf(),
            status = IncidentStatus.REPORTED,
            severity = request.severity ?: type.severity,
        ).apply {
            this.tenantId = tenantId
        }

        val saved = incidentRepository.save(incident)

        // Log initial journal entry
        val initialEntry = IncidentJournal(
            incidentId = saved.id ?: UUID.randomUUID(),
            entry = "Incident reported: ${type.name}. Initial status set to REPORTED.",
            enteredBy = reportedByEmployeeId,
            enteredAt = Instant.now(),
        ).apply {
            this.tenantId = tenantId
        }
        journalRepository.save(initialEntry)

        val empName = employeeLookupService.findDisplayName(saved.employeeId) ?: "Employee ${saved.employeeId}"
        val reporterName = employeeLookupService.findDisplayName(reportedByEmployeeId) ?: "Reporter $reportedByEmployeeId"
        val subtype = saved.subtypeId?.let { incidentSubtypeRepository.findById(it).orElse(null) }

        return DisciplinaryIncidentItem(
            id = saved.id ?: UUID.randomUUID(),
            incidentNumber = saved.incidentNumber,
            employeeId = saved.employeeId,
            employeeName = empName,
            reportedByEmployeeId = reportedByEmployeeId,
            reportedByEmployeeName = reporterName,
            typeCode = type.code,
            typeName = type.name,
            subtypeName = subtype?.name,
            incidentDate = saved.incidentDate,
            location = saved.location,
            description = saved.description,
            severity = saved.severity,
            status = saved.status,
            createdAt = saved.createdAt ?: Instant.now(),
        )
    }

    /**
     * Issues a progressive corrective action for an incident.
     */
    @Transactional
    fun issueCorrectiveAction(
        tenantId: UUID = defaultTenantId,
        incidentId: UUID,
        issuedBy: UUID,
        request: IssueCorrectiveActionRequest,
    ): CorrectiveActionItem {
        require(request.title.isNotBlank()) { "Action title cannot be blank" }
        require(request.details.isNotBlank()) { "Action details cannot be blank" }

        val incident = incidentRepository.findByTenantIdAndId(tenantId, incidentId)
            ?: error("Incident not found: $incidentId")

        val action = CorrectiveAction(
            incidentId = incidentId,
            actionType = request.actionType,
            issuedAt = Instant.now(),
            issuedBy = issuedBy,
            title = request.title,
            details = request.details,
            responseDueDate = request.responseDueDate,
            effectiveFrom = request.effectiveFrom,
            effectiveTo = request.effectiveTo,
            status = CorrectiveActionStatus.ISSUED,
        ).apply {
            this.tenantId = tenantId
        }

        val savedAction = correctiveActionRepository.save(action)

        // Advance incident status
        incident.status = IncidentStatus.ACTION_ISSUED
        incidentRepository.save(incident)

        // Journal log
        val journal = IncidentJournal(
            incidentId = incidentId,
            entry = "Issued progressive corrective action: [${request.actionType}] ${request.title}. Response due: ${request.responseDueDate ?: "N/A"}.",
            enteredBy = issuedBy,
            enteredAt = Instant.now(),
        ).apply {
            this.tenantId = tenantId
        }
        journalRepository.save(journal)

        val issuerName = employeeLookupService.findDisplayName(issuedBy) ?: "HR Authority"
        return CorrectiveActionItem(
            id = savedAction.id ?: UUID.randomUUID(),
            incidentId = savedAction.incidentId,
            actionType = savedAction.actionType,
            issuedAt = savedAction.issuedAt,
            issuedBy = savedAction.issuedBy,
            issuedByName = issuerName,
            title = savedAction.title,
            details = savedAction.details,
            responseDueDate = savedAction.responseDueDate,
            employeeResponse = savedAction.employeeResponse,
            respondedAt = savedAction.respondedAt,
            outcome = savedAction.outcome,
            effectiveFrom = savedAction.effectiveFrom,
            effectiveTo = savedAction.effectiveTo,
            status = savedAction.status,
        )
    }

    /**
     * Appends an investigation journal entry or interview note.
     */
    @Transactional
    fun addJournalEntry(
        tenantId: UUID = defaultTenantId,
        incidentId: UUID,
        enteredBy: UUID,
        request: AddJournalEntryRequest,
    ): IncidentJournalEntryItem {
        require(request.entry.isNotBlank()) { "Journal entry text cannot be blank" }

        incidentRepository.findByTenantIdAndId(tenantId, incidentId)
            ?: error("Incident not found: $incidentId")

        val journal = IncidentJournal(
            incidentId = incidentId,
            entry = request.entry,
            enteredBy = enteredBy,
            enteredAt = Instant.now(),
        ).apply {
            this.tenantId = tenantId
        }
        val saved = journalRepository.save(journal)
        val authorName = employeeLookupService.findDisplayName(enteredBy) ?: "Investigator"

        return IncidentJournalEntryItem(
            id = saved.id ?: UUID.randomUUID(),
            incidentId = saved.incidentId,
            entry = saved.entry,
            enteredBy = saved.enteredBy,
            enteredByName = authorName,
            enteredAt = saved.enteredAt,
        )
    }

    /**
     * Allows an employee to submit their formal explanation or response to a show cause notice or warning.
     */
    @Transactional
    fun respondToCorrectiveAction(
        tenantId: UUID = defaultTenantId,
        actionId: UUID,
        employeeId: UUID,
        request: CorrectiveActionResponseRequest,
    ): CorrectiveActionItem {
        require(request.employeeResponse.isNotBlank()) { "Employee response cannot be blank" }

        val action = correctiveActionRepository.findByTenantIdAndId(tenantId, actionId)
            ?: error("Corrective action not found: $actionId")

        action.employeeResponse = request.employeeResponse
        action.respondedAt = Instant.now()
        action.status = CorrectiveActionStatus.RESPONDED
        val saved = correctiveActionRepository.save(action)

        // Journal log on parent incident
        val journal = IncidentJournal(
            incidentId = action.incidentId,
            entry = "Employee submitted formal written response to action [${action.actionType}]. Status transitioned to RESPONDED.",
            enteredBy = employeeId,
            enteredAt = Instant.now(),
        ).apply {
            this.tenantId = tenantId
        }
        journalRepository.save(journal)

        val issuerName = employeeLookupService.findDisplayName(action.issuedBy) ?: "HR Authority"
        return CorrectiveActionItem(
            id = saved.id ?: UUID.randomUUID(),
            incidentId = saved.incidentId,
            actionType = saved.actionType,
            issuedAt = saved.issuedAt,
            issuedBy = saved.issuedBy,
            issuedByName = issuerName,
            title = saved.title,
            details = saved.details,
            responseDueDate = saved.responseDueDate,
            employeeResponse = saved.employeeResponse,
            respondedAt = saved.respondedAt,
            outcome = saved.outcome,
            effectiveFrom = saved.effectiveFrom,
            effectiveTo = saved.effectiveTo,
            status = saved.status,
        )
    }

    /**
     * Submits a formal appeal against a disciplinary corrective action.
     */
    @Transactional
    fun appealCorrectiveAction(
        tenantId: UUID = defaultTenantId,
        actionId: UUID,
        employeeId: UUID,
        request: DisciplinaryAppealRequest,
    ): DisciplinaryAppealItem {
        require(request.reason.isNotBlank()) { "Appeal reason cannot be blank" }

        val action = correctiveActionRepository.findByTenantIdAndId(tenantId, actionId)
            ?: error("Corrective action not found: $actionId")

        val existingAppeal = appealRepository.findByTenantIdAndCorrectiveActionId(tenantId, actionId)
        if (existingAppeal != null) {
            error("An appeal has already been lodged for corrective action $actionId")
        }

        val appeal = DisciplinaryAppeal(
            incidentId = action.incidentId,
            correctiveActionId = actionId,
            reason = request.reason,
            appealedAt = Instant.now(),
            status = DisciplinaryAppealStatus.SUBMITTED,
        ).apply {
            this.tenantId = tenantId
        }
        val savedAppeal = appealRepository.save(appeal)

        action.status = CorrectiveActionStatus.APPEALED
        correctiveActionRepository.save(action)

        val incident = incidentRepository.findByTenantIdAndId(tenantId, action.incidentId)
        if (incident != null) {
            incident.status = IncidentStatus.APPEALED
            incidentRepository.save(incident)
        }

        // Journal entry
        val journal = IncidentJournal(
            incidentId = action.incidentId,
            entry = "Employee lodged formal appeal against corrective action [${action.actionType}]. Reason: ${request.reason}",
            enteredBy = employeeId,
            enteredAt = Instant.now(),
        ).apply {
            this.tenantId = tenantId
        }
        journalRepository.save(journal)

        return DisciplinaryAppealItem(
            id = savedAppeal.id ?: UUID.randomUUID(),
            incidentId = savedAppeal.incidentId,
            correctiveActionId = savedAppeal.correctiveActionId,
            reason = savedAppeal.reason,
            appealedAt = savedAppeal.appealedAt,
            outcome = savedAppeal.outcome,
            reviewedAt = savedAppeal.reviewedAt,
            status = savedAppeal.status,
        )
    }
}
