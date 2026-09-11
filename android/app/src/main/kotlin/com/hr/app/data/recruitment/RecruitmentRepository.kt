package com.hr.app.data.recruitment

import com.hr.client.api.RecruitmentApi
import com.hr.client.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecruitmentRepository
    @Inject
    constructor(
        private val recruitmentApi: RecruitmentApi,
    ) {
        private val _vacancies = MutableStateFlow<List<VacancyItem>>(emptyList())
        val vacancies: StateFlow<List<VacancyItem>> = _vacancies.asStateFlow()

        private val _candidates = MutableStateFlow<List<CandidateItem>>(emptyList())
        val candidates: StateFlow<List<CandidateItem>> = _candidates.asStateFlow()

        private val _applications = MutableStateFlow<List<ApplicationItem>>(emptyList())
        val applications: StateFlow<List<ApplicationItem>> = _applications.asStateFlow()

        private val _interviews = MutableStateFlow<List<InterviewItem>>(emptyList())
        val interviews: StateFlow<List<InterviewItem>> = _interviews.asStateFlow()

        private val _selectedOffer = MutableStateFlow<OfferDetail?>(null)
        val selectedOffer: StateFlow<OfferDetail?> = _selectedOffer.asStateFlow()

        // ---------------------------------------------------------------------
        // Vacancies
        // ---------------------------------------------------------------------

        suspend fun refreshVacancies(
            status: String? = null,
            departmentId: UUID? = null,
        ): Result<List<VacancyItem>> =
            runCatching {
                val apiStatus = status?.let { s ->
                    runCatching { RecruitmentApi.StatusGetVacancies.valueOf(s) }.getOrNull()
                }
                val response = recruitmentApi.getVacancies(status = apiStatus, departmentId = departmentId)
                val body = response.body()?.items.takeIf { response.isSuccessful }
                    ?: error("GET /v1/recruitment/vacancies failed: ${response.code()}")
                _vacancies.value = body
                body
            }.onFailure {
                if (_vacancies.value.isEmpty()) {
                    _vacancies.value = createOfflineDefaultVacancies()
                }
            }

        suspend fun createVacancy(request: VacancyCreateRequest): Result<VacancyItem> =
            runCatching {
                val response = recruitmentApi.createVacancy(request)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/recruitment/vacancies failed: ${response.code()}")
                _vacancies.update { listOf(body) + it }
                body
            }.recoverCatching {
                val offline = VacancyItem(
                    id = UUID.randomUUID(),
                    jobCode = request.jobCode,
                    title = request.title,
                    departmentId = request.departmentId,
                    departmentName = "Engineering",
                    location = request.location,
                    employmentType = request.employmentType,
                    experienceLevel = request.experienceLevel,
                    minSalary = request.minSalary,
                    maxSalary = request.maxSalary,
                    currency = request.currency,
                    description = request.description,
                    requirements = request.requirements,
                    openPositions = request.openPositions,
                    filledPositions = 0,
                    targetHireDate = request.targetHireDate,
                    closingDate = request.closingDate,
                    status = VacancyItem.Status.OPEN,
                    createdAt = OffsetDateTime.now(),
                )
                _vacancies.update { listOf(offline) + it }
                offline
            }

        // ---------------------------------------------------------------------
        // Candidates
        // ---------------------------------------------------------------------

        suspend fun refreshCandidates(query: String? = null): Result<List<CandidateItem>> =
            runCatching {
                val response = recruitmentApi.getCandidates(query = query)
                val body = response.body()?.items.takeIf { response.isSuccessful }
                    ?: error("GET /v1/recruitment/candidates failed: ${response.code()}")
                _candidates.value = body
                body
            }.onFailure {
                if (_candidates.value.isEmpty()) {
                    _candidates.value = createOfflineDefaultCandidates()
                }
            }

        suspend fun createCandidate(request: CandidateCreateRequest): Result<CandidateItem> =
            runCatching {
                val response = recruitmentApi.createCandidate(request)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/recruitment/candidates failed: ${response.code()}")
                _candidates.update { listOf(body) + it }
                body
            }.recoverCatching {
                val offline = CandidateItem(
                    id = UUID.randomUUID(),
                    firstName = request.firstName,
                    lastName = request.lastName,
                    email = request.email,
                    phone = request.phone,
                    currentCompany = request.currentCompany,
                    currentTitle = request.currentTitle,
                    yearsOfExperience = request.yearsOfExperience,
                    resumeUrl = request.resumeUrl,
                    linkedinUrl = request.linkedinUrl,
                    skills = request.skills ?: emptyList(),
                    notes = request.notes,
                    createdAt = OffsetDateTime.now(),
                )
                _candidates.update { listOf(offline) + it }
                offline
            }

        // ---------------------------------------------------------------------
        // Applications
        // ---------------------------------------------------------------------

        suspend fun refreshApplications(
            vacancyId: UUID? = null,
            stage: String? = null,
            status: String? = null,
        ): Result<List<ApplicationItem>> =
            runCatching {
                val response = recruitmentApi.getApplications(vacancyId = vacancyId, stage = stage, status = status)
                val body = response.body()?.items.takeIf { response.isSuccessful }
                    ?: error("GET /v1/recruitment/applications failed: ${response.code()}")
                _applications.value = body
                body
            }.onFailure {
                if (_applications.value.isEmpty()) {
                    _applications.value = createOfflineDefaultApplications()
                }
            }

        suspend fun createApplication(request: ApplicationCreateRequest): Result<ApplicationItem> =
            runCatching {
                val response = recruitmentApi.createApplication(request)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/recruitment/applications failed: ${response.code()}")
                _applications.update { listOf(body) + it }
                body
            }.recoverCatching {
                val vac = _vacancies.value.find { it.id == request.vacancyId }
                val cand = _candidates.value.find { it.id == request.candidateId }
                val offline = ApplicationItem(
                    id = UUID.randomUUID(),
                    applicationNumber = "APP-2026-${(_applications.value.size + 1).toString().padStart(3, '0')}",
                    vacancyId = request.vacancyId,
                    vacancyTitle = vac?.title ?: "Open Vacancy",
                    candidateId = request.candidateId,
                    candidateName = cand?.let { "${it.firstName} ${it.lastName}" } ?: "Applicant",
                    candidateEmail = cand?.email ?: "",
                    stage = ApplicationItem.Stage.APPLIED,
                    status = ApplicationItem.Status.ACTIVE,
                    rating = null,
                    source = request.source ?: "CAREERS_PORTAL",
                    appliedDate = LocalDate.now(),
                    rejectionReason = null,
                    createdAt = OffsetDateTime.now(),
                )
                _applications.update { listOf(offline) + it }
                offline
            }

        suspend fun updateApplicationStage(
            id: UUID,
            request: ApplicationStageUpdateRequest,
        ): Result<ApplicationItem> =
            runCatching {
                val response = recruitmentApi.updateApplicationStage(id = id, applicationStageUpdateRequest = request)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/recruitment/applications/$id/stage failed: ${response.code()}")
                _applications.update { list -> list.map { if (it.id == id) body else it } }
                body
            }.recoverCatching {
                val existing = _applications.value.find { it.id == id }
                    ?: error("Application not found")
                val targetStage = when (request.stage) {
                    ApplicationStageUpdateRequest.Stage.APPLIED -> ApplicationItem.Stage.APPLIED
                    ApplicationStageUpdateRequest.Stage.SCREENING -> ApplicationItem.Stage.SCREENING
                    ApplicationStageUpdateRequest.Stage.INTERVIEW_ROUND_1 -> ApplicationItem.Stage.INTERVIEW_ROUND_1
                    ApplicationStageUpdateRequest.Stage.INTERVIEW_ROUND_2 -> ApplicationItem.Stage.INTERVIEW_ROUND_2
                    ApplicationStageUpdateRequest.Stage.TECHNICAL_ASSESSMENT -> ApplicationItem.Stage.TECHNICAL_ASSESSMENT
                    ApplicationStageUpdateRequest.Stage.FINAL_INTERVIEW -> ApplicationItem.Stage.FINAL_INTERVIEW
                    ApplicationStageUpdateRequest.Stage.OFFER_EXTENDED -> ApplicationItem.Stage.OFFER_EXTENDED
                    ApplicationStageUpdateRequest.Stage.OFFER_ACCEPTED -> ApplicationItem.Stage.OFFER_ACCEPTED
                    ApplicationStageUpdateRequest.Stage.OFFER_DECLINED -> ApplicationItem.Stage.OFFER_DECLINED
                    ApplicationStageUpdateRequest.Stage.REJECTED -> ApplicationItem.Stage.REJECTED
                    ApplicationStageUpdateRequest.Stage.WITHDRAWN -> ApplicationItem.Stage.WITHDRAWN
                }
                val updated = existing.copy(stage = targetStage)
                _applications.update { list -> list.map { if (it.id == id) updated else it } }
                updated
            }

        // ---------------------------------------------------------------------
        // Interviews
        // ---------------------------------------------------------------------

        suspend fun refreshInterviews(
            applicationId: UUID? = null,
            interviewerEmployeeId: UUID? = null,
            status: String? = null,
        ): Result<List<InterviewItem>> =
            runCatching {
                val response = recruitmentApi.getInterviews(
                    applicationId = applicationId,
                    interviewerEmployeeId = interviewerEmployeeId,
                    status = status,
                )
                val body = response.body()?.items.takeIf { response.isSuccessful }
                    ?: error("GET /v1/recruitment/interviews failed: ${response.code()}")
                _interviews.value = body
                body
            }.onFailure {
                if (_interviews.value.isEmpty()) {
                    _interviews.value = createOfflineDefaultInterviews()
                }
            }

        suspend fun scheduleInterview(request: InterviewScheduleRequest): Result<InterviewItem> =
            runCatching {
                val response = recruitmentApi.scheduleInterview(request)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/recruitment/interviews failed: ${response.code()}")
                _interviews.update { listOf(body) + it }
                body
            }.recoverCatching {
                val app = _applications.value.find { it.id == request.applicationId }
                val offline = InterviewItem(
                    id = UUID.randomUUID(),
                    applicationId = request.applicationId,
                    candidateName = app?.candidateName ?: "Candidate",
                    vacancyTitle = app?.vacancyTitle ?: "Position",
                    interviewRound = request.interviewRound,
                    title = request.title,
                    interviewType = when (request.interviewType) {
                        InterviewScheduleRequest.InterviewType.PHONE_SCREEN -> InterviewItem.InterviewType.PHONE_SCREEN
                        InterviewScheduleRequest.InterviewType.TECHNICAL -> InterviewItem.InterviewType.TECHNICAL
                        InterviewScheduleRequest.InterviewType.SYSTEM_DESIGN -> InterviewItem.InterviewType.SYSTEM_DESIGN
                        InterviewScheduleRequest.InterviewType.BEHAVIORAL -> InterviewItem.InterviewType.BEHAVIORAL
                        InterviewScheduleRequest.InterviewType.HR -> InterviewItem.InterviewType.HR
                        InterviewScheduleRequest.InterviewType.EXECUTIVE -> InterviewItem.InterviewType.EXECUTIVE
                    },
                    scheduledStart = request.scheduledStart,
                    scheduledEnd = request.scheduledEnd,
                    locationOrLink = request.locationOrLink,
                    meetingLink = request.meetingLink,
                    status = InterviewItem.Status.SCHEDULED,
                    interviewerEmployeeIds = request.interviewerEmployeeIds,
                    notes = request.notes,
                    scorecards = emptyList(),
                )
                _interviews.update { listOf(offline) + it }
                offline
            }

        suspend fun submitScorecard(
            interviewId: UUID,
            request: ScorecardSubmitRequest,
        ): Result<InterviewScorecardItem> =
            runCatching {
                val response = recruitmentApi.submitInterviewScorecard(id = interviewId, scorecardSubmitRequest = request)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/recruitment/interviews/$interviewId/scorecard failed: ${response.code()}")
                _interviews.update { list ->
                    list.map { item ->
                        if (item.id == interviewId) {
                            item.copy(
                                status = InterviewItem.Status.COMPLETED,
                                scorecards = listOf(body) + item.scorecards,
                            )
                        } else item
                    }
                }
                body
            }.recoverCatching {
                val rec = when (request.overallRecommendation) {
                    ScorecardSubmitRequest.OverallRecommendation.STRONG_HIRE -> InterviewScorecardItem.OverallRecommendation.STRONG_HIRE
                    ScorecardSubmitRequest.OverallRecommendation.HIRE -> InterviewScorecardItem.OverallRecommendation.HIRE
                    ScorecardSubmitRequest.OverallRecommendation.NEUTRAL -> InterviewScorecardItem.OverallRecommendation.NEUTRAL
                    ScorecardSubmitRequest.OverallRecommendation.NO_HIRE -> InterviewScorecardItem.OverallRecommendation.NO_HIRE
                    ScorecardSubmitRequest.OverallRecommendation.STRONG_NO_HIRE -> InterviewScorecardItem.OverallRecommendation.STRONG_NO_HIRE
                }
                val offline = InterviewScorecardItem(
                    id = UUID.randomUUID(),
                    interviewId = interviewId,
                    interviewerEmployeeId = UUID.fromString("e0000000-0000-0000-0000-000000000010"),
                    interviewerName = "Kasun Mendis",
                    overallRecommendation = rec,
                    technicalSkillRating = request.technicalSkillRating,
                    communicationRating = request.communicationRating,
                    problemSolvingRating = request.problemSolvingRating,
                    culturalFitRating = request.culturalFitRating,
                    strengths = request.strengths,
                    weaknesses = request.weaknesses,
                    summaryNotes = request.summaryNotes,
                    submittedAt = OffsetDateTime.now(),
                )
                _interviews.update { list ->
                    list.map { item ->
                        if (item.id == interviewId) {
                            item.copy(
                                status = InterviewItem.Status.COMPLETED,
                                scorecards = listOf(offline) + item.scorecards,
                            )
                        } else item
                    }
                }
                offline
            }

        // ---------------------------------------------------------------------
        // Offers
        // ---------------------------------------------------------------------

        suspend fun getApplicationOffer(applicationId: UUID): Result<OfferDetail> =
            runCatching {
                val response = recruitmentApi.getApplicationOffer(applicationId)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/recruitment/applications/$applicationId/offer failed: ${response.code()}")
                _selectedOffer.value = body
                body
            }.onFailure {
                if (_selectedOffer.value == null) {
                    _selectedOffer.value = createOfflineDefaultOffer(applicationId)
                }
            }

        suspend fun createOffer(
            applicationId: UUID,
            request: OfferCreateRequest,
        ): Result<OfferDetail> =
            runCatching {
                val response = recruitmentApi.createApplicationOffer(id = applicationId, offerCreateRequest = request)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/recruitment/applications/$applicationId/offer failed: ${response.code()}")
                _selectedOffer.value = body
                _applications.update { list ->
                    list.map { if (it.id == applicationId) it.copy(stage = ApplicationItem.Stage.OFFER_EXTENDED) else it }
                }
                body
            }.recoverCatching {
                val offline = OfferDetail(
                    id = UUID.randomUUID(),
                    applicationId = applicationId,
                    offerNumber = "OFF-2026-001",
                    baseSalary = request.baseSalary,
                    variableBonus = request.variableBonus,
                    currency = request.currency,
                    startDate = request.startDate,
                    expiryDate = request.expiryDate,
                    offerLetterUrl = request.offerLetterUrl,
                    status = OfferDetail.Status.EXTENDED,
                    approvedBy = null,
                    approvedAt = null,
                    decisionNotes = request.decisionNotes,
                    createdAt = OffsetDateTime.now(),
                )
                _selectedOffer.value = offline
                _applications.update { list ->
                    list.map { if (it.id == applicationId) it.copy(stage = ApplicationItem.Stage.OFFER_EXTENDED) else it }
                }
                offline
            }

        // ---------------------------------------------------------------------
        // Offline Fallback Seed Fixtures (Acme Corp & Kasun Mendis LK010)
        // ---------------------------------------------------------------------

        private fun createOfflineDefaultVacancies(): List<VacancyItem> {
            val v1 = VacancyItem(
                id = UUID.fromString("fa000000-0000-0000-0000-000000000001"),
                jobCode = "VAC-2026-001",
                title = "Senior Backend Engineer (Kotlin & Distributed Systems)",
                departmentId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                departmentName = "Engineering",
                location = "Colombo, Sri Lanka",
                employmentType = "FULL_TIME",
                experienceLevel = "SENIOR_LEVEL",
                minSalary = BigDecimal("350000"),
                maxSalary = BigDecimal("480000"),
                currency = "LKR",
                description = "Lead the engineering of mission-critical payroll calculation engines, ANTLR DSLs, and Postgres multi-tenant row-level security architectures.",
                requirements = "5+ years experience in JVM/Kotlin, Spring Boot, Postgres RLS, asynchronous event-driven architectures, and high-precision financial computing.",
                openPositions = 2,
                filledPositions = 0,
                targetHireDate = LocalDate.of(2026, 4, 1),
                closingDate = LocalDate.of(2026, 3, 31),
                status = VacancyItem.Status.OPEN,
                createdAt = OffsetDateTime.now().minusDays(20),
            )
            val v2 = VacancyItem(
                id = UUID.fromString("fa000000-0000-0000-0000-000000000002"),
                jobCode = "VAC-2026-002",
                title = "Mobile Engineering Lead (Android & Jetpack Compose)",
                departmentId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                departmentName = "Engineering",
                location = "Colombo, Sri Lanka",
                employmentType = "FULL_TIME",
                experienceLevel = "LEAD",
                minSalary = BigDecimal("420000"),
                maxSalary = BigDecimal("550000"),
                currency = "LKR",
                description = "Direct mobile platform architecture across offline-first SQLite outbox synchronization, deep linking, and enterprise design system components.",
                requirements = "6+ years mobile development with 3+ years in Kotlin Multiplatform/Jetpack Compose, Room/SQLite sync, and biometric authentication.",
                openPositions = 1,
                filledPositions = 0,
                targetHireDate = LocalDate.of(2026, 4, 15),
                closingDate = LocalDate.of(2026, 4, 10),
                status = VacancyItem.Status.OPEN,
                createdAt = OffsetDateTime.now().minusDays(15),
            )
            return listOf(v1, v2)
        }

        private fun createOfflineDefaultCandidates(): List<CandidateItem> {
            return listOf(
                CandidateItem(
                    id = UUID.fromString("ca000000-0000-0000-0000-000000000001"),
                    firstName = "Nuwan",
                    lastName = "Senaratne",
                    email = "nuwan.senaratne@example.com",
                    phone = "+94771234567",
                    currentCompany = "Virtusa Global",
                    currentTitle = "Senior Software Engineer",
                    yearsOfExperience = BigDecimal("7.5"),
                    resumeUrl = "https://storage.hrapp.io/resumes/nuwan-senaratne.pdf",
                    linkedinUrl = "https://linkedin.com/in/nuwan-senaratne-demo",
                    skills = listOf("Kotlin", "Spring Boot", "PostgreSQL", "Kafka"),
                    notes = "Candidate has strong distributed systems background.",
                    createdAt = OffsetDateTime.now().minusDays(18),
                ),
                CandidateItem(
                    id = UUID.fromString("ca000000-0000-0000-0000-000000000002"),
                    firstName = "Dulani",
                    lastName = "Jayawardena",
                    email = "dulani.j@example.com",
                    phone = "+94719876543",
                    currentCompany = "WSO2 Lanka",
                    currentTitle = "Associate Technical Lead - Mobile",
                    yearsOfExperience = BigDecimal("6.0"),
                    resumeUrl = "https://storage.hrapp.io/resumes/dulani-j.pdf",
                    linkedinUrl = "https://linkedin.com/in/dulani-jayawardena-demo",
                    skills = listOf("Android", "Jetpack Compose", "Coroutines", "Offline Sync"),
                    notes = "High proficiency in Compose animations and clean architecture.",
                    createdAt = OffsetDateTime.now().minusDays(14),
                ),
                CandidateItem(
                    id = UUID.fromString("ca000000-0000-0000-0000-000000000003"),
                    firstName = "Kaveen",
                    lastName = "Alwis",
                    email = "kaveen.alwis@example.com",
                    phone = "+94765551234",
                    currentCompany = "Sysco LABS Sri Lanka",
                    currentTitle = "Software Engineer",
                    yearsOfExperience = BigDecimal("4.0"),
                    resumeUrl = "https://storage.hrapp.io/resumes/kaveen-alwis.pdf",
                    linkedinUrl = "https://linkedin.com/in/kaveen-alwis-demo",
                    skills = listOf("Java", "Kotlin", "REST APIs", "Spring"),
                    notes = "Good fundamentals in REST API services.",
                    createdAt = OffsetDateTime.now().minusDays(7),
                ),
            )
        }

        private fun createOfflineDefaultApplications(): List<ApplicationItem> {
            return listOf(
                ApplicationItem(
                    id = UUID.fromString("ap000000-0000-0000-0000-000000000001"),
                    applicationNumber = "APP-2026-001",
                    vacancyId = UUID.fromString("fa000000-0000-0000-0000-000000000001"),
                    vacancyTitle = "Senior Backend Engineer (Kotlin & Distributed Systems)",
                    candidateId = UUID.fromString("ca000000-0000-0000-0000-000000000001"),
                    candidateName = "Nuwan Senaratne",
                    candidateEmail = "nuwan.senaratne@example.com",
                    stage = ApplicationItem.Stage.OFFER_EXTENDED,
                    status = ApplicationItem.Status.ACTIVE,
                    rating = BigDecimal("4.70"),
                    source = "CAREERS_PORTAL",
                    appliedDate = LocalDate.of(2026, 2, 15),
                    rejectionReason = null,
                    createdAt = OffsetDateTime.now().minusDays(21),
                ),
                ApplicationItem(
                    id = UUID.fromString("ap000000-0000-0000-0000-000000000002"),
                    applicationNumber = "APP-2026-002",
                    vacancyId = UUID.fromString("fa000000-0000-0000-0000-000000000002"),
                    vacancyTitle = "Mobile Engineering Lead (Android & Jetpack Compose)",
                    candidateId = UUID.fromString("ca000000-0000-0000-0000-000000000002"),
                    candidateName = "Dulani Jayawardena",
                    candidateEmail = "dulani.j@example.com",
                    stage = ApplicationItem.Stage.INTERVIEW_ROUND_2,
                    status = ApplicationItem.Status.ACTIVE,
                    rating = BigDecimal("4.85"),
                    source = "LINKEDIN",
                    appliedDate = LocalDate.of(2026, 2, 20),
                    rejectionReason = null,
                    createdAt = OffsetDateTime.now().minusDays(16),
                ),
                ApplicationItem(
                    id = UUID.fromString("ap000000-0000-0000-0000-000000000003"),
                    applicationNumber = "APP-2026-003",
                    vacancyId = UUID.fromString("fa000000-0000-0000-0000-000000000001"),
                    vacancyTitle = "Senior Backend Engineer (Kotlin & Distributed Systems)",
                    candidateId = UUID.fromString("ca000000-0000-0000-0000-000000000003"),
                    candidateName = "Kaveen Alwis",
                    candidateEmail = "kaveen.alwis@example.com",
                    stage = ApplicationItem.Stage.SCREENING,
                    status = ApplicationItem.Status.ACTIVE,
                    rating = BigDecimal("3.80"),
                    source = "REFERRAL",
                    appliedDate = LocalDate.of(2026, 3, 1),
                    rejectionReason = null,
                    createdAt = OffsetDateTime.now().minusDays(7),
                ),
            )
        }

        private fun createOfflineDefaultInterviews(): List<InterviewItem> {
            val kasunId = UUID.fromString("e0000000-0000-0000-0000-000000000010")
            val sc1 = InterviewScorecardItem(
                id = UUID.fromString("sc000000-0000-0000-0000-000000000001"),
                interviewId = UUID.fromString("in000000-0000-0000-0000-000000000001"),
                interviewerEmployeeId = kasunId,
                interviewerName = "Kasun Mendis",
                overallRecommendation = InterviewScorecardItem.OverallRecommendation.STRONG_HIRE,
                technicalSkillRating = BigDecimal("4.80"),
                communicationRating = BigDecimal("4.60"),
                problemSolvingRating = BigDecimal("4.90"),
                culturalFitRating = BigDecimal("4.75"),
                strengths = "Exceptional understanding of Kotlin coroutines concurrency dispatchers and local database caching.",
                weaknesses = "None of concern.",
                summaryNotes = "Demonstrated exemplary mobile system design capabilities. Enthusiastically recommend hire for Lead role.",
                submittedAt = OffsetDateTime.now().minusDays(1),
            )

            val i1 = InterviewItem(
                id = UUID.fromString("in000000-0000-0000-0000-000000000001"),
                applicationId = UUID.fromString("ap000000-0000-0000-0000-000000000002"),
                candidateName = "Dulani Jayawardena",
                vacancyTitle = "Mobile Engineering Lead (Android & Jetpack Compose)",
                interviewRound = 2,
                title = "Mobile Architecture & Offline-First State Sync Deep Dive",
                interviewType = InterviewItem.InterviewType.TECHNICAL,
                scheduledStart = OffsetDateTime.now().plusDays(2).withHour(10).withMinute(0),
                scheduledEnd = OffsetDateTime.now().plusDays(2).withHour(11).withMinute(30),
                locationOrLink = "Conference Room Alpha / Google Meet",
                meetingLink = "https://meet.google.com/abc-ats-tech",
                status = InterviewItem.Status.SCHEDULED,
                interviewerEmployeeIds = listOf(kasunId),
                notes = "Technical evaluation focusing on Compose stability and Room migrations.",
                scorecards = listOf(sc1),
            )
            return listOf(i1)
        }

        private fun createOfflineDefaultOffer(applicationId: UUID): OfferDetail {
            return OfferDetail(
                id = UUID.fromString("of000000-0000-0000-0000-000000000001"),
                applicationId = applicationId,
                offerNumber = "OFF-2026-001",
                baseSalary = BigDecimal("450000.00"),
                variableBonus = BigDecimal("50000.00"),
                currency = "LKR",
                startDate = LocalDate.of(2026, 4, 1),
                expiryDate = LocalDate.of(2026, 3, 25),
                offerLetterUrl = "https://storage.hrapp.io/offers/off-2026-001-nuwan.pdf",
                status = OfferDetail.Status.EXTENDED,
                approvedBy = UUID.fromString("e0000000-0000-0000-0000-000000000002"),
                approvedAt = OffsetDateTime.now().minusDays(3),
                decisionNotes = "Competitive offer extended meeting top tier market compensation for senior engineer.",
                createdAt = OffsetDateTime.now().minusDays(4),
            )
        }
    }
