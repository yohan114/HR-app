package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.AssignShiftPayload
import com.hr.client.model.AttendancePayrollSummaryResponse
import com.hr.client.model.AttendancePunchRequest
import com.hr.client.model.AttendancePunchResponse
import com.hr.client.model.AttendanceRegulariseRequest
import com.hr.client.model.AttendanceRegulariseResponse
import com.hr.client.model.BiometricDeviceItem
import com.hr.client.model.BiometricDeviceListResponse
import com.hr.client.model.BiometricLiveStreamResponse
import com.hr.client.model.CreateBiometricDevicePayload
import com.hr.client.model.DailyAttendanceAdmin
import com.hr.client.model.DailyAttendanceListResponse
import com.hr.client.model.HardwarePunchBatchPayload
import com.hr.client.model.HardwarePunchItem
import com.hr.client.model.HardwarePunchResult
import com.hr.client.model.IngestPunchAdminPayload
import com.hr.client.model.RawPunchAdmin
import com.hr.client.model.RawPunchListResponse
import com.hr.client.model.RecomputeAttendancePayload
import com.hr.client.model.RecomputeAttendanceResponse
import com.hr.client.model.RosterResponse
import com.hr.client.model.ShiftListResponse
import com.hr.client.model.ShiftRosterResponse
import com.hr.client.model.ShiftScheduleItem
import com.hr.client.model.ShiftSwapRequest
import com.hr.client.model.ShiftSwapResponse
import com.hr.client.model.TeamCoverageResponse
import com.hr.client.model.TodayAttendanceResponse
import com.hr.client.model.UpdateBiometricDevicePayload

interface AttendanceApi {
    /**
     * POST v1/attendance/shifts/assign
     * Assign or swap an employee shift
     * Assigns a specific shift or marks a rest day for an employee on a work date.
     * Responses:
     *  - 200: Shift assignment saved
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param assignShiftPayload 
     * @return [ShiftScheduleItem]
     */
    @POST("v1/attendance/shifts/assign")
    suspend fun assignShift(@Body assignShiftPayload: AssignShiftPayload): Response<ShiftScheduleItem>

    /**
     * POST v1/attendance/devices
     * Register a new biometric attendance terminal
     * Adds a physical time-clock device configuration to the tenant.
     * Responses:
     *  - 201: Device registered
     *  - 401: Authentication required or token invalid
     *
     * @param createBiometricDevicePayload 
     * @return [BiometricDeviceItem]
     */
    @POST("v1/attendance/devices")
    suspend fun createBiometricDevice(@Body createBiometricDevicePayload: CreateBiometricDevicePayload): Response<BiometricDeviceItem>

    /**
     * DELETE v1/attendance/devices/{id}
     * Delete biometric terminal
     * De-registers a biometric hardware terminal from the tenant.
     * Responses:
     *  - 204: Device deleted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [Unit]
     */
    @DELETE("v1/attendance/devices/{id}")
    suspend fun deleteBiometricDevice(@Path("id") id: java.util.UUID): Response<Unit>

    /**
     * GET v1/attendance/devices/{id}
     * Get biometric terminal details
     * Returns details of a registered biometric hardware device.
     * Responses:
     *  - 200: Biometric device details
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [BiometricDeviceItem]
     */
    @GET("v1/attendance/devices/{id}")
    suspend fun getBiometricDevice(@Path("id") id: java.util.UUID): Response<BiometricDeviceItem>

    /**
     * GET v1/attendance/devices/live-stream
     * Live feed of biometric terminal punches
     * Returns real-time stream of punches ingested from biometric devices with employee and location metadata.
     * Responses:
     *  - 200: Live stream of punches
     *  - 401: Authentication required or token invalid
     *
     * @param limit  (optional, default to 20)
     * @return [BiometricLiveStreamResponse]
     */
    @GET("v1/attendance/devices/live-stream")
    suspend fun getBiometricLiveStream(@Query("limit") limit: kotlin.Int? = 20): Response<BiometricLiveStreamResponse>

    /**
     * GET v1/attendance/daily/{id}
     * Get daily attendance record details with formula trace
     * Retrieves a single daily attendance record including detailed calculation audit trace.
     * Responses:
     *  - 200: Daily attendance detail
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [DailyAttendanceAdmin]
     */
    @GET("v1/attendance/daily/{id}")
    suspend fun getDailyAttendanceDetails(@Path("id") id: java.util.UUID): Response<DailyAttendanceAdmin>

