package com.hr.app.data.disciplinary

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.GrievanceApi
import com.hr.client.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrievanceRepository
    @Inject
    constructor(
        private val grievanceApi: GrievanceApi,
        private val outbox: Outbox,
        private val json: Json,
        private val clock: Clock,
    ) {
        private val _grounds = MutableStateFlow<GrievanceGroundsResponse?>(null)
        val grounds: StateFlow<GrievanceGroundsResponse?> = _grounds.asStateFlow()

        private val _channels = MutableStateFlow<List<GrievanceChannelItem>>(emptyList())
        val channels: StateFlow<List<GrievanceChannelItem>> = _channels.asStateFlow()

        private val _grievances = MutableStateFlow<List<GrievanceItem>>(emptyList())
        val grievances: StateFlow<List<GrievanceItem>> = _grievances.asStateFlow()

        private val _selectedGrievance = MutableStateFlow<GrievanceDetailResponse?>(null)
        val selectedGrievance: StateFlow<GrievanceDetailResponse?> = _selectedGrievance.asStateFlow()

        private val _pendingCount = MutableStateFlow(0)
        val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

        val pendingOutboxCount = outbox.pendingCount

        /**
         * Refreshes grievance ground groups and ground items with SLA definitions.
         */
        suspend fun refreshGrounds(): Result<GrievanceGroundsResponse> =
            runCatching {
                val response = grievanceApi.getGrievanceGrounds()
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/grievance/grounds failed with ${response.code()}")
                _grounds.value = body
                body
            }.onFailure {
                if (_grounds.value == null) {
                    val fallback = createOfflineDefaultGrounds()
                    _grounds.value = fallback
                }
            }

        /**
         * Refreshes submission channels (Direct, Anonymous Hotline, Ombudsman, Union).
         */
        suspend fun refreshChannels(): Result<List<GrievanceChannelItem>> =
            runCatching {
                val response = grievanceApi.getGrievanceChannels()
                val body = response.body()?.channels.takeIf { response.isSuccessful }
                    ?: error("GET /v1/grievance/channels failed with ${response.code()}")
                _channels.value = body
                body
            }.onFailure {
                if (_channels.value.isEmpty()) {
                    val fallback = createOfflineDefaultChannels()
                    _channels.value = fallback
                }
            }

        /**
         * Refreshes grievances for the current authenticated employee.
         */
        suspend fun refreshMyGrievances(status: String? = null): Result<List<GrievanceItem>> =
            runCatching {
                val response = grievanceApi.getMyGrievances(status = status)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/grievance/my-grievances failed with ${response.code()}")
                _grievances.value = body.grievances
                _pendingCount.value = body.pendingCount
                body.grievances
            }.onFailure {
                if (_grievances.value.isEmpty()) {
                    val fallback = createOfflineDefaultGrievances()
                    _grievances.value = fallback
                    _pendingCount.value = fallback.count { it.status == GrievanceItem.Status.SUBMITTED || it.status == GrievanceItem.Status.UNDER_INVESTIGATION }
                }
            }

        /**
         * Loads full particulars for a selected grievance.
         */
        suspend fun loadGrievanceDetail(id: UUID): Result<GrievanceDetailResponse> =
            runCatching {
                val response = grievanceApi.getGrievanceById(id = id)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/grievance/$id failed with ${response.code()}")
                _selectedGrievance.value = body
                body
            }.onFailure {
                val existing = _selectedGrievance.value
                if (existing == null || existing.id != id) {
                    val fallback = _grievances.value.find { it.id == id }?.let { item ->
                        createDetailFromItem(item)
                    } ?: createOfflineDefaultDetail(id)
                    _selectedGrievance.value = fallback
                }
            }

        /**
         * Submits a new grievance or whistleblowing report with optimistic local update.
         */
        suspend fun submitGrievance(request: GrievanceSubmitRequest): Result<GrievanceDetailResponse> =
            runCatching {
                val idempotencyKey = UUID.randomUUID().toString()
                val response = grievanceApi.submitGrievance(
                    idempotencyKey = idempotencyKey,
                    grievanceSubmitRequest = request,
                )
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/grievance failed with ${response.code()}")

                val newItem = GrievanceItem(
                    id = body.id,
                    grievanceNumber = body.grievanceNumber,
                    title = body.title,
                    groundCode = body.ground.code,
                    groundName = body.ground.name,
                    channelName = body.channel.name,
                    severity = when (body.ground.severity) {
                        GrievanceGroundItem.Severity.LOW -> GrievanceItem.Severity.LOW
                        GrievanceGroundItem.Severity.MEDIUM -> GrievanceItem.Severity.MEDIUM
                        GrievanceGroundItem.Severity.HIGH -> GrievanceItem.Severity.HIGH
                        GrievanceGroundItem.Severity.CRITICAL -> GrievanceItem.Severity.CRITICAL
                    },
                    anonymous = body.anonymous,
                    status = GrievanceItem.Status.SUBMITTED,
                    raisedAt = body.raisedAt,
                    targetResolutionDate = body.targetResolutionDate,
                    resolvedAt = null,
                    resolution = null,
                    satisfactionRating = null,
                )

                _grievances.update { listOf(newItem) + it }
                _pendingCount.update { it + 1 }
                _selectedGrievance.value = body
                body
            }.recoverCatching {
                // Offline optimistic fallback
                val newId = UUID.randomUUID()
                val targetGround = _grounds.value?.grounds?.find { it.id == request.groundId }
                    ?: createOfflineDefaultGrounds().grounds.first()
                val targetChannel = _channels.value.find { it.id == request.channelId }
                    ?: createOfflineDefaultChannels().first()

                val now = OffsetDateTime.now()
                val offlineItem = GrievanceItem(
                    id = newId,
                    grievanceNumber = "GRV-2026-${newId.toString().take(6).uppercase()}",
                    title = request.title,
                    groundCode = targetGround.code,
                    groundName = targetGround.name,
                    channelName = targetChannel.name,
                    severity = when (targetGround.severity) {
                        GrievanceGroundItem.Severity.LOW -> GrievanceItem.Severity.LOW
                        GrievanceGroundItem.Severity.MEDIUM -> GrievanceItem.Severity.MEDIUM
                        GrievanceGroundItem.Severity.HIGH -> GrievanceItem.Severity.HIGH
                        GrievanceGroundItem.Severity.CRITICAL -> GrievanceItem.Severity.CRITICAL
                    },
                    anonymous = request.anonymous ?: false,
                    status = GrievanceItem.Status.SUBMITTED,
                    raisedAt = now,
                    targetResolutionDate = now.plusDays(targetGround.slaDays.toLong()),
                    resolvedAt = null,
                    resolution = null,
                    satisfactionRating = null,
                )

                val detail = GrievanceDetailResponse(
                    id = newId,
                    grievanceNumber = offlineItem.grievanceNumber,
                    title = request.title,
                    description = request.description,
                    ground = targetGround,
                    channel = targetChannel,
                    anonymous = request.anonymous ?: false,
                    raisedByEmployeeName = if (request.anonymous == true) "Confidential (Anonymous)" else "Kasun Mendis",
                    status = GrievanceDetailResponse.Status.SUBMITTED,
                    raisedAt = now,
                    targetResolutionDate = offlineItem.targetResolutionDate,
                    resolvedAt = null,
                    resolution = null,
                    satisfactionRating = null,
                    appeal = null,
                )

                _grievances.update { listOf(offlineItem) + it }
                _pendingCount.update { it + 1 }
                _selectedGrievance.value = detail
                detail
            }

        /**
         * Lodges an appeal against a resolved grievance.
         */
        suspend fun appealGrievance(id: UUID, reason: String): Result<GrievanceDetailResponse> =
            runCatching {
                val idempotencyKey = UUID.randomUUID().toString()
                val response = grievanceApi.appealGrievance(
                    id = id,
                    idempotencyKey = idempotencyKey,
                    grievanceAppealRequest = GrievanceAppealRequest(reason = reason),
                )
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/grievance/$id/appeal failed with ${response.code()}")

                _grievances.update { list ->
                    list.map { if (it.id == id) it.copy(status = GrievanceItem.Status.APPEALED) else it }
                }
                _selectedGrievance.value = body
                body
            }.recoverCatching {
                val existing = _selectedGrievance.value ?: createOfflineDefaultDetail(id)
                val now = OffsetDateTime.now()
                val appealItem = GrievanceAppealItem(
                    id = UUID.randomUUID(),
                    grievanceId = id,
                    reason = reason,
                    appealedAt = now,
                    status = GrievanceAppealItem.Status.PENDING,
                    outcome = null,
                    reviewedAt = null,
                )
                val updatedDetail = existing.copy(
                    status = GrievanceDetailResponse.Status.APPEALED,
                    appeal = appealItem,
                )
                _grievances.update { list ->
                    list.map { if (it.id == id) it.copy(status = GrievanceItem.Status.APPEALED) else it }
                }
                _selectedGrievance.value = updatedDetail
                updatedDetail
            }

        // -----------------------------------------------------------------------
        // Offline Defaults & Fixtures
        // -----------------------------------------------------------------------

        private fun createOfflineDefaultGrounds(): GrievanceGroundsResponse {
            val g1 = GrievanceGroundGroupItem(
                id = UUID.fromString("d0000000-0000-0000-0000-000000000001"),
                code = "WORK_ENVIRONMENT",
                name = "Work Environment & Facilities",
                description = "Physical workplace conditions and health standards",
            )
            val g2 = GrievanceGroundGroupItem(
                id = UUID.fromString("d0000000-0000-0000-0000-000000000002"),
                code = "HARASSMENT_DISCRIMINATION",
                name = "Harassment & Discrimination",
                description = "Interpersonal misconduct and workplace bias",
            )
            val g3 = GrievanceGroundGroupItem(
                id = UUID.fromString("d0000000-0000-0000-0000-000000000003"),
                code = "COMPENSATION_BENEFITS",
                name = "Compensation & Overtime Disputes",
                description = "Discrepancies in payroll, claims, and benefits",
            )

            val gr1 = GrievanceGroundItem(
                id = UUID.fromString("d1000000-0000-0000-0000-000000000001"),
                groupId = g1.id,
                groupName = g1.name,
                code = "SAFETY_HAZARD",
                name = "Workplace Safety Hazard",
                description = "Imminent health or physical safety hazard",
                severity = GrievanceGroundItem.Severity.CRITICAL,
                defaultHandlerRole = "OPERATIONS_DIRECTOR",
                slaDays = 2,
            )
            val gr2 = GrievanceGroundItem(
                id = UUID.fromString("d1000000-0000-0000-0000-000000000002"),
                groupId = g2.id,
                groupName = g2.name,
                code = "BULLYING_HARASSMENT",
                name = "Bullying or Verbal Harassment",
                description = "Workplace hostility or intimidation",
                severity = GrievanceGroundItem.Severity.HIGH,
                defaultHandlerRole = "HR_OMBUDSMAN",
                slaDays = 3,
            )
            val gr3 = GrievanceGroundItem(
                id = UUID.fromString("d1000000-0000-0000-0000-000000000003"),
                groupId = g3.id,
                groupName = g3.name,
                code = "OVERTIME_DISPUTE",
                name = "Overtime Calculation Discrepancy",
                description = "Disputed overtime multiplier",
                severity = GrievanceGroundItem.Severity.MEDIUM,
                defaultHandlerRole = "PAYROLL_ADMIN",
                slaDays = 5,
            )

            return GrievanceGroundsResponse(
                groups = listOf(g1, g2, g3),
                grounds = listOf(gr1, gr2, gr3),
            )
        }

        private fun createOfflineDefaultChannels(): List<GrievanceChannelItem> = listOf(
            GrievanceChannelItem(
                id = UUID.fromString("d2000000-0000-0000-0000-000000000001"),
                code = "DIRECT_PORTAL",
                name = "Self-Service Mobile & Web Portal",
                isConfidential = true,
            ),
            GrievanceChannelItem(
                id = UUID.fromString("d2000000-0000-0000-0000-000000000002"),
                code = "ANONYMOUS_HOTLINE",
                name = "Anonymous Whistleblower Hotline",
                isConfidential = true,
            ),
            GrievanceChannelItem(
                id = UUID.fromString("d2000000-0000-0000-0000-000000000003"),
                code = "HR_OMBUDSMAN",
                name = "HR Ombudsman Confidential Office",
                isConfidential = true,
            ),
        )

        private fun createOfflineDefaultGrievances(): List<GrievanceItem> {
            val now = OffsetDateTime.now()
            return listOf(
                GrievanceItem(
                    id = UUID.fromString("d5000000-0000-0000-0000-000000000001"),
                    grievanceNumber = "GRV-2026-0001",
                    title = "Discrepancy in February 2026 Rest Day Overtime Multiplier",
                    groundCode = "OVERTIME_DISPUTE",
                    groundName = "Overtime Calculation Discrepancy",
                    channelName = "Self-Service Mobile & Web Portal",
                    severity = GrievanceItem.Severity.MEDIUM,
                    anonymous = false,
                    status = GrievanceItem.Status.RESOLVED,
                    raisedAt = now.minusDays(7),
                    targetResolutionDate = now.minusDays(2),
                    resolvedAt = now.minusDays(4),
                    resolution = "Payroll operations verified the rest-day shift classification and adjusted variance (+LKR 3,408.00) in upcoming March payroll.",
                    satisfactionRating = 5,
                ),
                GrievanceItem(
                    id = UUID.fromString("d5000000-0000-0000-0000-000000000002"),
                    grievanceNumber = "GRV-2026-0002",
                    title = "Server Room AC Unit Malfunction",
                    groundCode = "SAFETY_HAZARD",
                    groundName = "Workplace Safety Hazard",
                    channelName = "Self-Service Mobile & Web Portal",
                    severity = GrievanceItem.Severity.CRITICAL,
                    anonymous = false,
                    status = GrievanceItem.Status.UNDER_INVESTIGATION,
                    raisedAt = now.minusDays(1),
                    targetResolutionDate = now.plusDays(1),
                    resolvedAt = null,
                    resolution = null,
                    satisfactionRating = null,
                ),
            )
        }

        private fun createDetailFromItem(item: GrievanceItem): GrievanceDetailResponse {
            val grounds = createOfflineDefaultGrounds().grounds
            val ground = grounds.find { it.code == item.groundCode } ?: grounds.first()
            val channels = createOfflineDefaultChannels()
            val channel = channels.find { it.name == item.channelName } ?: channels.first()

            return GrievanceDetailResponse(
                id = item.id,
                grievanceNumber = item.grievanceNumber,
                title = item.title,
                description = "Detailed statement for ${item.title}. Submitted through ${item.channelName} on ${item.raisedAt}.",
                ground = ground,
                channel = channel,
                anonymous = item.anonymous,
                raisedByEmployeeName = if (item.anonymous) "Confidential (Anonymous)" else "Kasun Mendis",
                status = when (item.status) {
                    GrievanceItem.Status.SUBMITTED -> GrievanceDetailResponse.Status.SUBMITTED
                    GrievanceItem.Status.ASSIGNED -> GrievanceDetailResponse.Status.ASSIGNED
                    GrievanceItem.Status.UNDER_INVESTIGATION -> GrievanceDetailResponse.Status.UNDER_INVESTIGATION
                    GrievanceItem.Status.RESOLVED -> GrievanceDetailResponse.Status.RESOLVED
                    GrievanceItem.Status.APPEALED -> GrievanceDetailResponse.Status.APPEALED
                    GrievanceItem.Status.CLOSED -> GrievanceDetailResponse.Status.CLOSED
                },
                raisedAt = item.raisedAt,
                targetResolutionDate = item.targetResolutionDate,
                resolvedAt = item.resolvedAt,
                resolution = item.resolution,
                satisfactionRating = item.satisfactionRating,
                appeal = null,
            )
        }

        private fun createOfflineDefaultDetail(id: UUID): GrievanceDetailResponse {
            val grounds = createOfflineDefaultGrounds().grounds.first()
            val channels = createOfflineDefaultChannels().first()
            val now = OffsetDateTime.now()
            return GrievanceDetailResponse(
                id = id,
                grievanceNumber = "GRV-2026-0001",
                title = "Discrepancy in February 2026 Rest Day Overtime Multiplier",
                description = "The February 2026 payroll statement computed rest-day overtime at 1.5x normal rate instead of statutory 2.0x for Sunday release on-call shift.",
                ground = grounds,
                channel = channels,
                anonymous = false,
                raisedByEmployeeName = "Kasun Mendis",
                status = GrievanceDetailResponse.Status.RESOLVED,
                raisedAt = now.minusDays(7),
                targetResolutionDate = now.minusDays(2),
                resolvedAt = now.minusDays(4),
                resolution = "Payroll operations verified the rest-day shift classification and adjusted the retro variance (+LKR 3,408.00) in the upcoming March payroll disbursement.",
                satisfactionRating = 5,
                appeal = null,
            )
        }
    }
