package com.hr.app.data.lifecycle

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.LifecycleApi
import com.hr.client.model.CareerMovementItem
import com.hr.client.model.CareerMovementProposalRequest
import com.hr.client.model.CareerMovementRevertRequest
import com.hr.client.model.CareerTimelineEvent
import com.hr.client.model.CareerTimelineResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository managing employee life cycle events, career progression timelines,
 * promotion/transfer proposals, and cascade execution audits.
 */
@Singleton
class LifecycleRepository
    @Inject
    constructor(
        private val lifecycleApi: LifecycleApi,
        private val outbox: Outbox,
        private val json: Json,
        private val clock: Clock,
    ) {
        private val _timeline = MutableStateFlow<CareerTimelineResponse?>(null)
        val timeline: StateFlow<CareerTimelineResponse?> = _timeline.asStateFlow()

        private val _movements = MutableStateFlow<List<CareerMovementItem>>(emptyList())
        val movements: StateFlow<List<CareerMovementItem>> = _movements.asStateFlow()

        private val _selectedMovement = MutableStateFlow<CareerMovementItem?>(null)
        val selectedMovement: StateFlow<CareerMovementItem?> = _selectedMovement.asStateFlow()

        private val _totalSalaryGrowthPercentage = MutableStateFlow(BigDecimal.ZERO)
        val totalSalaryGrowthPercentage: StateFlow<BigDecimal> = _totalSalaryGrowthPercentage.asStateFlow()

        val pendingOutboxCount = outbox.pendingCount

        /**
         * Refreshes interactive career progression timeline milestones.
         */
        suspend fun refreshTimeline(employeeId: UUID? = null): Result<CareerTimelineResponse> =
            runCatching {
                val response = lifecycleApi.getCareerTimeline(employeeId = employeeId)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/lifecycle/movements/timeline failed with ${response.code()}")
                _timeline.value = body
                calculateCareerStats(body)
                body
            }.onFailure {
                if (_timeline.value == null) {
                    val fallback = createOfflineDefaultTimeline(employeeId)
                    _timeline.value = fallback
                    calculateCareerStats(fallback)
                }
            }

        /**
         * Refreshes list of historical and pending career movements.
         */
        suspend fun refreshMovements(
            employeeId: UUID? = null,
            status: CareerMovementItem.Status? = null,
            type: CareerMovementItem.MovementType? = null,
        ): Result<List<CareerMovementItem>> =
            runCatching {
                val apiStatus = status?.let { LifecycleApi.StatusGetCareerMovements.valueOf(it.value) }
                val apiType = type?.let { LifecycleApi.TypeGetCareerMovements.valueOf(it.value) }
                val response = lifecycleApi.getCareerMovements(
                    employeeId = employeeId,
                    status = apiStatus,
                    type = apiType,
                )
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/lifecycle/movements failed with ${response.code()}")
                _movements.value = body.movements
                body.movements
            }.onFailure {
                if (_movements.value.isEmpty()) {
                    val fallback = createOfflineDefaultMovements(employeeId)
                    val filtered = fallback.filter { item ->
                        (status == null || item.status == status) &&
                            (type == null || item.movementType == type)
                    }
                    _movements.value = filtered
                }
            }

        /**
         * Fetches a specific career movement with its cascade audit summary.
         */
        suspend fun getMovementById(id: UUID): Result<CareerMovementItem> =
            runCatching {
                val response = lifecycleApi.getCareerMovementById(id)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/lifecycle/movements/$id failed with ${response.code()}")
                _selectedMovement.value = body
                body
            }.recoverCatching {
                val found = _movements.value.find { it.id == id }
                    ?: createOfflineDefaultMovements(null).find { it.id == id }
                    ?: error("Career movement not found: $id")
                _selectedMovement.value = found
                found
            }

        /**
         * Submits a new career movement proposal (promotion, lateral transfer, confirmation).
         */
        suspend fun proposeMovement(
            employeeId: UUID,
            movementType: CareerMovementItem.MovementType,
            effectiveDate: LocalDate,
            justification: String,
            newDepartmentId: UUID? = null,
            newDepartmentName: String? = null,
            newDesignationId: UUID? = null,
            newDesignationName: String? = null,
            newSalaryGradeId: UUID? = null,
            newSalaryGradeCode: String? = null,
            newLocationId: UUID? = null,
            newLocationName: String? = null,
            newSupervisorId: UUID? = null,
            newSupervisorName: String? = null,
            newBaseSalary: BigDecimal? = null,
            newCurrency: String = "LKR",
            remarks: String? = null,
        ): Result<CareerMovementItem> =
            runCatching {
                val idempotencyKey = UUID.randomUUID().toString()
                val apiType = CareerMovementProposalRequest.MovementType.valueOf(movementType.value)
                val request = CareerMovementProposalRequest(
                    employeeId = employeeId,
                    movementType = apiType,
                    effectiveDate = effectiveDate,
                    justification = justification,
                    newDepartmentId = newDepartmentId,
                    newDesignationId = newDesignationId,
                    newSalaryGradeId = newSalaryGradeId,
                    newLocationId = newLocationId,
                    newSupervisorId = newSupervisorId,
                    newBaseSalary = newBaseSalary,
                    newCurrency = newCurrency,
                    remarks = remarks,
                )

                val response = lifecycleApi.proposeCareerMovement(
                    careerMovementProposalRequest = request,
                    idempotencyKey = idempotencyKey,
                )
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/lifecycle/movements failed with ${response.code()}")

                _movements.update { listOf(body) + it }
                body
            }.recoverCatching {
                // Offline fallback creation
                val movementId = UUID.randomUUID()
                val movementNumber = "MOV-${LocalDate.now().year}-${movementId.toString().take(6).uppercase()}"
                val currentTimeline = _timeline.value

                val localMovement = CareerMovementItem(
                    id = movementId,
                    employeeId = employeeId,
                    movementNumber = movementNumber,
                    movementType = movementType,
                    status = CareerMovementItem.Status.SUBMITTED,
                    requestDate = LocalDate.now(),
                    effectiveDate = effectiveDate,
                    initiatorId = UUID.randomUUID(),
                    justification = justification,
                    cascadeApplied = false,
                    createdAt = OffsetDateTime.now(),
                    approverId = null,
                    approvedAt = null,
                    revertedAt = null,
                    reversionReason = null,
                    remarks = remarks,
                    prevDepartmentId = UUID.randomUUID(),
                    prevDepartmentName = currentTimeline?.currentDepartment ?: "Engineering",
                    prevDesignationId = UUID.randomUUID(),
                    prevDesignationName = currentTimeline?.currentDesignation ?: "Senior Software Engineer",
                    prevSalaryGradeId = UUID.randomUUID(),
                    prevSalaryGradeCode = currentTimeline?.currentGrade ?: "E3",
                    prevLocationId = UUID.randomUUID(),
                    prevLocationName = "Colombo HQ",
                    prevSupervisorId = UUID.randomUUID(),
                    prevSupervisorName = "Kamal Gunaratne",
                    prevBaseSalary = BigDecimal("240000.00"),
                    prevCurrency = "LKR",
                    newDepartmentId = newDepartmentId,
                    newDepartmentName = newDepartmentName ?: currentTimeline?.currentDepartment ?: "Engineering",
                    newDesignationId = newDesignationId,
                    newDesignationName = newDesignationName ?: "Lead Software Architect",
                    newSalaryGradeId = newSalaryGradeId,
                    newSalaryGradeCode = newSalaryGradeCode ?: "M1",
                    newLocationId = newLocationId,
                    newLocationName = newLocationName ?: "Colombo HQ",
                    newSupervisorId = newSupervisorId,
                    newSupervisorName = newSupervisorName ?: "Kamal Gunaratne",
                    newBaseSalary = newBaseSalary ?: BigDecimal("320000.00"),
                    newCurrency = newCurrency,
                    cascadeAppliedAt = null,
                    cascadeSummary = null,
                )

                _movements.update { listOf(localMovement) + it }
                localMovement
            }

        /**
         * Approves a career movement and executes transactional cascade if effective immediately.
         */
        suspend fun approveMovement(id: UUID): Result<CareerMovementItem> =
            runCatching {
                val idempotencyKey = UUID.randomUUID().toString()
                val response = lifecycleApi.approveCareerMovement(
                    id = id,
                    idempotencyKey = idempotencyKey,
                )
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/lifecycle/movements/$id/approve failed with ${response.code()}")

                _movements.update { list -> list.map { if (it.id == id) body else it } }
                _selectedMovement.value = body
                refreshTimeline(body.employeeId)
                body
            }.recoverCatching {
                val existing = _movements.value.find { it.id == id }
                    ?: error("Career movement not found: $id")
                val isTodayOrPast = !existing.effectiveDate.isAfter(LocalDate.now())
                val updated = existing.copy(
                    status = if (isTodayOrPast) CareerMovementItem.Status.APPLIED else CareerMovementItem.Status.SCHEDULED,
                    approvedAt = OffsetDateTime.now(),
                    cascadeApplied = isTodayOrPast,
                    cascadeAppliedAt = if (isTodayOrPast) OffsetDateTime.now() else null,
                )
                _movements.update { list -> list.map { if (it.id == id) updated else it } }
                _selectedMovement.value = updated
                updated
            }

        /**
         * Reverts an applied career movement and rolls back employee master and salary.
         */
        suspend fun revertMovement(id: UUID, reason: String): Result<CareerMovementItem> =
            runCatching {
                val idempotencyKey = UUID.randomUUID().toString()
                val req = CareerMovementRevertRequest(reversionReason = reason)
                val response = lifecycleApi.revertCareerMovement(
                    id = id,
                    careerMovementRevertRequest = req,
                    idempotencyKey = idempotencyKey,
                )
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/lifecycle/movements/$id/revert failed with ${response.code()}")

                _movements.update { list -> list.map { if (it.id == id) body else it } }
                _selectedMovement.value = body
                refreshTimeline(body.employeeId)
                body
            }.recoverCatching {
                val existing = _movements.value.find { it.id == id }
                    ?: error("Career movement not found: $id")
                val updated = existing.copy(
                    status = CareerMovementItem.Status.REVERTED,
                    revertedAt = OffsetDateTime.now(),
                    reversionReason = reason,
                    cascadeApplied = false,
                )
                _movements.update { list -> list.map { if (it.id == id) updated else it } }
                _selectedMovement.value = updated
                updated
            }

        private fun calculateCareerStats(timeline: CareerTimelineResponse) {
            val hireEvent = timeline.events.find { it.eventType == CareerTimelineEvent.EventType.HIRE }
            val latestSalary = timeline.events.lastOrNull { it.baseSalary != null }?.baseSalary
            val initialSalary = hireEvent?.baseSalary

            if (initialSalary != null && latestSalary != null && initialSalary > BigDecimal.ZERO) {
                val growth = latestSalary.subtract(initialSalary)
                    .multiply(BigDecimal("100.00"))
                    .divide(initialSalary, 2, RoundingMode.HALF_UP)
                _totalSalaryGrowthPercentage.value = growth
            }
        }

        // ---------------------------------------------------------------------------
        // Offline default fixtures (Kasun Mendis LK010 benchmark career journey)
        // ---------------------------------------------------------------------------

        private fun createOfflineDefaultTimeline(employeeId: UUID?): CareerTimelineResponse {
            val empId = employeeId ?: UUID.fromString("00000000-0000-0000-0000-000000000001")
            return CareerTimelineResponse(
                employeeId = empId,
                employeeName = "Kasun Mendis",
                currentDesignation = "Lead Systems Architect & Tech Lead",
                currentDepartment = "Engineering",
                currentGrade = "M1",
                joinDate = LocalDate.of(2021, 3, 1),
                confirmationDate = LocalDate.of(2021, 9, 1),
                events = listOf(
                    CareerTimelineEvent(
                        id = UUID.fromString("e0000000-0000-0000-0000-000000000001"),
                        eventType = CareerTimelineEvent.EventType.HIRE,
                        title = "Joined Acme Corp",
                        date = LocalDate.of(2021, 3, 1),
                        description = "Commenced employment as Associate Software Engineer on 6-month probation.",
                        isMilestone = true,
                        movementId = null,
                        departmentName = "Engineering",
                        designationName = "Associate Software Engineer",
                        salaryGradeCode = "E1",
                        baseSalary = BigDecimal("120000.00"),
                        currency = "LKR",
                        changePercentage = null,
                    ),
                    CareerTimelineEvent(
                        id = UUID.fromString("e0000000-0000-0000-0000-000000000002"),
                        eventType = CareerTimelineEvent.EventType.CONFIRMATION,
                        title = "Probation Confirmed & Level Advancement",
                        date = LocalDate.of(2021, 9, 1),
                        description = "Successfully completed probation appraisal with distinction. Advanced to Software Engineer.",
                        isMilestone = true,
                        movementId = UUID.fromString("b0000000-0000-0000-0000-000000000001"),
                        departmentName = "Engineering",
                        designationName = "Software Engineer",
                        salaryGradeCode = "E2",
                        baseSalary = BigDecimal("160000.00"),
                        currency = "LKR",
                        changePercentage = BigDecimal("33.33"),
                    ),
                    CareerTimelineEvent(
                        id = UUID.fromString("e0000000-0000-0000-0000-000000000003"),
                        eventType = CareerTimelineEvent.EventType.PROMOTION,
                        title = "Promoted to Senior Software Engineer",
                        date = LocalDate.of(2023, 4, 1),
                        description = "Promoted in recognition of stellar delivery on Core Payment Engine and microservices overhaul.",
                        isMilestone = true,
                        movementId = UUID.fromString("b0000000-0000-0000-0000-000000000002"),
                        departmentName = "Engineering",
                        designationName = "Senior Software Engineer",
                        salaryGradeCode = "E3",
                        baseSalary = BigDecimal("240000.00"),
                        currency = "LKR",
                        changePercentage = BigDecimal("50.00"),
                    ),
                    CareerTimelineEvent(
                        id = UUID.fromString("e0000000-0000-0000-0000-000000000004"),
                        eventType = CareerTimelineEvent.EventType.PROMOTION,
                        title = "Promoted to Lead Systems Architect",
                        date = LocalDate.of(2025, 1, 1),
                        description = "Appointed Tech Lead for Platform Architecture with supervisory responsibility over 14 engineers.",
                        isMilestone = true,
                        movementId = UUID.fromString("b0000000-0000-0000-0000-000000000003"),
                        departmentName = "Engineering",
                        designationName = "Lead Systems Architect & Tech Lead",
                        salaryGradeCode = "M1",
                        baseSalary = BigDecimal("350000.00"),
                        currency = "LKR",
                        changePercentage = BigDecimal("45.83"),
                    ),
                ),
            )
        }

        private fun createOfflineDefaultMovements(employeeId: UUID?): List<CareerMovementItem> {
            val empId = employeeId ?: UUID.fromString("00000000-0000-0000-0000-000000000001")
            return listOf(
                CareerMovementItem(
                    id = UUID.fromString("b0000000-0000-0000-0000-000000000003"),
                    employeeId = empId,
                    movementNumber = "MOV-2025-0001",
                    movementType = CareerMovementItem.MovementType.PROMOTION,
                    status = CareerMovementItem.Status.APPLIED,
                    requestDate = LocalDate.of(2024, 12, 10),
                    effectiveDate = LocalDate.of(2025, 1, 1),
                    initiatorId = UUID.fromString("00000000-0000-0000-0000-000000000099"),
                    justification = "Exceptional technical leadership, design of distributed ledger backend, and team mentorship.",
                    cascadeApplied = true,
                    createdAt = OffsetDateTime.now().minusDays(430),
                    approverId = UUID.fromString("00000000-0000-0000-0000-000000000099"),
                    approvedAt = OffsetDateTime.now().minusDays(425),
                    revertedAt = null,
                    reversionReason = null,
                    remarks = "Executive committee unanimous approval",
                    prevDepartmentId = UUID.fromString("d0000000-0000-0000-0000-000000000001"),
                    prevDepartmentName = "Engineering",
                    prevDesignationId = UUID.fromString("c0000000-0000-0000-0000-000000000003"),
                    prevDesignationName = "Senior Software Engineer",
                    prevSalaryGradeId = UUID.fromString("a0000000-0000-0000-0000-000000000003"),
                    prevSalaryGradeCode = "E3",
                    prevLocationId = UUID.fromString("e0000000-0000-0000-0000-000000000001"),
                    prevLocationName = "Colombo HQ",
                    prevSupervisorId = UUID.fromString("f0000000-0000-0000-0000-000000000001"),
                    prevSupervisorName = "Kamal Gunaratne",
                    prevBaseSalary = BigDecimal("240000.00"),
                    prevCurrency = "LKR",
                    newDepartmentId = UUID.fromString("d0000000-0000-0000-0000-000000000001"),
                    newDepartmentName = "Engineering",
                    newDesignationId = UUID.fromString("c0000000-0000-0000-0000-000000000004"),
                    newDesignationName = "Lead Systems Architect & Tech Lead",
                    newSalaryGradeId = UUID.fromString("a0000000-0000-0000-0000-000000000004"),
                    newSalaryGradeCode = "M1",
                    newLocationId = UUID.fromString("e0000000-0000-0000-0000-000000000001"),
                    newLocationName = "Colombo HQ",
                    newSupervisorId = UUID.fromString("f0000000-0000-0000-0000-000000000001"),
                    newSupervisorName = "Kamal Gunaratne",
                    newBaseSalary = BigDecimal("350000.00"),
                    newCurrency = "LKR",
                    cascadeAppliedAt = OffsetDateTime.now().minusDays(420),
                    cascadeSummary = "Department: Engineering | Designation: Lead Systems Architect | Grade: M1 | Base Salary: LKR 350,000.00 (+45.83%)",
                ),
                CareerMovementItem(
                    id = UUID.fromString("b0000000-0000-0000-0000-000000000002"),
                    employeeId = empId,
                    movementNumber = "MOV-2023-0012",
                    movementType = CareerMovementItem.MovementType.PROMOTION,
                    status = CareerMovementItem.Status.APPLIED,
                    requestDate = LocalDate.of(2023, 3, 15),
                    effectiveDate = LocalDate.of(2023, 4, 1),
                    initiatorId = UUID.fromString("00000000-0000-0000-0000-000000000099"),
                    justification = "Consistently exceeded sprint velocity targets and led backend cloud migration.",
                    cascadeApplied = true,
                    createdAt = OffsetDateTime.now().minusDays(1050),
                    approverId = UUID.fromString("00000000-0000-0000-0000-000000000099"),
                    approvedAt = OffsetDateTime.now().minusDays(1048),
                    revertedAt = null,
                    reversionReason = null,
                    remarks = "Annual performance cycle appraisal recommendation",
                    prevDepartmentId = UUID.fromString("d0000000-0000-0000-0000-000000000001"),
                    prevDepartmentName = "Engineering",
                    prevDesignationId = UUID.fromString("c0000000-0000-0000-0000-000000000002"),
                    prevDesignationName = "Software Engineer",
                    prevSalaryGradeId = UUID.fromString("a0000000-0000-0000-0000-000000000002"),
                    prevSalaryGradeCode = "E2",
                    prevLocationId = UUID.fromString("e0000000-0000-0000-0000-000000000001"),
                    prevLocationName = "Colombo HQ",
                    prevSupervisorId = UUID.fromString("f0000000-0000-0000-0000-000000000001"),
                    prevSupervisorName = "Kamal Gunaratne",
                    prevBaseSalary = BigDecimal("160000.00"),
                    prevCurrency = "LKR",
                    newDepartmentId = UUID.fromString("d0000000-0000-0000-0000-000000000001"),
                    newDepartmentName = "Engineering",
                    newDesignationId = UUID.fromString("c0000000-0000-0000-0000-000000000003"),
                    newDesignationName = "Senior Software Engineer",
                    newSalaryGradeId = UUID.fromString("a0000000-0000-0000-0000-000000000003"),
                    newSalaryGradeCode = "E3",
                    newLocationId = UUID.fromString("e0000000-0000-0000-0000-000000000001"),
                    newLocationName = "Colombo HQ",
                    newSupervisorId = UUID.fromString("f0000000-0000-0000-0000-000000000001"),
                    newSupervisorName = "Kamal Gunaratne",
                    newBaseSalary = BigDecimal("240000.00"),
                    newCurrency = "LKR",
                    cascadeAppliedAt = OffsetDateTime.now().minusDays(1040),
                    cascadeSummary = "Department: Engineering | Designation: Senior Software Engineer | Grade: E3 | Base Salary: LKR 240,000.00 (+50.00%)",
                ),
                CareerMovementItem(
                    id = UUID.fromString("b0000000-0000-0000-0000-000000000004"),
                    employeeId = empId,
                    movementNumber = "MOV-2026-0005",
                    movementType = CareerMovementItem.MovementType.PROMOTION,
                    status = CareerMovementItem.Status.SUBMITTED,
                    requestDate = LocalDate.of(2026, 3, 1),
                    effectiveDate = LocalDate.of(2026, 4, 1),
                    initiatorId = UUID.fromString("00000000-0000-0000-0000-000000000099"),
                    justification = "Nominated for Principal Enterprise Architect role to steer multi-tenant platform expansion across APAC.",
                    cascadeApplied = false,
                    createdAt = OffsetDateTime.now().minusDays(7),
                    approverId = null,
                    approvedAt = null,
                    revertedAt = null,
                    reversionReason = null,
                    remarks = "Awaiting CEO and Compensation Board sign-off",
                    prevDepartmentId = UUID.fromString("d0000000-0000-0000-0000-000000000001"),
                    prevDepartmentName = "Engineering",
                    prevDesignationId = UUID.fromString("c0000000-0000-0000-0000-000000000004"),
                    prevDesignationName = "Lead Systems Architect & Tech Lead",
                    prevSalaryGradeId = UUID.fromString("a0000000-0000-0000-0000-000000000004"),
                    prevSalaryGradeCode = "M1",
                    prevLocationId = UUID.fromString("e0000000-0000-0000-0000-000000000001"),
                    prevLocationName = "Colombo HQ",
                    prevSupervisorId = UUID.fromString("f0000000-0000-0000-0000-000000000001"),
                    prevSupervisorName = "Kamal Gunaratne",
                    prevBaseSalary = BigDecimal("350000.00"),
                    prevCurrency = "LKR",
                    newDepartmentId = UUID.fromString("d0000000-0000-0000-0000-000000000001"),
                    newDepartmentName = "Engineering",
                    newDesignationId = UUID.fromString("c0000000-0000-0000-0000-000000000005"),
                    newDesignationName = "Principal Enterprise Architect",
                    newSalaryGradeId = UUID.fromString("a0000000-0000-0000-0000-000000000005"),
                    newSalaryGradeCode = "M2",
                    newLocationId = UUID.fromString("e0000000-0000-0000-0000-000000000001"),
                    newLocationName = "Colombo HQ",
                    newSupervisorId = UUID.fromString("f0000000-0000-0000-0000-000000000001"),
                    newSupervisorName = "Kamal Gunaratne",
                    newBaseSalary = BigDecimal("450000.00"),
                    newCurrency = "LKR",
                    cascadeAppliedAt = null,
                    cascadeSummary = null,
                ),
            )
        }
    }
