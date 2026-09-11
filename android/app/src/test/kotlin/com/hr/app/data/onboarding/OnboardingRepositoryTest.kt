package com.hr.app.data.onboarding

import com.hr.client.api.OffboardingApi
import com.hr.client.api.OnboardingApi
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class OnboardingRepositoryTest {

    private val onboardingApi = mockk<OnboardingApi>()
    private val offboardingApi = mockk<OffboardingApi>()
    private lateinit var repository: OnboardingRepository

    private val instanceId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()
    private val taskId = UUID.randomUUID()
    private val exitNoticeId = UUID.randomUUID()
    private val clearanceTaskId = UUID.randomUUID()

    private val sampleTask = OnboardingTaskItem(
        id = taskId,
        instanceId = instanceId,
        title = "Hardware & Laptop Provisioning",
        description = "MacBook Pro configured",
        ownerRole = OnboardingTaskItem.OwnerRole.IT_OPS,
        assigneeName = "IT Support",
        dueDate = LocalDate.now(),
        status = OnboardingTaskItem.Status.PENDING,
        notes = null,
    )

    private val sampleDetail = OnboardingInstanceDetail(
        id = instanceId,
        candidateId = null,
        employeeId = employeeId,
        employeeName = "Kasun Mendis",
        employeeCode = "LK010",
        profileId = UUID.randomUUID(),
        profileName = "Engineering Standard Onboarding Track",
        joinDate = LocalDate.now(),
        status = OnboardingInstanceDetail.Status.IN_PROGRESS,
        progressPct = BigDecimal.ZERO,
        buddyEmployeeId = null,
        buddyName = null,
        completedAt = null,
        tasks = listOf(sampleTask),
    )

    private val sampleSummary = OnboardingInstanceSummary(
        id = instanceId,
        employeeId = employeeId,
        employeeName = "Kasun Mendis",
        employeeCode = "LK010",
        profileId = UUID.randomUUID(),
        profileName = "Engineering Standard Onboarding Track",
        joinDate = LocalDate.now(),
        status = OnboardingInstanceSummary.Status.IN_PROGRESS,
        progressPct = BigDecimal.ZERO,
        buddyEmployeeId = null,
        buddyName = null,
        totalTasks = 1,
        completedTasks = 0,
    )

    @Before
    fun setUp() {
        repository = OnboardingRepository(onboardingApi, offboardingApi)
    }

    @Test
    fun `refreshMyOnboarding fetches instances and detail from API`() = runTest {
        coEvery { onboardingApi.getOnboardingInstances(any(), any()) } returns Response.success(
            OnboardingInstanceListResponse(items = listOf(sampleSummary), totalCount = 1)
        )
        coEvery { onboardingApi.getOnboardingInstanceById(instanceId) } returns Response.success(sampleDetail)

        val result = repository.refreshMyOnboarding()
        assertTrue(result.isSuccess)
        assertNotNull(repository.myOnboarding.value)
        assertEquals(instanceId, repository.myOnboarding.value?.id)
        assertEquals("Kasun Mendis", repository.myOnboarding.value?.employeeName)
    }

    @Test
    fun `refreshMyOnboarding falls back to offline default when network fails`() = runTest {
        coEvery { onboardingApi.getOnboardingInstances(any(), any()) } throws RuntimeException("Connection failed")

        val result = repository.refreshMyOnboarding()
        assertTrue(result.isFailure)
        assertNotNull(repository.myOnboarding.value)
        assertTrue(repository.myOnboarding.value!!.tasks.isNotEmpty())
    }

    @Test
    fun `completeTask completes task via API and updates progress`() = runTest {
        val completedTask = sampleTask.copy(
            status = OnboardingTaskItem.Status.COMPLETED,
            completedAt = OffsetDateTime.now(),
            notes = "MacBook handed over",
        )
        coEvery { onboardingApi.getOnboardingInstances(any(), any()) } returns Response.success(
            OnboardingInstanceListResponse(items = listOf(sampleSummary), totalCount = 1)
        )
        coEvery { onboardingApi.getOnboardingInstanceById(instanceId) } returns Response.success(sampleDetail)
        repository.refreshMyOnboarding()

        coEvery { onboardingApi.completeOnboardingTask(taskId, any()) } returns Response.success(completedTask)

        val result = repository.completeTask(taskId, notes = "MacBook handed over")
        assertTrue(result.isSuccess)
        assertEquals(OnboardingTaskItem.Status.COMPLETED, result.getOrNull()?.status)
        assertEquals(BigDecimal("100.00"), repository.myOnboarding.value?.progressPct)
    }

    @Test
    fun `refreshTeamOnboarding updates teamInstances state flow`() = runTest {
        coEvery { onboardingApi.getOnboardingInstances(any(), any()) } returns Response.success(
            OnboardingInstanceListResponse(items = listOf(sampleSummary), totalCount = 1)
        )

        val result = repository.refreshTeamOnboarding()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.teamInstances.value.size)
        assertEquals("LK010", repository.teamInstances.value.first().employeeCode)
    }

    @Test
    fun `refreshExitTypes updates exitTypes state flow`() = runTest {
        val sampleType = ExitTypeItem(
            id = UUID.randomUUID(),
            code = "RESIGNATION",
            name = "Voluntary Resignation",
            voluntary = true,
            noticeDays = 30,
            requiresInterview = true,
            requiresClearance = true,
            reasons = emptyList(),
        )
        coEvery { offboardingApi.getExitTypes() } returns Response.success(
            ExitTypeListResponse(items = listOf(sampleType))
        )

        val result = repository.refreshExitTypes()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.exitTypes.value.size)
        assertEquals("RESIGNATION", repository.exitTypes.value.first().code)
    }

    @Test
    fun `submitExitNotice calls API and prepends to exitNotices`() = runTest {
        val typeId = UUID.randomUUID()
        val req = ExitNoticeCreateRequest(
            exitTypeId = typeId,
            requestedLastWorkingDate = LocalDate.now().plusDays(30),
            remarks = "Career change",
        )
        val notice = ExitNoticeItem(
            id = exitNoticeId,
            noticeNumber = "EXIT-2026-001",
            employeeId = employeeId,
            employeeName = "Kasun Mendis",
            employeeCode = "LK010",
            exitTypeId = typeId,
            exitTypeCode = "RESIGNATION",
            exitTypeName = "Voluntary Resignation",
            noticeDate = LocalDate.now(),
            requestedLastWorkingDate = LocalDate.now().plusDays(30),
            status = ExitNoticeItem.Status.SUBMITTED,
        )
        coEvery { offboardingApi.createExitNotice(req) } returns Response.success(notice)

        val result = repository.submitExitNotice(req)
        assertTrue(result.isSuccess)
        assertEquals(1, repository.exitNotices.value.size)
        assertEquals("EXIT-2026-001", repository.exitNotices.value.first().noticeNumber)
    }

    @Test
    fun `updateClearanceTask signs off clearance task and updates counts`() = runTest {
        val clrTask = ClearanceTaskItem(
            id = clearanceTaskId,
            exitNoticeId = exitNoticeId,
            employeeId = employeeId,
            department = ClearanceTaskItem.Department.IT_INFRASTRUCTURE,
            title = "MacBook Return",
            assigneeName = "IT Support",
            status = ClearanceTaskItem.Status.PENDING,
            recoverableAmount = BigDecimal.ZERO,
        )
        val clrDetail = ClearanceDetailResponse(
            exitNoticeId = exitNoticeId,
            employeeId = employeeId,
            employeeName = "Kasun Mendis",
            totalTasks = 1,
            clearedTasks = 0,
            pendingTasks = 1,
            totalRecoverableAmount = BigDecimal.ZERO,
            tasks = listOf(clrTask),
        )
        coEvery { offboardingApi.getExitNoticeClearance(exitNoticeId) } returns Response.success(clrDetail)
        repository.refreshClearance(exitNoticeId)

        val updatedTask = clrTask.copy(status = ClearanceTaskItem.Status.CLEARED, clearedAt = OffsetDateTime.now())
        val req = ClearanceTaskStatusUpdateRequest(
            status = ClearanceTaskStatusUpdateRequest.Status.CLEARED,
            remarks = "Returned in pristine condition",
            recoverableAmount = BigDecimal.ZERO,
        )
        coEvery { offboardingApi.updateClearanceTaskStatus(clearanceTaskId, req) } returns Response.success(updatedTask)

        val result = repository.updateClearanceTask(clearanceTaskId, req)
        assertTrue(result.isSuccess)
        assertEquals(1, repository.selectedClearance.value?.clearedTasks)
        assertEquals(0, repository.selectedClearance.value?.pendingTasks)
    }
}
