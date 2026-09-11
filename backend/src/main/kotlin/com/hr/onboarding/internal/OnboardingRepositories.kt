package com.hr.onboarding.internal

import com.hr.onboarding.ClearanceDepartment
import com.hr.onboarding.ClearanceTaskStatus
import com.hr.onboarding.ExitNoticeStatus
import com.hr.onboarding.OnboardingInstanceStatus
import com.hr.onboarding.OnboardingTaskStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OnboardingStageRepository : JpaRepository<OnboardingStageEntity, UUID> {
    fun findAllByOrderBySequenceAsc(): List<OnboardingStageEntity>
    fun findByCode(code: String): OnboardingStageEntity?
}

@Repository
interface OnboardingProfileRepository : JpaRepository<OnboardingProfileEntity, UUID> {
    fun findAllByActiveTrue(): List<OnboardingProfileEntity>
    fun findByCode(code: String): OnboardingProfileEntity?
}

@Repository
interface OnboardingActionRepository : JpaRepository<OnboardingActionEntity, UUID> {
    fun findByProfileId(profileId: UUID): List<OnboardingActionEntity>
    fun findByProfileIdAndStageId(profileId: UUID, stageId: UUID): List<OnboardingActionEntity>
}

@Repository
interface OnboardingInstanceRepository : JpaRepository<OnboardingInstanceEntity, UUID> {
    fun findByEmployeeId(employeeId: UUID): OnboardingInstanceEntity?
    fun findByStatus(status: OnboardingInstanceStatus): List<OnboardingInstanceEntity>
    fun findByBuddyEmployeeId(buddyEmployeeId: UUID): List<OnboardingInstanceEntity>
}

@Repository
interface OnboardingTaskRepository : JpaRepository<OnboardingTaskEntity, UUID> {
    fun findByInstanceId(instanceId: UUID): List<OnboardingTaskEntity>
    fun findByInstanceIdAndStatus(instanceId: UUID, status: OnboardingTaskStatus): List<OnboardingTaskEntity>
    fun findByAssigneeEmployeeId(assigneeEmployeeId: UUID): List<OnboardingTaskEntity>
}

@Repository
interface ExitTypeRepository : JpaRepository<ExitTypeEntity, UUID> {
    fun findByCode(code: String): ExitTypeEntity?
}

@Repository
interface ExitReasonRepository : JpaRepository<ExitReasonEntity, UUID> {
    fun findByExitTypeId(exitTypeId: UUID): List<ExitReasonEntity>
}

@Repository
interface ExitNoticeRepository : JpaRepository<ExitNoticeEntity, UUID> {
    fun findByEmployeeId(employeeId: UUID): List<ExitNoticeEntity>
    fun findByStatus(status: ExitNoticeStatus): List<ExitNoticeEntity>
    fun findByEmployeeIdAndStatus(employeeId: UUID, status: ExitNoticeStatus): List<ExitNoticeEntity>
    fun findByNoticeNumber(noticeNumber: String): ExitNoticeEntity?
}

@Repository
interface ExitInterviewRepository : JpaRepository<ExitInterviewEntity, UUID> {
    fun findByExitNoticeId(exitNoticeId: UUID): ExitInterviewEntity?
    fun findByEmployeeId(employeeId: UUID): List<ExitInterviewEntity>
}

@Repository
interface ClearanceItemRepository : JpaRepository<ClearanceItemEntity, UUID> {
    fun findByDepartment(department: ClearanceDepartment): List<ClearanceItemEntity>
}

@Repository
interface ClearanceTaskRepository : JpaRepository<ClearanceTaskEntity, UUID> {
    fun findByExitNoticeId(exitNoticeId: UUID): List<ClearanceTaskEntity>
    fun findByExitNoticeIdAndDepartment(exitNoticeId: UUID, department: ClearanceDepartment): List<ClearanceTaskEntity>
    fun findByAssigneeEmployeeId(assigneeEmployeeId: UUID): List<ClearanceTaskEntity>
}
