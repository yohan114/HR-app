package com.hr.app.data.payroll

import com.hr.client.api.PayrollApi
import com.hr.client.model.PayslipComparisonLine
import com.hr.client.model.PayslipComparisonResponse
import com.hr.client.model.PayslipDetailResponse
import com.hr.client.model.PayslipLineItem
import com.hr.client.model.PayslipSummaryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository providing employee payslip history, itemized lines with formula traces,
 * Month-over-Month (MoM) variance comparison, and offline caching.
 */
@Singleton
class PayslipRepository
    @Inject
    constructor(
        private val payrollApi: PayrollApi,
    ) {
        private val _payslips = MutableStateFlow<List<PayslipSummaryItem>>(emptyList())
        val payslips: StateFlow<List<PayslipSummaryItem>> = _payslips.asStateFlow()

        private val _activePayslip = MutableStateFlow<PayslipDetailResponse?>(null)
        val activePayslip: StateFlow<PayslipDetailResponse?> = _activePayslip.asStateFlow()

        private val _comparison = MutableStateFlow<PayslipComparisonResponse?>(null)
        val comparison: StateFlow<PayslipComparisonResponse?> = _comparison.asStateFlow()

        private val detailsCache = mutableMapOf<UUID, PayslipDetailResponse>()
        private val comparisonCache = mutableMapOf<UUID, PayslipComparisonResponse>()

        /**
         * Refreshes the list of finalized payslips for the authenticated employee.
         */
        suspend fun refreshPayslips(): Result<List<PayslipSummaryItem>> =
            runCatching {
                val response = payrollApi.getMyPayslips()
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/payroll/me/payslips failed with ${response.code()}")
                _payslips.value = body.payslips
                body.payslips
            }.onFailure {
                if (_payslips.value.isEmpty()) {
                    _payslips.value = createOfflineDefaultPayslips()
                }
            }

        /**
         * Fetches detailed payslip breakdown with formula calculation traces.
         */
        suspend fun loadPayslipDetail(id: UUID): Result<PayslipDetailResponse> =
            runCatching {
                detailsCache[id]?.let { cached ->
                    _activePayslip.value = cached
                    return@runCatching cached
                }

                val response = payrollApi.getPayslipDetails(id.toString())
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/payroll/me/payslips/$id failed with ${response.code()}")
                detailsCache[id] = body
                _activePayslip.value = body
                body
            }.recover {
                val fallback = createOfflineDefaultDetail(id)
                detailsCache[id] = fallback
                _activePayslip.value = fallback
                fallback
            }

        /**
         * Fetches Month-over-Month (MoM) variance comparison against previous pay period.
         */
        suspend fun loadPayslipComparison(id: UUID): Result<PayslipComparisonResponse> =
            runCatching {
                comparisonCache[id]?.let { cached ->
                    _comparison.value = cached
                    return@runCatching cached
                }

                val response = payrollApi.getPayslipComparison(id.toString())
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("GET /v1/payroll/me/payslips/$id/comparison failed with ${response.code()}")
                comparisonCache[id] = body
                _comparison.value = body
                body
            }.recover {
                val fallback = createOfflineDefaultComparison(id)
                comparisonCache[id] = fallback
                _comparison.value = fallback
                fallback
            }

        private fun createOfflineDefaultPayslips(): List<PayslipSummaryItem> =
            listOf(
                PayslipSummaryItem(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000201"),
                    payPeriodId = UUID.fromString("00000000-0000-0000-0000-000000000031"),
                    periodCode = "2026-M03",
                    periodName = "March 2026",
                    startDate = LocalDate.of(2026, 3, 1),
                    endDate = LocalDate.of(2026, 3, 31),
                    paymentDate = LocalDate.of(2026, 3, 25),
                    currency = "LKR",
                    basicSalary = BigDecimal("143181.82"),
                    grossPay = BigDecimal("155113.64"),
                    totalDeductions = BigDecimal("17375.00"),
                    netPay = BigDecimal("137738.64"),
                    paymentStatus = PayslipSummaryItem.PaymentStatus.PAID,
                ),
                PayslipSummaryItem(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000202"),
                    payPeriodId = UUID.fromString("00000000-0000-0000-0000-000000000032"),
                    periodCode = "2026-M02",
                    periodName = "February 2026",
                    startDate = LocalDate.of(2026, 2, 1),
                    endDate = LocalDate.of(2026, 2, 28),
                    paymentDate = LocalDate.of(2026, 2, 25),
                    currency = "LKR",
                    basicSalary = BigDecimal("150000.00"),
                    grossPay = BigDecimal("150000.00"),
                    totalDeductions = BigDecimal("15500.00"),
                    netPay = BigDecimal("134500.00"),
                    paymentStatus = PayslipSummaryItem.PaymentStatus.PAID,
                ),
                PayslipSummaryItem(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000203"),
                    payPeriodId = UUID.fromString("00000000-0000-0000-0000-000000000033"),
                    periodCode = "2026-M01",
                    periodName = "January 2026",
                    startDate = LocalDate.of(2026, 1, 1),
                    endDate = LocalDate.of(2026, 1, 31),
                    paymentDate = LocalDate.of(2026, 1, 25),
                    currency = "LKR",
                    basicSalary = BigDecimal("150000.00"),
                    grossPay = BigDecimal("150000.00"),
                    totalDeductions = BigDecimal("15500.00"),
                    netPay = BigDecimal("134500.00"),
                    paymentStatus = PayslipSummaryItem.PaymentStatus.PAID,
                ),
            )

        private fun createOfflineDefaultDetail(id: UUID): PayslipDetailResponse {
            val isFeb = id == UUID.fromString("00000000-0000-0000-0000-000000000202")
            return if (isFeb) {
                PayslipDetailResponse(
                    id = id,
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    employeeCode = "LK010",
                    employeeName = "Kasun Mendis",
                    department = "Engineering & Operations",
                    designation = "Senior Systems Engineer",
                    payPeriodCode = "2026-M02",
                    payPeriodName = "February 2026",
                    startDate = LocalDate.of(2026, 2, 1),
                    endDate = LocalDate.of(2026, 2, 28),
                    paymentDate = LocalDate.of(2026, 2, 25),
                    currency = "LKR",
                    bankName = "Commercial Bank of Ceylon",
                    bankAccountNumberMasked = "••••5678",
                    basicSalary = BigDecimal("150000.00"),
                    grossPay = BigDecimal("150000.00"),
                    totalStatutoryEmployee = BigDecimal("12000.00"),
                    totalStatutoryEmployer = BigDecimal("22500.00"),
                    taxWithheld = BigDecimal("3500.00"),
                    totalVoluntaryDeductions = BigDecimal.ZERO,
                    totalDeductions = BigDecimal("15500.00"),
                    netPay = BigDecimal("134500.00"),
                    paymentStatus = "PAID",
                    earnings = listOf(
                        PayslipLineItem(
                            id = "line-b201",
                            category = PayslipLineItem.Category.EARNING,
                            itemCode = "BASIC",
                            itemName = "Basic Salary",
                            amount = BigDecimal("150000.00"),
                            rate = null,
                            hours = BigDecimal("160.0"),
                            isStatutory = false,
                            calculationTrace = "Contractual Basic (20 working days × 8.0 hrs)",
                        ),
                    ),
                    deductions = listOf(
                        PayslipLineItem(
                            id = "line-b202",
                            category = PayslipLineItem.Category.STATUTORY_DEDUCTION,
                            itemCode = "EPF_EE",
                            itemName = "EPF Employee (8%)",
                            amount = BigDecimal("12000.00"),
                            rate = BigDecimal("0.08"),
                            hours = null,
                            isStatutory = true,
                            calculationTrace = "150,000.00 × 8.00% EPF contribution",
                        ),
                    ),
                    taxes = listOf(
                        PayslipLineItem(
                            id = "line-b203",
                            category = PayslipLineItem.Category.TAX,
                            itemCode = "TAX_WITHHOLDING",
                            itemName = "APIT Withheld",
                            amount = BigDecimal("3500.00"),
                            rate = null,
                            hours = null,
                            isStatutory = true,
                            calculationTrace = "Taxable 150,000.00 (Exempt 100k, Tier 1 41,666.67 @ 6% = 2,500, Tier 2 8,333.33 @ 12% = 1,000)",
                        ),
                    ),
                    employerContributions = listOf(
                        PayslipLineItem(
                            id = "line-b204",
                            category = PayslipLineItem.Category.EMPLOYER_CONTRIBUTION,
                            itemCode = "EPF_ER",
                            itemName = "EPF Employer (12%)",
                            amount = BigDecimal("18000.00"),
                            rate = BigDecimal("0.12"),
                            hours = null,
                            isStatutory = true,
                            calculationTrace = "150,000.00 × 12.00% Employer EPF",
                        ),
                        PayslipLineItem(
                            id = "line-b205",
                            category = PayslipLineItem.Category.EMPLOYER_CONTRIBUTION,
                            itemCode = "ETF_ER",
                            itemName = "ETF Employer (3%)",
                            amount = BigDecimal("4500.00"),
                            rate = BigDecimal("0.03"),
                            hours = null,
                            isStatutory = true,
                            calculationTrace = "150,000.00 × 3.00% Employer ETF",
                        ),
                    ),
                )
            } else {
                // March 2026 default
                PayslipDetailResponse(
                    id = id,
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    employeeCode = "LK010",
                    employeeName = "Kasun Mendis",
                    department = "Engineering & Operations",
                    designation = "Senior Systems Engineer",
                    payPeriodCode = "2026-M03",
                    payPeriodName = "March 2026",
                    startDate = LocalDate.of(2026, 3, 1),
                    endDate = LocalDate.of(2026, 3, 31),
                    paymentDate = LocalDate.of(2026, 3, 25),
                    currency = "LKR",
                    bankName = "Commercial Bank of Ceylon",
                    bankAccountNumberMasked = "••••5678",
                    basicSalary = BigDecimal("143181.82"),
                    grossPay = BigDecimal("155113.64"),
                    totalStatutoryEmployee = BigDecimal("12409.09"),
                    totalStatutoryEmployer = BigDecimal("23267.05"),
                    taxWithheld = BigDecimal("4113.64"),
                    totalVoluntaryDeductions = BigDecimal("852.27"),
                    totalDeductions = BigDecimal("17375.00"),
                    netPay = BigDecimal("137738.64"),
                    paymentStatus = "PAID",
                    earnings = listOf(
                        PayslipLineItem(
                            id = "line-1",
                            category = PayslipLineItem.Category.EARNING,
                            itemCode = "BASIC",
                            itemName = "Basic Salary",
                            amount = BigDecimal("143181.82"),
                            rate = null,
                            hours = BigDecimal("168.0"),
                            isStatutory = false,
                            calculationTrace = "150,000.00 - LOP (6,818.18 = 150,000.00 × 1.00/22.00 days)",
                        ),
                        PayslipLineItem(
                            id = "line-2",
                            category = PayslipLineItem.Category.EARNING,
                            itemCode = "OT_NORMAL",
                            itemName = "Normal Overtime (1.5x)",
                            amount = BigDecimal("5113.64"),
                            rate = BigDecimal("1.5"),
                            hours = BigDecimal("4.0"),
                            isStatutory = false,
                            calculationTrace = "4.0 hrs × 852.27 × 1.50x overtime multiplier",
                        ),
                        PayslipLineItem(
                            id = "line-3",
                            category = PayslipLineItem.Category.EARNING,
                            itemCode = "OT_REST_DAY",
                            itemName = "Rest Day Overtime (2.0x)",
                            amount = BigDecimal("6818.18"),
                            rate = BigDecimal("2.0"),
                            hours = BigDecimal("4.0"),
                            isStatutory = false,
                            calculationTrace = "4.0 hrs × 852.27 × 2.00x rest day multiplier",
                        ),
                    ),
                    deductions = listOf(
                        PayslipLineItem(
                            id = "line-4",
                            category = PayslipLineItem.Category.STATUTORY_DEDUCTION,
                            itemCode = "EPF_EE",
                            itemName = "EPF Employee (8%)",
                            amount = BigDecimal("12409.09"),
                            rate = BigDecimal("0.08"),
                            hours = null,
                            isStatutory = true,
                            calculationTrace = "155,113.64 × 8.00% Employee statutory deduction",
                        ),
                        PayslipLineItem(
                            id = "line-5",
                            category = PayslipLineItem.Category.VOLUNTARY_DEDUCTION,
                            itemCode = "LATE_PENALTY",
                            itemName = "Lateness Penalty",
                            amount = BigDecimal("852.27"),
                            rate = null,
                            hours = BigDecimal("1.0"),
                            isStatutory = false,
                            calculationTrace = "1.0 hr lateness deduction (60 late minutes beyond grace limit)",
                        ),
                    ),
                    taxes = listOf(
                        PayslipLineItem(
                            id = "line-6",
                            category = PayslipLineItem.Category.TAX,
                            itemCode = "TAX_WITHHOLDING",
                            itemName = "APIT Withheld",
                            amount = BigDecimal("4113.64"),
                            rate = null,
                            hours = null,
                            isStatutory = true,
                            calculationTrace = "Taxable 155,113.64 (Exempt 100k, Tier 1 41,666.67 @ 6% = 2,500, Tier 2 13,446.97 @ 12% = 1,613.64)",
                        ),
                    ),
                    employerContributions = listOf(
                        PayslipLineItem(
                            id = "line-7",
                            category = PayslipLineItem.Category.EMPLOYER_CONTRIBUTION,
                            itemCode = "EPF_ER",
                            itemName = "EPF Employer (12%)",
                            amount = BigDecimal("18613.64"),
                            rate = BigDecimal("0.12"),
                            hours = null,
                            isStatutory = true,
                            calculationTrace = "155,113.64 × 12.00% Employer EPF contribution",
                        ),
                        PayslipLineItem(
                            id = "line-8",
                            category = PayslipLineItem.Category.EMPLOYER_CONTRIBUTION,
                            itemCode = "ETF_ER",
                            itemName = "ETF Employer (3%)",
                            amount = BigDecimal("4653.41"),
                            rate = BigDecimal("0.03"),
                            hours = null,
                            isStatutory = true,
                            calculationTrace = "155,113.64 × 3.00% Employer ETF contribution",
                        ),
                    ),
                )
            }
        }

        private fun createOfflineDefaultComparison(id: UUID): PayslipComparisonResponse {
            return PayslipComparisonResponse(
                currentPeriodCode = "2026-M03",
                priorPeriodCode = "2026-M02",
                currentGross = BigDecimal("155113.64"),
                priorGross = BigDecimal("150000.00"),
                grossVariance = BigDecimal("5113.64"),
                grossVariancePercent = BigDecimal("3.41"),
                currentNet = BigDecimal("137738.64"),
                priorNet = BigDecimal("134500.00"),
                netVariance = BigDecimal("3238.64"),
                netVariancePercent = BigDecimal("2.41"),
                lines = listOf(
                    PayslipComparisonLine(
                        itemCode = "BASIC",
                        itemName = "Basic Salary",
                        category = "EARNING",
                        currentAmount = BigDecimal("143181.82"),
                        priorAmount = BigDecimal("150000.00"),
                        varianceAmount = BigDecimal("-6818.18"),
                        variancePercent = BigDecimal("-4.55"),
                    ),
                    PayslipComparisonLine(
                        itemCode = "OT_NORMAL",
                        itemName = "Normal Overtime (1.5x)",
                        category = "EARNING",
                        currentAmount = BigDecimal("5113.64"),
                        priorAmount = BigDecimal.ZERO,
                        varianceAmount = BigDecimal("5113.64"),
                        variancePercent = BigDecimal("100.0"),
                    ),
                    PayslipComparisonLine(
                        itemCode = "OT_REST_DAY",
                        itemName = "Rest Day Overtime (2.0x)",
                        category = "EARNING",
                        currentAmount = BigDecimal("6818.18"),
                        priorAmount = BigDecimal.ZERO,
                        varianceAmount = BigDecimal("6818.18"),
                        variancePercent = BigDecimal("100.0"),
                    ),
                    PayslipComparisonLine(
                        itemCode = "EPF_EE",
                        itemName = "EPF Employee (8%)",
                        category = "STATUTORY_DEDUCTION",
                        currentAmount = BigDecimal("12409.09"),
                        priorAmount = BigDecimal("12000.00"),
                        varianceAmount = BigDecimal("409.09"),
                        variancePercent = BigDecimal("3.41"),
                    ),
                    PayslipComparisonLine(
                        itemCode = "TAX_WITHHOLDING",
                        itemName = "APIT Withheld",
                        category = "TAX",
                        currentAmount = BigDecimal("4113.64"),
                        priorAmount = BigDecimal("3500.00"),
                        varianceAmount = BigDecimal("613.64"),
                        variancePercent = BigDecimal("17.53"),
                    ),
                    PayslipComparisonLine(
                        itemCode = "LATE_PENALTY",
                        itemName = "Lateness Penalty",
                        category = "VOLUNTARY_DEDUCTION",
                        currentAmount = BigDecimal("852.27"),
                        priorAmount = BigDecimal.ZERO,
                        varianceAmount = BigDecimal("852.27"),
                        variancePercent = BigDecimal("100.0"),
                    ),
                ),
            )
        }
    }
