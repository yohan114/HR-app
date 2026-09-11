package com.hr.performance

import com.hr.employee.EmployeeLeaveProfile
import com.hr.employee.EmployeeLookupService
import com.hr.performance.internal.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Appraisal Service Unit Tests")
class AppraisalServiceTest {

    private val appraisalRepository = mockk<PerformanceAppraisalRepository>()
    private val evaluationCycleRepository = mockk<EvaluationCycleRepository>()
    private val goalRepository = mockk<GoalRepository>()
    private val goalCycleRepository = mockk<GoalCycleRepository>()
    private val goalRatingRepository = mockk<AppraisalGoalRatingRepository>()
    private val competencyGroupRepository = mockk<CompetencyGroupRepository>()
    private val competencyRepository = mockk<CompetencyRepository>()
    private val competencyRatingRepository = mockk<AppraisalCompetencyRatingRepository>()
    private val mraRequestRepository = mockk<MraRequestRepository>()
    private val mraRatingRepository = mockk<MraRatingRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)
    private val scoringEngine = AppraisalScoringEngine()

    private lateinit var service: AppraisalService

    private val tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val employeeId = UUID.randomUUID()
    private val managerId = UUID.randomUUID()
    private val goalId = UUID.randomUUID()
    private val sampleEmployee = EmployeeLeaveProfile(
        id = employeeId,
        tenantId = tenantId,
        employeeCode = "LK010",
        firstName = "Kasun",
        lastName = "Mendis",
        displayName = "Kasun Mendis",
        joinDate = LocalDate.of(2024, 1, 1),
        status = "ACTIVE",
    )

    private val sampleManager = EmployeeLeaveProfile(
        id = managerId,
        tenantId = tenantId,
        employeeCode = "LK002",
        firstName = "Nimal",
        lastName = "Perera",
        displayName = "Nimal Perera",
        joinDate = LocalDate.of(2020, 1, 1),
        status = "ACTIVE",
    )

    private val sampleCycle = EvaluationCycleEntity(
        code = "CYCLE_2026_H1",
        name = "2026 H1 Review",
        startDate = LocalDate.of(2026, 1, 1),
        endDate = LocalDate.of(2026, 6, 30),
        goalWeight = BigDecimal("60.00"),
        competencyWeight = BigDecimal("30.00"),
        mraWeight = BigDecimal("10.00"),
        selfReviewDeadline = LocalDate.of(2026, 6, 15),
        managerReviewDeadline = LocalDate.of(2026, 6, 25),
        calibrationDeadline = LocalDate.of(2026, 6, 30),
    ).apply {
        this.tenantId = this@AppraisalServiceTest.tenantId
    }
    private val cycleId get() = sampleCycle.id

    private val sampleAppraisal = PerformanceAppraisalEntity(
        cycleId = sampleCycle.id,
        employeeId = employeeId,
        managerId = managerId,
        status = AppraisalStatus.NOT_STARTED,
    ).apply {
        this.tenantId = this@AppraisalServiceTest.tenantId
    }
    private val appraisalId get() = sampleAppraisal.id

    private val sampleCompetencyGroup = CompetencyGroup(
        code = "TECHNICAL",
        name = "Technical Skills",
        description = "Core tech",
    ).apply {
        this.tenantId = this@AppraisalServiceTest.tenantId
    }
    private val groupId get() = sampleCompetencyGroup.id

    private val sampleCompetency = CompetencyEntity(
        groupId = sampleCompetencyGroup.id,
        code = "SYS_ARCH",
        name = "System Architecture",
        description = "Distributed systems",
        targetLevel = 4,
    ).apply {
        this.tenantId = this@AppraisalServiceTest.tenantId
    }
    private val competencyId get() = sampleCompetency.id

    @BeforeEach
    fun setUp() {
        service = AppraisalService(
            appraisalRepository = appraisalRepository,
            evaluationCycleRepository = evaluationCycleRepository,
            goalRepository = goalRepository,
            goalCycleRepository = goalCycleRepository,
            goalRatingRepository = goalRatingRepository,
            competencyGroupRepository = competencyGroupRepository,
            competencyRepository = competencyRepository,
            competencyRatingRepository = competencyRatingRepository,
            mraRequestRepository = mraRequestRepository,
            mraRatingRepository = mraRatingRepository,
            employeeLookupService = employeeLookupService,
            scoringEngine = scoringEngine,
        )

        every { employeeLookupService.findById(employeeId) } returns sampleEmployee
        every { employeeLookupService.findById(managerId) } returns sampleManager
        every { evaluationCycleRepository.findByIdAndTenantId(any(), any()) } returns sampleCycle
        every { goalRepository.findAllByTenantIdAndEmployeeId(any(), any()) } returns emptyList()
        every { goalRatingRepository.findAllByTenantIdAndAppraisalId(any(), any()) } returns emptyList()
        every { competencyGroupRepository.findAllByTenantId(any()) } returns listOf(sampleCompetencyGroup)
        every { competencyRepository.findAllByTenantId(any()) } returns listOf(sampleCompetency)
        every { competencyRatingRepository.findAllByTenantIdAndAppraisalId(any(), any()) } returns emptyList()
        every { mraRequestRepository.findAllByTenantIdAndAppraisalId(any(), any()) } returns emptyList()
    }

    @Test
    fun `getCompetencies returns competency framework catalogue`() {
        val framework = service.getCompetencies(tenantId)
        assertEquals(1, framework.groups.size)
        assertEquals("TECHNICAL", framework.groups[0].code)
        assertEquals(1, framework.competencies.size)
        assertEquals("SYS_ARCH", framework.competencies[0].code)
    }

    @Test
    fun `submitSelfReview updates ratings and advances status to SELF_REVIEW_SUBMITTED`() {
        every { appraisalRepository.findByIdAndTenantId(appraisalId, tenantId) } returns sampleAppraisal
        every { goalRatingRepository.findByTenantIdAndAppraisalIdAndGoalId(any(), any(), any()) } returns null
        every { goalRatingRepository.save(any()) } answers { firstArg() }
        every { competencyRatingRepository.findByTenantIdAndAppraisalIdAndCompetencyId(any(), any(), any()) } returns null
        every { competencyRatingRepository.save(any()) } answers { firstArg() }
        every { appraisalRepository.save(any()) } answers { firstArg() }

        val request = AppraisalSelfReviewRequestDto(
            overallComments = "Achieved key milestones ahead of time",
            goalRatings = listOf(GoalRatingInputDto(goalId = goalId, rating = BigDecimal("90.00"), comments = "Delivered")),
            competencyRatings = listOf(CompetencyRatingInputDto(competencyId = competencyId, proficiencyLevel = 4, comments = "Proficient")),
        )

        val result = service.submitSelfReview(
            id = appraisalId,
            request = request,
            employeeId = employeeId,
            tenantId = tenantId,
        )

        assertEquals(AppraisalStatus.SELF_REVIEW_SUBMITTED, result.appraisal.status)
        assertEquals("Achieved key milestones ahead of time", result.selfOverallComments)
        verify(exactly = 1) { appraisalRepository.save(any()) }
    }

    @Test
    fun `submitManagerReview computes score, sets rating and updates status to MANAGER_REVIEW_SUBMITTED`() {
        every { appraisalRepository.findByIdAndTenantId(appraisalId, tenantId) } returns sampleAppraisal
        every { goalRatingRepository.findByTenantIdAndAppraisalIdAndGoalId(any(), any(), any()) } returns null
        every { goalRatingRepository.save(any()) } answers { firstArg() }
        every { competencyRatingRepository.findByTenantIdAndAppraisalIdAndCompetencyId(any(), any(), any()) } returns null
        every { competencyRatingRepository.save(any()) } answers { firstArg() }
        every { appraisalRepository.save(any()) } answers { firstArg() }

        val request = AppraisalManagerReviewRequestDto(
            overallComments = "Excellent engineering execution and team ownership",
            goalRatings = listOf(GoalRatingInputDto(goalId = goalId, rating = BigDecimal("95.00"), comments = "Exceeded")),
            competencyRatings = listOf(CompetencyRatingInputDto(competencyId = competencyId, proficiencyLevel = 5, comments = "Role model")),
        )

        val result = service.submitManagerReview(
            id = appraisalId,
            request = request,
            managerId = managerId,
            tenantId = tenantId,
        )

        assertEquals(AppraisalStatus.MANAGER_REVIEW_SUBMITTED, result.appraisal.status)
        assertNotNull(result.appraisal.finalScore)
        assertEquals("Outstanding", result.appraisal.finalRating)
        verify(exactly = 1) { appraisalRepository.save(any()) }
    }

    @Test
    fun `acknowledgeAppraisal updates status to ACKNOWLEDGED and timestamps acknowledgement`() {
        sampleAppraisal.status = AppraisalStatus.MANAGER_REVIEW_SUBMITTED
        every { appraisalRepository.findByIdAndTenantId(appraisalId, tenantId) } returns sampleAppraisal
        every { appraisalRepository.save(any()) } answers { firstArg() }

        val result = service.acknowledgeAppraisal(
            id = appraisalId,
            employeeId = employeeId,
            tenantId = tenantId,
        )

        assertEquals(AppraisalStatus.ACKNOWLEDGED, result.appraisal.status)
        assertNotNull(result.appraisal.employeeAcknowledgedAt)
        verify(exactly = 1) { appraisalRepository.save(any()) }
    }

    @Test
    fun `mra breakdown is hidden when completed count is under anonymity threshold N less than 3`() {
        val rater1 = MraRequestEntity(
            appraisalId = appraisalId,
            subjectEmployeeId = employeeId,
            raterEmployeeId = UUID.randomUUID(),
            relationship = MraRelationship.PEER,
            status = MraStatus.COMPLETED,
        )

        val rater2 = MraRequestEntity(
            appraisalId = appraisalId,
            subjectEmployeeId = employeeId,
            raterEmployeeId = UUID.randomUUID(),
            relationship = MraRelationship.PEER,
            status = MraStatus.COMPLETED,
        )

        // Only 2 completed raters (N = 2 < 3)
        every { mraRequestRepository.findAllByTenantIdAndAppraisalId(any(), any()) } returns listOf(rater1, rater2)
        every { mraRatingRepository.findAllByTenantIdAndMraRequestIdIn(any(), any()) } returns listOf(
            MraRatingEntity(mraRequestId = rater1.id!!, competencyId = competencyId, score = BigDecimal("4.5")),
            MraRatingEntity(mraRequestId = rater2.id!!, competencyId = competencyId, score = BigDecimal("4.0")),
        )
        every { appraisalRepository.findByIdAndTenantId(appraisalId, tenantId) } returns sampleAppraisal

        val detail = service.getAppraisalById(appraisalId, tenantId)

        assertNotNull(detail.mraSummary)
        assertEquals(2, detail.mraSummary?.completedRequests)
        // Anonymity rule: breakdown MUST be empty because N < 3
        assertTrue(detail.mraSummary?.relationshipBreakdown?.isEmpty() == true)
    }

    @Test
    fun `mra breakdown is disclosed when completed count reaches anonymity threshold N at least 3`() {
        val rater1 = MraRequestEntity(
            appraisalId = appraisalId,
            subjectEmployeeId = employeeId,
            raterEmployeeId = UUID.randomUUID(),
            relationship = MraRelationship.PEER,
            status = MraStatus.COMPLETED,
        )

        val rater2 = MraRequestEntity(
            appraisalId = appraisalId,
            subjectEmployeeId = employeeId,
            raterEmployeeId = UUID.randomUUID(),
            relationship = MraRelationship.PEER,
            status = MraStatus.COMPLETED,
        )

        val rater3 = MraRequestEntity(
            appraisalId = appraisalId,
            subjectEmployeeId = employeeId,
            raterEmployeeId = UUID.randomUUID(),
            relationship = MraRelationship.SUBORDINATE,
            status = MraStatus.COMPLETED,
        )

        // 3 completed raters (N = 3 >= 3)
        every { mraRequestRepository.findAllByTenantIdAndAppraisalId(any(), any()) } returns listOf(rater1, rater2, rater3)
        every { mraRatingRepository.findAllByTenantIdAndMraRequestIdIn(any(), any()) } returns listOf(
            MraRatingEntity(mraRequestId = rater1.id!!, competencyId = competencyId, score = BigDecimal("4.5")),
            MraRatingEntity(mraRequestId = rater2.id!!, competencyId = competencyId, score = BigDecimal("4.0")),
            MraRatingEntity(mraRequestId = rater3.id!!, competencyId = competencyId, score = BigDecimal("5.0")),
        )
        every { appraisalRepository.findByIdAndTenantId(appraisalId, tenantId) } returns sampleAppraisal

        val detail = service.getAppraisalById(appraisalId, tenantId)

        assertNotNull(detail.mraSummary)
        assertEquals(3, detail.mraSummary?.completedRequests)
        // Anonymity rule: breakdown is now revealed!
        assertEquals(2, detail.mraSummary?.relationshipBreakdown?.size)
    }
}
