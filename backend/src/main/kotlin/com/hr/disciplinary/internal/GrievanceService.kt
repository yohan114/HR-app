package com.hr.disciplinary.internal

import com.hr.disciplinary.*
import com.hr.employee.EmployeeLookupService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.Year
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class GrievanceService(
    private val groundGroupRepository: GrievanceGroundGroupRepository,
    private val groundRepository: GrievanceGroundRepository,
    private val channelRepository: GrievanceChannelRepository,
    private val grievanceRepository: GrievanceRepository,
    private val appealRepository: GrievanceAppealRepository,
    private val employeeLookupService: EmployeeLookupService,
) {

    private val defaultTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    /**
     * Retrieves all configured grievance ground groups and detailed grounds with SLA definitions.
     */
    @Transactional(readOnly = true)
    fun getGrounds(tenantId: UUID = defaultTenantId): GrievanceGroundsResponse {
        val groups = groundGroupRepository.findByTenantIdOrderByCodeAsc(tenantId)
        val grounds = groundRepository.findByTenantIdOrderByCodeAsc(tenantId)
        val groupMap = groups.associateBy { it.id }

        return GrievanceGroundsResponse(
            groups = groups.map { g ->
                GrievanceGroundGroupItem(
                    id = g.id ?: UUID.randomUUID(),
                    code = g.code,
                    name = g.name,
                    description = g.description,
                )
            },
            grounds = grounds.map { gr ->
                GrievanceGroundItem(
                    id = gr.id ?: UUID.randomUUID(),
                    groupId = gr.groupId,
                    groupName = groupMap[gr.groupId]?.name,
                    code = gr.code,
                    name = gr.name,
                    description = gr.description,
                    severity = gr.severity,
                    defaultHandlerRole = gr.defaultHandlerRole,
                    slaDays = gr.slaDays,
                )
            },
        )
    }

    /**
     * Lists available grievance submission channels.
     */
    @Transactional(readOnly = true)
    fun getChannels(tenantId: UUID = defaultTenantId): GrievanceChannelsResponse {
        val channels = channelRepository.findByTenantIdOrderByCodeAsc(tenantId)
        return GrievanceChannelsResponse(
            channels = channels.map { c ->
                GrievanceChannelItem(
                    id = c.id ?: UUID.randomUUID(),
                    code = c.code,
                    name = c.name,
                    isConfidential = c.isConfidential,
                )
            },
        )
    }

    /**
     * Lists grievances raised by the authenticated employee.
     */
    @Transactional(readOnly = true)
    fun getMyGrievances(
        tenantId: UUID = defaultTenantId,
        employeeId: UUID,
        status: GrievanceStatus? = null,
    ): GrievanceListResponse {
        val all = grievanceRepository.findByTenantIdAndRaisedByEmployeeIdOrderByRaisedAtDesc(tenantId, employeeId)
        val filtered = if (status != null) all.filter { it.status == status } else all

        val grounds = groundRepository.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }
        val channels = channelRepository.findByTenantIdOrderByCodeAsc(tenantId).associateBy { it.id }

        val pendingCount = all.count {
            it.status in setOf(GrievanceStatus.SUBMITTED, GrievanceStatus.ASSIGNED, GrievanceStatus.UNDER_INVESTIGATION, GrievanceStatus.APPEALED)
        }

        return GrievanceListResponse(
            totalCount = filtered.size,
            pendingCount = pendingCount,
            grievances = filtered.map { g ->
                val ground = grounds[g.groundId]
                val channel = channels[g.channelId]
                GrievanceItem(
                    id = g.id ?: UUID.randomUUID(),
                    grievanceNumber = g.grievanceNumber,
                    title = g.title,
                    groundCode = ground?.code ?: "GENERAL",
                    groundName = ground?.name ?: "General Grievance",
                    channelName = channel?.name ?: "Direct Portal",
                    severity = ground?.severity ?: GrievanceSeverity.MEDIUM,
                    anonymous = g.anonymous,
                    status = g.status,
                    raisedAt = g.raisedAt,
                    targetResolutionDate = g.targetResolutionDate,
                    resolvedAt = g.resolvedAt,
                    resolution = g.resolution,
                    satisfactionRating = g.satisfactionRating,
                )
            },
        )
    }

    /**
     * Retrieves detailed grievance particulars, masking complainant identity if submitted anonymously.
     */
    @Transactional(readOnly = true)
    fun getGrievanceById(tenantId: UUID = defaultTenantId, id: UUID): GrievanceDetailResponse {
        val grievance = grievanceRepository.findByTenantIdAndId(tenantId, id)
            ?: error("Grievance not found: $id")

        val ground = groundRepository.findById(grievance.groundId).orElse(null)
            ?: error("Grievance ground not found: ${grievance.groundId}")
        val group = groundGroupRepository.findById(ground.groupId).orElse(null)

        val channel = channelRepository.findById(grievance.channelId).orElse(null)
            ?: error("Grievance channel not found: ${grievance.channelId}")

        val appeal = appealRepository.findByTenantIdAndGrievanceId(tenantId, id)

        val raisedByName = if (grievance.anonymous) {
            "Confidential (Anonymous Whistleblower)"
        } else if (grievance.raisedByEmployeeId != null) {
            employeeLookupService.findDisplayName(grievance.raisedByEmployeeId!!) ?: "Unknown Employee"
        } else {
            "External Complainant"
        }

        return GrievanceDetailResponse(
            id = grievance.id ?: id,
            grievanceNumber = grievance.grievanceNumber,
            title = grievance.title,
            description = grievance.description,
            ground = GrievanceGroundItem(
                id = ground.id ?: UUID.randomUUID(),
                groupId = ground.groupId,
                groupName = group?.name,
                code = ground.code,
                name = ground.name,
                description = ground.description,
                severity = ground.severity,
                defaultHandlerRole = ground.defaultHandlerRole,
                slaDays = ground.slaDays,
            ),
            channel = GrievanceChannelItem(
                id = channel.id ?: UUID.randomUUID(),
                code = channel.code,
                name = channel.name,
                isConfidential = channel.isConfidential,
            ),
            anonymous = grievance.anonymous,
            raisedByEmployeeName = raisedByName,
            status = grievance.status,
            raisedAt = grievance.raisedAt,
            targetResolutionDate = grievance.targetResolutionDate,
            resolvedAt = grievance.resolvedAt,
            resolution = grievance.resolution,
            satisfactionRating = grievance.satisfactionRating,
            appeal = appeal?.let {
                GrievanceAppealItem(
                    id = it.id ?: UUID.randomUUID(),
                    grievanceId = it.grievanceId,
                    reason = it.reason,
                    appealedAt = it.appealedAt,
                    status = it.status,
                    outcome = it.outcome,
                    reviewedAt = it.reviewedAt,
                )
            },
        )
    }

    /**
     * Submits a new grievance or anonymous whistleblowing complaint.
     */
    @Transactional
    fun submitGrievance(
        tenantId: UUID = defaultTenantId,
        employeeId: UUID,
        request: GrievanceSubmitRequest,
    ): GrievanceDetailResponse {
        require(request.title.isNotBlank()) { "Grievance title cannot be blank" }
        require(request.description.isNotBlank()) { "Grievance description cannot be blank" }

        val ground = groundRepository.findById(request.groundId).orElse(null)
            ?: error("Invalid ground ID: ${request.groundId}")
        val channel = channelRepository.findById(request.channelId).orElse(null)
            ?: error("Invalid channel ID: ${request.channelId}")

        val year = Year.now().value
        val sequenceHex = UUID.randomUUID().toString().substring(0, 6).uppercase()
        val grievanceNumber = "GRV-$year-$sequenceHex"

        val now = Instant.now()
        val targetResolution = now.plus(ground.slaDays.toLong(), ChronoUnit.DAYS)

        val grievance = Grievance(
            grievanceNumber = grievanceNumber,
            raisedByEmployeeId = employeeId,
            onBehalfOfEmployeeId = request.onBehalfOfEmployeeId,
            anonymous = request.anonymous,
            groundId = request.groundId,
            channelId = request.channelId,
            title = request.title,
            description = request.description,
            status = GrievanceStatus.SUBMITTED,
            raisedAt = now,
            targetResolutionDate = targetResolution,
        ).apply {
            this.tenantId = tenantId
        }

        val saved = grievanceRepository.save(grievance)
        return getGrievanceById(tenantId, saved.id ?: UUID.randomUUID())
    }

    /**
     * Lodges an appeal against a resolved grievance.
     */
    @Transactional
    fun appealGrievance(
        tenantId: UUID = defaultTenantId,
        grievanceId: UUID,
        employeeId: UUID,
        request: GrievanceAppealRequest,
    ): GrievanceDetailResponse {
        require(request.reason.isNotBlank()) { "Appeal reason cannot be blank" }

        val grievance = grievanceRepository.findByTenantIdAndId(tenantId, grievanceId)
            ?: error("Grievance not found: $grievanceId")

        val existingAppeal = appealRepository.findByTenantIdAndGrievanceId(tenantId, grievanceId)
        if (existingAppeal != null) {
            error("Grievance $grievanceId already has an appeal lodged on ${existingAppeal.appealedAt}")
        }

        val appeal = GrievanceAppeal(
            grievanceId = grievanceId,
            reason = request.reason,
            appealedAt = Instant.now(),
            status = AppealStatus.PENDING,
        ).apply {
            this.tenantId = tenantId
        }
        appealRepository.save(appeal)

        grievance.status = GrievanceStatus.APPEALED
        grievanceRepository.save(grievance)

        return getGrievanceById(tenantId, grievanceId)
    }
}
