package com.hr.performance.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GoalCycleRepository : JpaRepository<GoalCycle, UUID> {
    fun findAllByTenantId(tenantId: UUID): List<GoalCycle>
    fun findByIdAndTenantId(id: UUID, tenantId: UUID): GoalCycle?
}

@Repository
interface GoalRepository : JpaRepository<GoalEntity, UUID> {
    fun findAllByTenantId(tenantId: UUID): List<GoalEntity>
    fun findAllByTenantIdAndEmployeeId(tenantId: UUID, employeeId: UUID): List<GoalEntity>
    fun findAllByTenantIdAndCycleId(tenantId: UUID, cycleId: UUID): List<GoalEntity>
    fun findAllByTenantIdAndEmployeeIdAndCycleId(tenantId: UUID, employeeId: UUID, cycleId: UUID): List<GoalEntity>
    fun findByIdAndTenantId(id: UUID, tenantId: UUID): GoalEntity?
}

@Repository
interface GoalCheckInRepository : JpaRepository<GoalCheckInEntity, UUID> {
    fun findAllByTenantIdAndGoalIdOrderByCreatedAtDesc(tenantId: UUID, goalId: UUID): List<GoalCheckInEntity>
}

@Repository
interface CompetencyGroupRepository : JpaRepository<CompetencyGroup, UUID> {
    fun findAllByTenantId(tenantId: UUID): List<CompetencyGroup>
}

@Repository
interface CompetencyRepository : JpaRepository<CompetencyEntity, UUID> {
    fun findAllByTenantId(tenantId: UUID): List<CompetencyEntity>
    fun findAllByTenantIdAndGroupId(tenantId: UUID, groupId: UUID): List<CompetencyEntity>
    fun findByIdAndTenantId(id: UUID, tenantId: UUID): CompetencyEntity?
}

@Repository
interface EvaluationCycleRepository : JpaRepository<EvaluationCycleEntity, UUID> {
    fun findAllByTenantId(tenantId: UUID): List<EvaluationCycleEntity>
    fun findByIdAndTenantId(id: UUID, tenantId: UUID): EvaluationCycleEntity?
}

@Repository
interface PerformanceAppraisalRepository : JpaRepository<PerformanceAppraisalEntity, UUID> {
    fun findAllByTenantIdAndEmployeeId(tenantId: UUID, employeeId: UUID): List<PerformanceAppraisalEntity>
    fun findAllByTenantIdAndManagerId(tenantId: UUID, managerId: UUID): List<PerformanceAppraisalEntity>
    fun findByIdAndTenantId(id: UUID, tenantId: UUID): PerformanceAppraisalEntity?
    fun findByTenantIdAndCycleIdAndEmployeeId(tenantId: UUID, cycleId: UUID, employeeId: UUID): PerformanceAppraisalEntity?
}

@Repository
interface AppraisalGoalRatingRepository : JpaRepository<AppraisalGoalRatingEntity, UUID> {
    fun findAllByTenantIdAndAppraisalId(tenantId: UUID, appraisalId: UUID): List<AppraisalGoalRatingEntity>
    fun findByTenantIdAndAppraisalIdAndGoalId(tenantId: UUID, appraisalId: UUID, goalId: UUID): AppraisalGoalRatingEntity?
}

@Repository
interface AppraisalCompetencyRatingRepository : JpaRepository<AppraisalCompetencyRatingEntity, UUID> {
    fun findAllByTenantIdAndAppraisalId(tenantId: UUID, appraisalId: UUID): List<AppraisalCompetencyRatingEntity>
    fun findByTenantIdAndAppraisalIdAndCompetencyId(tenantId: UUID, appraisalId: UUID, competencyId: UUID): AppraisalCompetencyRatingEntity?
}

@Repository
interface ContinuousFeedbackRepository : JpaRepository<ContinuousFeedbackEntity, UUID> {
    fun findAllByTenantIdAndRecipientEmployeeIdOrderByCreatedAtDesc(tenantId: UUID, recipientEmployeeId: UUID): List<ContinuousFeedbackEntity>
    fun findAllByTenantIdAndSenderEmployeeIdOrderByCreatedAtDesc(tenantId: UUID, senderEmployeeId: UUID): List<ContinuousFeedbackEntity>
    fun findAllByTenantIdOrderByCreatedAtDesc(tenantId: UUID): List<ContinuousFeedbackEntity>
}

@Repository
interface MraRequestRepository : JpaRepository<MraRequestEntity, UUID> {
    fun findAllByTenantIdAndAppraisalId(tenantId: UUID, appraisalId: UUID): List<MraRequestEntity>
    fun findAllByTenantIdAndSubjectEmployeeId(tenantId: UUID, subjectEmployeeId: UUID): List<MraRequestEntity>
    fun findAllByTenantIdAndRaterEmployeeId(tenantId: UUID, raterEmployeeId: UUID): List<MraRequestEntity>
}

@Repository
interface MraRatingRepository : JpaRepository<MraRatingEntity, UUID> {
    fun findAllByTenantIdAndMraRequestId(tenantId: UUID, mraRequestId: UUID): List<MraRatingEntity>
    fun findAllByTenantIdAndMraRequestIdIn(tenantId: UUID, mraRequestIds: List<UUID>): List<MraRatingEntity>
}
