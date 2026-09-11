package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.CareerMovementItem
import com.hr.client.model.CareerMovementListResponse
import com.hr.client.model.CareerMovementProposalRequest
import com.hr.client.model.CareerMovementRevertRequest
import com.hr.client.model.CareerTimelineResponse

interface LifecycleApi {
    /**
     * POST v1/lifecycle/movements/{id}/approve
     * Approve career movement and trigger cascade
     * Approves the career movement and executes the transactional cascade (or schedules it for effective date).
     * Responses:
     *  - 200: Career movement approved and cascade executed or scheduled
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [CareerMovementItem]
     */
    @POST("v1/lifecycle/movements/{id}/approve")
    suspend fun approveCareerMovement(@Path("id") id: java.util.UUID, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<CareerMovementItem>

    /**
     * GET v1/lifecycle/movements/{id}
     * Get career movement details
     * Returns full particulars, previous vs proposed snapshot, and cascade audit summary.
     * Responses:
     *  - 200: Movement details retrieved
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [CareerMovementItem]
     */
    @GET("v1/lifecycle/movements/{id}")
    suspend fun getCareerMovementById(@Path("id") id: java.util.UUID): Response<CareerMovementItem>


    /**
    * enum for parameter status
    */
    enum class StatusGetCareerMovements(val value: kotlin.String) {
        @SerialName(value = "DRAFT") DRAFT("DRAFT"),
        @SerialName(value = "SUBMITTED") SUBMITTED("SUBMITTED"),
        @SerialName(value = "APPROVED") APPROVED("APPROVED"),
        @SerialName(value = "SCHEDULED") SCHEDULED("SCHEDULED"),
        @SerialName(value = "APPLIED") APPLIED("APPLIED"),
        @SerialName(value = "REJECTED") REJECTED("REJECTED"),
        @SerialName(value = "CANCELLED") CANCELLED("CANCELLED"),
        @SerialName(value = "REVERTED") REVERTED("REVERTED")
    }


    /**
    * enum for parameter type
    */
    enum class TypeGetCareerMovements(val value: kotlin.String) {
        @SerialName(value = "PROMOTION") PROMOTION("PROMOTION"),
        @SerialName(value = "LATERAL_TRANSFER") LATERAL_TRANSFER("LATERAL_TRANSFER"),
        @SerialName(value = "DEMOTION") DEMOTION("DEMOTION"),
        @SerialName(value = "CONFIRMATION") CONFIRMATION("CONFIRMATION"),
        @SerialName(value = "SALARY_REVISION") SALARY_REVISION("SALARY_REVISION"),
        @SerialName(value = "DESIGNATION_CHANGE") DESIGNATION_CHANGE("DESIGNATION_CHANGE"),
        @SerialName(value = "SECONDMENT") SECONDMENT("SECONDMENT")
    }

    /**
     * GET v1/lifecycle/movements
     * List employee career movements
     * Returns filterable career movements including promotions, lateral transfers, and probation confirmations.
     * Responses:
     *  - 200: List of career movements
     *  - 401: Authentication required or token invalid
     *
     * @param employeeId  (optional)
     * @param status  (optional)
     * @param type  (optional)
     * @return [CareerMovementListResponse]
     */
    @GET("v1/lifecycle/movements")
    suspend fun getCareerMovements(@Query("employeeId") employeeId: java.util.UUID? = null, @Query("status") status: StatusGetCareerMovements? = null, @Query("type") type: TypeGetCareerMovements? = null): Response<CareerMovementListResponse>

    /**
     * GET v1/lifecycle/movements/timeline
     * Get interactive career timeline
     * Returns chronological career milestones including hire, probation confirmation, promotions, and transfers for the employee.
     * Responses:
     *  - 200: Career journey timeline milestones
     *  - 401: Authentication required or token invalid
     *
     * @param employeeId  (optional)
     * @return [CareerTimelineResponse]
     */
    @GET("v1/lifecycle/movements/timeline")
    suspend fun getCareerTimeline(@Query("employeeId") employeeId: java.util.UUID? = null): Response<CareerTimelineResponse>

    /**
     * POST v1/lifecycle/movements
     * Propose a career movement
     * Initiates a new promotion, lateral transfer, confirmation, or salary revision request.
     * Responses:
     *  - 201: Career movement proposed successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param careerMovementProposalRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [CareerMovementItem]
     */
    @POST("v1/lifecycle/movements")
    suspend fun proposeCareerMovement(@Body careerMovementProposalRequest: CareerMovementProposalRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<CareerMovementItem>

    /**
     * POST v1/lifecycle/movements/{id}/revert
     * Revert an approved or applied career movement
     * Restores prior employee master fields and rolls back salary records.
     * Responses:
     *  - 200: Career movement reverted successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param careerMovementRevertRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [CareerMovementItem]
     */
    @POST("v1/lifecycle/movements/{id}/revert")
    suspend fun revertCareerMovement(@Path("id") id: java.util.UUID, @Body careerMovementRevertRequest: CareerMovementRevertRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<CareerMovementItem>

}
