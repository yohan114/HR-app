package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.GrievanceAppealRequest
import com.hr.client.model.GrievanceChannelsResponse
import com.hr.client.model.GrievanceDetailResponse
import com.hr.client.model.GrievanceGroundsResponse
import com.hr.client.model.GrievanceListResponse
import com.hr.client.model.GrievanceSubmitRequest

interface GrievanceApi {
    /**
     * POST v1/grievance/{id}/appeal
     * Appeal a resolved grievance
     * Lodges a formal appeal against a grievance resolution.
     * Responses:
     *  - 200: Appeal submitted successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param grievanceAppealRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [GrievanceDetailResponse]
     */
    @POST("v1/grievance/{id}/appeal")
    suspend fun appealGrievance(@Path("id") id: java.util.UUID, @Body grievanceAppealRequest: GrievanceAppealRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<GrievanceDetailResponse>

    /**
     * GET v1/grievance/{id}
     * Get grievance particulars and resolution
     * Returns detailed grievance status, SLA tracking, investigation findings, and appeal.
     * Responses:
     *  - 200: Grievance details
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [GrievanceDetailResponse]
     */
    @GET("v1/grievance/{id}")
    suspend fun getGrievanceById(@Path("id") id: java.util.UUID): Response<GrievanceDetailResponse>

    /**
     * GET v1/grievance/channels
     * List grievance submission channels
     * Returns available channels (Direct Portal, Anonymous Hotline, Ombudsman, Union).
     * Responses:
     *  - 200: Grievance channels list
     *  - 401: Authentication required or token invalid
     *
     * @return [GrievanceChannelsResponse]
     */
    @GET("v1/grievance/channels")
    suspend fun getGrievanceChannels(): Response<GrievanceChannelsResponse>

    /**
     * GET v1/grievance/grounds
     * List grievance ground groups and grounds
     * Returns all configured grievance categories, grounds with SLA resolution days, severity, and default handler roles.
     * Responses:
     *  - 200: Grievance grounds catalogue
     *  - 401: Authentication required or token invalid
     *
     * @return [GrievanceGroundsResponse]
     */
    @GET("v1/grievance/grounds")
    suspend fun getGrievanceGrounds(): Response<GrievanceGroundsResponse>

    /**
     * GET v1/grievance/my-grievances
     * List grievances for authenticated employee
     * Returns active and past grievances filed by or involving the employee.
     * Responses:
     *  - 200: Employee grievances
     *  - 401: Authentication required or token invalid
     *
     * @param status  (optional)
     * @return [GrievanceListResponse]
     */
    @GET("v1/grievance/my-grievances")
    suspend fun getMyGrievances(@Query("status") status: kotlin.String? = null): Response<GrievanceListResponse>

    /**
     * POST v1/grievance
     * Submit a new grievance or whistleblowing report
     * Submits a grievance with confidential handling and optional anonymous whistleblowing flag.
     * Responses:
     *  - 201: Grievance submitted successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param grievanceSubmitRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [GrievanceDetailResponse]
     */
    @POST("v1/grievance")
    suspend fun submitGrievance(@Body grievanceSubmitRequest: GrievanceSubmitRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<GrievanceDetailResponse>

}
