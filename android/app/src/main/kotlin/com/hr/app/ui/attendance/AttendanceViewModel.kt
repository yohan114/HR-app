package com.hr.app.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.attendance.AttendanceRepository
import com.hr.client.model.AttendancePunchItem
import com.hr.client.model.AttendanceRegulariseRequest
import com.hr.client.model.ShiftSwapRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AttendanceViewModel
    @Inject
    constructor(
        private val repository: AttendanceRepository,
    ) : ViewModel() {

        private val _state = MutableStateFlow(AttendanceState(loading = true))
        val state: StateFlow<AttendanceState> = _state.asStateFlow()

        private var tickerJob: kotlinx.coroutines.Job? = null

        init {
            observeRepository()
            loadToday()
        }

        private fun observeRepository() {
            viewModelScope.launch {
                repository.todayAttendance.collect { attendance ->
                    _state.update { it.copy(attendance = attendance, loading = false) }
                }
            }

            viewModelScope.launch {
                repository.pendingOutboxCount.collect { count ->
                    _state.update { it.copy(pendingOutboxCount = count) }
                }
            }
        }

        fun startClockTicker() {
            tickerJob?.cancel()
            tickerJob = viewModelScope.launch {
                while (isActive) {
                    _state.update { it.copy(currentTime = LocalTime.now()) }
                    delay(1000)
                }
            }
        }

        fun stopClockTicker() {
            tickerJob?.cancel()
            tickerJob = null
        }

        fun loadToday(isRefresh: Boolean = false) {
            _state.update { it.copy(refreshing = isRefresh, error = null) }
            viewModelScope.launch {
                repository.refresh()
                    .onFailure { err ->
                        _state.update { it.copy(error = err.message, refreshing = false, loading = false) }
                    }
                    .onSuccess {
                        _state.update { it.copy(refreshing = false, loading = false) }
                    }
            }
        }

        fun selectTab(tab: AttendanceTab) {
            _state.update { it.copy(selectedTab = tab) }
            if (tab == AttendanceTab.SHIFT_ROSTER && _state.value.rosterDays.isEmpty()) {
                loadRoster()
            }
        }

        fun loadRoster(
            startDate: LocalDate? = null,
            endDate: LocalDate? = null,
        ) {
            val start = startDate ?: _state.value.currentWeekStart
            val end = endDate ?: start.plusDays(6)

            _state.update { it.copy(rosterLoading = true, rosterError = null, currentWeekStart = start) }
            viewModelScope.launch {
                repository.getShiftRoster(start, end)
                    .onFailure { err ->
                        _state.update { it.copy(rosterLoading = false, rosterError = err.message) }
                    }
                    .onSuccess { days ->
                        _state.update { current ->
                            val selectedStillValid = days.any { it.date == current.selectedRosterDate }
                            val newSelectedDate = if (selectedStillValid) current.selectedRosterDate else start
                            current.copy(
                                rosterLoading = false,
                                rosterDays = days,
                                selectedRosterDate = newSelectedDate,
                            )
                        }
                        loadTeamCoverage(_state.value.selectedRosterDate)
                    }
            }
        }

        fun selectRosterDate(date: LocalDate) {
            _state.update { it.copy(selectedRosterDate = date) }
            loadTeamCoverage(date)
        }

        fun changeWeek(offsetWeeks: Long) {
            val newWeekStart = _state.value.currentWeekStart.plusWeeks(offsetWeeks)
            val newSelectedDate = _state.value.selectedRosterDate.plusWeeks(offsetWeeks)
            _state.update { it.copy(currentWeekStart = newWeekStart, selectedRosterDate = newSelectedDate) }
            loadRoster(startDate = newWeekStart, endDate = newWeekStart.plusDays(6))
        }

        fun loadTeamCoverage(date: LocalDate = _state.value.selectedRosterDate) {
            _state.update { it.copy(teamCoverageLoading = true) }
            viewModelScope.launch {
                repository.getTeamCoverage(date)
                    .onFailure {
                        _state.update { it.copy(teamCoverageLoading = false) }
                    }
                    .onSuccess { coverage ->
                        _state.update { it.copy(teamCoverageLoading = false, teamCoverage = coverage) }
                    }
            }
        }

        fun openSwapDialog() {
            _state.update { it.copy(isSwapDialogOpen = true) }
        }

        fun closeSwapDialog() {
            _state.update { it.copy(isSwapDialogOpen = false) }
        }

        fun submitSwap(
            targetEmployeeId: UUID,
            targetWorkDate: LocalDate,
            reason: String,
            workDate: LocalDate = _state.value.selectedRosterDate,
        ) {
            _state.update { it.copy(swapSubmitting = true) }
            viewModelScope.launch {
                val req = ShiftSwapRequest(
                    workDate = workDate,
                    targetEmployeeId = targetEmployeeId,
                    targetWorkDate = targetWorkDate,
                    reason = reason,
                )
                repository.requestShiftSwap(req)
                    .onSuccess { resp ->
                        _state.update {
                            it.copy(
                                swapSubmitting = false,
                                isSwapDialogOpen = false,
                                actionMessage = "✅ Shift swap request submitted for $workDate",
                            )
                        }
                    }
                    .onFailure { err ->
                        _state.update {
                            it.copy(
                                swapSubmitting = false,
                                isSwapDialogOpen = false,
                                actionMessage = "⚠️ Shift swap request queued: ${err.message}",
                            )
                        }
                    }
            }
        }

        fun openRegulariseDialog() {
            _state.update { it.copy(isRegulariseDialogOpen = true) }
        }

        fun closeRegulariseDialog() {
            _state.update { it.copy(isRegulariseDialogOpen = false) }
        }

        fun submitRegularise(
            requestedInTime: String?,
            requestedOutTime: String?,
            reason: String,
            workDate: LocalDate = _state.value.selectedRosterDate,
        ) {
            _state.update { it.copy(regulariseSubmitting = true) }
            viewModelScope.launch {
                val req = AttendanceRegulariseRequest(
                    workDate = workDate,
                    requestedInTime = requestedInTime,
                    requestedOutTime = requestedOutTime,
                    reason = reason,
                )
                repository.requestRegularisation(req)
                    .onSuccess { resp ->
                        _state.update {
                            it.copy(
                                regulariseSubmitting = false,
                                isRegulariseDialogOpen = false,
                                actionMessage = "✅ Attendance regularisation requested for $workDate",
                            )
                        }
                    }
                    .onFailure { err ->
                        _state.update {
                            it.copy(
                                regulariseSubmitting = false,
                                isRegulariseDialogOpen = false,
                                actionMessage = "⚠️ Attendance regularisation queued: ${err.message}",
                            )
                        }
                    }
            }
        }

        fun selectLocationPreset(preset: GeoSimulationPreset) {
            _state.update { it.copy(selectedLocationPreset = preset) }
        }

        fun punch(punchType: String) {
            val preset = _state.value.selectedLocationPreset
            viewModelScope.launch {
                val punchItem = repository.recordPunch(
                    punchType = punchType,
                    geoLat = preset.latitude,
                    geoLng = preset.longitude,
                    geoAccuracyM = preset.accuracyMeters,
                    isMockLocation = preset.isMock,
                    locationName = preset.description,
                )

                val message = when {
                    preset.isMock -> "⚠️ Clock $punchType logged with Mock GPS Security Warning"
                    punchItem.geofenceStatus == AttendancePunchItem.GeofenceStatus.OUTSIDE -> "⚠️ Clock $punchType logged Outside Office Geofence"
                    else -> "✅ Clock $punchType successful (${punchItem.geofenceStatus.value})"
                }

                _state.update { it.copy(actionMessage = message) }
            }
        }

        fun clearActionMessage() {
            _state.update { it.copy(actionMessage = null) }
        }
    }
