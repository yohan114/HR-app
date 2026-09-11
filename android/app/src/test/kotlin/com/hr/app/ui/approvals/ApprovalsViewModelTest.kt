package com.hr.app.ui.approvals

import com.hr.app.data.approval.ApprovalsRepository
import com.hr.client.model.ApprovalDecisionResponse
import com.hr.client.model.ApprovalItem
import com.hr.client.model.AttendanceApprovalDetails
import com.hr.client.model.LeaveApprovalDetails
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalsViewModelTest {

    private val repository = mockk<ApprovalsRepository>(relaxed = true)
    private val pendingApprovalsFlow = MutableStateFlow<List<ApprovalItem>>(emptyList())
    private val pendingOutboxCountFlow = MutableStateFlow(0)
    private val testDispatcher = StandardTestDispatcher()

    private val leaveItem = ApprovalItem(
        id = "leave-001",
        type = "LEAVE",
        requesterId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        requesterName = "Kasun Perera",
        submittedAt = OffsetDateTime.parse("2026-09-07T08:00:00Z"),
        title = "Annual Leave",
        summary = "Vacation",
        status = "PENDING",
        urgency = ApprovalItem.Urgency.URGENT,
        leaveDetails = LeaveApprovalDetails(
            leaveTypeName = "Annual Leave",
            startDate = LocalDate.of(2026, 9, 10),
            endDate = LocalDate.of(2026, 9, 11),
            workingDays = BigDecimal.valueOf(2.0),
            reason = "Vacation",
            employeeBalanceDays = BigDecimal.valueOf(12.0),
            teamCoverageWarning = null,
        ),
    )

    private val attendanceItem = ApprovalItem(
        id = "att-001",
        type = "ATTENDANCE",
        requesterId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        requesterName = "Dilani Silva",
        submittedAt = OffsetDateTime.parse("2026-09-07T09:00:00Z"),
        title = "Missed Clock In",
        summary = "Scanner down",
        status = "PENDING",
        urgency = ApprovalItem.Urgency.NORMAL,
        attendanceDetails = AttendanceApprovalDetails(
            workDate = LocalDate.of(2026, 9, 7),
            requestedPunchType = "CLOCK_IN",
            requestedTime = "08:35:00",
            shiftName = "General",
            reason = "Scanner down",
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.pendingApprovals } returns pendingApprovalsFlow
        every { repository.pendingOutboxCount } returns pendingOutboxCountFlow
        coEvery { repository.refresh() } returns Result.success(listOf(leaveItem, attendanceItem))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads approvals and observes flows`() = runTest {
        pendingApprovalsFlow.value = listOf(leaveItem, attendanceItem)
        pendingOutboxCountFlow.value = 2

        val viewModel = ApprovalsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.loading)
        assertEquals(2, state.totalCount)
        assertEquals(1, state.leaveCount)
        assertEquals(1, state.attendanceCount)
        assertEquals(2, state.pendingOutboxCount)
    }

    @Test
    fun `selectTab filters items accordingly`() = runTest {
        pendingApprovalsFlow.value = listOf(leaveItem, attendanceItem)

        val viewModel = ApprovalsViewModel(repository)
        advanceUntilIdle()

        viewModel.selectTab(ApprovalFilterTab.LEAVE)
        assertEquals(1, viewModel.state.value.filteredItems.size)
        assertEquals("leave-001", viewModel.state.value.filteredItems.first().id)

        viewModel.selectTab(ApprovalFilterTab.ATTENDANCE)
        assertEquals(1, viewModel.state.value.filteredItems.size)
        assertEquals("att-001", viewModel.state.value.filteredItems.first().id)

        viewModel.selectTab(ApprovalFilterTab.ALL)
        assertEquals(2, viewModel.state.value.filteredItems.size)
    }

    @Test
    fun `approve dispatches decision and clears detail sheet`() = runTest {
        pendingApprovalsFlow.value = listOf(leaveItem)
        coEvery {
            repository.submitDecision(leaveItem.id, leaveItem.type, "APPROVE", null)
        } returns ApprovalDecisionResponse(
            id = leaveItem.id,
            status = "APPROVED",
            decidedAt = java.time.OffsetDateTime.parse("2026-09-07T10:00:00Z"),
            message = "Approved",
        )

        val viewModel = ApprovalsViewModel(repository)
        advanceUntilIdle()

        viewModel.openDetail(leaveItem)
        assertEquals(leaveItem, viewModel.state.value.selectedItemForDetail)

        viewModel.approve(leaveItem)
        advanceUntilIdle()

        coVerify { repository.submitDecision(leaveItem.id, leaveItem.type, "APPROVE", null) }
        assertNull(viewModel.state.value.selectedItemForDetail)
        assertNotNull(viewModel.state.value.successSnackbarMessage)
        assertTrue(viewModel.state.value.successSnackbarMessage!!.contains("Approved"))
    }

    @Test
    fun `confirmReject submits rejection with remarks`() = runTest {
        pendingApprovalsFlow.value = listOf(leaveItem)
        coEvery {
            repository.submitDecision(leaveItem.id, leaveItem.type, "REJECT", "Project crunch time")
        } returns ApprovalDecisionResponse(
            id = leaveItem.id,
            status = "REJECTED",
            decidedAt = java.time.OffsetDateTime.parse("2026-09-07T10:00:00Z"),
            message = "Rejected",
        )

        val viewModel = ApprovalsViewModel(repository)
        advanceUntilIdle()

        viewModel.promptReject(leaveItem)
        assertEquals(leaveItem, viewModel.state.value.rejectionTargetItem)

        viewModel.setRejectionRemarks("Project crunch time")
        viewModel.confirmReject()
        advanceUntilIdle()

        coVerify { repository.submitDecision(leaveItem.id, leaveItem.type, "REJECT", "Project crunch time") }
        assertNull(viewModel.state.value.rejectionTargetItem)
        assertNotNull(viewModel.state.value.successSnackbarMessage)
        assertTrue(viewModel.state.value.successSnackbarMessage!!.contains("Rejected"))
    }
}
