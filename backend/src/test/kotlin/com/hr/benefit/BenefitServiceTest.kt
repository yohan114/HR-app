package com.hr.benefit

import com.hr.benefit.internal.*
import com.hr.employee.EmployeeLeaveProfile
import com.hr.employee.EmployeeLookupService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Benefit Service Unit Tests")
class BenefitServiceTest {

    private val categoryRepository = mockk<BenefitCategoryRepository>()
    private val policyRepository = mockk<BenefitPolicyRepository>()
    private val enrollmentRepository = mockk<EmployeeBenefitEnrollmentRepository>()
    private val dependentRepository = mockk<BenefitDependentRepository>()
    private val claimRepository = mockk<BenefitClaimRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>()

    private val benefitService = BenefitService(
        categoryRepository = categoryRepository,
        policyRepository = policyRepository,
        enrollmentRepository = enrollmentRepository,
        dependentRepository = dependentRepository,
        claimRepository = claimRepository,
        employeeLookupService = employeeLookupService,
    )

    private val healthCategory = BenefitCategory(
        code = "HEALTH_INSURANCE",
        name = "Outpatient Medical Insurance",
        description = "OPD medical consultations",
        benefitKind = BenefitKind.NON_CASH,
        isActive = true,
    )

    private val opticalCategory = BenefitCategory(
        code = "OPTICAL",
        name = "Optical & Vision Care",
        description = "Vision care",
        benefitKind = BenefitKind.REIMBURSEMENT,
        isActive = true,
    )

    private val familyMedicalPolicy = BenefitPolicy(
        categoryId = healthCategory.id,
        code = "POL_OPD_FAMILY",
        name = "Comprehensive Outpatient Medical (Family)",
        coverageTier = CoverageTier.FAMILY,
        annualLimit = BigDecimal("250000.00"),
        currency = "LKR",
        coPayPercentage = BigDecimal("10.00"),
        deductibleAmount = BigDecimal.ZERO,
        minServiceMonths = 3,
        eligibleGrades = "ALL",
        requiresReceipt = true,
        isActive = true,
    )

    private val execWellnessPolicy = BenefitPolicy(
        categoryId = healthCategory.id,
        code = "POL_WELLNESS_EXEC",
        name = "Executive Wellness & Gym",
        coverageTier = CoverageTier.INDIVIDUAL,
        annualLimit = BigDecimal("60000.00"),
        currency = "LKR",
        coPayPercentage = BigDecimal.ZERO,
        deductibleAmount = BigDecimal.ZERO,
        minServiceMonths = 6,
        eligibleGrades = "M1,M2,EX",
        requiresReceipt = false,
        isActive = true,
    )

    @Test
    fun `getBenefitCatalogue evaluates eligibility and ineligibility reasons accurately`() {
        val employeeId = UUID.randomUUID()
        val mockEmployee = EmployeeLeaveProfile(
            id = employeeId,
            tenantId = UUID.randomUUID(),
            employeeCode = "EMP001",
            displayName = "Test Employee",
            joinDate = LocalDate.now().minusMonths(4),
            status = "ACTIVE",
            salaryGradeId = UUID.randomUUID(),
        )
        every { employeeLookupService.findById(employeeId) } returns mockEmployee

        every { categoryRepository.findAllByIsActiveTrueOrderByCodeAsc() } returns listOf(healthCategory, opticalCategory)
        every { policyRepository.findAllByIsActiveTrueOrderByCodeAsc() } returns listOf(familyMedicalPolicy, execWellnessPolicy)

        val catalogue = benefitService.getBenefitCatalogue(employeeId)

        assertThat(catalogue.categories).hasSize(2)
        assertThat(catalogue.policies).hasSize(2)

        val medical = catalogue.policies.first { it.code == "POL_OPD_FAMILY" }
        assertThat(medical.isEligible).isTrue()
        assertThat(medical.ineligibilityReason).isNull()

        val exec = catalogue.policies.first { it.code == "POL_WELLNESS_EXEC" }
        assertThat(exec.isEligible).isFalse()
        assertThat(exec.ineligibilityReason).contains("months of service")
    }

