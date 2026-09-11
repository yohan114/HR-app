package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.ClearanceDetailResponse
import com.hr.client.model.ClearanceTaskItem
import com.hr.client.model.ClearanceTaskStatusUpdateRequest
import com.hr.client.model.ExitInterviewItem
import com.hr.client.model.ExitInterviewSubmitRequest
import com.hr.client.model.ExitNoticeApproveRequest
import com.hr.client.model.ExitNoticeCreateRequest
import com.hr.client.model.ExitNoticeItem
import com.hr.client.model.ExitNoticeListResponse
import com.hr.client.model.ExitTypeListResponse
import com.hr.client.model.RejectExitNoticeRequest

interface OffboardingApi {
    /**
     * POST v1/offboarding/exit-notices/{id}/approve
     * Approve exit notice
     * Approves resignation notice, finalizes last working date, and initializes departmental clearance tasks.
     * Responses:
     *  - 200: Exit notice approved
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param exitNoticeApproveRequest 
     * @return [ExitNoticeItem]
     */
    @POST("v1/offboarding/exit-notices/{id}/approve")
    suspend fun approveExitNotice(@Path("id") id: java.util.UUID, @Body exitNoticeApproveRequest: ExitNoticeApproveRequest): Response<ExitNoticeItem>

    /**
     * POST v1/offboarding/exit-notices
     * Submit resignation exit notice
     * Submits a formal resignation notice with requested last working date and reason.
     * Responses:
     *  - 201: Resignation notice submitted
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param exitNoticeCreateRequest 
     * @return [ExitNoticeItem]
     */
    @POST("v1/offboarding/exit-notices")
    suspend fun createExitNotice(@Body exitNoticeCreateRequest: ExitNoticeCreateRequest): Response<ExitNoticeItem>

    /**
     * GET v1/offboarding/exit-notices/{id}/interview
     * Get exit interview
     * Retrieves exit interview responses and ratings for an exit notice.
     * Responses:
     *  - 200: Exit interview detail
     *  - 404: Not found
     *
     * @param id 
     * @return [ExitInterviewItem]
     */
    @GET("v1/offboarding/exit-notices/{id}/interview")
    suspend fun getExitInterview(@Path("id") id: java.util.UUID): Response<ExitInterviewItem>

    /**
     * GET v1/offboarding/exit-notices/{id}/clearance
     * Get clearance checklist
     * Retrieves cross-departmental clearance items and financial dues recovery summary.
     * Responses:
     *  - 200: Clearance checklist detail
     *  - 404: Not found
     *
     * @param id 
     * @return [ClearanceDetailResponse]
     */
    @GET("v1/offboarding/exit-notices/{id}/clearance")
    suspend fun getExitNoticeClearance(@Path("id") id: java.util.UUID): Response<ClearanceDetailResponse>


    /**
    * enum for parameter status
    */
    enum class StatusGetExitNotices(val value: kotlin.String) {
        @SerialName(value = "DRAFT") DRAFT("DRAFT"),
        @SerialName(value = "SUBMITTED") SUBMITTED("SUBMITTED"),
        @SerialName(value = "APPROVED") APPROVED("APPROVED"),
        @SerialName(value = "REJECTED") REJECTED("REJECTED"),
        @SerialName(value = "WITHDRAWN") WITHDRAWN("WITHDRAWN"),
        @SerialName(value = "REVERSED") REVERSED("REVERSED")
    }

    /**
     * GET v1/offboarding/exit-notices
     * List exit notices
     * Returns employee resignation and termination notices filtered by employee or status.
     * Responses:
     *  - 200: Exit notice list
     *  - 401: Authentication required or token invalid
     *
     * @param employeeId  (optional)
     * @param status  (optional)
     * @return [ExitNoticeListResponse]
     */
    @GET("v1/offboarding/exit-notices")
    suspend fun getExitNotices(@Query("employeeId") employeeId: java.util.UUID? = null, @Query("status") status: StatusGetExitNotices? = null): Response<ExitNoticeListResponse>

    /**
     * GET v1/offboarding/exit-types
     * List exit types and reasons
     * Returns exit departure types and configured exit reasons catalog.
     * Responses:
     *  - 200: Exit types catalog
     *  - 401: Authentication required or token invalid
     *
     * @return [ExitTypeListResponse]
     */
    @GET("v1/offboarding/exit-types")
    suspend fun getExitTypes(): Response<ExitTypeListResponse>

    /**
     * POST v1/offboarding/exit-notices/{id}/reject
     * Reject exit notice
     * Rejects or rescinds an exit notice with reasons or remarks.
     * Responses:
     *  - 200: Exit notice rejected
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param rejectExitNoticeRequest  (optional)
     * @return [ExitNoticeItem]
     */
    @POST("v1/offboarding/exit-notices/{id}/reject")
    suspend fun rejectExitNotice(@Path("id") id: java.util.UUID, @Body rejectExitNoticeRequest: RejectExitNoticeRequest? = null): Response<ExitNoticeItem>

    /**
     * POST v1/offboarding/exit-notices/{id}/interview
     * Submit exit interview
     * Submits feedback, ratings, and sentiment responses from employee exit interview.
     * Responses:
     *  - 201: Exit interview recorded
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param exitInterviewSubmitRequest 
     * @return [ExitInterviewItem]
     */
    @POST("v1/offboarding/exit-notices/{id}/interview")
    suspend fun submitExitInterview(@Path("id") id: java.util.UUID, @Body exitInterviewSubmitRequest: ExitInterviewSubmitRequest): Response<ExitInterviewItem>

    /**
     * POST v1/offboarding/clearance-tasks/{id}/status
     * Update clearance task status
     * Departmental officer clears, waives, or rejects a clearance item with notes and recoverable charges.
     * Responses:
     *  - 200: Clearance task updated
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param clearanceTaskStatusUpdateRequest 
     * @return [ClearanceTaskItem]
     */
    @POST("v1/offboarding/clearance-tasks/{id}/status")
    suspend fun updateClearanceTaskStatus(@Path("id") id: java.util.UUID, @Body clearanceTaskStatusUpdateRequest: ClearanceTaskStatusUpdateRequest): Response<ClearanceTaskItem>

}
