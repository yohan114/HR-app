package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.ApprovalDecisionRequest
import com.hr.client.model.ApprovalDecisionResponse
import com.hr.client.model.PendingApprovalsResponse

interface ApprovalsApi {
    /**
     * POST v1/approvals/{id}/decision
     * Record manager approval or rejection decision
     * Submits an approval or rejection decision on an item. Supports single-use Idempotency-Key for safe offline outbox retry. Returns 409 ALREADY_DECIDED if another approver acted first. 
     * Responses:
     *  - 200: Decision recorded
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param id 
     * @param approvalDecisionRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [ApprovalDecisionResponse]
     */
    @POST("v1/approvals/{id}/decision")
    suspend fun decideApproval(@Path("id") id: kotlin.String, @Body approvalDecisionRequest: ApprovalDecisionRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<ApprovalDecisionResponse>

    /**
     * GET v1/approvals/pending
     * Pending approval inbox for the authenticated manager
     * Returns unified pending approvals across all modules (leave applications, attendance regularizations), including requester context, remaining leave balances, roster conflict warnings, and urgency indicators. 
     * Responses:
     *  - 200: Pending approvals list
     *  - 401: Authentication required or token invalid
     *
     * @return [PendingApprovalsResponse]
     */
    @GET("v1/approvals/pending")
    suspend fun listPendingApprovals(): Response<PendingApprovalsResponse>

}
