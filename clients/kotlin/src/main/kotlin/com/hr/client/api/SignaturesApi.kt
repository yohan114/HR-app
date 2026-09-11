package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.SignatureRequestCreateRequest
import com.hr.client.model.SignatureRequestDetailResponse
import com.hr.client.model.SignatureRequestItem
import com.hr.client.model.SignatureRequestListResponse
import com.hr.client.model.SignatureSignRequest

interface SignaturesApi {
    /**
     * POST v1/signatures/requests
     * Create signature request
     * Dispatches document for sequential/multi-signer digital signing.
     * Responses:
     *  - 201: Signature request created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *
     * @param signatureRequestCreateRequest 
     * @return [SignatureRequestItem]
     */
    @POST("v1/signatures/requests")
    suspend fun createSignatureRequest(@Body signatureRequestCreateRequest: SignatureRequestCreateRequest): Response<SignatureRequestItem>

    /**
     * GET v1/signatures/requests/{id}
     * Get signature request details
     * Retrieves signature request with signers status and immutable audit logs.
     * Responses:
     *  - 200: Signature request details
     *  - 404: Not found
     *
     * @param id 
     * @return [SignatureRequestDetailResponse]
     */
    @GET("v1/signatures/requests/{id}")
    suspend fun getSignatureRequestDetails(@Path("id") id: java.util.UUID): Response<SignatureRequestDetailResponse>

    /**
     * GET v1/signatures/requests
     * List digital signature requests
     * Retrieves signature requests filtered by status or participant employee.
     * Responses:
     *  - 200: List of signature requests
     *
     * @param status  (optional)
     * @param signerEmployeeId  (optional)
     * @return [SignatureRequestListResponse]
     */
    @GET("v1/signatures/requests")
    suspend fun listSignatureRequests(@Query("status") status: kotlin.String? = null, @Query("signerEmployeeId") signerEmployeeId: java.util.UUID? = null): Response<SignatureRequestListResponse>

    /**
     * POST v1/signatures/requests/{id}/sign
     * Sign document
     * Submits canvas signature drawing or typed signature with legal consent acknowledgment.
     * Responses:
     *  - 200: Document signed successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param signatureSignRequest 
     * @return [SignatureRequestDetailResponse]
     */
    @POST("v1/signatures/requests/{id}/sign")
    suspend fun signDocument(@Path("id") id: java.util.UUID, @Body signatureSignRequest: SignatureSignRequest): Response<SignatureRequestDetailResponse>

}
