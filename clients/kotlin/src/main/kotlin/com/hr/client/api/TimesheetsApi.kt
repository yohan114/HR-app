package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.TimesheetActivityListResponse
import com.hr.client.model.TimesheetApproveRequest
import com.hr.client.model.TimesheetClientListResponse
import com.hr.client.model.TimesheetCopyPreviousRequest
import com.hr.client.model.TimesheetDetailResponse
import com.hr.client.model.TimesheetListResponse
import com.hr.client.model.TimesheetProjectCreateRequest
import com.hr.client.model.TimesheetProjectItem
import com.hr.client.model.TimesheetProjectListResponse
import com.hr.client.model.TimesheetReconciliationResponse
import com.hr.client.model.TimesheetRejectRequest
import com.hr.client.model.TimesheetSaveRequest

interface TimesheetsApi {
    /**
     * POST v1/timesheets/{id}/approve
     * Approve submitted timesheet
     * Approves a submitted timesheet and locks entries for billing.
     * Responses:
     *  - 200: Approved timesheet
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param timesheetApproveRequest  (optional)
     * @return [TimesheetDetailResponse]
     */
    @POST("v1/timesheets/{id}/approve")
    suspend fun approveTimesheet(@Path("id") id: java.util.UUID, @Body timesheetApproveRequest: TimesheetApproveRequest? = null): Response<TimesheetDetailResponse>

    /**
     * POST v1/timesheets/copy-previous
     * Copy previous week timesheet rows
     * Pre-populates the current week draft with projects and activities from the prior week.
     * Responses:
     *  - 200: Pre-populated timesheet draft
     *  - 400: Malformed request, or a field value that could not be interpreted
     *
     * @param timesheetCopyPreviousRequest 
     * @return [TimesheetDetailResponse]
     */
    @POST("v1/timesheets/copy-previous")
    suspend fun copyPreviousWeekTimesheet(@Body timesheetCopyPreviousRequest: TimesheetCopyPreviousRequest): Response<TimesheetDetailResponse>

    /**
     * POST v1/timesheets/projects
     * Create timesheet project
     * Registers a new project under a client organisation.
     * Responses:
     *  - 201: Project created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param timesheetProjectCreateRequest 
     * @return [TimesheetProjectItem]
     */
    @POST("v1/timesheets/projects")
    suspend fun createTimesheetProject(@Body timesheetProjectCreateRequest: TimesheetProjectCreateRequest): Response<TimesheetProjectItem>

    /**
     * GET v1/timesheets/{id}
     * Get timesheet details
     * Retrieves a weekly timesheet including all daily project/activity entries.
     * Responses:
     *  - 200: Timesheet detail with entries
     *  - 404: Not found
     *
     * @param id 
     * @return [TimesheetDetailResponse]
     */
    @GET("v1/timesheets/{id}")
    suspend fun getTimesheetById(@Path("id") id: java.util.UUID): Response<TimesheetDetailResponse>

    /**
     * GET v1/timesheets/reconciliation
     * Reconcile timesheet with attendance
     * Compares logged project timesheet hours with clocked attendance hours for a given week.
     * Responses:
     *  - 200: Weekly attendance reconciliation summary
     *  - 400: Malformed request, or a field value that could not be interpreted
     *
     * @param weekStart 
     * @param employeeId  (optional)
     * @return [TimesheetReconciliationResponse]
     */
    @GET("v1/timesheets/reconciliation")
    suspend fun getTimesheetReconciliation(@Query("weekStart") weekStart: java.time.LocalDate, @Query("employeeId") employeeId: java.util.UUID? = null): Response<TimesheetReconciliationResponse>

    /**
     * GET v1/timesheets/activities
     * List global timesheet activities
     * Retrieves task types and activities configured across the organisation or for a project.
     * Responses:
     *  - 200: List of activities
     *  - 401: Authentication required or token invalid
     *
     * @param projectId  (optional)
     * @return [TimesheetActivityListResponse]
     */
    @GET("v1/timesheets/activities")
    suspend fun listGlobalTimesheetActivities(@Query("projectId") projectId: java.util.UUID? = null): Response<TimesheetActivityListResponse>

