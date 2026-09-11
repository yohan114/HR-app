package com.hr.performance.internal

import com.hr.employee.EmployeeLookupService
import com.hr.performance.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class AppraisalService(
    private val appraisalRepository: PerformanceAppraisalRepository,
    private val evaluationCycleRepository: EvaluationCycleRepository,
    private val goalRepository: GoalRepository,
    private val goalCycleRepository: GoalCycleRepository,
    private val goalRatingRepository: AppraisalGoalRatingRepository,
    private val competencyGroupRepository: CompetencyGroupRepository,
    private val competencyRepository: CompetencyRepository,
    private val competencyRatingRepository: AppraisalCompetencyRatingRepository,
    private val mraRequestRepository: MraRequestRepository,
    private val mraRatingRepository: MraRatingRepository,
    private val employeeLookupService: EmployeeLookupService,
    private val scoringEngine: AppraisalScoringEngine,
) {
    private val defaultTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Transactional(readOnly = true)
    fun getCompetencies(tenantId: UUID = defaultTenantId): CompetencyFrameworkResponseDto {
        val groups = competencyGroupRepository.findAllByTenantId(tenantId)
        val groupMap = groups.associateBy { it.id }
        val competencies = competencyRepository.findAllByTenantId(tenantId)

        return CompetencyFrameworkResponseDto(
            groups = groups.map { g ->
                CompetencyGroupItemDto(
                    id = g.id ?: UUID.randomUUID(),
                    code = g.code,
                    name = g.name,
                    description = g.description,
                )
            },
            competencies = competencies.map { c ->
                val group = groupMap[c.groupId]
                CompetencyItemDto(
                    id = c.id ?: UUID.randomUUID(),
                    groupId = c.groupId,
                    groupName = group?.name ?: "General Competency",
                    code = c.code,
                    name = c.name,
                    description = c.description,
                    targetLevel = c.targetLevel,
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun getMyAppraisals(
        employeeId: UUID,
        cycleId: UUID? = null,
        tenantId: UUID = defaultTenantId,
    ): AppraisalListResponseDto {
        val appraisals = appraisalRepository.findAllByTenantIdAndEmployeeId(tenantId, employeeId)
        val filtered = if (cycleId != null) appraisals.filter { it.cycleId == cycleId } else appraisals
        return AppraisalListResponseDto(filtered.map { mapToSummary(it, tenantId) })
    }

    @Transactional(readOnly = true)
    fun getTeamAppraisals(
        managerId: UUID,
        cycleId: UUID? = null,
        tenantId: UUID = defaultTenantId,
    ): AppraisalListResponseDto {
        val appraisals = appraisalRepository.findAllByTenantIdAndManagerId(tenantId, managerId)
        val filtered = if (cycleId != null) appraisals.filter { it.cycleId == cycleId } else appraisals
        return AppraisalListResponseDto(filtered.map { mapToSummary(it, tenantId) })
    }

    @Transactional(readOnly = true)
    fun getAppraisalById(id: UUID, tenantId: UUID = defaultTenantId): AppraisalDetailResponseDto {
        val appraisal = appraisalRepository.findByIdAndTenantId(id, tenantId)
            ?: throw NoSuchElementException("Appraisal not found: $id")
        return mapToDetail(appraisal, tenantId)
    }

    @Transactional
    fun submitSelfReview(
        id: UUID,
        request: AppraisalSelfReviewRequestDto,
        employeeId: UUID,
        tenantId: UUID = defaultTenantId,
    ): AppraisalDetailResponseDto {
        val appraisal = appraisalRepository.findByIdAndTenantId(id, tenantId)
            ?: throw NoSuchElementException("Appraisal not found: $id")

        if (appraisal.employeeId != employeeId) {
            throw IllegalArgumentException("Only the assigned employee can submit self-review")
        }

        // Record goal self-ratings
        var totalGoalSelfScore = BigDecimal.ZERO
        var goalCount = 0
        for (gr in request.goalRatings) {
            var ratingEntity = goalRatingRepository.findByTenantIdAndAppraisalIdAndGoalId(tenantId, id, gr.goalId)
            if (ratingEntity == null) {
                ratingEntity = AppraisalGoalRatingEntity(
                    appraisalId = id,
                    goalId = gr.goalId,
                    selfRating = gr.rating,
                    selfComments = gr.comments,
                ).apply { this.tenantId = tenantId }
            } else {
                ratingEntity.selfRating = gr.rating
                ratingEntity.selfComments = gr.comments
            }
            goalRatingRepository.save(ratingEntity)
            totalGoalSelfScore += gr.rating
            goalCount++
        }

        // Record competency self-ratings
        var totalCompSelfScore = BigDecimal.ZERO
        var compCount = 0
        for (cr in request.competencyRatings) {
            var compEntity = competencyRatingRepository.findByTenantIdAndAppraisalIdAndCompetencyId(tenantId, id, cr.competencyId)
            if (compEntity == null) {
                compEntity = AppraisalCompetencyRatingEntity(
                    appraisalId = id,
                    competencyId = cr.competencyId,
                    selfProficiencyLevel = cr.proficiencyLevel,
                    selfComments = cr.comments,
                ).apply { this.tenantId = tenantId }
            } else {
                compEntity.selfProficiencyLevel = cr.proficiencyLevel
                compEntity.selfComments = cr.comments
            }
            competencyRatingRepository.save(compEntity)
            totalCompSelfScore += BigDecimal(cr.proficiencyLevel)
            compCount++
        }

        appraisal.selfOverallComments = request.overallComments
        appraisal.selfGoalsScore = if (goalCount > 0) totalGoalSelfScore.divide(BigDecimal(goalCount), 2, RoundingMode.HALF_UP) else null
        appraisal.selfCompetencyScore = if (compCount > 0) totalCompSelfScore.divide(BigDecimal(compCount), 2, RoundingMode.HALF_UP) else null
        appraisal.status = AppraisalStatus.SELF_REVIEW_SUBMITTED

        val updated = appraisalRepository.save(appraisal)
        return mapToDetail(updated, tenantId)
    }

    @Transactional
    fun submitManagerReview(
        id: UUID,
        request: AppraisalManagerReviewRequestDto,
        managerId: UUID,
        tenantId: UUID = defaultTenantId,
    ): AppraisalDetailResponseDto {
        val appraisal = appraisalRepository.findByIdAndTenantId(id, tenantId)
            ?: throw NoSuchElementException("Appraisal not found: $id")

        val cycle = evaluationCycleRepository.findByIdAndTenantId(appraisal.cycleId, tenantId)

        // Record goal manager-ratings
        var totalGoalMgrScore = BigDecimal.ZERO
        var goalCount = 0
        for (gr in request.goalRatings) {
            var ratingEntity = goalRatingRepository.findByTenantIdAndAppraisalIdAndGoalId(tenantId, id, gr.goalId)
            if (ratingEntity == null) {
                ratingEntity = AppraisalGoalRatingEntity(
                    appraisalId = id,
                    goalId = gr.goalId,
                    managerRating = gr.rating,
                    managerComments = gr.comments,
                    weightedScore = gr.rating,
                ).apply { this.tenantId = tenantId }
            } else {
                ratingEntity.managerRating = gr.rating
                ratingEntity.managerComments = gr.comments
                ratingEntity.weightedScore = gr.rating
            }
            goalRatingRepository.save(ratingEntity)
            totalGoalMgrScore += gr.rating
            goalCount++
        }

        // Record competency manager-ratings
        var totalCompMgrScore = BigDecimal.ZERO
        var compCount = 0
        for (cr in request.competencyRatings) {
            var compEntity = competencyRatingRepository.findByTenantIdAndAppraisalIdAndCompetencyId(tenantId, id, cr.competencyId)
            if (compEntity == null) {
                compEntity = AppraisalCompetencyRatingEntity(
                    appraisalId = id,
                    competencyId = cr.competencyId,
                    managerProficiencyLevel = cr.proficiencyLevel,
                    managerComments = cr.comments,
                ).apply { this.tenantId = tenantId }
            } else {
                compEntity.managerProficiencyLevel = cr.proficiencyLevel
                compEntity.managerComments = cr.comments
            }
            competencyRatingRepository.save(compEntity)
            // Convert 1-5 scale to percentage (e.g. 5 = 100, 4 = 80, 3 = 60, etc.)
            val pct = BigDecimal(cr.proficiencyLevel).multiply(BigDecimal("20.00"))
            totalCompMgrScore += pct
            compCount++
        }

        val managerGoalsScore = if (goalCount > 0) totalGoalMgrScore.divide(BigDecimal(goalCount), 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
        val managerCompScore = if (compCount > 0) totalCompMgrScore.divide(BigDecimal(compCount), 2, RoundingMode.HALF_UP) else BigDecimal.ZERO

        // Calculate MRA score
        val mraRequests = mraRequestRepository.findAllByTenantIdAndAppraisalId(tenantId, id)
        val completedMra = mraRequests.filter { it.status == MraStatus.COMPLETED }
        val mraRatings = if (completedMra.isNotEmpty()) {
            mraRatingRepository.findAllByTenantIdAndMraRequestIdIn(tenantId, completedMra.mapNotNull { it.id })
        } else {
            emptyList()
        }

        val mraScore = if (mraRatings.isNotEmpty()) {
            val sum = mraRatings.fold(BigDecimal.ZERO) { acc, r -> acc + r.score.multiply(BigDecimal("20.00")) }
            sum.divide(BigDecimal(mraRatings.size), 2, RoundingMode.HALF_UP)
        } else null

        val goalWeight = cycle?.goalWeight ?: BigDecimal("60.00")
        val compWeight = cycle?.competencyWeight ?: BigDecimal("30.00")
        val mraWeight = cycle?.mraWeight ?: BigDecimal("10.00")

        val calculation = scoringEngine.calculateScore(
            goalsScore = managerGoalsScore,
            goalWeight = goalWeight,
            competencyScore = managerCompScore,
            competencyWeight = compWeight,
            mraScore = mraScore,
            mraWeight = mraWeight,
        )

        appraisal.managerGoalsScore = managerGoalsScore
        appraisal.managerCompetencyScore = managerCompScore
        appraisal.mraScore = mraScore
        appraisal.finalScore = calculation.finalScore
        appraisal.finalRating = calculation.finalRating
        appraisal.managerOverallComments = request.overallComments
        appraisal.status = AppraisalStatus.MANAGER_REVIEW_SUBMITTED

        val updated = appraisalRepository.save(appraisal)
        return mapToDetail(updated, tenantId)
    }

    @Transactional
    fun acknowledgeAppraisal(
        id: UUID,
        employeeId: UUID,
        tenantId: UUID = defaultTenantId,
    ): AppraisalDetailResponseDto {
        val appraisal = appraisalRepository.findByIdAndTenantId(id, tenantId)
            ?: throw NoSuchElementException("Appraisal not found: $id")

        if (appraisal.employeeId != employeeId) {
            throw IllegalArgumentException("Only the appraised employee can acknowledge")
        }

        appraisal.employeeAcknowledgedAt = Instant.now()
        appraisal.status = AppraisalStatus.ACKNOWLEDGED

        val updated = appraisalRepository.save(appraisal)
        return mapToDetail(updated, tenantId)
    }

    private fun mapToSummary(appraisal: PerformanceAppraisalEntity, tenantId: UUID): AppraisalSummaryItemDto {
        val employeeName = employeeLookupService.findDisplayName(appraisal.employeeId) ?: "Employee"
        val managerName = employeeLookupService.findDisplayName(appraisal.managerId) ?: "Manager"
        val cycle = evaluationCycleRepository.findByIdAndTenantId(appraisal.cycleId, tenantId)

        return AppraisalSummaryItemDto(
            id = appraisal.id ?: UUID.randomUUID(),
            cycleId = appraisal.cycleId,
            cycleName = cycle?.name ?: "Evaluation Cycle",
            employeeId = appraisal.employeeId,
            employeeName = employeeName,
            managerId = appraisal.managerId,
            managerName = managerName,
            status = appraisal.status,
            finalScore = appraisal.finalScore,
            finalRating = appraisal.finalRating,
            selfReviewDeadline = cycle?.selfReviewDeadline ?: LocalDate.now().plusDays(14),
            managerReviewDeadline = cycle?.managerReviewDeadline ?: LocalDate.now().plusDays(21),
            employeeAcknowledgedAt = appraisal.employeeAcknowledgedAt,
        )
    }

    private fun mapToDetail(appraisal: PerformanceAppraisalEntity, tenantId: UUID): AppraisalDetailResponseDto {
        val appraisalId = appraisal.id ?: UUID.randomUUID()
        val summary = mapToSummary(appraisal, tenantId)

        // Goals & ratings
        val goals = goalRepository.findAllByTenantIdAndEmployeeId(tenantId, appraisal.employeeId)
        val goalRatings = goalRatingRepository.findAllByTenantIdAndAppraisalId(tenantId, appraisalId).associateBy { it.goalId }

        val goalItems = goals.map { g ->
            val rating = goalRatings[g.id]
            AppraisalGoalItemDto(
                goalId = g.id ?: UUID.randomUUID(),
                title = g.title,
                category = g.category.name,
                weight = g.weight,
                progressPercentage = g.progressPercentage,
                selfRating = rating?.selfRating,
                selfComments = rating?.selfComments,
                managerRating = rating?.managerRating,
                managerComments = rating?.managerComments,
                weightedScore = rating?.weightedScore,
            )
        }

        // Competencies & ratings
        val competencies = competencyRepository.findAllByTenantId(tenantId)
        val groups = competencyGroupRepository.findAllByTenantId(tenantId).associateBy { it.id }
        val compRatings = competencyRatingRepository.findAllByTenantIdAndAppraisalId(tenantId, appraisalId).associateBy { it.competencyId }

        val compItems = competencies.map { c ->
            val rating = compRatings[c.id]
            val group = groups[c.groupId]
            AppraisalCompetencyItemDto(
                competencyId = c.id ?: UUID.randomUUID(),
                code = c.code,
                name = c.name,
                groupName = group?.name ?: "General",
                targetLevel = c.targetLevel,
                selfProficiencyLevel = rating?.selfProficiencyLevel,
                managerProficiencyLevel = rating?.managerProficiencyLevel,
                selfComments = rating?.selfComments,
                managerComments = rating?.managerComments,
            )
        }

        // 360 MRA with anonymity protection (minimum N >= 3 responses before disclosing breakdown)
        val mraRequests = mraRequestRepository.findAllByTenantIdAndAppraisalId(tenantId, appraisalId)
        val completed = mraRequests.filter { it.status == MraStatus.COMPLETED }
        val mraRatings = if (completed.isNotEmpty()) {
            mraRatingRepository.findAllByTenantIdAndMraRequestIdIn(tenantId, completed.mapNotNull { it.id })
        } else {
            emptyList()
        }

        val avgScore = if (mraRatings.isNotEmpty()) {
            val sum = mraRatings.fold(BigDecimal.ZERO) { acc, r -> acc + r.score }
            sum.divide(BigDecimal(mraRatings.size), 2, RoundingMode.HALF_UP)
        } else null

        // Anonymity gate: reveal relationship breakdown only when N >= 3
        val relationshipBreakdown = if (completed.size >= 3) {
            completed.groupBy { it.relationship }.map { (rel, reqs) ->
                val relRatings = mraRatingRepository.findAllByTenantIdAndMraRequestIdIn(tenantId, reqs.mapNotNull { it.id })
                val relAvg = if (relRatings.isNotEmpty()) {
                    val s = relRatings.fold(BigDecimal.ZERO) { acc, r -> acc + r.score }
                    s.divide(BigDecimal(relRatings.size), 2, RoundingMode.HALF_UP)
                } else BigDecimal.ZERO
                MraRelationshipScoreItemDto(
                    relationship = rel.name,
                    respondentCount = reqs.size,
                    averageScore = relAvg,
                )
            }
        } else {
            emptyList()
        }

        val mraSummary = MraSummaryItemDto(
            totalRequests = mraRequests.size,
            completedRequests = completed.size,
            averageScore = avgScore,
            relationshipBreakdown = relationshipBreakdown,
        )

        return AppraisalDetailResponseDto(
            appraisal = summary,
            goals = goalItems,
            competencies = compItems,
            selfOverallComments = appraisal.selfOverallComments,
            managerOverallComments = appraisal.managerOverallComments,
            calibrationNotes = appraisal.calibrationNotes,
            mraSummary = mraSummary,
        )
    }
}
