package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.CancelLeaveRequest
import com.hr.client.model.LeaveApplicationItem
import com.hr.client.model.LeaveApplicationRequest
import com.hr.client.model.LeaveApplicationsResponse
import com.hr.client.model.LeaveBalancesResponse
import com.hr.client.model.LeaveEligibilityRequest
import com.hr.client.model.LeaveEligibilityResponse
import com.hr.client.model.LeaveLedgerResponse
import com.hr.client.model.TeamCalendarResponse

interface LeaveApi {
    /**
     * POST v1/leave/applications/{id}/cancel
     * Cancel a leave application
     * Cancels a pending or approved leave application and restores ledger debits.
     * Responses:
     *  - 200: Application cancelled
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param cancelLeaveRequest  (optional)
     * @return [LeaveApplicationItem]
     */
    @POST("v1/leave/applications/{id}/cancel")
    suspend fun cancelLeaveApplication(@Path("id") id: java.util.UUID, @Body cancelLeaveRequest: CancelLeaveRequest? = null): Response<LeaveApplicationItem>

    /**
     * POST v1/leave/eligibility
     * Check leave application eligibility
     * Pre-validates a requested date range, expands working days skipping weekends and holidays, and returns balance projection.
     * Responses:
     *  - 200: Eligibility evaluated
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param leaveEligibilityRequest 
     * @return [LeaveEligibilityResponse]
     */
    @POST("v1/leave/eligibility")
    suspend fun checkLeaveEligibility(@Body leaveEligibilityRequest: LeaveEligibilityRequest): Response<LeaveEligibilityResponse>

    /**
     * GET v1/leave/applications
     * Retrieve employee leave applications
     * Returns list of submitted leave requests for the authenticated employee.
     * Responses:
     *  - 200: Leave applications retrieved
     *  - 401: Authentication required or token invalid
     *
     * @param status  (optional)
     * @return [LeaveApplicationsResponse]
     */
    @GET("v1/leave/applications")
    suspend fun getMyLeaveApplications(@Query("status") status: kotlin.String? = null): Response<LeaveApplicationsResponse>

    /**
     * GET v1/leave/balances
     * Retrieve current employee leave balances
     * Returns active leave balances with entitled, accrued, taken, pending, and available days.
     * Responses:
     *  - 200: Leave balances retrieved
     *  - 401: Authentication required or token invalid
     *
     * @return [LeaveBalancesResponse]
     */
    @GET("v1/leave/balances")
    suspend fun getMyLeaveBalances(): Response<LeaveBalancesResponse>

    /**
     * GET v1/leave/ledger
     * Retrieve leave ledger statement
     * Retrieves the immutable audit ledger entries explaining balance movements.
     * Responses:
     *  - 200: Leave ledger statement entries retrieved
     *  - 401: Authentication required or token invalid
     *
     * @param leaveTypeId  (optional)
     * @return [LeaveLedgerResponse]
     */
    @GET("v1/leave/ledger")
    suspend fun getMyLeaveLedger(@Query("leaveTypeId") leaveTypeId: kotlin.String? = null): Response<LeaveLedgerResponse>

    /**
     * GET v1/leave/team-calendar
     * Retrieve team leave calendar
     * Retrieves daily absences and public holidays for a given month and year.
     * Responses:
     *  - 200: Team leave calendar retrieved
     *  - 401: Authentication required or token invalid
     *
     * @param year 
     * @param month 
     * @return [TeamCalendarResponse]
     */
    @GET("v1/leave/team-calendar")
    suspend fun getTeamCalendar(@Query("year") year: kotlin.Int, @Query("month") month: kotlin.Int): Response<TeamCalendarResponse>

    /**
     * POST v1/leave/applications
     * Submit a leave application
     * Submits a new leave request and triggers the approval workflow.
     * Responses:
     *  - 201: Leave application created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param leaveApplicationRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [LeaveApplicationItem]
     */
    @POST("v1/leave/applications")
    suspend fun submitLeaveApplication(@Body leaveApplicationRequest: LeaveApplicationRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<LeaveApplicationItem>

}
