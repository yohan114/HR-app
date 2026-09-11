package com.hr.recruitment.internal

import com.hr.recruitment.ApplicationStage
import com.hr.recruitment.VacancyStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RecruitmentRequisitionRepository : JpaRepository<RecruitmentRequisition, UUID> {
    fun findAllByTenantId(tenantId: UUID): List<RecruitmentRequisition>
    fun findByTenantIdAndRequisitionNumber(tenantId: UUID, requisitionNumber: String): RecruitmentRequisition?
}

@Repository
interface RecruitmentVacancyRepository : JpaRepository<RecruitmentVacancy, UUID> {
    fun findAllByTenantId(tenantId: UUID): List<RecruitmentVacancy>
    fun findByTenantIdAndStatus(tenantId: UUID, status: VacancyStatus): List<RecruitmentVacancy>
    fun findByTenantIdAndDepartmentId(tenantId: UUID, departmentId: UUID): List<RecruitmentVacancy>
    fun findByTenantIdAndJobCode(tenantId: UUID, jobCode: String): RecruitmentVacancy?
}

@Repository
interface RecruitmentCandidateRepository : JpaRepository<RecruitmentCandidate, UUID> {
    fun findAllByTenantId(tenantId: UUID): List<RecruitmentCandidate>
    fun findByTenantIdAndEmail(tenantId: UUID, email: String): RecruitmentCandidate?
}

@Repository
interface RecruitmentApplicationRepository : JpaRepository<RecruitmentApplication, UUID> {
    fun findAllByTenantId(tenantId: UUID): List<RecruitmentApplication>
    fun findByTenantIdAndVacancyId(tenantId: UUID, vacancyId: UUID): List<RecruitmentApplication>
    fun findByTenantIdAndCandidateId(tenantId: UUID, candidateId: UUID): List<RecruitmentApplication>
    fun findByTenantIdAndStage(tenantId: UUID, stage: ApplicationStage): List<RecruitmentApplication>
    fun findByTenantIdAndApplicationNumber(tenantId: UUID, applicationNumber: String): RecruitmentApplication?
}

@Repository
interface RecruitmentInterviewRepository : JpaRepository<RecruitmentInterview, UUID> {
    fun findAllByTenantId(tenantId: UUID): List<RecruitmentInterview>
    fun findByTenantIdAndApplicationId(tenantId: UUID, applicationId: UUID): List<RecruitmentInterview>
}

@Repository
interface RecruitmentInterviewPanelRepository : JpaRepository<RecruitmentInterviewPanel, UUID> {
    fun findByTenantIdAndInterviewId(tenantId: UUID, interviewId: UUID): List<RecruitmentInterviewPanel>
    fun findByTenantIdAndInterviewerEmployeeId(tenantId: UUID, interviewerEmployeeId: UUID): List<RecruitmentInterviewPanel>
}

@Repository
interface RecruitmentInterviewScorecardRepository : JpaRepository<RecruitmentInterviewScorecard, UUID> {
    fun findByTenantIdAndInterviewId(tenantId: UUID, interviewId: UUID): List<RecruitmentInterviewScorecard>
    fun findByTenantIdAndInterviewIdAndInterviewerEmployeeId(
        tenantId: UUID,
        interviewId: UUID,
        interviewerEmployeeId: UUID,
    ): RecruitmentInterviewScorecard?
}

@Repository
interface RecruitmentOfferRepository : JpaRepository<RecruitmentOffer, UUID> {
    fun findByTenantIdAndApplicationId(tenantId: UUID, applicationId: UUID): RecruitmentOffer?
    fun findByTenantIdAndOfferNumber(tenantId: UUID, offerNumber: String): RecruitmentOffer?
}
