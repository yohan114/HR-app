package com.hr.app.data.timesheet

import com.hr.client.api.TimesheetsApi
import com.hr.client.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimesheetRepository
    @Inject
    constructor(
        private val timesheetsApi: TimesheetsApi,
    ) {
        private val _clients = MutableStateFlow<List<TimesheetClientItem>>(emptyList())
        val clients: StateFlow<List<TimesheetClientItem>> = _clients.asStateFlow()

        private val _projects = MutableStateFlow<List<TimesheetProjectItem>>(emptyList())
        val projects: StateFlow<List<TimesheetProjectItem>> = _projects.asStateFlow()

        private val _activities = MutableStateFlow<List<TimesheetActivityItem>>(emptyList())
        val activities: StateFlow<List<TimesheetActivityItem>> = _activities.asStateFlow()

        private val _timesheets = MutableStateFlow<List<TimesheetListItem>>(emptyList())
        val timesheets: StateFlow<List<TimesheetListItem>> = _timesheets.asStateFlow()

        private val _currentTimesheet = MutableStateFlow<TimesheetDetailResponse?>(null)
        val currentTimesheet: StateFlow<TimesheetDetailResponse?> = _currentTimesheet.asStateFlow()

        private val _reconciliation = MutableStateFlow<TimesheetReconciliationResponse?>(null)
        val reconciliation: StateFlow<TimesheetReconciliationResponse?> = _reconciliation.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        suspend fun loadClients(): Result<List<TimesheetClientItem>> =
            runCatching {
                val response = timesheetsApi.listTimesheetClients()
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load clients: ${response.code()}")
                val list = body.clients
                _clients.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load clients"
            }

        suspend fun loadProjects(clientId: UUID? = null): Result<List<TimesheetProjectItem>> =
            runCatching {
                val response = timesheetsApi.listTimesheetProjects(clientId)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load projects: ${response.code()}")
                val list = body.projects
                _projects.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load projects"
            }

        suspend fun loadActivities(projectId: UUID): Result<List<TimesheetActivityItem>> =
            runCatching {
                val response = timesheetsApi.listTimesheetActivities(projectId)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load activities: ${response.code()}")
                val list = body.activities
                _activities.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load activities"
            }

        suspend fun loadMyTimesheets(employeeId: UUID? = null): Result<List<TimesheetListItem>> =
            runCatching {
                _isLoading.value = true
                val response = timesheetsApi.listMyTimesheets(employeeId)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load timesheets: ${response.code()}")
                val list = body.timesheets
                _timesheets.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load timesheets"
            }.also {
                _isLoading.value = false
            }

        suspend fun loadTimesheetDetail(id: UUID): Result<TimesheetDetailResponse> =
            runCatching {
                _isLoading.value = true
                val response = timesheetsApi.getTimesheetById(id)
                val detail = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load timesheet detail: ${response.code()}")
                _currentTimesheet.value = detail
                detail
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load timesheet detail"
            }.also {
                _isLoading.value = false
            }

        suspend fun saveTimesheet(request: TimesheetSaveRequest): Result<TimesheetDetailResponse> =
            runCatching {
                _isLoading.value = true
                val response = timesheetsApi.saveTimesheetDraft(request)
                val saved = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to save timesheet: ${response.code()}")
                _currentTimesheet.value = saved
                saved
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to save timesheet"
            }.also {
                _isLoading.value = false
            }

        suspend fun submitTimesheet(id: UUID): Result<TimesheetDetailResponse> =
            runCatching {
                _isLoading.value = true
                val response = timesheetsApi.submitTimesheet(id)
                val submitted = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to submit timesheet: ${response.code()}")
                _currentTimesheet.value = submitted
                submitted
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to submit timesheet"
            }.also {
                _isLoading.value = false
            }

        suspend fun approveTimesheet(id: UUID, request: TimesheetApproveRequest? = null): Result<TimesheetDetailResponse> =
            runCatching {
                _isLoading.value = true
                val response = timesheetsApi.approveTimesheet(id, request)
                val approved = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to approve timesheet: ${response.code()}")
                _currentTimesheet.value = approved
                approved
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to approve timesheet"
            }.also {
                _isLoading.value = false
            }

        suspend fun rejectTimesheet(id: UUID, request: TimesheetRejectRequest): Result<TimesheetDetailResponse> =
            runCatching {
                _isLoading.value = true
                val response = timesheetsApi.rejectTimesheet(id, request)
                val rejected = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to reject timesheet: ${response.code()}")
                _currentTimesheet.value = rejected
                rejected
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to reject timesheet"
            }.also {
                _isLoading.value = false
            }

        suspend fun copyPreviousWeek(request: TimesheetCopyPreviousRequest): Result<TimesheetDetailResponse> =
            runCatching {
                _isLoading.value = true
                val response = timesheetsApi.copyPreviousWeekTimesheet(request)
                val copied = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to copy previous week timesheet: ${response.code()}")
                _currentTimesheet.value = copied
                copied
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to copy previous week"
            }.also {
                _isLoading.value = false
            }

        suspend fun loadReconciliation(weekStart: LocalDate, employeeId: UUID? = null): Result<TimesheetReconciliationResponse> =
            runCatching {
                _isLoading.value = true
                val response = timesheetsApi.getTimesheetReconciliation(weekStart, employeeId)
                val recon = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load reconciliation: ${response.code()}")
                _reconciliation.value = recon
                recon
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load reconciliation"
            }.also {
                _isLoading.value = false
            }

        fun clearError() {
            _error.value = null
        }
    }