    @Test
    fun `getMyBenefits returns aggregated entitlements, usage, and dependent roster`() {
        val employeeId = UUID.randomUUID()
        val enrollment = EmployeeBenefitEnrollment(
            employeeId = employeeId,
            policyId = familyMedicalPolicy.id,
            policyNumber = "MED-2026-9912",
            enrollmentYear = 2026,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 12, 31),
            annualEntitlement = BigDecimal("250000.00"),
            usedAmount = BigDecimal("45000.00"),
            pendingAmount = BigDecimal("12500.00"),
            status = EnrollmentStatus.ACTIVE,
        )

        val spouse = BenefitDependent(
            enrollmentId = enrollment.id,
            fullName = "Anoma Perera",
            relationship = DependentRelationship.SPOUSE,
            dateOfBirth = LocalDate.of(1992, 5, 14),
            nationalIdOrPassport = "199264501234",
            isCovered = true,
        )

        every { enrollmentRepository.findAllByEmployeeIdAndStatusOrderByStartDateDesc(employeeId, EnrollmentStatus.ACTIVE) } returns listOf(enrollment)
        every { policyRepository.findAll() } returns listOf(familyMedicalPolicy)
        every { categoryRepository.findAll() } returns listOf(healthCategory)
        every { dependentRepository.findAllByEnrollmentIdAndIsCoveredTrue(enrollment.id) } returns listOf(spouse)

        val myBenefits = benefitService.getMyBenefits(employeeId)

