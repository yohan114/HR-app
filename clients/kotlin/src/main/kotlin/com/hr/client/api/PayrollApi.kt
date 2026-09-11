package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.BankAdviceRequest
import com.hr.client.model.BankAdviceResponse
import com.hr.client.model.CalculatePayrollRequest
import com.hr.client.model.PayGroupsResponse
import com.hr.client.model.PayPeriodsResponse
import com.hr.client.model.PayrollResultDetailResponse
import com.hr.client.model.PayrollResultsResponse
import com.hr.client.model.PayrollRunItem
import com.hr.client.model.PayrollRunsResponse
import com.hr.client.model.PayrollVarianceResponse
import com.hr.client.model.PayslipComparisonResponse
import com.hr.client.model.PayslipDetailResponse
import com.hr.client.model.PayslipsResponse

interface PayrollApi {
    /**
     * POST v1/payroll/runs/{id}/approve
     * Approve payroll run
     * Approves a calculated payroll run for commitment and bank file generation.
     * Responses:
     *  - 200: Payroll run approved
     *  - 404: Not found
     *
     * @param id 
     * @return [PayrollRunItem]
     */
    @POST("v1/payroll/runs/{id}/approve")
    suspend fun approvePayrollRun(@Path("id") id: kotlin.String): Response<PayrollRunItem>

    /**
     * POST v1/payroll/runs/calculate
     * Calculate payroll run
     * Initiates a gross-to-net calculation batch for a pay group and period.
     * Responses:
     *  - 200: Calculation initiated or completed
     *  - 400: Malformed request, or a field value that could not be interpreted
     *
     * @param calculatePayrollRequest 
     * @return [PayrollRunItem]
     */
    @POST("v1/payroll/runs/calculate")
    suspend fun calculatePayrollRun(@Body calculatePayrollRequest: CalculatePayrollRequest): Response<PayrollRunItem>

    /**
     * POST v1/payroll/runs/{id}/commit
     * Commit payroll run
     * Finalizes and locks a payroll run, generating immutable payslip snapshots.
     * Responses:
     *  - 200: Payroll run committed
     *  - 404: Not found
     *
     * @param id 
     * @return [PayrollRunItem]
     */
    @POST("v1/payroll/runs/{id}/commit")
    suspend fun commitPayrollRun(@Path("id") id: kotlin.String): Response<PayrollRunItem>

    /**
     * POST v1/payroll/runs/{id}/bank-advice
     * Generate bank advice file
     * Exports direct deposit batch instructions in standard CSV or NACHA bank formats.
     * Responses:
     *  - 200: Generated bank advice payload
     *  - 404: Not found
     *
     * @param id 
     * @param bankAdviceRequest 
     * @return [BankAdviceResponse]
     */
    @POST("v1/payroll/runs/{id}/bank-advice")
    suspend fun generateBankAdvice(@Path("id") id: kotlin.String, @Body bankAdviceRequest: BankAdviceRequest): Response<BankAdviceResponse>

    /**
     * GET v1/payroll/me/payslips
     * Retrieve employee payslip history
     * Retrieves finalized payslips for the authenticated employee.
     * Responses:
     *  - 200: List of employee payslips retrieved
     *  - 401: Authentication required or token invalid
     *
     * @return [PayslipsResponse]
     */
    @GET("v1/payroll/me/payslips")
    suspend fun getMyPayslips(): Response<PayslipsResponse>

    /**
     * GET v1/payroll/runs/{id}/results/{resultId}
     * Get employee payroll result details
     * Retrieves itemized earnings, deductions, and calculation traces for an employee result.
     * Responses:
     *  - 200: Detailed payroll result with item lines
     *  - 404: Not found
     *
     * @param id 
     * @param resultId 
     * @return [PayrollResultDetailResponse]
     */
    @GET("v1/payroll/runs/{id}/results/{resultId}")
    suspend fun getPayrollResultDetails(@Path("id") id: kotlin.String, @Path("resultId") resultId: kotlin.String): Response<PayrollResultDetailResponse>

