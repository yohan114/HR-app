package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.BenefitCatalogueResponse
import com.hr.client.model.BenefitClaimItem
import com.hr.client.model.BenefitClaimSubmitRequest
import com.hr.client.model.BenefitClaimsResponse
import com.hr.client.model.CancelBenefitClaimRequest
import com.hr.client.model.MyBenefitsResponse

interface BenefitsApi {
    /**
     * POST v1/benefits/claims/{id}/cancel
     * Cancel or withdraw a pending benefit claim
     * Cancels a submitted benefit claim before approval or payment.
     * Responses:
     *  - 200: Benefit claim cancelled
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @param cancelBenefitClaimRequest  (optional)
     * @return [BenefitClaimItem]
     */
    @POST("v1/benefits/claims/{id}/cancel")
    suspend fun cancelBenefitClaim(@Path("id") id: java.util.UUID, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null, @Body cancelBenefitClaimRequest: CancelBenefitClaimRequest? = null): Response<BenefitClaimItem>

    /**
     * GET v1/benefits/catalogue
     * Retrieve company benefit catalogue and eligibility
     * Returns available benefit categories and policies, evaluating employee eligibility and returning reasons if ineligible.
     * Responses:
     *  - 200: Available benefit catalogue retrieved
     *  - 401: Authentication required or token invalid
     *
     * @return [BenefitCatalogueResponse]
     */
    @GET("v1/benefits/catalogue")
    suspend fun getBenefitCatalogue(): Response<BenefitCatalogueResponse>

    /**
     * GET v1/benefits/claims
     * Retrieve employee benefit claims history
     * Returns all submitted, approved, and reimbursed benefit claims for the authenticated employee.
     * Responses:
     *  - 200: Benefit claims retrieved
     *  - 401: Authentication required or token invalid
     *
     * @param status  (optional)
     * @return [BenefitClaimsResponse]
     */
    @GET("v1/benefits/claims")
    suspend fun getMyBenefitClaims(@Query("status") status: kotlin.String? = null): Response<BenefitClaimsResponse>

    /**
     * GET v1/benefits/my-benefits
     * Retrieve active benefit enrollments and remaining balances
     * Returns current employee benefit enrollments with annual entitlement, used amounts, pending claims, and registered dependents.
     * Responses:
     *  - 200: Employee benefit enrollments retrieved
     *  - 401: Authentication required or token invalid
     *
     * @return [MyBenefitsResponse]
     */
    @GET("v1/benefits/my-benefits")
    suspend fun getMyBenefits(): Response<MyBenefitsResponse>

    /**
     * POST v1/benefits/claims
     * Submit a medical or flexible benefit reimbursement claim
     * Submits a benefit reimbursement claim against an active enrollment policy with receipt attachment.
     * Responses:
     *  - 201: Benefit claim submitted
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param benefitClaimSubmitRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [BenefitClaimItem]
     */
    @POST("v1/benefits/claims")
    suspend fun submitBenefitClaim(@Body benefitClaimSubmitRequest: BenefitClaimSubmitRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<BenefitClaimItem>

}
