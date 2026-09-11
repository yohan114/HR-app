package com.hr.app.ui.timesheet

import com.hr.client.model.*
import java.time.LocalDate
import java.util.UUID

enum class TimesheetTab(val label: String) {
    CURRENT_WEEK("Weekly Matrix"),
    MY_TIMESHEETS("History"),
    RECONCILIATION("Attendance Match"),
    PROJECTS("Projects"),
}

data class TimesheetGridRow(
    val projectId: UUID,
    val projectCode: String,
    val projectName: String,
    val activityId: UUID,
    val activityCode: String,
    val activityName: String,
    val billable: Boolean,
    val dailyHours: Map<LocalDate, Double>, // Monday through Sunday
    val rowTotalHours: Double,
)

data class TimesheetUiState(
    val selectedTab: TimesheetTab = TimesheetTab.CURRENT_WEEK,
    val selectedWeekStart: LocalDate = LocalDate.now().minusDays(((LocalDate.now().dayOfWeek.value - 1) % 7).toLong()),
    val currentTimesheet: TimesheetDetailResponse? = null,
    val gridRows: List<TimesheetGridRow> = emptyList(),
    val dayTotals: Map<LocalDate, Double> = emptyMap(),
    val weekTotalHours: Double = 0.0,
    val weekBillableHours: Double = 0.0,
    val weekNonBillableHours: Double = 0.0,
    val timesheetsHistory: List<TimesheetListItem> = emptyList(),
    val reconciliation: TimesheetReconciliationResponse? = null,
    val clients: List<TimesheetClientItem> = emptyList(),
    val projects: List<TimesheetProjectItem> = emptyList(),
    val activitiesByProject: Map<UUID, List<TimesheetActivityItem>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,

    // Dialog states
    val isAddRowDialogOpen: Boolean = false,
    val isEditCellDialogOpen: Boolean = false,
    val selectedRowForEdit: TimesheetGridRow? = null,
    val selectedDateForEdit: LocalDate? = null,
    val currentCellHoursInput: String = "",
    val currentCellNotesInput: String = "",
    val isSubmitConfirmDialogOpen: Boolean = false,
)