    /**
     * GET v1/attendance/me/roster
     * Shift roster schedule for authenticated employee
     * Returns the caller&#39;s rostered work shifts, rest days, and holidays across a date window. 
     * Responses:
     *  - 200: Shift roster schedule
     *  - 401: Authentication required or token invalid
     *
     * @param startDate  (optional)
     * @param endDate  (optional)
     * @return [ShiftRosterResponse]
     */
    @GET("v1/attendance/me/roster")
    suspend fun getMyShiftRoster(@Query("startDate") startDate: java.time.LocalDate? = null, @Query("endDate") endDate: java.time.LocalDate? = null): Response<ShiftRosterResponse>

    /**
     * GET v1/attendance/me/today
     * Today&#39;s attendance status and shift rules for authenticated employee
     * Returns the caller&#39;s active shift schedule for today, morning check-in grace limits, current punch status, punch timeline, and branch geofence boundary coordinates. 
     * Responses:
     *  - 200: Today's attendance status
     *  - 401: Authentication required or token invalid
     *
     * @return [TodayAttendanceResponse]
     */
    @GET("v1/attendance/me/today")
    suspend fun getMyTodayAttendance(): Response<TodayAttendanceResponse>

    /**
     * GET v1/attendance/payroll-variable-summary
     * Retrieve variable payroll inputs summary
     * Aggregates overtime hours, late deductions, and unpaid leave days for payroll period.
     * Responses:
     *  - 200: Variable payroll summary
     *  - 401: Authentication required or token invalid
     *
     * @param payPeriodId  (optional)
     * @return [AttendancePayrollSummaryResponse]
     */
    @GET("v1/attendance/payroll-variable-summary")
    suspend fun getPayrollVariableSummary(@Query("payPeriodId") payPeriodId: kotlin.String? = null): Response<AttendancePayrollSummaryResponse>

    /**
     * GET v1/attendance/roster
     * Retrieve workforce shift roster matrix
     * Retrieves roster schedules and employee assignments across date range and departments.
     * Responses:
     *  - 200: Roster schedule matrix
     *  - 401: Authentication required or token invalid
     *
     * @param startDate  (optional)
     * @param endDate  (optional)
     * @param department  (optional)
     * @return [RosterResponse]
     */
    @GET("v1/attendance/roster")
    suspend fun getRoster(@Query("startDate") startDate: java.time.LocalDate? = null, @Query("endDate") endDate: java.time.LocalDate? = null, @Query("department") department: kotlin.String? = null): Response<RosterResponse>

    /**
     * GET v1/attendance/team/coverage
     * Team shift coverage for a selected work date
     * Returns scheduled and active team members on a specified date with shift codes, roles, and duty status. 
     * Responses:
     *  - 200: Team coverage roster
     *  - 401: Authentication required or token invalid
     *
     * @param date 
     * @return [TeamCoverageResponse]
     */
    @GET("v1/attendance/team/coverage")
    suspend fun getTeamShiftCoverage(@Query("date") date: java.time.LocalDate): Response<TeamCoverageResponse>

