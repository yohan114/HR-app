package com.hr.app.ui.timesheet

import com.hr.app.data.timesheet.TimesheetRepository
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
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TimesheetViewModelTest {

    private val repository = mockk<TimesheetRepository>()
    private val testDispatcher = StandardTestDispatcher()

    private val clientsFlow = MutableStateFlow<List<TimesheetClientItem>>(emptyList())
    private val projectsFlow = MutableStateFlow<List<TimesheetProjectItem>>(emptyList())
    private val activitiesFlow = MutableStateFlow<List<TimesheetActivityItem>>(emptyList())
    private val timesheetsFlow = MutableStateFlow<List<TimesheetListItem>>(emptyList())
    private val currentTimesheetFlow = MutableStateFlow<TimesheetDetailResponse?>(null)
    private val reconciliationFlow = MutableStateFlow<TimesheetReconciliationResponse?>(null)
    private val isLoadingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    private val timesheetId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val activityId = UUID.randomUUID()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.clients } returns clientsFlow
        coEvery { repository.projects } returns projectsFlow
        coEvery { repository.activities } returns activitiesFlow
        coEvery { repository.timesheets } returns timesheetsFlow
        coEvery { repository.currentTimesheet } returns currentTimesheetFlow
        coEvery { repository.reconciliation } returns reconciliationFlow
        coEvery { repository.isLoading } returns isLoadingFlow
        coEvery { repository.error } returns errorFlow

        coEvery { repository.loadClients() } returns Result.success(emptyList())
        coEvery { repository.loadProjects(any()) } returns Result.success(emptyList())
        coEvery { repository.loadMyTimesheets(any()) } returns Result.success(emptyList())
        coEvery { repository.loadReconciliation(any(), any()) } returns Result.success(mockk())
        coEvery { repository.loadActivities(any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects repository data and default tab`() = runTest {
        val viewModel = TimesheetViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(TimesheetTab.CURRENT_WEEK, viewModel.uiState.value.selectedTab)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `selectTab updates selectedTab in uiState`() = runTest {
        val viewModel = TimesheetViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectTab(TimesheetTab.MY_TIMESHEETS)
        assertEquals(TimesheetTab.MY_TIMESHEETS, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(TimesheetTab.PROJECTS)
        assertEquals(TimesheetTab.PROJECTS, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun `open and close add row dialog updates state`() = runTest {
        val viewModel = TimesheetViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAddRowDialogOpen)
        viewModel.openAddRowDialog()
        assertTrue(viewModel.uiState.value.isAddRowDialogOpen)
        viewModel.closeAddRowDialog()
        assertFalse(viewModel.uiState.value.isAddRowDialogOpen)
    }

    @Test
    fun `addRow appends row to gridRows`() = runTest {
        val viewModel = TimesheetViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addRow(projectId, activityId, true)
        assertEquals(1, viewModel.uiState.value.gridRows.size)
        assertEquals(projectId, viewModel.uiState.value.gridRows.first().projectId)
        assertEquals(activityId, viewModel.uiState.value.gridRows.first().activityId)
        assertTrue(viewModel.uiState.value.gridRows.first().billable)
    }

    @Test
    fun `submitCurrentTimesheet calls repository when timesheet exists`() = runTest {
        val mockTs = mockk<TimesheetDetailResponse>(relaxed = true)
        coEvery { mockTs.id } returns timesheetId
        currentTimesheetFlow.value = mockTs

        coEvery { repository.submitTimesheet(timesheetId) } returns Result.success(mockTs)

        val viewModel = TimesheetViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitCurrentTimesheet()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.submitTimesheet(timesheetId) }
    }
}
