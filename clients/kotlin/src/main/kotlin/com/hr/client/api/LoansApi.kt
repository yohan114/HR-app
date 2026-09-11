package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.EmployeeLoanDetailResponse
import com.hr.client.model.EmployeeLoanItem
import com.hr.client.model.EmployeeLoansResponse
import com.hr.client.model.LoanApplicationRequest
import com.hr.client.model.LoanEligibilityRequest
import com.hr.client.model.LoanEligibilityResponse
import com.hr.client.model.LoanSettlementRequest
import com.hr.client.model.LoanSettlementResponse
import com.hr.client.model.LoanTypesResponse

interface LoansApi {
    /**
     * POST v1/loans/eligibility
     * Pre-check loan eligibility and calculate projected installments
     * Evaluates requested principal and tenure against employee tenure, basic salary multiples, and debt limits, returning instant pass/fail with EMI preview.
     * Responses:
     *  - 200: Eligibility evaluated
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param loanEligibilityRequest 
     * @return [LoanEligibilityResponse]
     */
    @POST("v1/loans/eligibility")
    suspend fun checkLoanEligibility(@Body loanEligibilityRequest: LoanEligibilityRequest): Response<LoanEligibilityResponse>

    /**
     * GET v1/loans/types
     * Retrieve company loan types and entitlement policies
     * Returns active loan programs (e.g. Festival Advance, Distress Loan, Vehicle Loan) with tenure, interest rates, and eligibility rules.
     * Responses:
     *  - 200: Available loan types retrieved
     *  - 401: Authentication required or token invalid
     *
     * @return [LoanTypesResponse]
     */
    @GET("v1/loans/types")
    suspend fun getLoanTypes(): Response<LoanTypesResponse>

    /**
     * GET v1/loans/me/{id}
     * Retrieve detailed loan particulars and amortization repayment schedule
     * Returns loan details with installment-by-installment schedule, principal/interest breakdown, and deduction statuses.
     * Responses:
     *  - 200: Loan details with amortization schedule retrieved
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [EmployeeLoanDetailResponse]
     */
    @GET("v1/loans/me/{id}")
    suspend fun getMyLoanDetails(@Path("id") id: kotlin.String): Response<EmployeeLoanDetailResponse>

    /**
     * GET v1/loans/me
     * Retrieve employee active and historical loans
     * Returns loans and salary advances for the authenticated employee with remaining balances and repayment progress.
     * Responses:
     *  - 200: Employee loans retrieved
     *  - 401: Authentication required or token invalid
     *
     * @return [EmployeeLoansResponse]
     */
    @GET("v1/loans/me")
    suspend fun getMyLoans(): Response<EmployeeLoansResponse>

    /**
     * POST v1/loans/me/{id}/settle
     * Request early settlement of an active loan
     * Calculates early payoff amount, waives future unaccrued interest if applicable, and marks remaining installments settled.
     * Responses:
     *  - 200: Early settlement processed
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @param loanSettlementRequest  (optional)
     * @return [LoanSettlementResponse]
     */
    @POST("v1/loans/me/{id}/settle")
    suspend fun requestLoanSettlement(@Path("id") id: kotlin.String, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null, @Body loanSettlementRequest: LoanSettlementRequest? = null): Response<LoanSettlementResponse>

    /**
     * POST v1/loans/applications
     * Submit a loan or salary advance application
     * Submits loan request, generates amortization schedule, and initiates approval workflow.
     * Responses:
     *  - 201: Loan application submitted
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 401: Authentication required or token invalid
     *
     * @param loanApplicationRequest 
     * @param idempotencyKey Client-generated key (UUIDv7) making the request safe to retry. Required on endpoints the offline outbox replays. Reusing a key with a different payload is rejected rather than silently returning the earlier result.  (optional)
     * @return [EmployeeLoanItem]
     */
    @POST("v1/loans/applications")
    suspend fun submitLoanApplication(@Body loanApplicationRequest: LoanApplicationRequest, @Header("Idempotency-Key") idempotencyKey: kotlin.String? = null): Response<EmployeeLoanItem>

}