    /**
     * POST v1/attendance/punches
     * Ingest a clock punch from mobile or biometric source
     * Records a clock-in, clock-out, or break punch. Accepts single-use Idempotency-Key header for reliable offline outbox retry. Evaluates branch geofence radius and flags mock GPS providers. 
     * Responses:
     *  - 201: Punch recorded
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param attendancePunchRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [AttendancePunchResponse]
     */
    @POST("v1/attendance/punches")
    suspend fun ingestAttendancePunch(@Body attendancePunchRequest: AttendancePunchRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<AttendancePunchResponse>

    /**
     * POST v1/attendance/punches/ingest
     * Ingest punch via web admin
     * Ingests a clock punch from administrator console with employee assignment.
     * Responses:
     *  - 201: Punch ingested
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param ingestPunchAdminPayload 
     * @return [RawPunchAdmin]
     */
    @POST("v1/attendance/punches/ingest")
    suspend fun ingestPunchAdmin(@Body ingestPunchAdminPayload: IngestPunchAdminPayload): Response<RawPunchAdmin>

    /**
     * GET v1/attendance/devices
     * List registered biometric hardware terminals
     * Returns all biometric devices (ZKTeco, Hikvision, Suprema, Anviz) registered for this tenant.
     * Responses:
     *  - 200: Biometric devices list
     *  - 401: Authentication required or token invalid
     *
     * @return [BiometricDeviceListResponse]
     */
    @GET("v1/attendance/devices")
    suspend fun listBiometricDevices(): Response<BiometricDeviceListResponse>

    /**
     * GET v1/attendance/daily
     * List calculated daily attendance rollups
     * Retrieves daily attendance calculations with worked hours, OT, and calculation traces.
     * Responses:
     *  - 200: Daily attendance records and summary metrics
     *  - 401: Authentication required or token invalid
     *
     * @param date  (optional)
     * @param department  (optional)
     * @param status  (optional)
     * @param anomaly  (optional)
     * @return [DailyAttendanceListResponse]
     */
    @GET("v1/attendance/daily")
    suspend fun listDailyAttendance(@Query("date") date: java.time.LocalDate? = null, @Query("department") department: kotlin.String? = null, @Query("status") status: kotlin.String? = null, @Query("anomaly") anomaly: kotlin.String? = null): Response<DailyAttendanceListResponse>

    /**
     * GET v1/attendance/punches
     * List raw clock punches with filters
     * Retrieves raw punch logs across tenant filtered by date, employee, source, or anomaly flag.
     * Responses:
     *  - 200: Raw punch list
     *  - 401: Authentication required or token invalid
     *
     * @param date  (optional)
     * @param employeeId  (optional)
     * @param flag  (optional)
     * @param source  (optional)
     * @return [RawPunchListResponse]
     */
    @GET("v1/attendance/punches")
    suspend fun listPunches(@Query("date") date: java.time.LocalDate? = null, @Query("employeeId") employeeId: java.util.UUID? = null, @Query("flag") flag: kotlin.String? = null, @Query("source") source: kotlin.String? = null): Response<RawPunchListResponse>

    /**
     * GET v1/attendance/shifts
     * List configured work shifts
     * Retrieves active work shift templates with timing and grace rules.
     * Responses:
     *  - 200: Shift template list
     *  - 401: Authentication required or token invalid
     *
     * @return [ShiftListResponse]
     */
    @GET("v1/attendance/shifts")
    suspend fun listShifts(): Response<ShiftListResponse>

    /**
     * POST v1/attendance/devices/{id}/ping
     * Ping biometric terminal
     * Tests connectivity and updates heartbeat for a registered biometric device.
     * Responses:
     *  - 200: Device ping result
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [BiometricDeviceItem]
     */
    @POST("v1/attendance/devices/{id}/ping")
    suspend fun pingBiometricDevice(@Path("id") id: java.util.UUID): Response<BiometricDeviceItem>

    /**
     * POST v1/attendance/recompute
     * Recompute attendance calculations
     * Triggers attendance pairing, OT tiering, and lateness penalties calculation for date or employee.
     * Responses:
     *  - 200: Recomputation completed
     *  - 401: Authentication required or token invalid
     *
     * @param recomputeAttendancePayload  (optional)
     * @return [RecomputeAttendanceResponse]
     */
    @POST("v1/attendance/recompute")
    suspend fun recomputeAttendance(@Body recomputeAttendancePayload: RecomputeAttendancePayload? = null): Response<RecomputeAttendanceResponse>

    /**
     * POST v1/attendance/me/regularise
     * Request attendance punch regularisation
     * Submits an attendance adjustment/regularisation request for missed punches to manager approval inbox. 
     * Responses:
     *  - 200: Regularisation request submitted
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param attendanceRegulariseRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [AttendanceRegulariseResponse]
     */
    @POST("v1/attendance/me/regularise")
    suspend fun requestAttendanceRegularisation(@Body attendanceRegulariseRequest: AttendanceRegulariseRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<AttendanceRegulariseResponse>

    /**
     * POST v1/attendance/roster/swap-request
     * Submit a shift swap request
     * Initiates a shift swap request with another team member. Supports Idempotency-Key for offline outbox sync. 
     * Responses:
     *  - 200: Shift swap request submitted
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param shiftSwapRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [ShiftSwapResponse]
     */
    @POST("v1/attendance/roster/swap-request")
    suspend fun requestShiftSwap(@Body shiftSwapRequest: ShiftSwapRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<ShiftSwapResponse>

    /**
     * POST v1/attendance/devices/simulate-punch
     * Simulate a hardware biometric terminal punch
     * Simulates an incoming biometric clock transaction for testing and manual validation.
     * Responses:
     *  - 200: Simulation result
     *  - 401: Authentication required or token invalid
     *
     * @param hardwarePunchItem 
     * @return [HardwarePunchResult]
     */
    @POST("v1/attendance/devices/simulate-punch")
    suspend fun simulateBiometricHardwarePunch(@Body hardwarePunchItem: HardwarePunchItem): Response<HardwarePunchResult>

    /**
     * POST v1/attendance/devices/sync
     * Batch ingest hardware biometric punches
     * Ingests batches of punch transactions pushed from edge IoT controllers or biometric middleware.
     * Responses:
     *  - 200: Batch ingestion results
     *  - 401: Authentication required or token invalid
     *
     * @param hardwarePunchBatchPayload 
     * @return [HardwarePunchResult]
     */
    @POST("v1/attendance/devices/sync")
    suspend fun syncBatchBiometricPunches(@Body hardwarePunchBatchPayload: HardwarePunchBatchPayload): Response<HardwarePunchResult>

    /**
     * PUT v1/attendance/devices/{id}
     * Update biometric terminal settings
     * Modifies network, location, or direction settings for a biometric device.
     * Responses:
     *  - 200: Device updated
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param updateBiometricDevicePayload 
     * @return [BiometricDeviceItem]
     */
    @PUT("v1/attendance/devices/{id}")
    suspend fun updateBiometricDevice(@Path("id") id: java.util.UUID, @Body updateBiometricDevicePayload: UpdateBiometricDevicePayload): Response<BiometricDeviceItem>

    /**
     * POST iclock/cdata
     * ZKTeco ADMS attendance log push
     * Ingests raw attendance log transactions pushed by ZKTeco biometric terminals.
     * Responses:
     *  - 200: Acknowledgment response (OK or OK count)
     *
     * @param SN  (optional)
     * @param table  (optional)
     * @param body  (optional)
     * @return [kotlin.String]
     */
    @POST("iclock/cdata")
    suspend fun zkTecoAttendanceLogPush(@Query("SN") SN: kotlin.String? = null, @Query("table") table: kotlin.String? = null, @Body body: kotlin.String? = null): Response<kotlin.String>

    /**
     * POST iclock/devicecmd
     * ZKTeco ADMS device command acknowledgment
     * Terminal feedback reporting execution status of previously dispatched commands.
     * Responses:
     *  - 200: Acknowledgment response (OK)
     *
     * @param SN  (optional)
     * @param body  (optional)
     * @return [kotlin.String]
     */
    @POST("iclock/devicecmd")
    suspend fun zkTecoDeviceCommandAck(@Query("SN") SN: kotlin.String? = null, @Body body: kotlin.String? = null): Response<kotlin.String>

    /**
     * GET iclock/getrequest
     * ZKTeco ADMS terminal heartbeat polling
     * Periodic polling from ZKTeco terminal to check-in and retrieve pending commands.
     * Responses:
     *  - 200: Command delivery or OK
     *
     * @param SN  (optional)
     * @return [kotlin.String]
     */
    @GET("iclock/getrequest")
    suspend fun zkTecoGetRequest(@Query("SN") SN: kotlin.String? = null): Response<kotlin.String>

    /**
     * GET iclock/cdata
     * ZKTeco ADMS terminal handshake
     * Handles initial handshake and option negotiation for ZKTeco ADMS push protocol.
     * Responses:
     *  - 200: ZKTeco server configuration options
     *
     * @param SN  (optional)
     * @param options  (optional)
     * @return [kotlin.String]
     */
    @GET("iclock/cdata")
    suspend fun zkTecoHandshake(@Query("SN") SN: kotlin.String? = null, @Query("options") options: kotlin.String? = null): Response<kotlin.String>

}
