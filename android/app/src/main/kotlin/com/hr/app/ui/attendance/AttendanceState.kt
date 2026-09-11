package com.hr.app.ui.attendance

import com.hr.client.model.ShiftRosterDayItem
import com.hr.client.model.TeamCoverageResponse
import com.hr.client.model.TodayAttendanceResponse
import java.time.LocalDate
import java.time.LocalTime

enum class AttendanceTab {
    TODAY_CLOCK,
    SHIFT_ROSTER,
}

data class AttendanceState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val attendance: TodayAttendanceResponse? = null,
    val currentTime: LocalTime = LocalTime.now(),
    val selectedLocationPreset: GeoSimulationPreset = GeoSimulationPreset.HQ_INSIDE,
    val pendingOutboxCount: Int = 0,
    val actionMessage: String? = null,

    // Shift Roster & Team Schedule state
    val selectedTab: AttendanceTab = AttendanceTab.TODAY_CLOCK,
    val rosterLoading: Boolean = false,
    val rosterError: String? = null,
    val currentWeekStart: LocalDate = LocalDate.now().with(java.time.DayOfWeek.MONDAY),
    val rosterDays: List<ShiftRosterDayItem> = emptyList(),
    val selectedRosterDate: LocalDate = LocalDate.now(),
    val teamCoverageLoading: Boolean = false,
    val teamCoverage: TeamCoverageResponse? = null,
    val isSwapDialogOpen: Boolean = false,
    val isRegulariseDialogOpen: Boolean = false,
    val swapSubmitting: Boolean = false,
    val regulariseSubmitting: Boolean = false,
)
