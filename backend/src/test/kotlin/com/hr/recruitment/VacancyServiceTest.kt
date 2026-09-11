package com.hr.recruitment

import com.hr.organisation.OrganisationLookupService
import com.hr.recruitment.internal.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@DisplayName("Vacancy Service Unit Tests")
class VacancyServiceTest {

    private val vacancyRepository = mockk<RecruitmentVacancyRepository>()
    private val organisationLookupService = mockk<OrganisationLookupService>(relaxed = true)

    private lateinit var vacancyService: VacancyService

    private val tenantId = UUID.randomUUID()
    private val departmentId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        vacancyService = VacancyService(
            vacancyRepository = vacancyRepository,
            organisationLookupService = organisationLookupService,
        )
    }

    @Test
    fun `getVacancies filters by status and department`() {
        val vacancy1 = RecruitmentVacancy(
            jobCode = "VAC-001",
            title = "Senior Backend Engineer",
            departmentId = departmentId,
            description = "Kotlin development",
            status = VacancyStatus.OPEN,
        ).apply { this.tenantId = tenantId }

        val vacancy2 = RecruitmentVacancy(
            jobCode = "VAC-002",
            title = "QA Lead",
            departmentId = UUID.randomUUID(),
            description = "QA Automation",
            status = VacancyStatus.CLOSED,
        ).apply { this.tenantId = tenantId }

        every { vacancyRepository.findAllByTenantId(tenantId) } returns listOf(vacancy1, vacancy2)
        every { organisationLookupService.findDepartmentName(departmentId) } returns "Engineering"

        val result = vacancyService.getVacancies(
            status = VacancyStatus.OPEN,
            departmentId = departmentId,
            tenantId = tenantId,
        )

        assertEquals(1, result.totalCount)
        assertEquals("VAC-001", result.items[0].jobCode)
        assertEquals("Engineering", result.items[0].departmentName)
    }

    @Test
    fun `createVacancy saves and returns DTO`() {
        val request = VacancyCreateRequestDto(
            jobCode = "VAC-NEW-01",
            title = "Mobile Lead",
            departmentId = departmentId,
            location = "Colombo",
            employmentType = "FULL_TIME",
            experienceLevel = "LEAD",
            minSalary = BigDecimal("400000"),
            maxSalary = BigDecimal("500000"),
            description = "Lead Android team",
            openPositions = 1,
            targetHireDate = LocalDate.now().plusMonths(1),
        )

        val savedVacancy = RecruitmentVacancy(
            jobCode = request.jobCode,
            title = request.title,
            departmentId = request.departmentId,
            location = request.location,
            employmentType = request.employmentType,
            experienceLevel = request.experienceLevel,
            minSalary = request.minSalary,
            maxSalary = request.maxSalary,
            description = request.description,
            openPositions = request.openPositions,
            targetHireDate = request.targetHireDate,
            status = VacancyStatus.OPEN,
        ).apply { this.tenantId = tenantId }

        every { vacancyRepository.save(any()) } returns savedVacancy
        every { organisationLookupService.findDepartmentName(departmentId) } returns null

        val result = vacancyService.createVacancy(request, tenantId)

        assertNotNull(result)
        assertEquals("VAC-NEW-01", result.jobCode)
        assertEquals("Mobile Lead", result.title)
        verify(exactly = 1) { vacancyRepository.save(any()) }
    }
}
