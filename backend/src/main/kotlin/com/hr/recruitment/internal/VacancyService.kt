package com.hr.recruitment.internal

import com.hr.organisation.OrganisationLookupService
import com.hr.recruitment.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class VacancyService(
    private val vacancyRepository: RecruitmentVacancyRepository,
    private val organisationLookupService: OrganisationLookupService,
) {
    private val defaultTenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Transactional(readOnly = true)
    fun getVacancies(
        status: VacancyStatus?,
        departmentId: UUID?,
        tenantId: UUID = defaultTenantId,
    ): VacancyListResponseDto {
        val all = vacancyRepository.findAllByTenantId(tenantId)
        val filtered = all.filter { v ->
            (status == null || v.status == status) &&
            (departmentId == null || v.departmentId == departmentId)
        }
        val items = filtered.map { mapVacancyToDto(it) }
        return VacancyListResponseDto(items = items, totalCount = items.size)
    }

    @Transactional(readOnly = true)
    fun getVacancyById(id: UUID, tenantId: UUID = defaultTenantId): VacancyItemDto {
        val vacancy = vacancyRepository.findById(id).orElse(null)
            ?: throw NoSuchElementException("Vacancy not found with id $id")
        return mapVacancyToDto(vacancy)
    }

    @Transactional
    fun createVacancy(req: VacancyCreateRequestDto, tenantId: UUID = defaultTenantId): VacancyItemDto {
        val vacancy = RecruitmentVacancy(
            jobCode = req.jobCode,
            title = req.title,
            departmentId = req.departmentId,
            location = req.location,
            employmentType = req.employmentType,
            experienceLevel = req.experienceLevel,
            minSalary = req.minSalary,
            maxSalary = req.maxSalary,
            currency = req.currency,
            description = req.description,
            requirements = req.requirements,
            openPositions = req.openPositions,
            filledPositions = 0,
            targetHireDate = req.targetHireDate,
            closingDate = req.closingDate,
            status = VacancyStatus.OPEN,
        )
        vacancy.tenantId = tenantId
        val saved = vacancyRepository.save(vacancy)
        return mapVacancyToDto(saved)
    }

    private fun mapVacancyToDto(v: RecruitmentVacancy): VacancyItemDto {
        val deptName = v.departmentId?.let { organisationLookupService.findDepartmentName(it) }
            ?: "Engineering"
        return VacancyItemDto(
            id = v.id,
            jobCode = v.jobCode,
            title = v.title,
            departmentId = v.departmentId,
            departmentName = deptName,
            location = v.location,
            employmentType = v.employmentType,
            experienceLevel = v.experienceLevel,
            minSalary = v.minSalary,
            maxSalary = v.maxSalary,
            currency = v.currency,
            description = v.description,
            requirements = v.requirements,
            openPositions = v.openPositions,
            filledPositions = v.filledPositions,
            targetHireDate = v.targetHireDate,
            closingDate = v.closingDate,
            status = v.status,
            createdAt = v.createdAt ?: Instant.now(),
        )
    }
}