    /**
     * GET v1/payroll/runs/{id}
     * Get payroll run by ID
     * Retrieves status and summary totals for a specific payroll calculation run.
     * Responses:
     *  - 200: Payroll run details
     *  - 404: Not found
     *
     * @param id 
     * @return [PayrollRunItem]
     */
    @GET("v1/payroll/runs/{id}")
    suspend fun getPayrollRunById(@Path("id") id: kotlin.String): Response<PayrollRunItem>

    /**
     * GET v1/payroll/runs/{id}/variance
     * Get payroll run variance
     * Computes period-over-period variance anomalies and headcount differences.
     * Responses:
     *  - 200: Payroll variance breakdown and flagged anomalies
     *  - 404: Not found
     *
     * @param id 
     * @return [PayrollVarianceResponse]
     */
    @GET("v1/payroll/runs/{id}/variance")
    suspend fun getPayrollVariance(@Path("id") id: kotlin.String): Response<PayrollVarianceResponse>

    /**
     * GET v1/payroll/me/payslips/{id}/comparison
     * Retrieve Month-over-Month payslip comparison
     * Compares the payslip with the immediate prior period.
     * Responses:
     *  - 200: Payslip comparison retrieved
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [PayslipComparisonResponse]
     */
    @GET("v1/payroll/me/payslips/{id}/comparison")
    suspend fun getPayslipComparison(@Path("id") id: kotlin.String): Response<PayslipComparisonResponse>

    /**
     * GET v1/payroll/me/payslips/{id}
     * Retrieve detailed payslip with calculation traces
     * Retrieves the detailed payslip breakdown, bank info, and formula traces.
     * Responses:
     *  - 200: Detailed payslip retrieved
     *  - 401: Authentication required or token invalid
     *  - 404: Not found
     *
     * @param id 
     * @return [PayslipDetailResponse]
     */
    @GET("v1/payroll/me/payslips/{id}")
    suspend fun getPayslipDetails(@Path("id") id: kotlin.String): Response<PayslipDetailResponse>

    /**
     * GET v1/payroll/pay-groups
     * List pay groups
     * Retrieves configured payroll calculation groups with frequency and currency rules.
     * Responses:
     *  - 200: List of pay groups
     *  - 401: Authentication required or token invalid
     *
     * @return [PayGroupsResponse]
     */
    @GET("v1/payroll/pay-groups")
    suspend fun listPayGroups(): Response<PayGroupsResponse>

    /**
     * GET v1/payroll/pay-periods
     * List pay periods
     * Retrieves pay periods optionally filtered by pay group identifier.
     * Responses:
     *  - 200: List of pay periods
     *  - 401: Authentication required or token invalid
     *
     * @param payGroupId  (optional)
     * @return [PayPeriodsResponse]
     */
    @GET("v1/payroll/pay-periods")
    suspend fun listPayPeriods(@Query("payGroupId") payGroupId: kotlin.String? = null): Response<PayPeriodsResponse>

    /**
     * GET v1/payroll/runs/{id}/results
     * List payroll run results
     * Retrieves employee-level gross, tax, statutory, and net salary results for a run.
     * Responses:
     *  - 200: List of payroll results
     *  - 404: Not found
     *
     * @param id 
     * @return [PayrollResultsResponse]
     */
    @GET("v1/payroll/runs/{id}/results")
    suspend fun listPayrollResults(@Path("id") id: kotlin.String): Response<PayrollResultsResponse>

    /**
     * GET v1/payroll/runs
     * List payroll runs
     * Retrieves payroll calculation runs filtered optionally by pay period identifier.
     * Responses:
     *  - 200: List of payroll runs
     *  - 401: Authentication required or token invalid
     *
     * @param payPeriodId  (optional)
     * @return [PayrollRunsResponse]
     */
    @GET("v1/payroll/runs")
    suspend fun listPayrollRuns(@Query("payPeriodId") payPeriodId: kotlin.String? = null): Response<PayrollRunsResponse>

    /**
     * POST v1/payroll/runs/{id}/reject
     * Reject payroll run
     * Rejects a payroll run back to draft status for recalculation.
     * Responses:
     *  - 200: Payroll run rejected
     *  - 404: Not found
     *
     * @param id 
     * @return [PayrollRunItem]
     */
    @POST("v1/payroll/runs/{id}/reject")
    suspend fun rejectPayrollRun(@Path("id") id: kotlin.String): Response<PayrollRunItem>

}
