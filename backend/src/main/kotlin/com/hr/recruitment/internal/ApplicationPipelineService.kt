package com.hr.recruitment.internal

import com.hr.recruitment.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class ApplicationPipelineService(
    private val candidateRepository: RecruitmentCandidateRepository,
    private val applicationRepository: RecruitmentApplicationRepository,
    private val vacancyRepository: RecruitmentVacancyRepository,
    private val offerRepository: RecruitmentOfferRepository,
) {
    private val defaultTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    // -------------------------------------------------------------------------
    // Candidates
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun getCandidates(query: String?, tenantId: UUID = defaultTenantId): CandidateListResponseDto {
        val all = candidateRepository.findAllByTenantId(tenantId)
        val filtered = if (query.isNullOrBlank()) {
            all
        } else {
            val q = query.lowercase().trim()
            all.filter { c ->
                c.firstName.lowercase().contains(q) ||
                c.lastName.lowercase().contains(q) ||
                c.email.lowercase().contains(q) ||
                (c.currentCompany?.lowercase()?.contains(q) == true) ||
                (c.currentTitle?.lowercase()?.contains(q) == true) ||
                (c.skills?.any { it.lowercase().contains(q) } == true)
            }
        }
        val items = filtered.map { mapCandidateToDto(it) }
        return CandidateListResponseDto(items = items, totalCount = items.size)
    }

    @Transactional
    fun createCandidate(req: CandidateCreateRequestDto, tenantId: UUID = defaultTenantId): CandidateItemDto {
        val candidate = RecruitmentCandidate(
            firstName = req.firstName,
            lastName = req.lastName,
            email = req.email,
            phone = req.phone,
            currentCompany = req.currentCompany,
            currentTitle = req.currentTitle,
            yearsOfExperience = req.yearsOfExperience,
            resumeUrl = req.resumeUrl,
            portfolioUrl = null,
            linkedinUrl = req.linkedinUrl,
            skills = req.skills?.toTypedArray() ?: emptyArray(),
            notes = req.notes,
        )
        candidate.tenantId = tenantId
        val saved = candidateRepository.save(candidate)
        return mapCandidateToDto(saved)
    }

    // -------------------------------------------------------------------------
    // Applications
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun getApplications(
        vacancyId: UUID?,
        stage: ApplicationStage?,
        status: ApplicationStatus?,
        tenantId: UUID = defaultTenantId,
    ): ApplicationListResponseDto {
        val all = applicationRepository.findAllByTenantId(tenantId)
        val filtered = all.filter { app ->
            (vacancyId == null || app.vacancyId == vacancyId) &&
            (stage == null || app.stage == stage) &&
            (status == null || app.status == status)
        }
        val items = filtered.map { mapApplicationToDto(it) }
        return ApplicationListResponseDto(items = items, totalCount = items.size)
    }

    @Transactional
    fun createApplication(req: ApplicationCreateRequestDto, tenantId: UUID = defaultTenantId): ApplicationItemDto {
        val count = applicationRepository.findAllByTenantId(tenantId).size + 1
        val appNumber = "APP-2026-${count.toString().padStart(3, '0')}"
        val application = RecruitmentApplication(
            applicationNumber = appNumber,
            vacancyId = req.vacancyId,
            candidateId = req.candidateId,
            stage = ApplicationStage.APPLIED,
            status = ApplicationStatus.ACTIVE,
            rating = null,
            source = req.source ?: "CAREERS_PORTAL",
            appliedDate = LocalDate.now(),
            rejectionReason = null,
        )
        application.tenantId = tenantId
        val saved = applicationRepository.save(application)
        return mapApplicationToDto(saved)
    }

    @Transactional
    fun updateApplicationStage(
        applicationId: UUID,
        req: ApplicationStageUpdateRequestDto,
        tenantId: UUID = defaultTenantId,
    ): ApplicationItemDto {
        val application = applicationRepository.findById(applicationId).orElse(null)
            ?: throw NoSuchElementException("Application not found with id $applicationId")

        application.stage = req.stage
        if (req.status != null) {
            application.status = req.status
        }
        if (req.rejectionReason != null) {
            application.rejectionReason = req.rejectionReason
        }
        val saved = applicationRepository.save(application)
        return mapApplicationToDto(saved)
    }

    // -------------------------------------------------------------------------
    // Offers
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun getApplicationOffer(applicationId: UUID, tenantId: UUID = defaultTenantId): OfferDetailDto {
        val offer = offerRepository.findByTenantIdAndApplicationId(tenantId, applicationId)
            ?: throw NoSuchElementException("Offer not found for application id $applicationId")
        return mapOfferToDto(offer)
    }

    @Transactional
    fun createApplicationOffer(
        applicationId: UUID,
        req: OfferCreateRequestDto,
        tenantId: UUID = defaultTenantId,
    ): OfferDetailDto {
        val application = applicationRepository.findById(applicationId).orElse(null)
            ?: throw NoSuchElementException("Application not found with id $applicationId")

        val existing = offerRepository.findByTenantIdAndApplicationId(tenantId, applicationId)
        val offer = existing ?: RecruitmentOffer(
            applicationId = applicationId,
            offerNumber = "OFF-2026-${(offerRepository.findAll().size + 1).toString().padStart(3, '0')}",
            baseSalary = req.baseSalary,
            variableBonus = req.variableBonus,
            currency = req.currency,
            startDate = req.startDate,
            expiryDate = req.expiryDate,
            offerLetterUrl = req.offerLetterUrl,
            status = OfferStatus.EXTENDED,
            decisionNotes = req.decisionNotes,
        ).apply { this.tenantId = tenantId }

        if (existing != null) {
            existing.baseSalary = req.baseSalary
            existing.variableBonus = req.variableBonus
            existing.currency = req.currency
            existing.startDate = req.startDate
            existing.expiryDate = req.expiryDate
            existing.offerLetterUrl = req.offerLetterUrl
            existing.status = OfferStatus.EXTENDED
            existing.decisionNotes = req.decisionNotes
        }

        // Auto transition application stage to OFFER_EXTENDED
        application.stage = ApplicationStage.OFFER_EXTENDED
        applicationRepository.save(application)

        val saved = offerRepository.save(offer)
        return mapOfferToDto(saved)
    }

    // -------------------------------------------------------------------------
    // DTO Mappings
    // -------------------------------------------------------------------------

    private fun mapCandidateToDto(c: RecruitmentCandidate): CandidateItemDto {
        return CandidateItemDto(
            id = c.id,
            firstName = c.firstName,
            lastName = c.lastName,
            email = c.email,
            phone = c.phone,
            currentCompany = c.currentCompany,
            currentTitle = c.currentTitle,
            yearsOfExperience = c.yearsOfExperience,
            resumeUrl = c.resumeUrl,
            linkedinUrl = c.linkedinUrl,
            skills = c.skills?.toList() ?: emptyList(),
            notes = c.notes,
            createdAt = c.createdAt ?: Instant.now(),
        )
    }

    private fun mapApplicationToDto(app: RecruitmentApplication): ApplicationItemDto {
        val vacancy = vacancyRepository.findById(app.vacancyId).orElse(null)
        val candidate = candidateRepository.findById(app.candidateId).orElse(null)

        val candidateName = candidate?.let { "${it.firstName} ${it.lastName}" } ?: "Unknown Candidate"
        val candidateEmail = candidate?.email ?: ""
        val vacancyTitle = vacancy?.title ?: "Open Position"

        return ApplicationItemDto(
            id = app.id,
            applicationNumber = app.applicationNumber,
            vacancyId = app.vacancyId,
            vacancyTitle = vacancyTitle,
            candidateId = app.candidateId,
            candidateName = candidateName,
            candidateEmail = candidateEmail,
            stage = app.stage,
            status = app.status,
            rating = app.rating,
            source = app.source,
            appliedDate = app.appliedDate,
            rejectionReason = app.rejectionReason,
            createdAt = app.createdAt ?: Instant.now(),
        )
    }

    private fun mapOfferToDto(o: RecruitmentOffer): OfferDetailDto {
        return OfferDetailDto(
            id = o.id,
            applicationId = o.applicationId,
            offerNumber = o.offerNumber,
            baseSalary = o.baseSalary,
            variableBonus = o.variableBonus,
            currency = o.currency,
            startDate = o.startDate,
            expiryDate = o.expiryDate,
            offerLetterUrl = o.offerLetterUrl,
            status = o.status,
            approvedBy = o.approvedBy,
            approvedAt = o.approvedAt,
            decisionNotes = o.decisionNotes,
            createdAt = o.createdAt ?: Instant.now(),
        )
    }
}
