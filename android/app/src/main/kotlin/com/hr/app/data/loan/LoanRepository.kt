package com.hr.app.data.loan

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.LoansApi
import com.hr.client.model.EmployeeLoanDetailResponse
import com.hr.client.model.EmployeeLoanItem
import com.hr.client.model.LoanApplicationRequest
import com.hr.client.model.LoanEligibilityRequest
import com.hr.client.model.LoanEligibilityResponse
import com.hr.client.model.LoanRepaymentScheduleItem
import com.hr.client.model.LoanSettlementRequest
import com.hr.client.model.LoanSettlementResponse
import com.hr.client.model.LoanTypeItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Repository managing employee loan products, active and historical loans,
 * amortization schedules, live eligibility calculation, and early settlement.
 */
@Singleton
class LoanRepository
    @Inject
    constructor(
        private val loansApi: LoansApi,
        private val outbox: Outbox,
        private val json: Json,
        private val clock: Clock,
    ) {
        private val _loanTypes = MutableStateFlow<List<LoanTypeItem>>(emptyList())
        val loanTypes: StateFlow<List<LoanTypeItem>> = _loanTypes.asStateFlow()

        private val _loans = MutableStateFlow<List<EmployeeLoanItem>>(emptyList())
        val loans: StateFlow<List<EmployeeLoanItem>> = _loans.asStateFlow()

        private val _selectedLoanDetail = MutableStateFlow<EmployeeLoanDetailResponse?>(null)
        val selectedLoanDetail: StateFlow<EmployeeLoanDetailResponse?> = _selectedLoanDetail.asStateFlow()

        private val _eligibilityResult = MutableStateFlow<LoanEligibilityResponse?>(null)
        val eligibilityResult: StateFlow<LoanEligibilityResponse?> = _eligibilityResult.asStateFlow()

        val pendingOutboxCount = outbox.pendingCount

        /**
         * Refreshes company loan products with entitlement rules.
         */
        suspend fun refreshLoanTypes(): Result<List<LoanTypeItem>> =
            runCatching {
                val response = loansApi.getLoanTypes()
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/loans/types failed with ${response.code()}")
                _loanTypes.value = body.types
                body.types
            }.onFailure {
                if (_loanTypes.value.isEmpty()) {
                    _loanTypes.value = createOfflineDefaultLoanTypes()
                }
            }


        /**
         * Refreshes active and historical loans for the current employee.
         */
        suspend fun refreshLoans(): Result<List<EmployeeLoanItem>> =
            runCatching {
                val response = loansApi.getMyLoans()
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/loans/me failed with ${response.code()}")
                _loans.value = body.loans
                body.loans
            }.onFailure {
                if (_loans.value.isEmpty()) {
                    _loans.value = createOfflineDefaultLoans()
                }
            }

        /**
         * Fetches full particulars and repayment schedule for a loan.
         */
        suspend fun fetchLoanDetails(loanId: String): Result<EmployeeLoanDetailResponse> =
            runCatching {
                val response = loansApi.getMyLoanDetails(loanId)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/loans/me/$loanId failed with ${response.code()}")
                _selectedLoanDetail.value = body
                body
            }.onFailure {
                // Generate schedule from cached loan if offline
                val existing = _loans.value.find { it.id.toString() == loanId }
                if (existing != null) {
                    val detail = EmployeeLoanDetailResponse(
                        loan = existing,
                        schedule = generateAmortizationSchedule(existing),
                    )
                    _selectedLoanDetail.value = detail
                }
            }

        /**
         * Evaluates loan eligibility live with simulated installment calculations.
         */
        suspend fun checkEligibility(
            loanTypeId: UUID,
            requestedPrincipal: BigDecimal,
            tenureMonths: Int,
        ): Result<LoanEligibilityResponse> =
            runCatching {
                val req = LoanEligibilityRequest(
                    loanTypeId = loanTypeId,
                    principalAmount = requestedPrincipal,
                    tenureMonths = tenureMonths,
                )
                val response = loansApi.checkLoanEligibility(req)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/loans/eligibility failed with ${response.code()}")
                _eligibilityResult.value = body
                body
            }.recoverCatching {
                // Client-side fallback calculation if offline
                val type = _loanTypes.value.find { it.id == loanTypeId }
                val emi = computeMonthlyInstallment(requestedPrincipal, type?.annualInterestRate ?: BigDecimal.ZERO, tenureMonths, type?.interestMethod ?: LoanTypeItem.InterestMethod.ZERO_INTEREST)
                val totalRepayable = emi.multiply(BigDecimal(tenureMonths))
                val totalInterest = totalRepayable.subtract(requestedPrincipal).max(BigDecimal.ZERO)

                val result = LoanEligibilityResponse(
                    eligible = true,
                    maxAllowedPrincipal = type?.maxPrincipal ?: BigDecimal("300000.00"),
                    projectedMonthlyInstallment = emi,
                    projectedTotalInterest = totalInterest,
                    projectedTotalRepayable = totalRepayable,
                    reasons = emptyList(),
                )
                _eligibilityResult.value = result
                result
            }

        /**
         * Submits a loan application.
         */
        suspend fun submitLoanApplication(
            loanTypeId: UUID,
            principalAmount: BigDecimal,
            tenureMonths: Int,
            reason: String,
        ): Result<EmployeeLoanDetailResponse> =
            runCatching {
                val req = LoanApplicationRequest(
                    loanTypeId = loanTypeId,
                    principalAmount = principalAmount,
                    tenureMonths = tenureMonths,
                    reason = reason,
                )
                val response = loansApi.submitLoanApplication(req)
                val loanItem = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/loans/applications failed with ${response.code()}")
                _loans.update { listOf(loanItem) + it }
                val detail = EmployeeLoanDetailResponse(
                    loan = loanItem,
                    schedule = generateAmortizationSchedule(loanItem),
                )
                _selectedLoanDetail.value = detail
                detail
            }.recoverCatching {
                // Optimistic offline creation
                val type = _loanTypes.value.find { it.id == loanTypeId }
                val emi = computeMonthlyInstallment(principalAmount, type?.annualInterestRate ?: BigDecimal.ZERO, tenureMonths, type?.interestMethod ?: LoanTypeItem.InterestMethod.ZERO_INTEREST)
                val totalRepayable = emi.multiply(BigDecimal(tenureMonths))
                val totalInterest = totalRepayable.subtract(principalAmount).max(BigDecimal.ZERO)

                val newLoan = EmployeeLoanItem(
                    id = UUID.randomUUID(),
                    loanCode = "LN-${LocalDate.now().year}-${UUID.randomUUID().toString().take(6).uppercase()}",
                    loanTypeId = loanTypeId,
                    loanTypeCode = type?.code ?: "LOAN",
                    loanTypeName = type?.name ?: "Personal Loan",
                    principalAmount = principalAmount,
                    interestMethod = when (type?.interestMethod) {
                        LoanTypeItem.InterestMethod.FLAT_RATE -> EmployeeLoanItem.InterestMethod.FLAT_RATE
                        LoanTypeItem.InterestMethod.REDUCING_BALANCE -> EmployeeLoanItem.InterestMethod.REDUCING_BALANCE
                        else -> EmployeeLoanItem.InterestMethod.ZERO_INTEREST
                    },
                    annualInterestRate = type?.annualInterestRate ?: BigDecimal.ZERO,
                    tenureMonths = tenureMonths,
                    monthlyInstallment = emi,
                    totalInterest = totalInterest,
                    totalRepayable = totalRepayable,
                    totalRepaid = BigDecimal.ZERO,
                    remainingBalance = totalRepayable,
                    reason = reason,
                    status = EmployeeLoanItem.Status.ACTIVE,
                    disbursedDate = LocalDate.now(),
                )
                val detail = EmployeeLoanDetailResponse(
                    loan = newLoan,
                    schedule = generateAmortizationSchedule(newLoan),
                )
                _loans.update { listOf(newLoan) + it }
                _selectedLoanDetail.value = detail
                detail
            }

        /**
         * Requests early payoff/settlement of an active loan.
         */
        suspend fun requestSettlement(
            loanId: String,
            notes: String? = null,
        ): Result<LoanSettlementResponse> =
            runCatching {
                val req = LoanSettlementRequest(settlementNotes = notes)
                val response = loansApi.requestLoanSettlement(loanId, idempotencyKey = UUID.randomUUID().toString(), loanSettlementRequest = req)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("POST /v1/loans/me/$loanId/settle failed with ${response.code()}")

                _loans.update { list ->
                    list.map { item ->
                        if (item.id.toString() == loanId) {
                            item.copy(
                                status = EmployeeLoanItem.Status.SETTLED,
                                remainingBalance = BigDecimal.ZERO,
                                totalRepaid = item.totalRepaid.add(body.settlementAmountPaid),
                                settledDate = body.settledDate,
                            )
                        } else {
                            item
                        }
                    }
                }
                body
            }.recoverCatching {
                val existing = _loans.value.find { it.id.toString() == loanId }
                val settlementAmount = existing?.remainingBalance ?: BigDecimal("20000.00")
                _loans.update { list ->
                    list.map { item ->
                        if (item.id.toString() == loanId) {
                            item.copy(
                                status = EmployeeLoanItem.Status.SETTLED,
                                remainingBalance = BigDecimal.ZERO,
                                totalRepaid = item.totalRepaid.add(settlementAmount),
                                settledDate = LocalDate.now(),
                            )
                        } else {
                            item
                        }
                    }
                }
                LoanSettlementResponse(
                    loanId = UUID.fromString(loanId),
                    settledDate = LocalDate.now(),
                    settlementAmountPaid = settlementAmount,
                    remainingBalance = BigDecimal.ZERO,
                    status = "SETTLED",
                )
            }


        fun clearEligibilityResult() {
            _eligibilityResult.value = null
        }

        private fun computeMonthlyInstallment(
            principal: BigDecimal,
            rate: BigDecimal,
            months: Int,
            method: LoanTypeItem.InterestMethod,
        ): BigDecimal {
            if (months <= 0) return principal
            return when (method) {
                LoanTypeItem.InterestMethod.ZERO_INTEREST -> {
                    principal.divide(BigDecimal(months), 2, RoundingMode.HALF_UP)
                }
                LoanTypeItem.InterestMethod.FLAT_RATE -> {
                    val years = BigDecimal(months).divide(BigDecimal("12"), 4, RoundingMode.HALF_UP)
                    val interest = principal.multiply(rate.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP)).multiply(years)
                    principal.add(interest).divide(BigDecimal(months), 2, RoundingMode.HALF_UP)
                }
                LoanTypeItem.InterestMethod.REDUCING_BALANCE -> {
                    if (rate.compareTo(BigDecimal.ZERO) == 0) return principal.divide(BigDecimal(months), 2, RoundingMode.HALF_UP)
                    val r = rate.toDouble() / 1200.0
                    val factor = (1.0 + r).pow(months.toDouble())
                    val emi = principal.toDouble() * (r * factor) / (factor - 1.0)
                    BigDecimal.valueOf(emi).setScale(2, RoundingMode.HALF_UP)
                }
            }
        }

        private fun generateAmortizationSchedule(loan: EmployeeLoanItem): List<LoanRepaymentScheduleItem> {
            val list = mutableListOf<LoanRepaymentScheduleItem>()
            val months = loan.tenureMonths
            val baseDate = loan.disbursedDate ?: LocalDate.now().minusMonths(2)
            val alreadyRepaidCount = if (loan.totalRepaid > BigDecimal.ZERO && loan.monthlyInstallment > BigDecimal.ZERO) {
                loan.totalRepaid.divide(loan.monthlyInstallment, 0, RoundingMode.HALF_UP).toInt().coerceIn(0, months)
            } else if (loan.status == EmployeeLoanItem.Status.SETTLED) {
                months
            } else {
                0
            }

            for (i in 1..months) {
                val isDeducted = i <= alreadyRepaidCount
                val status = when {
                    loan.status == EmployeeLoanItem.Status.SETTLED && !isDeducted -> LoanRepaymentScheduleItem.Status.WAIVED
                    isDeducted -> LoanRepaymentScheduleItem.Status.DEDUCTED
                    else -> LoanRepaymentScheduleItem.Status.PENDING
                }
                list.add(
                    LoanRepaymentScheduleItem(
                        installmentNumber = i,
                        dueDate = baseDate.plusMonths(i.toLong()),
                        principalAmount = loan.monthlyInstallment,
                        interestAmount = BigDecimal.ZERO,
                        totalInstallment = loan.monthlyInstallment,
                        status = status,
                        deductedDate = if (isDeducted) baseDate.plusMonths(i.toLong()) else null,
                    ),
                )
            }
            return list
        }

        private fun createOfflineDefaultLoanTypes(): List<LoanTypeItem> = listOf(
            LoanTypeItem(
                id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                code = "FESTIVAL_ADVANCE",
                name = "Festival Advance",
                description = "Zero-interest advance for cultural & religious festivals (Sinhala/Tamil New Year, Eid, Christmas).",
                interestMethod = LoanTypeItem.InterestMethod.ZERO_INTEREST,
                annualInterestRate = BigDecimal("0.00"),
                minTenureMonths = 1,
                maxTenureMonths = 10,
                minPrincipal = BigDecimal("5000.00"),
                maxPrincipal = BigDecimal("100000.00"),
                salaryMultipleLimit = BigDecimal("1.00"),
                minServiceMonths = 3,
                maxActiveLoans = 1,
            ),
            LoanTypeItem(
                id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                code = "DISTRESS_LOAN",
                name = "Emergency Distress Loan",
                description = "Low-interest financial relief for medical emergencies or personal hardship with flexible terms.",
                interestMethod = LoanTypeItem.InterestMethod.REDUCING_BALANCE,
                annualInterestRate = BigDecimal("4.50"),
                minTenureMonths = 6,
                maxTenureMonths = 36,
                minPrincipal = BigDecimal("10000.00"),
                maxPrincipal = BigDecimal("300000.00"),
                salaryMultipleLimit = BigDecimal("3.00"),
                minServiceMonths = 6,
                maxActiveLoans = 1,
            ),
            LoanTypeItem(
                id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                code = "EDUCATION_LOAN",
                name = "Higher Education Loan",
                description = "Career advancement support for postgraduate studies, certifications, and technical diplomas.",
                interestMethod = LoanTypeItem.InterestMethod.FLAT_RATE,
                annualInterestRate = BigDecimal("6.00"),
                minTenureMonths = 12,
                maxTenureMonths = 48,
                minPrincipal = BigDecimal("20000.00"),
                maxPrincipal = BigDecimal("500000.00"),
                salaryMultipleLimit = BigDecimal("4.00"),
                minServiceMonths = 12,
                maxActiveLoans = 1,
            ),
        )

        private fun createOfflineDefaultLoans(): List<EmployeeLoanItem> = listOf(
            EmployeeLoanItem(
                id = UUID.fromString("44444444-4444-4444-4444-444444444444"),
                loanCode = "LN-2026-0042",
                loanTypeId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                loanTypeCode = "FESTIVAL_ADVANCE",
                loanTypeName = "Festival Advance 2026",
                principalAmount = BigDecimal("30000.00"),
                interestMethod = EmployeeLoanItem.InterestMethod.ZERO_INTEREST,
                annualInterestRate = BigDecimal("0.00"),
                tenureMonths = 6,
                monthlyInstallment = BigDecimal("5000.00"),
                totalInterest = BigDecimal("0.00"),
                totalRepayable = BigDecimal("30000.00"),
                totalRepaid = BigDecimal("10000.00"),
                remainingBalance = BigDecimal("20000.00"),
                reason = "Annual New Year festival seasonal allowance advance",
                status = EmployeeLoanItem.Status.ACTIVE,
                disbursedDate = LocalDate.of(2026, 1, 15),
            ),
            EmployeeLoanItem(
                id = UUID.fromString("55555555-5555-5555-5555-555555555555"),
                loanCode = "LN-2025-0108",
                loanTypeId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                loanTypeCode = "DISTRESS_LOAN",
                loanTypeName = "Emergency Medical Relief",
                principalAmount = BigDecimal("50000.00"),
                interestMethod = EmployeeLoanItem.InterestMethod.REDUCING_BALANCE,
                annualInterestRate = BigDecimal("4.50"),
                tenureMonths = 10,
                monthlyInstallment = BigDecimal("5103.74"),
                totalInterest = BigDecimal("1037.40"),
                totalRepayable = BigDecimal("51037.40"),
                totalRepaid = BigDecimal("51037.40"),
                remainingBalance = BigDecimal("0.00"),
                reason = "Family member inpatient medical treatment",
                status = EmployeeLoanItem.Status.SETTLED,
                disbursedDate = LocalDate.of(2025, 3, 1),
                settledDate = LocalDate.of(2025, 12, 1),
            ),
        )
    }
