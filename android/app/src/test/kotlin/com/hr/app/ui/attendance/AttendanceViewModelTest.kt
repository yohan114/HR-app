package com.hr.app.ui.attendance

import com.hr.app.data.attendance.AttendanceRepository
import com.hr.client.model.AttendancePunchItem
import com.hr.client.model.AttendanceRegulariseRequest
import com.hr.client.model.AttendanceRegulariseResponse
import com.hr.client.model.AttendanceShiftInfo
import com.hr.client.model.BranchGeofenceInfo
import com.hr.client.model.ShiftRosterDayItem
import com.hr.client.model.ShiftRosterResponse
import com.hr.client.model.ShiftSwapRequest
import com.hr.client.model.ShiftSwapResponse
import com.hr.client.model.TeamCoverageMemberItem
import com.hr.client.model.TeamCoverageResponse
import com.hr.client.model.TodayAttendanceResponse
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
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {

    private val repository = mockk<AttendanceRepository>(relaxed = true)
    private val todayAttendanceFlow = MutableStateFlow<TodayAttendanceResponse?>(null)
    private val pendingOutboxCountFlow = MutableStateFlow(0)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AttendanceViewModel

    private val sampleAttendance = TodayAttendanceResponse(
        employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        workDate = LocalDate.of(2026, 9, 7),
        dayStatus = "PRESENT",
        currentStatus = "CHECKED_IN",
        workedMinutes = 120,
        punches = listOf(
            AttendancePunchItem(
                id = "p-1",
                punchType = AttendancePunchItem.PunchType.IN,
                punchedAt = OffsetDateTime.ofInstant(Instant.parse("2026-09-07T08:30:00Z"), ZoneOffset.UTC),
                source = "MOBILE_APP",
                geofenceStatus = AttendancePunchItem.GeofenceStatus.INSIDE,
                isMockLocation = false,
                locationName = "HQ",
            ),
        ),
        geofence = BranchGeofenceInfo(
            branchName = "Colombo Head Office",
            latitude = BigDecimal.valueOf(6.9271),
            longitude = BigDecimal.valueOf(79.8612),
            radiusMeters = BigDecimal.valueOf(200.0),
        ),
        shift = AttendanceShiftInfo(
            id = "shift-1",
            code = "GEN_0830",
            name = "General Day Shift",
            startTime = "08:30:00",
            endTime = "17:30:00",
            graceInMinutes = 15,
            breakMinutes = 60,
        ),
    )

    private val sampleRosterDays = listOf(
        ShiftRosterDayItem(
            date = LocalDate.of(2026, 9, 7),
            dayOfWeek = "Mon",
            shiftCode = "GEN_0830",
            shiftName = "General Day (08:30 - 17:30)",
            startTime = "08:30",
            endTime = "17:30",
            isRestDay = false,
            isHoliday = false,
            dayStatus = "ON_DUTY",
            teamMembersOnDuty = 4,
        ),
        ShiftRosterDayItem(
            date = LocalDate.of(2026, 9, 8),
            dayOfWeek = "Tue",
            shiftCode = "GEN_0830",
            shiftName = "General Day (08:30 - 17:30)",
            startTime = "08:30",
            endTime = "17:30",
            isRestDay = false,
            isHoliday = false,
            dayStatus = "SCHEDULED",
            teamMembersOnDuty = 4,
        ),
    )

    private val sampleCoverage = TeamCoverageResponse(
        date = LocalDate.of(2026, 9, 7),
        totalScheduled = 4,
        totalOnDuty = 4,
        members = listOf(
            TeamCoverageMemberItem(
                employeeId = UUID.fromString("00000000-0000-0000-0000-000000000010"),
                employeeCode = "LK010",
                employeeName = "Kasun Mendis",
                jobTitle = "Senior Systems Engineer",
                departmentName = "Engineering & Operations",
                shiftCode = "GEN_0830",
                shiftName = "General Day (08:30 - 17:30)",
                startTime = "08:30",
                endTime = "17:30",
                status = "ON_DUTY",
            ),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.todayAttendance } returns todayAttendanceFlow
        every { repository.pendingOutboxCount } returns pendingOutboxCountFlow
        coEvery { repository.refresh() } returns Result.success(sampleAttendance)
        coEvery { repository.getShiftRoster(any(), any()) } returns Result.success(sampleRosterDays)
        coEvery { repository.getTeamCoverage(any()) } returns Result.success(sampleCoverage)

        viewModel = AttendanceViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observes repository and updates state`() = runTest {
        todayAttendanceFlow.value = sampleAttendance
        pendingOutboxCountFlow.value = 2
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("CHECKED_IN", state.attendance?.currentStatus)
        assertEquals(2, state.pendingOutboxCount)
        assertEquals(1, state.attendance?.punches?.size)
    }

    @Test
    fun `selectLocationPreset updates selected preset`() = runTest {
        viewModel.selectLocationPreset(GeoSimulationPreset.MOCK_SPOOFED)
        assertEquals(GeoSimulationPreset.MOCK_SPOOFED, viewModel.state.value.selectedLocationPreset)
        assertTrue(viewModel.state.value.selectedLocationPreset.isMock)
    }

    @Test
    fun `punch delegates to repository recordPunch with preset coordinates`() = runTest {
        viewModel.selectLocationPreset(GeoSimulationPreset.HQ_INSIDE)

        coEvery {
            repository.recordPunch(any(), any(), any(), any(), any(), any())
        } returns AttendancePunchItem(
            id = "punch-id-123",
            punchType = AttendancePunchItem.PunchType.IN,
            punchedAt = OffsetDateTime.ofInstant(Instant.parse("2026-09-07T08:30:00Z"), ZoneOffset.UTC),
            source = "MOBILE_APP",
            geofenceStatus = AttendancePunchItem.GeofenceStatus.INSIDE,
            isMockLocation = false,
            locationName = "Colombo HQ Lobby",
        )

        viewModel.punch("IN")
        advanceUntilIdle()

        coVerify {
            repository.recordPunch(
                punchType = "IN",
                geoLat = GeoSimulationPreset.HQ_INSIDE.latitude,
                geoLng = GeoSimulationPreset.HQ_INSIDE.longitude,
                geoAccuracyM = GeoSimulationPreset.HQ_INSIDE.accuracyMeters,
                isMockLocation = false,
                locationName = GeoSimulationPreset.HQ_INSIDE.description,
            )
        }

        assertNotNull(viewModel.state.value.actionMessage)
        assertTrue(viewModel.state.value.actionMessage!!.contains("Clock IN successful"))
        assertTrue(viewModel.state.value.actionMessage!!.contains("INSIDE"))

        viewModel.clearActionMessage()
        assertEquals(null, viewModel.state.value.actionMessage)
    }

    @Test
    fun `selectTab switches tab and loads shift roster`() = runTest {
        assertEquals(AttendanceTab.TODAY_CLOCK, viewModel.state.value.selectedTab)

        viewModel.selectTab(AttendanceTab.SHIFT_ROSTER)
        advanceUntilIdle()

        assertEquals(AttendanceTab.SHIFT_ROSTER, viewModel.state.value.selectedTab)
        assertEquals(2, viewModel.state.value.rosterDays.size)
        assertEquals("GEN_0830", viewModel.state.value.rosterDays.first().shiftCode)
        assertNotNull(viewModel.state.value.teamCoverage)
        assertEquals(1, viewModel.state.value.teamCoverage?.members?.size)

        coVerify { repository.getShiftRoster(any(), any()) }
        coVerify { repository.getTeamCoverage(any()) }
    }

    @Test
    fun `changeWeek navigates weeks and reloads roster`() = runTest {
        val initialStart = viewModel.state.value.currentWeekStart
        viewModel.changeWeek(1)
        advanceUntilIdle()

        val expectedNextWeek = initialStart.plusWeeks(1)
        assertEquals(expectedNextWeek, viewModel.state.value.currentWeekStart)
        coVerify { repository.getShiftRoster(expectedNextWeek, expectedNextWeek.plusDays(6)) }
    }

    @Test
    fun `selectRosterDate updates selected date and refreshes coverage`() = runTest {
        val targetDate = LocalDate.of(2026, 9, 8)
        viewModel.selectRosterDate(targetDate)
        advanceUntilIdle()

        assertEquals(targetDate, viewModel.state.value.selectedRosterDate)
        coVerify { repository.getTeamCoverage(targetDate) }
    }

    @Test
    fun `submitSwap sends ShiftSwapRequest and closes dialog`() = runTest {
        val targetEmp = UUID.fromString("00000000-0000-0000-0000-000000000011")
        val swapDate = LocalDate.of(2026, 9, 8)

        coEvery { repository.requestShiftSwap(any()) } returns Result.success(
            ShiftSwapResponse(
                requestId = "swap-123",
                status = "PENDING_APPROVAL",
                message = "Shift swap submitted",
            )
        )

        viewModel.openSwapDialog()
        assertTrue(viewModel.state.value.isSwapDialogOpen)

        viewModel.submitSwap(
            targetEmployeeId = targetEmp,
            targetWorkDate = swapDate,
            reason = "Family wedding",
            workDate = LocalDate.of(2026, 9, 7),
        )
        advanceUntilIdle()

        coVerify {
            repository.requestShiftSwap(
                match {
                    it.targetEmployeeId == targetEmp &&
                    it.targetWorkDate == swapDate &&
                    it.reason == "Family wedding" &&
                    it.workDate == LocalDate.of(2026, 9, 7)
                }
            )
        }

        assertFalse(viewModel.state.value.isSwapDialogOpen)
        assertFalse(viewModel.state.value.swapSubmitting)
        assertTrue(viewModel.state.value.actionMessage!!.contains("Shift swap request submitted"))
    }

    @Test
    fun `submitRegularise sends AttendanceRegulariseRequest and closes dialog`() = runTest {
        coEvery { repository.requestRegularisation(any()) } returns Result.success(
            AttendanceRegulariseResponse(
                requestId = "reg-456",
                status = "PENDING_APPROVAL",
                message = "Attendance regularised",
            )
        )

        viewModel.openRegulariseDialog()
        assertTrue(viewModel.state.value.isRegulariseDialogOpen)

        viewModel.submitRegularise(
            requestedInTime = "08:30",
            requestedOutTime = "17:30",
            reason = "Client onsite meeting",
            workDate = LocalDate.of(2026, 9, 7),
        )
        advanceUntilIdle()

        coVerify {
            repository.requestRegularisation(
                match {
                    it.requestedInTime == "08:30" &&
                    it.requestedOutTime == "17:30" &&
                    it.reason == "Client onsite meeting" &&
                    it.workDate == LocalDate.of(2026, 9, 7)
                }
            )
        }

        assertFalse(viewModel.state.value.isRegulariseDialogOpen)
        assertFalse(viewModel.state.value.regulariseSubmitting)
        assertTrue(viewModel.state.value.actionMessage!!.contains("Attendance regularisation requested"))
    }
}
