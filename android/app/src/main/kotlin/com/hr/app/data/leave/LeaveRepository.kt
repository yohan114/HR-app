package com.hr.app.data.leave

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.LeaveApi
import com.hr.client.model.CancelLeaveRequest
import com.hr.client.model.LeaveApplicationDayItem
import com.hr.client.model.LeaveApplicationItem
import com.hr.client.model.LeaveApplicationRequest
import com.hr.client.model.LeaveBalanceItem
import com.hr.client.model.LeaveEligibilityRequest
import com.hr.client.model.LeaveEligibilityResponse
import com.hr.client.model.LeaveLedgerEntryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository managing employee leave balances, interactive application flow with
 * live working-day projection, request history, cancellation, audit ledger, and offline Outbox sync.
 */
@Singleton
class LeaveRepository
    @Inject
    constructor(
        private val leaveApi: LeaveApi,
        private val outbox: Outbox,
        private val json: Json,
        private val clock: Clock,
    ) {
        private val _balances = MutableStateFlow<List<LeaveBalanceItem>>(emptyList())
        val balances: StateFlow<List<LeaveBalanceItem>> = _balances.asStateFlow()

        private val _leaveYear = MutableStateFlow("2026")
        val leaveYear: StateFlow<String> = _leaveYear.asStateFlow()

        private val _applications = MutableStateFlow<List<LeaveApplicationItem>>(emptyList())
        val applications: StateFlow<List<LeaveApplicationItem>> = _applications.asStateFlow()

        private val _ledger = MutableStateFlow<List<LeaveLedgerEntryItem>>(emptyList())
        val ledger: StateFlow<List<LeaveLedgerEntryItem>> = _ledger.asStateFlow()

        val pendingOutboxCount = outbox.pendingCount

        /**
         * Refreshes active leave balances.
         */
        suspend fun refreshBalances(): Result<List<LeaveBalanceItem>> =
            runCatching {
                val response = leaveApi.getMyLeaveBalances()
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/leave/balances failed with ${response.code()}")
                _leaveYear.value = body.leaveYear
                _balances.value = body.balances
                body.balances
            }.onFailure {
                if (_balances.value.isEmpty()) {
                    _balances.value = createOfflineDefaultBalances()
                }
            }

        /**
         * Refreshes submitted and historic leave applications.
         */
        suspend fun refreshApplications(status: String? = null): Result<List<LeaveApplicationItem>> =
            runCatching {
                val response = leaveApi.getMyLeaveApplications(status)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/leave/applications failed with ${response.code()}")
                _applications.value = body.applications
                body.applications
            }.onFailure {
                if (_applications.value.isEmpty()) {
                    _applications.value = createOfflineDefaultApplications()
                }
            }

        /**
         * Refreshes itemized audit ledger statement entries.
         */
        suspend fun refreshLedger(leaveTypeId: String? = null): Result<List<LeaveLedgerEntryItem>> =
            runCatching {
                val response = leaveApi.getMyLeaveLedger(leaveTypeId)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/leave/ledger failed with ${response.code()}")
                _ledger.value = body.ledger
                body.ledger
            }.onFailure {
                if (_ledger.value.isEmpty()) {
                    _ledger.value = createOfflineDefaultLedger()
                }
            }

        /**
         * Pre-validates a leave span, returning working days count, reasons, and balance projection.
         */
        suspend fun checkEligibility(
            leaveTypeId: String,
            startDate: LocalDate,
            endDate: LocalDate,
            dayPortion: String = "FULL_DAY",
        ): Result<LeaveEligibilityResponse> =
            runCatching {
                val req = LeaveEligibilityRequest(
                    leaveTypeId = leaveTypeId,
                    startDate = startDate,
                    endDate = endDate,
                    dayPortion = runCatching { LeaveEligibilityRequest.DayPortion.valueOf(dayPortion) }.getOrDefault(LeaveEligibilityRequest.DayPortion.FULL_DAY),
                )
                val response = leaveApi.checkLeaveEligibility(req)
                response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/leave/eligibility failed with ${response.code()}")
            }.recover {
                // Offline fallback evaluation
                calculateOfflineEligibility(leaveTypeId, startDate, endDate, dayPortion)
            }

        /**
         * Submits a leave application, updates local state optimistically, and queues to Outbox.
         */
        suspend fun submitApplication(
            leaveTypeId: String,
            startDate: LocalDate,
            endDate: LocalDate,
            dayPortion: String,
            reason: String,
        ): Result<LeaveApplicationItem> =
            runCatching {
                val req = LeaveApplicationRequest(
                    leaveTypeId = leaveTypeId,
                    startDate = startDate,
                    endDate = endDate,
                    dayPortion = runCatching { LeaveApplicationRequest.DayPortion.valueOf(dayPortion) }.getOrDefault(LeaveApplicationRequest.DayPortion.FULL_DAY),
                    reason = reason,
                )

                val selectedType = _balances.value.firstOrNull { it.leaveTypeId == leaveTypeId }
                val typeCode = selectedType?.leaveTypeCode ?: "ANNUAL"
                val typeName = selectedType?.leaveTypeName ?: "Annual Leave"

                val appId = UUID.randomUUID()
                val now = OffsetDateTime.ofInstant(Instant.ofEpochMilli(clock.now()), ZoneOffset.UTC)

                // Calculate optimistic days
                val (workingDays, daysList) = computeWorkingDaysList(startDate, endDate, dayPortion)

                val optimisticItem = LeaveApplicationItem(
                    id = appId,
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    employeeName = "Current Employee",
                    leaveTypeId = leaveTypeId,
                    leaveTypeCode = typeCode,
                    leaveTypeName = typeName,
                    startDate = startDate,
                    endDate = endDate,
                    dayPortion = runCatching { LeaveApplicationItem.DayPortion.valueOf(dayPortion) }.getOrDefault(LeaveApplicationItem.DayPortion.FULL_DAY),
                    totalDays = workingDays,
                    reason = reason,
                    status = LeaveApplicationItem.Status.SUBMITTED,
                    submittedAt = now,
                    approvedAt = null,
                    days = daysList,
                )

                // 1. Optimistically insert into applications flow
                _applications.update { listOf(optimisticItem) + it }

                // 2. Optimistically adjust pending and available balance
                _balances.update { list ->
                    list.map { b ->
                        if (b.leaveTypeId == leaveTypeId) {
                            val newPending = b.pendingDays + workingDays
                            val newAvail = (b.availableDays - workingDays).coerceAtLeast(BigDecimal.ZERO)
                            b.copy(pendingDays = newPending, availableDays = newAvail)
                        } else b
                    }
                }

                // 3. Queue into offline outbox
                val payload = json.encodeToString(req)
                outbox.enqueue(
                    aggregateType = "LEAVE_APPLICATION",
                    aggregateId = appId.toString(),
                    httpMethod = "POST",
                    path = "/v1/leave/applications",
                    payload = payload,
                )

                // 4. Attempt remote call
                runCatching {
                    val response = leaveApi.submitLeaveApplication(req)
                    if (response.isSuccessful && response.body() != null) {
                        val serverItem = response.body()!!
                        _applications.update { list ->
                            list.map { if (it.id == appId) serverItem else it }
                        }
                        serverItem
                    } else {
                        optimisticItem
                    }
                }.getOrDefault(optimisticItem)
            }

        /**
         * Cancels or withdraws a leave application.
         */
        suspend fun cancelApplication(
            applicationId: UUID,
            reason: String?,
        ): Result<LeaveApplicationItem> =
            runCatching {
                val current = _applications.value.firstOrNull { it.id == applicationId }
                    ?: error("Application not found")

                val updatedItem = current.copy(status = LeaveApplicationItem.Status.CANCELLED)

                // 1. Optimistically update local application state
                _applications.update { list ->
                    list.map { if (it.id == applicationId) updatedItem else it }
                }

                // 2. Restore balance if was pending or approved
                _balances.update { list ->
                    list.map { b ->
                        if (b.leaveTypeId == current.leaveTypeId) {
                            val newPending = (b.pendingDays - current.totalDays).coerceAtLeast(BigDecimal.ZERO)
                            val newAvail = b.availableDays + current.totalDays
                            b.copy(pendingDays = newPending, availableDays = newAvail)
                        } else b
                    }
                }

                // 3. Enqueue cancellation into outbox
                val cancelReq = CancelLeaveRequest(reason = reason)
                val payload = json.encodeToString(cancelReq)
                outbox.enqueue(
                    aggregateType = "LEAVE_CANCELLATION",
                    aggregateId = applicationId.toString(),
                    httpMethod = "POST",
                    path = "/v1/leave/applications/$applicationId/cancel",
                    payload = payload,
                )

                // 4. Attempt live API
                runCatching {
                    val response = leaveApi.cancelLeaveApplication(applicationId, cancelReq)
                    if (response.isSuccessful && response.body() != null) {
                        val serverResult = response.body()!!
                        _applications.update { list ->
                            list.map { if (it.id == applicationId) serverResult else it }
                        }
                        serverResult
                    } else {
                        updatedItem
                    }
                }.getOrDefault(updatedItem)
            }

        private fun calculateOfflineEligibility(
            leaveTypeId: String,
            startDate: LocalDate,
            endDate: LocalDate,
            dayPortion: String,
        ): LeaveEligibilityResponse {
            val (workingDays, daysList) = computeWorkingDaysList(startDate, endDate, dayPortion)
            val balance = _balances.value.firstOrNull { it.leaveTypeId == leaveTypeId }
            val available = balance?.availableDays ?: BigDecimal("14.0")
            val remaining = available - workingDays
            val reasons = mutableListOf<String>()

            if (endDate.isBefore(startDate)) {
                reasons.add("End date cannot precede start date")
            }
            if (workingDays <= BigDecimal.ZERO) {
                reasons.add("All selected days fall on weekends")
            }
            if (remaining < BigDecimal.ZERO) {
                reasons.add("Insufficient balance (available: $available, requested: $workingDays)")
            }

            return LeaveEligibilityResponse(
                eligible = reasons.isEmpty(),
                workingDaysRequested = workingDays,
                balanceAvailable = available,
                remainingAfter = remaining,
                reasons = reasons,
                days = daysList,
            )
        }

        private fun computeWorkingDaysList(
            startDate: LocalDate,
            endDate: LocalDate,
            dayPortion: String,
        ): Pair<BigDecimal, List<LeaveApplicationDayItem>> {
            if (endDate.isBefore(startDate)) return Pair(BigDecimal.ZERO, emptyList())

            val portionWeight = if (dayPortion == "FULL_DAY") BigDecimal.ONE else BigDecimal("0.5")
            val portionHours = if (dayPortion == "FULL_DAY") BigDecimal("8.0") else BigDecimal("4.0")
            val portionEnum = runCatching { LeaveApplicationDayItem.Portion.valueOf(dayPortion) }.getOrDefault(LeaveApplicationDayItem.Portion.FULL_DAY)

            var workingDays = BigDecimal.ZERO
            val list = mutableListOf<LeaveApplicationDayItem>()
            var curr = startDate

            while (!curr.isAfter(endDate)) {
                val isWeekend = curr.dayOfWeek == DayOfWeek.SATURDAY || curr.dayOfWeek == DayOfWeek.SUNDAY
                val isWorking = !isWeekend
                if (isWorking) {
                    workingDays += portionWeight
                }
                list.add(
                    LeaveApplicationDayItem(
                        date = curr,
                        dayOfWeek = curr.dayOfWeek.name,
                        isWorkingDay = isWorking,
                        isPublicHoliday = false,
                        holidayName = null,
                        portion = portionEnum,
                        hours = if (isWorking) portionHours else BigDecimal.ZERO,
                    )
                )
                curr = curr.plusDays(1)
            }

            return Pair(workingDays, list)
        }

        private fun createOfflineDefaultBalances(): List<LeaveBalanceItem> =
            listOf(
                LeaveBalanceItem(
                    leaveTypeId = "annual-id",
                    leaveTypeCode = "ANNUAL",
                    leaveTypeName = "Annual Leave",
                    color = "#0284c7",
                    entitledDays = BigDecimal("14.0"),
                    accruedDays = BigDecimal("14.0"),
                    takenDays = BigDecimal("3.0"),
                    pendingDays = BigDecimal.ZERO,
                    availableDays = BigDecimal("11.0"),
                ),
                LeaveBalanceItem(
                    leaveTypeId = "casual-id",
                    leaveTypeCode = "CASUAL",
                    leaveTypeName = "Casual Leave",
                    color = "#16a34a",
                    entitledDays = BigDecimal("7.0"),
                    accruedDays = BigDecimal("7.0"),
                    takenDays = BigDecimal("2.0"),
                    pendingDays = BigDecimal.ZERO,
                    availableDays = BigDecimal("5.0"),
                ),
                LeaveBalanceItem(
                    leaveTypeId = "medical-id",
                    leaveTypeCode = "MEDICAL",
                    leaveTypeName = "Medical Leave",
                    color = "#d97706",
                    entitledDays = BigDecimal("14.0"),
                    accruedDays = BigDecimal("14.0"),
                    takenDays = BigDecimal("1.0"),
                    pendingDays = BigDecimal.ZERO,
                    availableDays = BigDecimal("13.0"),
                ),
            )

        private fun createOfflineDefaultApplications(): List<LeaveApplicationItem> =
            listOf(
                LeaveApplicationItem(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000101"),
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    employeeName = "Kasun Perera",
                    leaveTypeId = "annual-id",
                    leaveTypeCode = "ANNUAL",
                    leaveTypeName = "Annual Leave",
                    startDate = LocalDate.now().minusDays(15),
                    endDate = LocalDate.now().minusDays(14),
                    dayPortion = LeaveApplicationItem.DayPortion.FULL_DAY,
                    totalDays = BigDecimal("2.0"),
                    reason = "Family vacation to Nuwara Eliya",
                    status = LeaveApplicationItem.Status.APPROVED,
                    submittedAt = OffsetDateTime.now().minusDays(20),
                    approvedAt = OffsetDateTime.now().minusDays(18),
                    days = emptyList(),
                ),
                LeaveApplicationItem(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000102"),
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    employeeName = "Kasun Perera",
                    leaveTypeId = "casual-id",
                    leaveTypeCode = "CASUAL",
                    leaveTypeName = "Casual Leave",
                    startDate = LocalDate.now().plusDays(5),
                    endDate = LocalDate.now().plusDays(5),
                    dayPortion = LeaveApplicationItem.DayPortion.FULL_DAY,
                    totalDays = BigDecimal("1.0"),
                    reason = "Home bank paperwork",
                    status = LeaveApplicationItem.Status.SUBMITTED,
                    submittedAt = OffsetDateTime.now().minusDays(2),
                    approvedAt = null,
                    days = emptyList(),
                ),
            )

        private fun createOfflineDefaultLedger(): List<LeaveLedgerEntryItem> =
            listOf(
                LeaveLedgerEntryItem(
                    id = "ledger-1",
                    date = LocalDate.of(2026, 1, 1),
                    leaveTypeId = "annual-id",
                    leaveTypeCode = "ANNUAL",
                    leaveTypeName = "Annual Leave",
                    eventType = LeaveLedgerEntryItem.EventType.OPENING,
                    daysCredited = BigDecimal("14.0"),
                    daysDebited = BigDecimal.ZERO,
                    balanceAfter = BigDecimal("14.0"),
                    referenceId = "2026",
                    notes = "Annual upfront entitlement 2026",
                ),
                LeaveLedgerEntryItem(
                    id = "ledger-2",
                    date = LocalDate.of(2026, 1, 1),
                    leaveTypeId = "casual-id",
                    leaveTypeCode = "CASUAL",
                    leaveTypeName = "Casual Leave",
                    eventType = LeaveLedgerEntryItem.EventType.OPENING,
                    daysCredited = BigDecimal("7.0"),
                    daysDebited = BigDecimal.ZERO,
                    balanceAfter = BigDecimal("7.0"),
                    referenceId = "2026",
                    notes = "Casual upfront entitlement 2026",
                ),
                LeaveLedgerEntryItem(
                    id = "ledger-3",
                    date = LocalDate.now().minusDays(15),
                    leaveTypeId = "annual-id",
                    leaveTypeCode = "ANNUAL",
                    leaveTypeName = "Annual Leave",
                    eventType = LeaveLedgerEntryItem.EventType.TAKEN,
                    daysCredited = BigDecimal.ZERO,
                    daysDebited = BigDecimal("2.0"),
                    balanceAfter = BigDecimal("12.0"),
                    referenceId = "APP-101",
                    notes = "Approved leave debit",
                ),
            )
    }
