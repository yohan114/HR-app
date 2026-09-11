package com.hr.onboarding

import com.hr.employee.EmployeeLeaveProfile
import com.hr.employee.EmployeeLookupService
import com.hr.onboarding.internal.*
import com.hr.organisation.OrganisationLookupService
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Onboarding Service Unit Tests")
class OnboardingServiceTest {

    private val stageRepository = mockk<OnboardingStageRepository>()
    private val profileRepository = mockk<OnboardingProfileRepository>()
    private val actionRepository = mockk<OnboardingActionRepository>()
    private val instanceRepository = mockk<OnboardingInstanceRepository>()
    private val taskRepository = mockk<OnboardingTaskRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)
    private val organisationLookupService = mockk<OrganisationLookupService>(relaxed = true)

    private lateinit var onboardingService: OnboardingService

    private val employeeId = UUID.randomUUID()
    private val buddyId = UUID.randomUUID()
    private val departmentId = UUID.randomUUID()
    private val stageId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        onboardingService = OnboardingService(
            stageRepository = stageRepository,
            profileRepository = profileRepository,
            actionRepository = actionRepository,
            instanceRepository = instanceRepository,
            taskRepository = taskRepository,
            employeeLookupService = employeeLookupService,
            organisationLookupService = organisationLookupService,
        )
    }

    @Test
    fun `getProfiles returns active profiles with department name`() {
        val profile = OnboardingProfileEntity(
            code = "ENG_STD",
            name = "Engineering Standard",
            departmentId = departmentId,
            description = "Engineering onboarding track",
            active = true,
        )

        every { profileRepository.findAllByActiveTrue() } returns listOf(profile)
        every { organisationLookupService.findAllDepartmentNames() } returns mapOf(departmentId to "Engineering")

        val response = onboardingService.getProfiles()

        assertEquals(1, response.totalCount)
        assertEquals("ENG_STD", response.items.first().code)
        assertEquals("Engineering", response.items.first().departmentName)
    }

    @Test
    fun `createInstance creates instance and instantiates tasks from profile actions`() {
        val emp = EmployeeLeaveProfile(
            id = employeeId,
            tenantId = UUID.randomUUID(),
            employeeCode = "LK010",
            firstName = "Kasun",
            lastName = "Mendis",
            displayName = "Kasun Mendis",
            joinDate = LocalDate.now(),
            status = "ACTIVE",
        )
        val prof = OnboardingProfileEntity(
            code = "ENG_STD",
            name = "Engineering Standard",
            departmentId = departmentId,
        )

        val action = OnboardingActionEntity(
            profileId = prof.id,
            stageId = stageId,
            code = "LAPTOP_PROVISION",
            name = "Laptop Provisioning",
            description = "Dev machine",
            ownerRole = OnboardingOwnerRole.IT_OPS,
            mandatory = true,
            dueDaysOffset = -2,
        )

        val instance = OnboardingInstanceEntity(
            employeeId = employeeId,
            profileId = prof.id,
            joinDate = LocalDate.now(),
            status = OnboardingInstanceStatus.IN_PROGRESS,
            progressPct = BigDecimal.ZERO,
            buddyEmployeeId = buddyId,
        )

        val task = OnboardingTaskEntity(
            instanceId = instance.id,
            actionId = action.id,
            title = action.name,
            description = action.description,
            ownerRole = action.ownerRole,
            assigneeEmployeeId = null,
            dueDate = LocalDate.now().minusDays(2),
            status = OnboardingTaskStatus.PENDING,
        )

        every { employeeLookupService.findById(employeeId) } returns emp
        every { profileRepository.findById(prof.id) } returns Optional.of(prof)
        every { instanceRepository.findByEmployeeId(employeeId) } returns null andThen instance
        every { instanceRepository.save(any()) } returns instance
        every { actionRepository.findByProfileId(prof.id) } returns listOf(action)
        every { taskRepository.save(any()) } returns task
        every { instanceRepository.findById(instance.id) } returns Optional.of(instance)
        every { taskRepository.findByInstanceId(instance.id) } returns listOf(task)
        every { employeeLookupService.findById(buddyId) } returns null

        val request = OnboardingInstanceCreateRequest(
            employeeId = employeeId,
            profileId = prof.id,
            joinDate = LocalDate.now(),
            buddyEmployeeId = buddyId,
        )

        val result = onboardingService.createInstance(request)

        assertNotNull(result)
        assertEquals(instance.id, result.id)
        assertEquals(employeeId, result.employeeId)
        assertEquals("Kasun Mendis", result.employeeName)
        assertEquals(1, result.tasks.size)
        assertEquals("Laptop Provisioning", result.tasks.first().title)
        verify { instanceRepository.save(any()) }
        verify { taskRepository.save(any()) }
    }

    @Test
    fun `completeTask updates task status and recalculates instance progress`() {
        val prof = OnboardingProfileEntity(
            code = "ENG_STD",
            name = "Engineering Standard",
        )
        val instance = OnboardingInstanceEntity(
            employeeId = employeeId,
            profileId = prof.id,
            joinDate = LocalDate.now(),
            status = OnboardingInstanceStatus.IN_PROGRESS,
            progressPct = BigDecimal.ZERO,
        )

        val task1 = OnboardingTaskEntity(
            instanceId = instance.id,
            title = "Task 1",
            ownerRole = OnboardingOwnerRole.NEW_HIRE,
            dueDate = LocalDate.now(),
            status = OnboardingTaskStatus.PENDING,
        )

        val task2 = OnboardingTaskEntity(
            instanceId = instance.id,
            title = "Task 2",
            ownerRole = OnboardingOwnerRole.BUDDY,
            dueDate = LocalDate.now(),
            status = OnboardingTaskStatus.PENDING,
        )

        every { taskRepository.findById(task1.id) } returns Optional.of(task1)
        every { taskRepository.save(task1) } returns task1
        every { taskRepository.findByInstanceId(instance.id) } returns listOf(task1.apply { status = OnboardingTaskStatus.COMPLETED }, task2)
        every { instanceRepository.findById(instance.id) } returns Optional.of(instance)
        every { instanceRepository.save(instance) } returns instance
        every { employeeLookupService.findById(any()) } returns null

        val req = OnboardingTaskCompleteRequest(notes = "Done on time", attachmentUrl = "https://docs.hrapp.io/signed.pdf")
        val result = onboardingService.completeTask(task1.id, req, employeeId)

        assertEquals(OnboardingTaskStatus.COMPLETED, result.status)
        assertEquals("Done on time", result.notes)
        assertEquals(BigDecimal("50.00"), instance.progressPct)
        verify { instanceRepository.save(match { it.progressPct == BigDecimal("50.00") }) }
    }
}
