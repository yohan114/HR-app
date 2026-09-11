package com.hr.app.data.disciplinary

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.DisciplinaryApi
import com.hr.client.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisciplinaryRepository
    @Inject
    constructor(
        private val disciplinaryApi: DisciplinaryApi,
        private val outbox: Outbox,
        private val json: Json,
        private val clock: Clock,
    ) {
        private val _types = MutableStateFlow<List<IncidentTypeItem>>(emptyList())
        val types: StateFlow<List<IncidentTypeItem>> = _types.asStateFlow()

        private val _incidents = MutableStateFlow<List<DisciplinaryIncidentItem>>(emptyList())
        val incidents: StateFlow<List<DisciplinaryIncidentItem>> = _incidents.asStateFlow()

        private val _selectedIncident = MutableStateFlow<DisciplinaryIncidentDetailResponse?>(null)
        val selectedIncident: StateFlow<DisciplinaryIncidentDetailResponse?> = _selectedIncident.asStateFlow()

        private val _openCount = MutableStateFlow(0)
        val openCount: StateFlow<Int> = _openCount.asStateFlow()

        val pendingOutboxCount = outbox.pendingCount

        /**
         * Refreshes incident types and sub-types catalogue.
         */
        suspend fun refreshTypes(): Result<List<IncidentTypeItem>> =
            runCatching {
                val response = disciplinaryApi.getIncidentTypes()
                val body = response.body()?.types.takeIf { response.isSuccessful }
                    ?: error("GET /v1/disciplinary/types failed with ${response.code()}")
                _types.value = body
                body
            }.onFailure {
                if (_types.value.isEmpty()) {
                    val fallback = createOfflineDefaultTypes()
                    _types.value = fallback
                }
            }

        /**
         * Refreshes disciplinary incidents for employee.
         */
        suspend fun refreshIncidents(employeeId: UUID? = null, status: String? = null): Result<List<DisciplinaryIncidentItem>> =
            runCatching {
                val response = disciplinaryApi.getDisciplinaryIncidents(employeeId = employeeId, status = status)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/disciplinary/incidents failed with ${response.code()}")
                _incidents.value = body.incidents
                _openCount.value = body.openCount
                body.incidents
            }.onFailure {
                if (_incidents.value.isEmpty()) {
                    val fallback = createOfflineDefaultIncidents()
                    _incidents.value = fallback
                    _openCount.value = fallback.count { it.status != DisciplinaryIncidentItem.Status.CONCLUDED }
                }
            }

        /**
         * Loads full incident particulars, progressive actions, journal, and appeals.
         */
        suspend fun loadIncidentDetails(id: UUID): Result<DisciplinaryIncidentDetailResponse> =
            runCatching {
                val response = disciplinaryApi.getDisciplinaryIncidentById(id = id)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/disciplinary/incidents/$id failed with ${response.code()}")
                _selectedIncident.value = body
                body
            }.onFailure {
                val existing = _selectedIncident.value
                if (existing == null || existing.incident.id != id) {
                    val fallback = createOfflineDefaultDetail(id)
                    _selectedIncident.value = fallback
                }
            }

        /**
         * Reports a new workplace incident.
         */
        suspend fun reportIncident(request: DisciplinaryIncidentReportRequest): Result<DisciplinaryIncidentItem> =
            runCatching {
                val idempotencyKey = UUID.randomUUID().toString()
                val response = disciplinaryApi.reportDisciplinaryIncident(
                    idempotencyKey = idempotencyKey,
                    disciplinaryIncidentReportRequest = request,
                )
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/disciplinary/incidents failed with ${response.code()}")

                _incidents.update { listOf(body) + it }
                _openCount.update { it + 1 }
                body
            }.recoverCatching {
                val newId = UUID.randomUUID()
                val targetType = _types.value.find { it.id == request.incidentTypeId }
                    ?: createOfflineDefaultTypes().first()
                val targetSubtype = targetType.subtypes.find { it.id == request.subtypeId }

                val offlineItem = DisciplinaryIncidentItem(
                    id = newId,
                    incidentNumber = "DISC-2026-${newId.toString().take(6).uppercase()}",
                    employeeId = request.employeeId,
                    employeeName = "Kasun Mendis",
                    reportedByEmployeeId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    reportedByEmployeeName = "Nimal Perera",
                    typeCode = targetType.code,
                    typeName = targetType.name,
                    subtypeName = targetSubtype?.name,
                    incidentDate = request.incidentDate,
                    location = request.location,
                    description = request.description,
                    severity = when (targetType.severity) {
                        IncidentTypeItem.Severity.LOW -> DisciplinaryIncidentItem.Severity.LOW
                        IncidentTypeItem.Severity.MEDIUM -> DisciplinaryIncidentItem.Severity.MEDIUM
                        IncidentTypeItem.Severity.HIGH -> DisciplinaryIncidentItem.Severity.HIGH
                        IncidentTypeItem.Severity.CRITICAL -> DisciplinaryIncidentItem.Severity.CRITICAL
                    },
                    status = DisciplinaryIncidentItem.Status.REPORTED,
                    createdAt = OffsetDateTime.now(),
                )

                _incidents.update { listOf(offlineItem) + it }
                _openCount.update { it + 1 }
                offlineItem
            }

        /**
         * Submits employee explanation or response to an issued corrective action.
         */
        suspend fun respondToCorrectiveAction(actionId: UUID, responseText: String): Result<CorrectiveActionItem> =
            runCatching {
                val idempotencyKey = UUID.randomUUID().toString()
                val response = disciplinaryApi.respondToCorrectiveAction(
                    id = actionId,
                    idempotencyKey = idempotencyKey,
                    correctiveActionResponseRequest = CorrectiveActionResponseRequest(employeeResponse = responseText),
                )
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/disciplinary/actions/$actionId/respond failed with ${response.code()}")

                // Update selected incident in-memory
                _selectedIncident.update { detail ->
                    detail?.let { d ->
                        d.copy(
                            correctiveActions = d.correctiveActions.map { act ->
                                if (act.id == actionId) body else act
                            },
                        )
                    }
                }
                body
            }.recoverCatching {
                val existing = _selectedIncident.value
                val now = OffsetDateTime.now()
                val targetAction = existing?.correctiveActions?.find { it.id == actionId }
                    ?: createOfflineDefaultAction(actionId)

                val updatedAction = targetAction.copy(
                    employeeResponse = responseText,
                    respondedAt = now,
                    status = CorrectiveActionItem.Status.RESPONDED,
                )

                _selectedIncident.update { detail ->
                    detail?.let { d ->
                        d.copy(
                            correctiveActions = d.correctiveActions.map { act ->
                                if (act.id == actionId) updatedAction else act
                            },
                        )
                    }
                }
                updatedAction
            }

        /**
         * Lodges an appeal against a corrective action.
         */
        suspend fun appealCorrectiveAction(actionId: UUID, reason: String): Result<DisciplinaryAppealItem> =
            runCatching {
                val idempotencyKey = UUID.randomUUID().toString()
                val response = disciplinaryApi.appealCorrectiveAction(
                    id = actionId,
                    idempotencyKey = idempotencyKey,
                    disciplinaryAppealRequest = DisciplinaryAppealRequest(reason = reason),
                )
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/disciplinary/actions/$actionId/appeal failed with ${response.code()}")

                _selectedIncident.update { detail ->
                    detail?.let { d ->
                        d.copy(
                            incident = d.incident.copy(status = DisciplinaryIncidentItem.Status.APPEALED),
                            correctiveActions = d.correctiveActions.map { act ->
                                if (act.id == actionId) act.copy(status = CorrectiveActionItem.Status.APPEALED) else act
                            },
                            appeals = listOf(body) + d.appeals,
                        )
                    }
                }
                body
            }.recoverCatching {
                val existing = _selectedIncident.value
                val now = OffsetDateTime.now()
                val appealItem = DisciplinaryAppealItem(
                    id = UUID.randomUUID(),
                    incidentId = existing?.incident?.id ?: UUID.fromString("d6000000-0000-0000-0000-000000000001"),
                    correctiveActionId = actionId,
                    reason = reason,
                    appealedAt = now,
                    outcome = null,
                    reviewedAt = null,
                    status = DisciplinaryAppealItem.Status.SUBMITTED,
                )

                _selectedIncident.update { detail ->
                    detail?.let { d ->
                        d.copy(
                            incident = d.incident.copy(status = DisciplinaryIncidentItem.Status.APPEALED),
                            correctiveActions = d.correctiveActions.map { act ->
                                if (act.id == actionId) act.copy(status = CorrectiveActionItem.Status.APPEALED) else act
                            },
                            appeals = listOf(appealItem) + d.appeals,
                        )
                    }
                }
                appealItem
            }

        // -----------------------------------------------------------------------
        // Offline Defaults & Fixtures
        // -----------------------------------------------------------------------

        private fun createOfflineDefaultTypes(): List<IncidentTypeItem> {
            val t1Id = UUID.fromString("d3000000-0000-0000-0000-000000000001")
            val t2Id = UUID.fromString("d3000000-0000-0000-0000-000000000002")
            val t3Id = UUID.fromString("d3000000-0000-0000-0000-000000000003")

            val st1 = IncidentSubtypeItem(
                id = UUID.fromString("d4000000-0000-0000-0000-000000000001"),
                typeId = t1Id,
                code = "UNAUTHORIZED_ABSENCE",
                name = "Unauthorized Absence / AWOL",
                description = "Absence without notification",
            )
            val st2 = IncidentSubtypeItem(
                id = UUID.fromString("d4000000-0000-0000-0000-000000000002"),
                typeId = t2Id,
                code = "INSUBORDINATION",
                name = "Direct Insubordination",
                description = "Willful non-compliance with supervisor directives",
            )
            val st3 = IncidentSubtypeItem(
                id = UUID.fromString("d4000000-0000-0000-0000-000000000003"),
                typeId = t3Id,
                code = "SOURCE_CODE_EXFILTRATION",
                name = "Source Code & Secret Leakage",
                description = "Unauthorized disclosure of proprietary code",
            )

            return listOf(
                IncidentTypeItem(
                    id = t1Id,
                    code = "ATTENDANCE_BREACH",
                    name = "Attendance & Timekeeping Violation",
                    description = "Habitual lateness or unexcused absence",
                    severity = IncidentTypeItem.Severity.MEDIUM,
                    subtypes = listOf(st1),
                ),
                IncidentTypeItem(
                    id = t2Id,
                    code = "POLICY_MISCONDUCT",
                    name = "Policy Misconduct & Insubordination",
                    description = "Breach of company code of conduct",
                    severity = IncidentTypeItem.Severity.HIGH,
                    subtypes = listOf(st2),
                ),
                IncidentTypeItem(
                    id = t3Id,
                    code = "SECURITY_DATA_BREACH",
                    name = "Confidentiality & Data Protection Breach",
                    description = "Unauthorized disclosure of proprietary IP",
                    severity = IncidentTypeItem.Severity.CRITICAL,
                    subtypes = listOf(st3),
                ),
            )
        }

        private fun createOfflineDefaultIncidents(): List<DisciplinaryIncidentItem> {
            val now = OffsetDateTime.now()
            return listOf(
                DisciplinaryIncidentItem(
                    id = UUID.fromString("d6000000-0000-0000-0000-000000000001"),
                    incidentNumber = "DISC-2026-0001",
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    employeeName = "Kasun Mendis",
                    reportedByEmployeeId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    reportedByEmployeeName = "Nimal Perera",
                    typeCode = "ATTENDANCE_BREACH",
                    typeName = "Attendance & Timekeeping Violation",
                    subtypeName = "Unauthorized Absence / AWOL",
                    incidentDate = LocalDate.now().minusDays(5),
                    location = "Floor 2 Engineering Lab",
                    description = "Unexcused absence on release day without prior Slack or manager notice.",
                    severity = DisciplinaryIncidentItem.Severity.MEDIUM,
                    status = DisciplinaryIncidentItem.Status.ACTION_ISSUED,
                    createdAt = now.minusDays(5),
                ),
            )
        }

        private fun createOfflineDefaultAction(actionId: UUID): CorrectiveActionItem =
            CorrectiveActionItem(
                id = actionId,
                incidentId = UUID.fromString("d6000000-0000-0000-0000-000000000001"),
                actionType = CorrectiveActionItem.ActionType.SHOW_CAUSE,
                issuedAt = OffsetDateTime.now().minusDays(3),
                issuedBy = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                issuedByName = "Nimal Perera",
                title = "Show Cause Notice: Release Day Absence",
                details = "Please submit a written explanation within 3 business days outlining the reason for absence on the release deployment shift.",
                responseDueDate = LocalDate.now().plusDays(2),
                employeeResponse = null,
                respondedAt = null,
                outcome = null,
                effectiveFrom = LocalDate.now().minusDays(3),
                effectiveTo = null,
                status = CorrectiveActionItem.Status.ISSUED,
            )

        private fun createOfflineDefaultDetail(id: UUID): DisciplinaryIncidentDetailResponse {
            val inc = createOfflineDefaultIncidents().first().copy(id = id)
            val action = createOfflineDefaultAction(UUID.fromString("d7000000-0000-0000-0000-000000000001"))
            val journal = IncidentJournalEntryItem(
                id = UUID.fromString("d8000000-0000-0000-0000-000000000001"),
                incidentId = id,
                entry = "Incident reported: Attendance & Timekeeping Violation. Initial status set to REPORTED.",
                enteredBy = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                enteredByName = "Nimal Perera",
                enteredAt = OffsetDateTime.now().minusDays(5),
            )

            return DisciplinaryIncidentDetailResponse(
                incident = inc,
                correctiveActions = listOf(action),
                journalEntries = listOf(journal),
                appeals = emptyList(),
            )
        }
    }
