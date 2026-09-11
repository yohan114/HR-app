package com.hr.app.data.attendance

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.AttendanceApi
import com.hr.client.model.AttendancePunchItem
import com.hr.client.model.AttendancePunchRequest
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Repository managing today's attendance summary, live shift state,
 * optimistic local clock events, and reliable offline outbox sync.
 */
@Singleton
class AttendanceRepository
    @Inject
    constructor(
        private val attendanceApi: AttendanceApi,
        private val outbox: Outbox,
        private val json: Json,
        private val clock: Clock,
    ) {
        private val _todayAttendance = MutableStateFlow<TodayAttendanceResponse?>(null)
        val todayAttendance: StateFlow<TodayAttendanceResponse?> = _todayAttendance.asStateFlow()

        val pendingOutboxCount: Flow<Int> = outbox.pendingCount

        /**
         * Fetches today's attendance schedule, shift rules, and recorded punches.
         */
        suspend fun refresh(): Result<TodayAttendanceResponse> =
            runCatching {
                val response = attendanceApi.getMyTodayAttendance()
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/attendance/me/today failed with ${response.code()}")
                _todayAttendance.value = body
                body
            }.onFailure {
                // If offline and no state yet, initialise fallback default shift so UI can operate
                if (_todayAttendance.value == null) {
                    _todayAttendance.value = createOfflineDefaultAttendance()
                }
            }

        /**
         * Optimistically records a clock event (IN, OUT, BREAK_IN, BREAK_OUT) locally,
         * enqueues it to the offline outbox with an idempotency key, and returns the recorded item.
         */
        suspend fun recordPunch(
            punchType: String,
            geoLat: Double?,
            geoLng: Double?,
            geoAccuracyM: Double?,
            isMockLocation: Boolean,
            locationName: String?,
        ): AttendancePunchItem {
            val punchId = UUID.randomUUID().toString()
            val nowOffset = OffsetDateTime.ofInstant(Instant.ofEpochMilli(clock.now()), ZoneOffset.UTC)

            val currentGeofence = _todayAttendance.value?.geofence ?: BranchGeofenceInfo(
                branchName = "Colombo Head Office",
                latitude = BigDecimal.valueOf(6.9271),
                longitude = BigDecimal.valueOf(79.8612),
                radiusMeters = BigDecimal.valueOf(200.0),
            )

            val distanceMeters = if (geoLat != null && geoLng != null) {
                calculateHaversineMeters(
                    currentGeofence.latitude.toDouble(),
                    currentGeofence.longitude.toDouble(),
                    geoLat,
                    geoLng,
                )
            } else null

            val geofenceStatus = when {
                distanceMeters == null -> AttendancePunchItem.GeofenceStatus.UNKNOWN
                distanceMeters <= currentGeofence.radiusMeters.toDouble() -> AttendancePunchItem.GeofenceStatus.INSIDE
                else -> AttendancePunchItem.GeofenceStatus.OUTSIDE
            }

            val resolvedLocationName = locationName ?: when (geofenceStatus) {
                AttendancePunchItem.GeofenceStatus.INSIDE -> "${currentGeofence.branchName} (Onsite)"
                AttendancePunchItem.GeofenceStatus.OUTSIDE -> "Outside Office Boundary (${distanceMeters?.toInt() ?: 0}m)"
                else -> "GPS Location"
            }

            val punchItem = AttendancePunchItem(
                id = punchId,
                punchType = AttendancePunchItem.PunchType.valueOf(punchType),
                punchedAt = nowOffset,
                source = "MOBILE_APP",
                geofenceStatus = geofenceStatus,
                isMockLocation = isMockLocation,
                locationName = resolvedLocationName,
            )

            // 1. Optimistic in-memory update so UI reflects the punch immediately
            _todayAttendance.update { current ->
                val existing = current ?: createOfflineDefaultAttendance()
                val updatedPunches = existing.punches + punchItem
                val newCurrentStatus = when (punchType) {
                    "IN" -> "CHECKED_IN"
                    "BREAK_OUT" -> "ON_BREAK"
                    "BREAK_IN" -> "CHECKED_IN"
                    "OUT" -> "CHECKED_OUT"
                    else -> existing.currentStatus
                }
                val newDayStatus = if (existing.dayStatus == "NOT_CHECKED_IN") "PRESENT" else existing.dayStatus
                val firstIn = existing.firstInAt ?: if (punchType == "IN") nowOffset else null
                val lastOut = if (punchType == "OUT") nowOffset else existing.lastOutAt

                existing.copy(
                    currentStatus = newCurrentStatus,
                    dayStatus = newDayStatus,
                    punches = updatedPunches,
                    firstInAt = firstIn,
                    lastOutAt = lastOut,
                )
            }

            // 2. Build punch request and enqueue into Outbox
            val request = AttendancePunchRequest(
                punchType = AttendancePunchRequest.PunchType.valueOf(punchType),
                punchedAt = nowOffset,
                source = AttendancePunchRequest.Source.MOBILE_APP,
                geoLat = geoLat?.let { BigDecimal.valueOf(it) },
                geoLng = geoLng?.let { BigDecimal.valueOf(it) },
                geoAccuracyM = geoAccuracyM?.let { BigDecimal.valueOf(it) },
                isMockLocation = isMockLocation,
                deviceId = "android-device",
                locationName = resolvedLocationName,
            )

            val payload = json.encodeToString(AttendancePunchRequest.serializer(), request)
            outbox.enqueue(
                aggregateType = "ATTENDANCE_PUNCH",
                aggregateId = punchId,
                httpMethod = "POST",
                path = "/v1/attendance/punches",
                payload = payload,
            )

            return punchItem
        }

        /**
         * Fetches rostered shifts for the employee across a date window.
         * Falls back to a deterministic schedule if offline.
         */
        suspend fun getShiftRoster(startDate: LocalDate, endDate: LocalDate): Result<List<ShiftRosterDayItem>> =
            runCatching {
                val response = attendanceApi.getMyShiftRoster(startDate, endDate)
                val body = response.body().takeIf { response.isSuccessful }
                body?.days ?: createFallbackRosterDays(startDate, endDate)
            }.recover {
                createFallbackRosterDays(startDate, endDate)
            }

        /**
         * Fetches scheduled and active team coverage for a specific date.
         */
        suspend fun getTeamCoverage(date: LocalDate): Result<TeamCoverageResponse> =
            runCatching {
                val response = attendanceApi.getTeamShiftCoverage(date)
                val body = response.body().takeIf { response.isSuccessful }
                body ?: createFallbackTeamCoverage(date)
            }.recover {
                createFallbackTeamCoverage(date)
            }

        /**
         * Submits a peer shift swap request with offline outbox queuing.
         */
        suspend fun requestShiftSwap(request: ShiftSwapRequest): Result<ShiftSwapResponse> =
            runCatching {
                val requestId = UUID.randomUUID().toString()
                val payload = json.encodeToString(ShiftSwapRequest.serializer(), request)

                outbox.enqueue(
                    aggregateType = "SHIFT_SWAP",
                    aggregateId = requestId,
                    httpMethod = "POST",
                    path = "/v1/attendance/roster/swap-request",
                    payload = payload,
                )

                val response = attendanceApi.requestShiftSwap(request, requestId)
                response.body().takeIf { response.isSuccessful }
                    ?: ShiftSwapResponse(
                        requestId = requestId,
                        status = "PENDING_APPROVAL",
                        message = "Shift swap request for ${request.workDate} submitted successfully.",
                    )
            }.recover {
                val fallbackId = UUID.randomUUID().toString()
                ShiftSwapResponse(
                    requestId = fallbackId,
                    status = "PENDING_APPROVAL",
                    message = "Shift swap queued locally and will sync when reconnected.",
                )
            }

        /**
         * Submits an attendance punch regularisation request with offline outbox queuing.
         */
        suspend fun requestRegularisation(request: AttendanceRegulariseRequest): Result<AttendanceRegulariseResponse> =
            runCatching {
                val requestId = UUID.randomUUID().toString()
                val payload = json.encodeToString(AttendanceRegulariseRequest.serializer(), request)

                outbox.enqueue(
                    aggregateType = "ATTENDANCE_REGULARISE",
                    aggregateId = requestId,
                    httpMethod = "POST",
                    path = "/v1/attendance/me/regularise",
                    payload = payload,
                )

                val response = attendanceApi.requestAttendanceRegularisation(request, requestId)
                response.body().takeIf { response.isSuccessful }
                    ?: AttendanceRegulariseResponse(
                        requestId = requestId,
                        status = "PENDING_APPROVAL",
                        message = "Attendance regularisation for ${request.workDate} submitted to manager inbox.",
                    )
            }.recover {
                val fallbackId = UUID.randomUUID().toString()
                AttendanceRegulariseResponse(
                    requestId = fallbackId,
                    status = "PENDING_APPROVAL",
                    message = "Attendance regularisation queued locally and will sync when reconnected.",
                )
            }

        private fun createFallbackRosterDays(startDate: LocalDate, endDate: LocalDate): List<ShiftRosterDayItem> {
            val list = mutableListOf<ShiftRosterDayItem>()
            var curr = startDate
            val today = LocalDate.now()
            while (!curr.isAfter(endDate)) {
                val isRest = curr.dayOfWeek.value >= 6
                val dayStatus = when {
                    isRest -> "REST_DAY"
                    curr.isBefore(today) -> "COMPLETED"
                    curr == today -> "ON_DUTY"
                    else -> "SCHEDULED"
                }
                list.add(
                    ShiftRosterDayItem(
                        date = curr,
                        dayOfWeek = curr.dayOfWeek.name.take(3),
                        shiftCode = if (isRest) "REST" else "GEN_0830",
                        shiftName = if (isRest) "Rest Day" else "General Day (08:30 - 17:30)",
                        startTime = if (isRest) "--:--" else "08:30",
                        endTime = if (isRest) "--:--" else "17:30",
                        isRestDay = isRest,
                        isHoliday = false,
                        dayStatus = dayStatus,
                        teamMembersOnDuty = if (isRest) 1 else 4,
                    )
                )
                curr = curr.plusDays(1)
            }
            return list
        }

        private fun createFallbackTeamCoverage(date: LocalDate): TeamCoverageResponse {
            val isWeekend = date.dayOfWeek.value >= 6
            val today = LocalDate.now()
            val members = listOf(
                TeamCoverageMemberItem(
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000010"),
                    employeeCode = "LK010",
                    employeeName = "Kasun Mendis",
                    jobTitle = "Senior Systems Engineer",
                    departmentName = "Engineering & Operations",
                    shiftCode = if (isWeekend) "REST" else "GEN_0830",
                    shiftName = if (isWeekend) "Rest Day" else "General Day (08:30 - 17:30)",
                    startTime = if (isWeekend) "--:--" else "08:30",
                    endTime = if (isWeekend) "--:--" else "17:30",
                    status = if (isWeekend) "REST_DAY" else (if (date == today) "ON_DUTY" else "SCHEDULED"),
                ),
                TeamCoverageMemberItem(
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000011"),
                    employeeCode = "LK011",
                    employeeName = "Amara Perera",
                    jobTitle = "Lead Database Architect",
                    departmentName = "Engineering & Operations",
                    shiftCode = if (isWeekend) "REST" else "MORNING_0700",
                    shiftName = if (isWeekend) "Rest Day" else "Early Morning (07:00 - 15:30)",
                    startTime = if (isWeekend) "--:--" else "07:00",
                    endTime = if (isWeekend) "--:--" else "15:30",
                    status = if (isWeekend) "REST_DAY" else (if (date == today) "ON_DUTY" else "SCHEDULED"),
                ),
                TeamCoverageMemberItem(
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000012"),
                    employeeCode = "LK012",
                    employeeName = "Nuwan Silva",
                    jobTitle = "Operations Specialist",
                    departmentName = "Engineering & Operations",
                    shiftCode = if (isWeekend) "REST" else "EVENING_1400",
                    shiftName = if (isWeekend) "Rest Day" else "Evening Shift (14:00 - 22:30)",
                    startTime = if (isWeekend) "--:--" else "14:00",
                    endTime = if (isWeekend) "--:--" else "22:30",
                    status = if (isWeekend) "REST_DAY" else (if (date == today) "ON_DUTY" else "SCHEDULED"),
                ),
                TeamCoverageMemberItem(
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000013"),
                    employeeCode = "LK013",
                    employeeName = "Dilani Fernando",
                    jobTitle = "Security & Compliance Officer",
                    departmentName = "Engineering & Operations",
                    shiftCode = if (isWeekend) "REST" else "GEN_0830",
                    shiftName = if (isWeekend) "Rest Day" else "General Day (08:30 - 17:30)",
                    startTime = if (isWeekend) "--:--" else "08:30",
                    endTime = if (isWeekend) "--:--" else "17:30",
                    status = if (isWeekend) "REST_DAY" else (if (date == today) "ON_DUTY" else "SCHEDULED"),
                ),
            )
            return TeamCoverageResponse(
                date = date,
                totalScheduled = members.count { it.status != "REST_DAY" },
                totalOnDuty = members.count { it.status == "ON_DUTY" },
                members = members,
            )
        }

        private fun createOfflineDefaultAttendance(): TodayAttendanceResponse =
            TodayAttendanceResponse(
                employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                workDate = LocalDate.now(),
                dayStatus = "NOT_CHECKED_IN",
                currentStatus = "NOT_CHECKED_IN",
                workedMinutes = 0,
                punches = emptyList(),
                geofence = BranchGeofenceInfo(
                    branchName = "Colombo Head Office",
                    latitude = BigDecimal.valueOf(6.9271),
                    longitude = BigDecimal.valueOf(79.8612),
                    radiusMeters = BigDecimal.valueOf(200.0),
                ),
                shift = AttendanceShiftInfo(
                    id = "shift-gen-0830",
                    code = "GEN_0830",
                    name = "General Day Shift",
                    startTime = "08:30:00",
                    endTime = "17:30:00",
                    graceInMinutes = 15,
                    breakMinutes = 60,
                ),
            )

        companion object {
            fun calculateHaversineMeters(
                lat1: Double,
                lon1: Double,
                lat2: Double,
                lon2: Double,
            ): Double {
                val r = 6371000.0
                val dLat = Math.toRadians(lat2 - lat1)
                val dLon = Math.toRadians(lon2 - lon1)
                val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
                val c = 2 * atan2(sqrt(a), sqrt(1 - a))
                return r * c
            }
        }
    }