    /**
     * GET v1/timesheets/my
     * List employee timesheets
     * Retrieves weekly timesheet summaries for an employee.
     * Responses:
     *  - 200: List of timesheets
     *  - 401: Authentication required or token invalid
     *
     * @param employeeId  (optional)
     * @return [TimesheetListResponse]
     */
    @GET("v1/timesheets/my")
    suspend fun listMyTimesheets(@Query("employeeId") employeeId: java.util.UUID? = null): Response<TimesheetListResponse>

    /**
     * GET v1/timesheets/projects/{id}/activities
     * List activities for project
     * Retrieves task types and activities configured for a project.
     * Responses:
     *  - 200: List of activities
     *  - 404: Not found
     *
     * @param id 
     * @return [TimesheetActivityListResponse]
     */
    @GET("v1/timesheets/projects/{id}/activities")
    suspend fun listTimesheetActivities(@Path("id") id: java.util.UUID): Response<TimesheetActivityListResponse>

    /**
     * GET v1/timesheets/clients
     * List timesheet clients
     * Retrieves active client organisations for project and activity billing.
     * Responses:
     *  - 200: List of clients
     *  - 401: Authentication required or token invalid
     *
     * @return [TimesheetClientListResponse]
     */
    @GET("v1/timesheets/clients")
    suspend fun listTimesheetClients(): Response<TimesheetClientListResponse>

    /**
     * GET v1/timesheets/projects
     * List billable and internal projects
     * Retrieves projects filtered optionally by client identifier.
     * Responses:
     *  - 200: List of projects
     *  - 401: Authentication required or token invalid
     *
     * @param clientId  (optional)
     * @return [TimesheetProjectListResponse]
     */
    @GET("v1/timesheets/projects")
    suspend fun listTimesheetProjects(@Query("clientId") clientId: java.util.UUID? = null): Response<TimesheetProjectListResponse>

    /**
     * GET v1/timesheets
     * List all timesheets
     * Retrieves submitted and draft timesheets across the organisation for review.
     * Responses:
     *  - 200: List of timesheets
     *  - 401: Authentication required or token invalid
     *
     * @param employeeId  (optional)
     * @param status  (optional)
     * @return [TimesheetListResponse]
     */
    @GET("v1/timesheets")
    suspend fun listTimesheets(@Query("employeeId") employeeId: java.util.UUID? = null, @Query("status") status: kotlin.String? = null): Response<TimesheetListResponse>

    /**
     * POST v1/timesheets/{id}/reject
     * Reject submitted timesheet
     * Rejects a submitted timesheet with an explanation for corrections.
     * Responses:
     *  - 200: Rejected timesheet
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param timesheetRejectRequest 
     * @return [TimesheetDetailResponse]
     */
    @POST("v1/timesheets/{id}/reject")
    suspend fun rejectTimesheet(@Path("id") id: java.util.UUID, @Body timesheetRejectRequest: TimesheetRejectRequest): Response<TimesheetDetailResponse>

    /**
     * POST v1/timesheets
     * Save timesheet draft
     * Creates or updates a weekly timesheet draft with daily time entries.
     * Responses:
     *  - 200: Saved timesheet draft
     *  - 400: Malformed request, or a field value that could not be interpreted
     *
     * @param timesheetSaveRequest 
     * @return [TimesheetDetailResponse]
     */
    @POST("v1/timesheets")
    suspend fun saveTimesheetDraft(@Body timesheetSaveRequest: TimesheetSaveRequest): Response<TimesheetDetailResponse>

    /**
     * POST v1/timesheets/{id}/submit
     * Submit timesheet for approval
     * Transitions timesheet from DRAFT to SUBMITTED status for manager review.
     * Responses:
     *  - 200: Submitted timesheet
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @return [TimesheetDetailResponse]
     */
    @POST("v1/timesheets/{id}/submit")
    suspend fun submitTimesheet(@Path("id") id: java.util.UUID): Response<TimesheetDetailResponse>

}
