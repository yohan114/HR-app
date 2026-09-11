package com.hr.app.ui.timesheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.timesheet.TimesheetRepository
import com.hr.client.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TimesheetViewModel
    @Inject
    constructor(
        private val repository: TimesheetRepository,
    ) : ViewModel() {

        private val _uiState = MutableStateFlow(TimesheetUiState())
        val uiState: StateFlow<TimesheetUiState> = _uiState.asStateFlow()

        init {
            loadInitialData()
        }

        fun loadInitialData() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }

                repository.loadClients()
                repository.loadProjects()
                repository.loadMyTimesheets()

                val weekStart = _uiState.value.selectedWeekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                _uiState.update { it.copy(selectedWeekStart = weekStart) }

                loadWeekData(weekStart)
                repository.loadReconciliation(weekStart)

                _uiState.update { it.copy(isLoading = false) }
            }

            viewModelScope.launch {
                repository.clients.collect { clients ->
                    _uiState.update { it.copy(clients = clients) }
                }
            }
            viewModelScope.launch {
                repository.projects.collect { projects ->
                    _uiState.update { it.copy(projects = projects) }
                }
            }
            viewModelScope.launch {
                repository.timesheets.collect { history ->
                    _uiState.update { it.copy(timesheetsHistory = history) }
                }
            }
            viewModelScope.launch {
                repository.reconciliation.collect { recon ->
                    _uiState.update { it.copy(reconciliation = recon) }
                }
            }
            viewModelScope.launch {
                repository.currentTimesheet.collect { ts ->
                    if (ts != null) {
                        rebuildGrid(ts)
                    }
                }
            }
        }

        fun selectTab(tab: TimesheetTab) {
            _uiState.update { it.copy(selectedTab = tab) }
        }

        fun previousWeek() {
            val newWeekStart = _uiState.value.selectedWeekStart.minusWeeks(1)
            changeWeek(newWeekStart)
        }

        fun nextWeek() {
            val newWeekStart = _uiState.value.selectedWeekStart.plusWeeks(1)
            changeWeek(newWeekStart)
        }

        private fun changeWeek(weekStart: LocalDate) {
            _uiState.update { it.copy(selectedWeekStart = weekStart) }
            viewModelScope.launch {
                loadWeekData(weekStart)
                repository.loadReconciliation(weekStart)
            }
        }

        private suspend fun loadWeekData(weekStart: LocalDate) {
            // Find in history or fetch detail if exists
            val matching = _uiState.value.timesheetsHistory.find { it.weekStartDate == weekStart }
            if (matching != null) {
                repository.loadTimesheetDetail(matching.id)
            } else {
                // Initialize empty grid for new draft week
                _uiState.update {
                    it.copy(
                        currentTimesheet = null,
                        gridRows = emptyList(),
                        dayTotals = emptyMap(),
                        weekTotalHours = 0.0,
                        weekBillableHours = 0.0,
                        weekNonBillableHours = 0.0,
                    )
                }
            }
        }

        fun openAddRowDialog() {
            _uiState.update { it.copy(isAddRowDialogOpen = true) }
        }

        fun closeAddRowDialog() {
            _uiState.update { it.copy(isAddRowDialogOpen = false) }
        }

        fun loadActivitiesForProject(projectId: UUID) {
            viewModelScope.launch {
                repository.loadActivities(projectId).onSuccess { list ->
                    val map = _uiState.value.activitiesByProject.toMutableMap()
                    map[projectId] = list
                    _uiState.update { it.copy(activitiesByProject = map) }
                }
            }
        }

        fun addRow(projectId: UUID, activityId: UUID, billable: Boolean) {
            val project = _uiState.value.projects.find { it.id == projectId }
            val activity = _uiState.value.activitiesByProject[projectId]?.find { it.id == activityId }

            val newRow = TimesheetGridRow(
                projectId = projectId,
                projectCode = project?.projectCode ?: "PRJ",
                projectName = project?.projectName ?: "Project",
                activityId = activityId,
                activityCode = activity?.activityCode ?: "ACT",
                activityName = activity?.activityName ?: "Activity",
                billable = billable,
                dailyHours = emptyMap(),
                rowTotalHours = 0.0,
            )

            _uiState.update {
                it.copy(
                    gridRows = it.gridRows + newRow,
                    isAddRowDialogOpen = false,
                )
            }
        }

        fun openEditCellDialog(row: TimesheetGridRow, date: LocalDate) {
            val currentHours = row.dailyHours[date] ?: 0.0
            _uiState.update {
                it.copy(
                    isEditCellDialogOpen = true,
                    selectedRowForEdit = row,
                    selectedDateForEdit = date,
                    currentCellHoursInput = if (currentHours > 0.0) currentHours.toString() else "",
                    currentCellNotesInput = "",
                )
            }
        }

        fun closeEditCellDialog() {
            _uiState.update {
                it.copy(
                    isEditCellDialogOpen = false,
                    selectedRowForEdit = null,
                    selectedDateForEdit = null,
                )
            }
        }

        fun updateCellInput(hours: String, notes: String) {
            _uiState.update {
                it.copy(
                    currentCellHoursInput = hours,
                    currentCellNotesInput = notes,
                )
            }
        }

        fun commitCellEdit() {
            val state = _uiState.value
            val row = state.selectedRowForEdit ?: return
            val date = state.selectedDateForEdit ?: return
            val hours = state.currentCellHoursInput.toDoubleOrNull() ?: 0.0

            if (hours < 0.0 || hours > 24.0) {
                _uiState.update { it.copy(error = "Hours must be between 0 and 24") }
                return
            }

            val updatedRows = state.gridRows.map { r ->
                if (r.projectId == row.projectId && r.activityId == row.activityId) {
                    val updatedDaily = r.dailyHours.toMutableMap()
                    if (hours > 0.0) {
                        updatedDaily[date] = hours
                    } else {
                        updatedDaily.remove(date)
                    }
                    val rowTotal = updatedDaily.values.sum()
                    r.copy(dailyHours = updatedDaily, rowTotalHours = rowTotal)
                } else {
                    r
                }
            }

            closeEditCellDialog()
            recalculateTotals(updatedRows)
        }

        private fun recalculateTotals(rows: List<TimesheetGridRow>) {
            val dayTotals = mutableMapOf<LocalDate, Double>()
            var total = 0.0
            var billable = 0.0
            var nonBillable = 0.0

            rows.forEach { row ->
                row.dailyHours.forEach { (date, hours) ->
                    dayTotals[date] = (dayTotals[date] ?: 0.0) + hours
                    total += hours
                    if (row.billable) {
                        billable += hours
                    } else {
                        nonBillable += hours
                    }
                }
            }

            _uiState.update {
                it.copy(
                    gridRows = rows,
                    dayTotals = dayTotals,
                    weekTotalHours = total,
                    weekBillableHours = billable,
                    weekNonBillableHours = nonBillable,
                )
            }
        }

        fun saveDraft() {
            val state = _uiState.value
            val entries = mutableListOf<TimesheetEntryInput>()

            state.gridRows.forEach { row ->
                row.dailyHours.forEach { (date, hours) ->
                    if (hours > 0.0) {
                        entries.add(
                            TimesheetEntryInput(
                                projectId = row.projectId,
                                activityId = row.activityId,
                                entryDate = date,
                                hours = hours,
                                billable = row.billable,
                            )
                        )
                    }
                }
            }

            val request = TimesheetSaveRequest(
                id = state.currentTimesheet?.id,
                weekStartDate = state.selectedWeekStart,
                propertyEntries = entries,
            )

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                repository.saveTimesheet(request)
                    .onSuccess { saved ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentTimesheet = saved,
                                successMessage = "Timesheet draft saved successfully",
                            )
                        }
                        repository.loadMyTimesheets()
                        repository.loadReconciliation(state.selectedWeekStart)
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "Failed to save timesheet draft",
                            )
                        }
                    }
            }
        }

        fun submitCurrentTimesheet() {
            val tsId = _uiState.value.currentTimesheet?.id
            if (tsId == null) {
                // Must save draft first before submit
                saveDraft()
                return
            }

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                repository.submitTimesheet(tsId)
                    .onSuccess { submitted ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentTimesheet = submitted,
                                successMessage = "Timesheet submitted for manager approval",
                            )
                        }
                        repository.loadMyTimesheets()
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "Failed to submit timesheet",
                            )
                        }
                    }
            }
        }

        fun copyPreviousWeek() {
            val state = _uiState.value
            val request = TimesheetCopyPreviousRequest(
                targetWeekStartDate = state.selectedWeekStart,
            )

            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                repository.copyPreviousWeek(request)
                    .onSuccess { copied ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentTimesheet = copied,
                                successMessage = "Copied projects and activities from previous week",
                            )
                        }
                        rebuildGrid(copied)
                        repository.loadMyTimesheets()
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "Failed to copy previous week",
                            )
                        }
                    }
            }
        }

        private fun rebuildGrid(ts: TimesheetDetailResponse) {
            val rowsMap = mutableMapOf<Pair<UUID, UUID>, MutableMap<LocalDate, Double>>()
            val rowMetaMap = mutableMapOf<Pair<UUID, UUID>, TimesheetEntryItem>()

            ts.propertyEntries.forEach { entry ->
                val key = Pair(entry.projectId, entry.activityId)
                val dayMap = rowsMap.getOrPut(key) { mutableMapOf() }
                dayMap[entry.entryDate] = entry.hours
                rowMetaMap[key] = entry
            }

            val gridRows = rowsMap.map { (key, dayMap) ->
                val meta = rowMetaMap[key]!!
                TimesheetGridRow(
                    projectId = key.first,
                    projectCode = meta.projectCode,
                    projectName = meta.projectName,
                    activityId = key.second,
                    activityCode = meta.activityCode,
                    activityName = meta.activityName,
                    billable = meta.billable,
                    dailyHours = dayMap,
                    rowTotalHours = dayMap.values.sum(),
                )
            }

            _uiState.update {
                it.copy(
                    currentTimesheet = ts,
                    selectedWeekStart = ts.weekStartDate,
                )
            }
            recalculateTotals(gridRows)
        }

        fun clearMessages() {
            _uiState.update { it.copy(error = null, successMessage = null) }
        }
    }
