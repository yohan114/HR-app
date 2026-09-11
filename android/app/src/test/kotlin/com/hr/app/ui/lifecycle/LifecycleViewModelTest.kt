package com.hr.app.ui.lifecycle

import com.hr.app.data.lifecycle.LifecycleRepository
import com.hr.client.model.CareerMovementItem
import com.hr.client.model.CareerTimelineEvent
import com.hr.client.model.CareerTimelineResponse
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
class LifecycleViewModelTest {

    private val repository = mockk<LifecycleRepository>(relaxed = true)

    private val timelineFlow = MutableStateFlow<CareerTimelineResponse?>(null)
    private val movementsFlow = MutableStateFlow<List<CareerMovementItem>>(emptyList())
    private val selectedMovementFlow = MutableStateFlow<CareerMovementItem?>(null)
    private val totalGrowthFlow = MutableStateFlow(BigDecimal.ZERO)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LifecycleViewModel

    private val employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val sampleMovement = CareerMovementItem(
        id = UUID.randomUUID(),
        employeeId = employeeId,
        movementNumber = "MOV-2026-001",
        movementType = CareerMovementItem.MovementType.PROMOTION,
        status = CareerMovementItem.Status.SUBMITTED,
        requestDate = LocalDate.now(),
        effectiveDate = LocalDate.now().plusMonths(1),
        initiatorId = UUID.randomUUID(),
        justification = "Promotion to Principal Architect",
        cascadeApplied = false,
        createdAt = OffsetDateTime.now(),
        prevDepartmentName = "Engineering",
        prevDesignationName = "Lead Systems Architect",
        prevSalaryGradeCode = "M1",
        prevBaseSalary = BigDecimal("350000.00"),
        prevCurrency = "LKR",
        newDepartmentName = "Engineering",
        newDesignationName = "Principal Architect",
        newSalaryGradeCode = "M2",
        newBaseSalary = BigDecimal("450000.00"),
        newCurrency = "LKR",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.timeline } returns timelineFlow
        every { repository.movements } returns movementsFlow
        every { repository.selectedMovement } returns selectedMovementFlow
        every { repository.totalSalaryGrowthPercentage } returns totalGrowthFlow

        coEvery { repository.refreshTimeline(any()) } returns Result.success(mockk(relaxed = true))
        coEvery { repository.refreshMovements(any(), any(), any()) } returns Result.success(emptyList())

        viewModel = LifecycleViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization triggers repository refresh`() = runTest {
        advanceUntilIdle()

        coVerify { repository.refreshTimeline(null) }
        coVerify { repository.refreshMovements(null, null, null) }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `selectTab updates activeTab and clears banners`() = runTest {
        viewModel.selectTab(LifecycleTab.MOVEMENTS)

        assertEquals(LifecycleTab.MOVEMENTS, viewModel.uiState.value.activeTab)
        assertNull(viewModel.uiState.value.errorBanner)
        assertNull(viewModel.uiState.value.successBanner)
    }

    @Test
    fun `openProposalDialog and closeProposalDialog manipulate dialog state`() = runTest {
        viewModel.openProposalDialog()

        assertTrue(viewModel.uiState.value.showProposalDialog)
        assertEquals(CareerMovementItem.MovementType.PROMOTION, viewModel.uiState.value.proposalMovementType)

        viewModel.closeProposalDialog()
        assertFalse(viewModel.uiState.value.showProposalDialog)
    }

    @Test
    fun `submitProposal without justification displays error banner`() = runTest {
        viewModel.openProposalDialog()
        viewModel.updateProposalDesignation("Principal Architect")
        viewModel.updateProposalJustification("")

        viewModel.submitProposal(employeeId)

        assertEquals("Please enter justification for this career movement.", viewModel.uiState.value.errorBanner)
        coVerify(exactly = 0) { repository.proposeMovement(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `submitProposal without designation displays error banner`() = runTest {
        viewModel.openProposalDialog()
        viewModel.updateProposalJustification("Outstanding technical contribution")
        viewModel.updateProposalDesignation("")

        viewModel.submitProposal(employeeId)

        assertEquals("Please specify the proposed designation title.", viewModel.uiState.value.errorBanner)
        coVerify(exactly = 0) { repository.proposeMovement(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `submitProposal with valid inputs calls repository and sets success banner`() = runTest {
        coEvery {
            repository.proposeMovement(
                employeeId = any(),
                movementType = any(),
                effectiveDate = any(),
                justification = any(),
                newDepartmentId = any(),
                newDepartmentName = any(),
                newDesignationId = any(),
                newDesignationName = any(),
                newSalaryGradeId = any(),
                newSalaryGradeCode = any(),
                newLocationId = any(),
                newLocationName = any(),
                newSupervisorId = any(),
                newSupervisorName = any(),
                newBaseSalary = any(),
                newCurrency = any(),
                remarks = any(),
            )
        } returns Result.success(sampleMovement)

        viewModel.openProposalDialog()
        viewModel.updateProposalDesignation("Principal Architect")
        viewModel.updateProposalJustification("Outstanding technical contribution")
        viewModel.updateProposalGrade("M2")
        viewModel.updateProposalSalary("450000")

        viewModel.submitProposal(employeeId)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showProposalDialog)
        assertNotNull(viewModel.uiState.value.successBanner)
        assertTrue(viewModel.uiState.value.successBanner!!.contains("MOV-2026-001"))
    }

    @Test
    fun `approveMovement calls repository and sets success banner`() = runTest {
        val approved = sampleMovement.copy(
            status = CareerMovementItem.Status.APPLIED,
            cascadeApplied = true,
        )
        coEvery { repository.approveMovement(sampleMovement.id) } returns Result.success(approved)

        viewModel.approveMovement(sampleMovement.id)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.successBanner)
        assertTrue(viewModel.uiState.value.successBanner!!.contains("approved and applied immediately"))
    }

    @Test
    fun `revert dialog submission calls repository and updates banner`() = runTest {
        val reverted = sampleMovement.copy(
            status = CareerMovementItem.Status.REVERTED,
            cascadeApplied = false,
        )
        coEvery { repository.revertMovement(sampleMovement.id, any()) } returns Result.success(reverted)

        viewModel.openRevertDialog(sampleMovement.id)
        viewModel.updateRevertReason("Clerical calibration error")

        viewModel.submitRevert()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showRevertDialog)
        assertNotNull(viewModel.uiState.value.successBanner)
        assertTrue(viewModel.uiState.value.successBanner!!.contains("reverted"))
    }
}
