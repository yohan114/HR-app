package com.hr.app.ui.leave

import com.hr.app.data.leave.LeaveRepository
import com.hr.client.model.LeaveApplicationItem
import com.hr.client.model.LeaveBalanceItem
import com.hr.client.model.LeaveEligibilityResponse
import com.hr.client.model.LeaveLedgerEntryItem
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class LeaveViewModelTest {

    private val repository = mockk<LeaveRepository>(relaxed = true)

    private val balancesFlow = MutableStateFlow<List<LeaveBalanceItem>>(emptyList())
    private val leaveYearFlow = MutableStateFlow("2026")
    private val applicationsFlow = MutableStateFlow<List<LeaveApplicationItem>>(emptyList())
    private val ledgerFlow = MutableStateFlow<List<LeaveLedgerEntryItem>>(emptyList())
    private val pendingOutboxCountFlow = MutableStateFlow(0)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LeaveViewModel

    private val sampleAnnualBalance = LeaveBalanceItem(
        leaveTypeId = "type-annual-1",
        leaveTypeCode = "ANNUAL",
        leaveTypeName = "Annual Leave",
        color = "#0284c7",
        entitledDays = BigDecimal("14.0"),
        accruedDays = BigDecimal("14.0"),
        takenDays = BigDecimal("3.0"),
        pendingDays = BigDecimal.ZERO,
        availableDays = BigDecimal("11.0"),
    )

    private val sampleCasualBalance = LeaveBalanceItem(
        leaveTypeId = "type-casual-2",
        leaveTypeCode = "CASUAL",
        leaveTypeName = "Casual Leave",
        color = "#16a34a",
        entitledDays = BigDecimal("7.0"),
        accruedDays = BigDecimal("7.0"),
        takenDays = BigDecimal("2.0"),
        pendingDays = BigDecimal.ZERO,
        availableDays = BigDecimal("5.0"),
    )

    private val sampleApplication = LeaveApplicationItem(
        id = UUID.fromString("00000000-0000-0000-0000-000000000101"),
        employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        employeeName = "Kasun Perera",
        leaveTypeId = "type-annual-1",
        leaveTypeCode = "ANNUAL",
        leaveTypeName = "Annual Leave",
        startDate = LocalDate.of(2026, 9, 14),
        endDate = LocalDate.of(2026, 9, 15),
        dayPortion = LeaveApplicationItem.DayPortion.FULL_DAY,
        totalDays = BigDecimal("2.0"),
        reason = "Family trip",
        status = LeaveApplicationItem.Status.SUBMITTED,
        submittedAt = OffsetDateTime.now(),
        approvedAt = null,
        days = emptyList(),
    )

    private val sampleLedgerEntry = LeaveLedgerEntryItem(
        id = "ledger-1",
        date = LocalDate.of(2026, 1, 1),
        leaveTypeId = "type-annual-1",
        leaveTypeCode = "ANNUAL",
        leaveTypeName = "Annual Leave",
        eventType = LeaveLedgerEntryItem.EventType.OPENING,
        daysCredited = BigDecimal("14.0"),
        daysDebited = BigDecimal.ZERO,
        balanceAfter = BigDecimal("14.0"),
        referenceId = "2026",
        notes = "Annual upfront entitlement 2026",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { repository.balances } returns balancesFlow
        every { repository.leaveYear } returns leaveYearFlow
        every { repository.applications } returns applicationsFlow
        every { repository.ledger } returns ledgerFlow
        every { repository.pendingOutboxCount } returns pendingOutboxCountFlow

        coEvery { repository.refreshBalances() } returns Result.success(listOf(sampleAnnualBalance, sampleCasualBalance))
        coEvery { repository.refreshApplications(any()) } returns Result.success(listOf(sampleApplication))
        coEvery { repository.refreshLedger(any()) } returns Result.success(listOf(sampleLedgerEntry))

        viewModel = LeaveViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads balances, applications, and ledger`() = runTest {
        balancesFlow.value = listOf(sampleAnnualBalance, sampleCasualBalance)
        applicationsFlow.value = listOf(sampleApplication)
        ledgerFlow.value = listOf(sampleLedgerEntry)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("2026", state.leaveYear)
        assertEquals(2, state.balances.size)
        assertEquals("type-annual-1", state.selectedBalanceTypeId)
        assertEquals(1, state.applications.size)
        assertEquals(1, state.ledger.size)
        assertFalse(state.loading)
    }

    @Test
    fun `selectTab switches active tab and triggers data refresh`() = runTest {
        viewModel.selectTab(LeaveTab.MY_APPLICATIONS)
        advanceUntilIdle()
        assertEquals(LeaveTab.MY_APPLICATIONS, viewModel.state.value.selectedTab)
        coVerify { repository.refreshApplications(null) }

        viewModel.selectTab(LeaveTab.STATEMENT_LEDGER)
        advanceUntilIdle()
        assertEquals(LeaveTab.STATEMENT_LEDGER, viewModel.state.value.selectedTab)
        coVerify { repository.refreshLedger(null) }
    }

    @Test
    fun `openApplyDialog preselects type and triggers eligibility calculation`() = runTest {
        balancesFlow.value = listOf(sampleAnnualBalance, sampleCasualBalance)
        advanceUntilIdle()

        coEvery { repository.checkEligibility(any(), any(), any(), any()) } returns Result.success(
            LeaveEligibilityResponse(
                eligible = true,
                workingDaysRequested = BigDecimal("2.0"),
                balanceAvailable = BigDecimal("11.0"),
                remainingAfter = BigDecimal("9.0"),
                reasons = emptyList(),
                days = emptyList(),
            )
        )

        viewModel.openApplyDialog("type-annual-1")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isApplyDialogOpen)
        assertEquals("type-annual-1", state.applyLeaveTypeId)
        assertNotNull(state.eligibility)
        assertTrue(state.eligibility!!.eligible)
        assertEquals(2.0, state.eligibility!!.workingDaysRequested.toDouble(), 0.01)
        assertEquals(9.0, state.eligibility!!.remainingAfter.toDouble(), 0.01)
    }

    @Test
    fun `submitApplication sends request and switches tab on success`() = runTest {
        balancesFlow.value = listOf(sampleAnnualBalance, sampleCasualBalance)
        advanceUntilIdle()

        coEvery { repository.checkEligibility(any(), any(), any(), any()) } returns Result.success(
            LeaveEligibilityResponse(
                eligible = true,
                workingDaysRequested = BigDecimal.ONE,
                balanceAvailable = BigDecimal("11.0"),
                remainingAfter = BigDecimal("10.0"),
                reasons = emptyList(),
                days = emptyList(),
            )
        )

        viewModel.openApplyDialog("type-annual-1")
        advanceUntilIdle()

        viewModel.updateApplyReason("Doctor consultation")

        coEvery { repository.submitApplication(any(), any(), any(), any(), any()) } returns Result.success(
            sampleApplication.copy(totalDays = BigDecimal.ONE, reason = "Doctor consultation")
        )

        viewModel.submitApplication()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isApplyDialogOpen)
        assertFalse(state.isSubmittingApplication)
        assertEquals(LeaveTab.MY_APPLICATIONS, state.selectedTab)
        assertNotNull(state.actionMessage)
        assertTrue(state.actionMessage!!.contains("submitted"))
    }

    @Test
    fun `cancelApplication opens confirmation dialog and executes cancellation`() = runTest {
        viewModel.openCancelDialog(sampleApplication)
        val stateWithDialog = viewModel.state.value
        assertNotNull(stateWithDialog.applicationToCancel)
        assertEquals(sampleApplication.id, stateWithDialog.applicationToCancel!!.id)

        viewModel.updateCancelReason("Rescheduling trip")
        assertEquals("Rescheduling trip", viewModel.state.value.cancelReason)

        coEvery { repository.cancelApplication(sampleApplication.id, "Rescheduling trip") } returns Result.success(
            sampleApplication.copy(status = LeaveApplicationItem.Status.CANCELLED)
        )

        viewModel.confirmCancelApplication()
        advanceUntilIdle()

        val stateAfter = viewModel.state.value
        assertNull(stateAfter.applicationToCancel)
        assertFalse(stateAfter.isCancellingApplication)
        assertNotNull(stateAfter.actionMessage)
        assertTrue(stateAfter.actionMessage!!.contains("cancelled"))
    }

    @Test
    fun `filterApplications updates filter and calls repository`() = runTest {
        viewModel.filterApplications("SUBMITTED")
        advanceUntilIdle()

        assertEquals("SUBMITTED", viewModel.state.value.applicationsFilter)
        coVerify { repository.refreshApplications("SUBMITTED") }
    }

    @Test
    fun `filterLedger updates type filter and calls repository`() = runTest {
        viewModel.filterLedger("type-annual-1")
        advanceUntilIdle()

        assertEquals("type-annual-1", viewModel.state.value.ledgerFilterTypeId)
        coVerify { repository.refreshLedger("type-annual-1") }
    }
}