        assertThat(myBenefits.totalAnnualEntitlement).isEqualByComparingTo(BigDecimal("250000.00"))
        assertThat(myBenefits.totalUsedAmount).isEqualByComparingTo(BigDecimal("45000.00"))
        assertThat(myBenefits.totalPendingAmount).isEqualByComparingTo(BigDecimal("12500.00"))
        assertThat(myBenefits.totalRemainingBalance).isEqualByComparingTo(BigDecimal("192500.00"))
        assertThat(myBenefits.enrollments).hasSize(1)
        assertThat(myBenefits.enrollments[0].dependents).hasSize(1)
        assertThat(myBenefits.enrollments[0].dependents[0].fullName).isEqualTo("Anoma Perera")
    }

    @Test
    fun `submitBenefitClaim succeeds, calculates co-pay, and updates pending amount on enrollment`() {
        val employeeId = UUID.randomUUID()
        val enrollment = EmployeeBenefitEnrollment(
            employeeId = employeeId,
            policyId = familyMedicalPolicy.id,
            policyNumber = "MED-2026-9912",
            enrollmentYear = 2026,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 12, 31),
            annualEntitlement = BigDecimal("250000.00"),
            usedAmount = BigDecimal("45000.00"),
            pendingAmount = BigDecimal("0.00"),
            status = EnrollmentStatus.ACTIVE,
        )

        every { enrollmentRepository.findByIdAndEmployeeId(enrollment.id, employeeId) } returns enrollment
        every { policyRepository.findById(familyMedicalPolicy.id) } returns Optional.of(familyMedicalPolicy)
        every { categoryRepository.findById(healthCategory.id) } returns Optional.of(healthCategory)
        every { claimRepository.save(any()) } answers { firstArg() }
        every { enrollmentRepository.save(any()) } answers { firstArg() }

        val request = BenefitClaimSubmitRequest(
            enrollmentId = enrollment.id,
            claimDate = LocalDate.now(),
            serviceProvider = "Asiri Surgical Hospital",
            diagnosisOrReason = "Emergency consultation and lab test",
            invoiceNumber = "INV-2026-881",
            claimedAmount = BigDecimal("20000.00"),
            currency = "LKR",
        )

        val result = benefitService.submitBenefitClaim(employeeId, request)

        assertThat(result.status).isEqualTo(BenefitClaimStatus.SUBMITTED)
        assertThat(result.claimedAmount).isEqualByComparingTo(BigDecimal("20000.00"))
        assertThat(result.coPayAmount).isEqualByComparingTo(BigDecimal("2000.00")) // 10% co-pay
        assertThat(result.payableAmount).isEqualByComparingTo(BigDecimal("18000.00"))
        assertThat(enrollment.pendingAmount).isEqualByComparingTo(BigDecimal("20000.00"))
    }

    @Test
    fun `submitBenefitClaim fails when claimed amount exceeds remaining policy balance`() {
        val employeeId = UUID.randomUUID()
        val enrollment = EmployeeBenefitEnrollment(
            employeeId = employeeId,
            policyId = familyMedicalPolicy.id,
            policyNumber = "MED-2026-9912",
            enrollmentYear = 2026,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 12, 31),
            annualEntitlement = BigDecimal("50000.00"),
            usedAmount = BigDecimal("45000.00"),
            pendingAmount = BigDecimal("0.00"),
            status = EnrollmentStatus.ACTIVE,
        )

        every { enrollmentRepository.findByIdAndEmployeeId(enrollment.id, employeeId) } returns enrollment
        every { policyRepository.findById(familyMedicalPolicy.id) } returns Optional.of(familyMedicalPolicy)
        every { categoryRepository.findById(healthCategory.id) } returns Optional.of(healthCategory)

        val request = BenefitClaimSubmitRequest(
            enrollmentId = enrollment.id,
            claimDate = LocalDate.now(),
            serviceProvider = "Nawaloka Hospital",
            diagnosisOrReason = "Specialist consultation",
            claimedAmount = BigDecimal("10000.00"), // Exceeds 5,000 remaining
        )

        assertThatThrownBy { benefitService.submitBenefitClaim(employeeId, request) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("exceeds remaining entitlement balance")
    }

    @Test
    fun `cancelBenefitClaim restores pending balance on active enrollment`() {
        val employeeId = UUID.randomUUID()
        val enrollment = EmployeeBenefitEnrollment(
            employeeId = employeeId,
            policyId = familyMedicalPolicy.id,
            policyNumber = "MED-2026-9912",
            enrollmentYear = 2026,
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 12, 31),
            annualEntitlement = BigDecimal("250000.00"),
            usedAmount = BigDecimal("0.00"),
            pendingAmount = BigDecimal("15000.00"),
            status = EnrollmentStatus.ACTIVE,
        )

        val claim = BenefitClaim(
            enrollmentId = enrollment.id,
            employeeId = employeeId,
            claimNumber = "CLM-BEN-2026-991",
            claimDate = LocalDate.now(),
            serviceProvider = "Lanka Hospital",
            diagnosisOrReason = "Routine consultation",
            claimedAmount = BigDecimal("15000.00"),
            coPayAmount = BigDecimal("1500.00"),
            payableAmount = BigDecimal("13500.00"),
            status = BenefitClaimStatus.SUBMITTED,
        )

        every { claimRepository.findByIdAndEmployeeId(claim.id, employeeId) } returns claim
        every { enrollmentRepository.findByIdAndEmployeeId(enrollment.id, employeeId) } returns enrollment
        every { enrollmentRepository.save(any()) } answers { firstArg() }
        every { claimRepository.save(any()) } answers { firstArg() }
        every { policyRepository.findById(familyMedicalPolicy.id) } returns Optional.of(familyMedicalPolicy)
        every { categoryRepository.findById(healthCategory.id) } returns Optional.of(healthCategory)

        val result = benefitService.cancelBenefitClaim(employeeId, claim.id, "Entered incorrect amount")

        assertThat(result.status).isEqualTo(BenefitClaimStatus.CANCELLED)
        assertThat(result.rejectionReason).isEqualTo("Entered incorrect amount")
        assertThat(enrollment.pendingAmount).isEqualByComparingTo(BigDecimal.ZERO)
    }
}
