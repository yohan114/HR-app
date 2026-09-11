package com.hr.recruitment

import com.hr.recruitment.internal.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@DisplayName("Application Pipeline Service Unit Tests")
class ApplicationPipelineServiceTest {

    private val candidateRepository = mockk<RecruitmentCandidateRepository>()
    private val applicationRepository = mockk<RecruitmentApplicationRepository>()
    private val vacancyRepository = mockk<RecruitmentVacancyRepository>()
    private val offerRepository = mockk<RecruitmentOfferRepository>()

    private lateinit var pipelineService: ApplicationPipelineService

    private val tenantId = UUID.randomUUID()
    private val vacancyId = UUID.randomUUID()
    private val candidateId = UUID.randomUUID()
    private val applicationId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        pipelineService = ApplicationPipelineService(
            candidateRepository = candidateRepository,
            applicationRepository = applicationRepository,
            vacancyRepository = vacancyRepository,
            offerRepository = offerRepository,
        )
    }

    @Test
    fun `getCandidates filters by search query`() {
        val cand1 = RecruitmentCandidate(
            firstName = "Nuwan",
            lastName = "Senaratne",
            email = "nuwan@example.com",
            currentCompany = "Virtusa",
            skills = arrayOf("Kotlin", "Postgres"),
        ).apply { this.tenantId = tenantId }

        val cand2 = RecruitmentCandidate(
            firstName = "Dulani",
            lastName = "Jayawardena",
            email = "dulani@example.com",
            currentCompany = "WSO2",
            skills = arrayOf("Android", "Compose"),
        ).apply { this.tenantId = tenantId }

        every { candidateRepository.findAllByTenantId(tenantId) } returns listOf(cand1, cand2)

        val result = pipelineService.getCandidates("kotlin", tenantId)
        assertEquals(1, result.totalCount)
        assertEquals("Nuwan", result.items[0].firstName)
    }

    @Test
    fun `createApplication generates application number and defaults to APPLIED stage`() {
        val req = ApplicationCreateRequestDto(
            vacancyId = vacancyId,
            candidateId = candidateId,
            source = "LINKEDIN",
        )

        val vacancy = RecruitmentVacancy(
            jobCode = "VAC-001",
            title = "Senior Backend Engineer",
            description = "Kotlin Dev",
        ).apply { this.tenantId = tenantId }

        val candidate = RecruitmentCandidate(
            firstName = "Kasun",
            lastName = "Silva",
            email = "kasun.silva@example.com",
        ).apply { this.tenantId = tenantId }

        val savedApp = RecruitmentApplication(
            applicationNumber = "APP-2026-001",
            vacancyId = vacancyId,
            candidateId = candidateId,
            stage = ApplicationStage.APPLIED,
            status = ApplicationStatus.ACTIVE,
            source = "LINKEDIN",
        ).apply { this.tenantId = tenantId }

        every { applicationRepository.findAllByTenantId(tenantId) } returns emptyList()
        every { applicationRepository.save(any()) } returns savedApp
        every { vacancyRepository.findById(vacancyId) } returns Optional.of(vacancy)
        every { candidateRepository.findById(candidateId) } returns Optional.of(candidate)

        val result = pipelineService.createApplication(req, tenantId)

        assertEquals("APP-2026-001", result.applicationNumber)
        assertEquals(ApplicationStage.APPLIED, result.stage)
        assertEquals("Kasun Silva", result.candidateName)
        verify(exactly = 1) { applicationRepository.save(any()) }
    }

    @Test
    fun `updateApplicationStage changes stage and status`() {
        val app = RecruitmentApplication(
            applicationNumber = "APP-2026-001",
            vacancyId = vacancyId,
            candidateId = candidateId,
            stage = ApplicationStage.SCREENING,
            status = ApplicationStatus.ACTIVE,
        ).apply { this.tenantId = tenantId }

        every { applicationRepository.findById(applicationId) } returns Optional.of(app)
        every { applicationRepository.save(any()) } answers { firstArg() }
        every { vacancyRepository.findById(vacancyId) } returns Optional.empty()
        every { candidateRepository.findById(candidateId) } returns Optional.empty()

        val req = ApplicationStageUpdateRequestDto(
            stage = ApplicationStage.TECHNICAL_ASSESSMENT,
            status = ApplicationStatus.ACTIVE,
        )

        val updated = pipelineService.updateApplicationStage(applicationId, req, tenantId)

        assertEquals(ApplicationStage.TECHNICAL_ASSESSMENT, updated.stage)
        verify(exactly = 1) { applicationRepository.save(app) }
    }

    @Test
    fun `createApplicationOffer extends offer and updates application stage`() {
        val app = RecruitmentApplication(
            applicationNumber = "APP-2026-001",
            vacancyId = vacancyId,
            candidateId = candidateId,
            stage = ApplicationStage.FINAL_INTERVIEW,
            status = ApplicationStatus.ACTIVE,
        ).apply { this.tenantId = tenantId }

        val req = OfferCreateRequestDto(
            baseSalary = BigDecimal("450000"),
            variableBonus = BigDecimal("50000"),
            currency = "LKR",
            startDate = LocalDate.now().plusMonths(1),
            expiryDate = LocalDate.now().plusWeeks(2),
            decisionNotes = "Standard compensation",
        )

        val savedOffer = RecruitmentOffer(
            applicationId = applicationId,
            offerNumber = "OFF-2026-001",
            baseSalary = req.baseSalary,
            variableBonus = req.variableBonus,
            currency = req.currency,
            startDate = req.startDate,
            expiryDate = req.expiryDate,
            status = OfferStatus.EXTENDED,
            decisionNotes = req.decisionNotes,
        ).apply { this.tenantId = tenantId }

        every { applicationRepository.findById(applicationId) } returns Optional.of(app)
        every { offerRepository.findByTenantIdAndApplicationId(tenantId, applicationId) } returns null
        every { offerRepository.findAll() } returns emptyList()
        every { applicationRepository.save(app) } returns app
        every { offerRepository.save(any()) } returns savedOffer

        val offerDto = pipelineService.createApplicationOffer(applicationId, req, tenantId)

        assertNotNull(offerDto)
        assertEquals(OfferStatus.EXTENDED, offerDto.status)
        assertEquals(ApplicationStage.OFFER_EXTENDED, app.stage)
        verify(exactly = 1) { offerRepository.save(any()) }
        verify(exactly = 1) { applicationRepository.save(app) }
    }
}
