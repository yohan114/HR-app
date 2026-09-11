package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.CancelExpenseClaimRequest
import com.hr.client.model.ExpenseCategoriesResponse
import com.hr.client.model.ExpenseClaimDetailResponse
import com.hr.client.model.ExpenseClaimItem
import com.hr.client.model.ExpenseClaimSubmitRequest
import com.hr.client.model.ExpenseClaimsResponse
import com.hr.client.model.ReceiptOcrRequest
import com.hr.client.model.ReceiptOcrResponse

interface ExpensesApi {
    /**
     * POST v1/expenses/claims/{id}/cancel
     * Cancel or withdraw a pending expense claim
     * Withdraws a submitted or draft expense claim that has not yet been approved or reimbursed.
     * Responses:
     *  - 200: Expense claim cancelled
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @param cancelExpenseClaimRequest  (optional)
     * @return [ExpenseClaimItem]
     */
    @POST("v1/expenses/claims/{id}/cancel")
    suspend fun cancelExpenseClaim(@Path("id") id: kotlin.String, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null, @Body cancelExpenseClaimRequest: CancelExpenseClaimRequest? = null): Response<ExpenseClaimItem>

    /**
     * GET v1/expenses/categories
     * Retrieve company expense categories and policy limits
     * Returns all active expense categories with per-claim spending limits, receipt requirements, and per-unit rates.
     * Responses:
     *  - 200: Expense categories retrieved
     *  - 401: Authentication required or token invalid
     *
     * @return [ExpenseCategoriesResponse]
     */
    @GET("v1/expenses/categories")
    suspend fun getExpenseCategories(): Response<ExpenseCategoriesResponse>

    /**
     * GET v1/expenses/claims/{id}
     * Retrieve expense claim details with itemized lines
     * Returns full particulars, itemized claim lines, receipt attachments, and approval metadata.
     * Responses:
     *  - 200: Expense claim details retrieved
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [ExpenseClaimDetailResponse]
     */
    @GET("v1/expenses/claims/{id}")
    suspend fun getMyExpenseClaimDetails(@Path("id") id: kotlin.String): Response<ExpenseClaimDetailResponse>

    /**
     * GET v1/expenses/claims
     * Retrieve employee expense claims
     * Returns expense reimbursement claims submitted by the current authenticated employee with summary metrics.
     * Responses:
     *  - 200: Expense claims retrieved
     *  - 401: Authentication required or token invalid
     *
     * @param status  (optional)
     * @return [ExpenseClaimsResponse]
     */
    @GET("v1/expenses/claims")
    suspend fun getMyExpenseClaims(@Query("status") status: kotlin.String? = null): Response<ExpenseClaimsResponse>

    /**
     * POST v1/expenses/ocr
     * Parse receipt image or text using OCR
     * Extracts merchant name, transaction date, total amount, and suggested category from a receipt payload.
     * Responses:
     *  - 200: Receipt fields successfully extracted
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param receiptOcrRequest 
     * @return [ReceiptOcrResponse]
     */
    @POST("v1/expenses/ocr")
    suspend fun scanReceiptOcr(@Body receiptOcrRequest: ReceiptOcrRequest): Response<ReceiptOcrResponse>

    /**
     * POST v1/expenses/claims
     * Submit a new multi-line expense claim
     * Submits an expense claim with itemized receipts and lines for manager/finance approval.
     * Responses:
     *  - 201: Expense claim submitted
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param expenseClaimSubmitRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [ExpenseClaimDetailResponse]
     */
    @POST("v1/expenses/claims")
    suspend fun submitExpenseClaim(@Body expenseClaimSubmitRequest: ExpenseClaimSubmitRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<ExpenseClaimDetailResponse>

}
