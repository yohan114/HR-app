package com.hr.app.ui.onboarding

import com.hr.app.data.onboarding.OnboardingRepository
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val repository = mockk<OnboardingRepository>()
    private val testDispatcher = StandardTestDispatcher()

    private val myOnboardingFlow = MutableStateFlow<OnboardingInstanceDetail?>(null)
    private val teamInstancesFlow = MutableStateFlow<List<OnboardingInstanceSummary>>(emptyList())
    private val exitTypesFlow = MutableStateFlow<List<ExitTypeItem>>(emptyList())
    private val exitNoticesFlow = MutableStateFlow<List<ExitNoticeItem>>(emptyList())
    private val selectedClearanceFlow = MutableStateFlow<ClearanceDetailResponse?>(null)

    private val instanceId = UUID.randomUUID()
    private val taskId = UUID.randomUUID()
    private val exitNoticeId = UUID.randomUUID()
    private val clearanceTaskId = UUID.randomUUID()

    private val sampleTask = OnboardingTaskItem(
        id = taskId,
        instanceId = instanceId,
        title = "Hardware Provisioning",
        description = "Laptop setup",
        ownerRole = OnboardingTaskItem.OwnerRole.IT_OPS,
        assigneeName = "IT Support",
        dueDate = LocalDate.now(),
        status = OnboardingTaskItem.Status.PENDING,
        notes = null,
    )

    private val sampleClearanceTask = ClearanceTaskItem(
        id = clearanceTaskId,
        exitNoticeId = exitNoticeId,
        employeeId = UUID.randomUUID(),
        department = ClearanceTaskItem.Department.IT_INFRASTRUCTURE,
        title = "Laptop Return",
        assigneeName = "IT Admin",
        status = ClearanceTaskItem.Status.PENDING,
        recoverableAmount = BigDecimal.ZERO,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.myOnboarding } returns myOnboardingFlow
        coEvery { repository.teamInstances } returns teamInstancesFlow
        coEvery { repository.exitTypes } returns exitTypesFlow
        coEvery { repository.exitNotices } returns exitNoticesFlow
        coEvery { repository.selectedClearance } returns selectedClearanceFlow

        coEvery { repository.refreshMyOnboarding(any()) } returns Result.success(null)
        coEvery { repository.refreshTeamOnboarding() } returns Result.success(emptyList())
        coEvery { repository.refreshExitTypes() } returns Result.success(emptyList())
        coEvery { repository.refreshExitNotices(any()) } returns Result.success(emptyList())
        coEvery { repository.refreshClearance(any()) } returns Result.success(mockk())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectTab updates activeTab in state`() = runTest {
        val viewModel = OnboardingViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(OnboardingTab.MY_ONBOARDING, viewModel.state.value.activeTab)
        viewModel.selectTab(OnboardingTab.CLEARANCE_SIGNOFFS)
        assertEquals(OnboardingTab.CLEARANCE_SIGNOFFS, viewModel.state.value.activeTab)
    }

    @Test
    fun `open and dismiss task complete dialog manages dialog state`() = runTest {
        val viewModel = OnboardingViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openTaskCompleteDialog(sampleTask)
        assertTrue(viewModel.state.value.isTaskCompleteDialogOpen)
        assertEquals(sampleTask, viewModel.state.value.selectedTaskForCompletion)

        viewModel.dismissTaskCompleteDialog()
        assertFalse(viewModel.state.value.isTaskCompleteDialogOpen)
        assertNull(viewModel.state.value.selectedTaskForCompletion)
    }

    @Test
    fun `submitTaskComplete calls repository and sets feedback message`() = runTest {
        val viewModel = OnboardingViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openTaskCompleteDialog(sampleTask)
        val completed = sampleTask.copy(status = OnboardingTaskItem.Status.COMPLETED)
        coEvery { repository.completeTask(taskId, any(), any()) } returns Result.success(completed)

        viewModel.submitTaskComplete("Done with setup")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.completeTask(taskId, notes = "Done with setup", attachmentUrl = null) }
        assertFalse(viewModel.state.value.isTaskCompleteDialogOpen)
        assertEquals("Task marked as completed!", viewModel.state.value.userMessage)
    }

    @Test
    fun `submitExitNotice calls repository and updates state message`() = runTest {
        val viewModel = OnboardingViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val exitTypeId = UUID.randomUUID()
        val exitReasonId = UUID.randomUUID()
        val lastDay = LocalDate.now().plusDays(30)
        val notice = ExitNoticeItem(
            id = exitNoticeId,
            noticeNumber = "EXIT-2026-001",
            employeeId = UUID.randomUUID(),
            employeeName = "Kasun Mendis",
            employeeCode = "LK010",
            exitTypeId = exitTypeId,
            exitTypeCode = "RESIGNATION",
            exitTypeName = "Voluntary Resignation",
            noticeDate = LocalDate.now(),
            requestedLastWorkingDate = lastDay,
            status = ExitNoticeItem.Status.SUBMITTED,
        )
        coEvery { repository.submitExitNotice(any()) } returns Result.success(notice)

        viewModel.openExitNoticeDialog()
        assertTrue(viewModel.state.value.isExitNoticeDialogOpen)

        viewModel.submitExitNotice(
            exitTypeId = exitTypeId,
            exitReasonId = exitReasonId,
            requestedLastWorkingDate = lastDay,
            remarks = "Career move",
        )
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.submitExitNotice(any()) }
        assertFalse(viewModel.state.value.isExitNoticeDialogOpen)
        assertEquals("Resignation notice submitted successfully!", viewModel.state.value.userMessage)
    }

    @Test
    fun `submitClearanceSignOff calls repository and records sign-off`() = runTest {
        val viewModel = OnboardingViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openClearanceSignOffDialog(sampleClearanceTask)
        assertTrue(viewModel.state.value.isClearanceSignOffDialogOpen)
        assertEquals(sampleClearanceTask, viewModel.state.value.selectedClearanceTask)

        val updated = sampleClearanceTask.copy(status = ClearanceTaskItem.Status.CLEARED)
        coEvery { repository.updateClearanceTask(clearanceTaskId, any()) } returns Result.success(updated)

        viewModel.submitClearanceSignOff(
            status = ClearanceTaskStatusUpdateRequest.Status.CLEARED,
            remarks = "Hardware received intact",
            recoverableAmount = BigDecimal.ZERO,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.updateClearanceTask(clearanceTaskId, any()) }
        assertFalse(viewModel.state.value.isClearanceSignOffDialogOpen)
        assertEquals("Clearance sign-off recorded!", viewModel.state.value.userMessage)
    }
}
