package com.hr.payroll.internal

import com.hr.identity.Caller
import com.hr.payroll.LineCategory
import com.hr.shared.api.NotFoundException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// ---------------------------------------------------------------------------
// Response DTOs matching OpenAPI contracts
// ---------------------------------------------------------------------------

data class PayslipSummaryItemResponse(
    val id: UUID,
    val payPeriodId: UUID,
    val periodCode: String,
    val periodName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val paymentDate: LocalDate,
    val currency: String,
    val basicSalary: Double,
    val grossPay: Double,
    val totalDeductions: Double,
    val netPay: Double,
    val paymentStatus: String,
)

data class PayslipsResponse(
    val payslips: List<PayslipSummaryItemResponse>,
)

data class PayslipLineItemResponse(
    val id: String,
    val category: String,
    val itemCode: String,
    val itemName: String,
    val amount: Double,
    val rate: Double? = null,
    val hours: Double? = null,
    val isStatutory: Boolean = false,
    val calculationTrace: String? = null,
)

data class PayslipDetailResponse(
    val id: UUID,
    val employeeId: UUID,
    val employeeCode: String,
    val employeeName: String,
    val department: String,
    val designation: String,
    val payPeriodCode: String,
    val payPeriodName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val paymentDate: LocalDate,
    val currency: String,
    val bankName: String?,
    val bankAccountNumberMasked: String?,
    val basicSalary: Double,
    val grossPay: Double,
    val totalStatutoryEmployee: Double,
    val totalStatutoryEmployer: Double,
    val taxWithheld: Double,
    val totalVoluntaryDeductions: Double,
    val totalDeductions: Double,
    val netPay: Double,
    val paymentStatus: String,
    val earnings: List<PayslipLineItemResponse>,
    val deductions: List<PayslipLineItemResponse>,
    val taxes: List<PayslipLineItemResponse>,
    val employerContributions: List<PayslipLineItemResponse>,
)

data class PayslipComparisonLineResponse(
    val itemCode: String,
    val itemName: String,
    val category: String,
    val currentAmount: Double,
    val priorAmount: Double,
    val varianceAmount: Double,
    val variancePercent: Double,
)

data class PayslipComparisonResponse(
    val currentPeriodCode: String,
    val priorPeriodCode: String,
    val currentGross: Double,
    val priorGross: Double,
    val grossVariance: Double,
    val grossVariancePercent: Double,
    val currentNet: Double,
    val priorNet: Double,
    val netVariance: Double,
    val netVariancePercent: Double,
    val lines: List<PayslipComparisonLineResponse>,
)

// ---------------------------------------------------------------------------
// Controller
// ---------------------------------------------------------------------------

@RestController
@RequestMapping("/v1/payroll/me/payslips")
@PreAuthorize("isAuthenticated()")
class EmployeePayslipController(
    private val payrollResultRepository: PayrollResultRepository,
    private val payrollResultLineRepository: PayrollResultLineRepository,
    private val payrollRunRepository: PayrollRunRepository,
    private val payPeriodRepository: PayPeriodRepository,
) {

    @GetMapping
    fun getMyPayslips(
        @AuthenticationPrincipal jwt: Jwt?,
    ): PayslipsResponse {
        val employeeId = resolveEmployeeId(jwt)
        val results = payrollResultRepository.findAllByEmployeeIdOrderByCreatedAtDesc(employeeId)

        val summaries = results.map { res ->
            val run = payrollRunRepository.findById(res.payrollRunId).orElse(null)
            val period = run?.let { payPeriodRepository.findById(it.payPeriodId).orElse(null) }

            val periodCode = period?.code ?: "PER-UNKNOWN"
            val periodName = period?.let { formatPeriodName(it.startDate, it.endDate) } ?: periodCode
            val startDate = period?.startDate ?: LocalDate.now()
            val endDate = period?.endDate ?: LocalDate.now()
            val paymentDate = period?.paymentDate ?: endDate

            val totalDeductions = res.totalStatutoryEmployee
                .add(res.taxWithheld)
                .add(res.totalVoluntaryDeductions)

            PayslipSummaryItemResponse(
                id = res.id,
                payPeriodId = period?.id ?: res.payrollRunId,
                periodCode = periodCode,
                periodName = periodName,
                startDate = startDate,
                endDate = endDate,
                paymentDate = paymentDate,
                currency = res.currency,
                basicSalary = res.basicSalary.toDouble(),
                grossPay = res.grossPay.toDouble(),
                totalDeductions = totalDeductions.toDouble(),
                netPay = res.netPay.toDouble(),
                paymentStatus = res.paymentStatus,
            )
        }

        return PayslipsResponse(payslips = summaries)
    }

    @GetMapping("/{id}")
    fun getPayslipDetails(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): PayslipDetailResponse {
        val employeeId = resolveEmployeeId(jwt)
        val res = payrollResultRepository.findByIdAndEmployeeId(id, employeeId)
            .orElseThrow { NotFoundException("Payslip not found: $id") }

        val run = payrollRunRepository.findById(res.payrollRunId).orElse(null)
        val period = run?.let { payPeriodRepository.findById(it.payPeriodId).orElse(null) }

        val periodCode = period?.code ?: "PER-UNKNOWN"
        val periodName = period?.let { formatPeriodName(it.startDate, it.endDate) } ?: periodCode
        val startDate = period?.startDate ?: LocalDate.now()
        val endDate = period?.endDate ?: LocalDate.now()
        val paymentDate = period?.paymentDate ?: endDate

        val lines = payrollResultLineRepository.findAllByPayrollResultId(id)

        val earnings = lines.filter { it.lineCategory == LineCategory.EARNING }.map { it.toResponse() }
        val deductions = lines.filter {
            it.lineCategory == LineCategory.STATUTORY_DEDUCTION || it.lineCategory == LineCategory.VOLUNTARY_DEDUCTION
        }.map { it.toResponse() }
        val taxes = lines.filter { it.lineCategory == LineCategory.TAX }.map { it.toResponse() }
        val employer = lines.filter { it.lineCategory == LineCategory.EMPLOYER_CONTRIBUTION }.map { it.toResponse() }

        val totalDeductions = res.totalStatutoryEmployee
            .add(res.taxWithheld)
            .add(res.totalVoluntaryDeductions)

        return PayslipDetailResponse(
            id = res.id,
            employeeId = res.employeeId,
            employeeCode = res.employeeCode,
            employeeName = res.employeeName,
            department = "Engineering & Operations",
            designation = "Senior Systems Engineer",
            payPeriodCode = periodCode,
            payPeriodName = periodName,
            startDate = startDate,
            endDate = endDate,
            paymentDate = paymentDate,
            currency = res.currency,
            bankName = "Commercial Bank of Ceylon",
            bankAccountNumberMasked = "••••5678",
            basicSalary = res.basicSalary.toDouble(),
            grossPay = res.grossPay.toDouble(),
            totalStatutoryEmployee = res.totalStatutoryEmployee.toDouble(),
            totalStatutoryEmployer = res.totalStatutoryEmployer.toDouble(),
            taxWithheld = res.taxWithheld.toDouble(),
            totalVoluntaryDeductions = res.totalVoluntaryDeductions.toDouble(),
            totalDeductions = totalDeductions.toDouble(),
            netPay = res.netPay.toDouble(),
            paymentStatus = res.paymentStatus,
            earnings = earnings,
            deductions = deductions,
            taxes = taxes,
            employerContributions = employer,
        )
    }

    @GetMapping("/{id}/comparison")
    fun getPayslipComparison(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): PayslipComparisonResponse {
        val employeeId = resolveEmployeeId(jwt)
        val currentRes = payrollResultRepository.findByIdAndEmployeeId(id, employeeId)
            .orElseThrow { NotFoundException("Payslip not found: $id") }

        val currentRun = payrollRunRepository.findById(currentRes.payrollRunId).orElse(null)
        val currentPeriod = currentRun?.let { payPeriodRepository.findById(it.payPeriodId).orElse(null) }
        val currentPeriodCode = currentPeriod?.code ?: "CURRENT"

        // Find all payslips for this employee to locate the prior period
        val allResults = payrollResultRepository.findAllByEmployeeIdOrderByCreatedAtDesc(employeeId)
        val currentIndex = allResults.indexOfFirst { it.id == id }
        val priorRes = if (currentIndex >= 0 && currentIndex + 1 < allResults.size) {
            allResults[currentIndex + 1]
        } else null

        val priorRun = priorRes?.let { payrollRunRepository.findById(it.payrollRunId).orElse(null) }
        val priorPeriod = priorRun?.let { payPeriodRepository.findById(it.payPeriodId).orElse(null) }
        val priorPeriodCode = priorPeriod?.code ?: "PRIOR"

        val currentGross = currentRes.grossPay.toDouble()
        val priorGross = priorRes?.grossPay?.toDouble() ?: currentGross
        val grossVariance = BigDecimal.valueOf(currentGross - priorGross).setScale(2, RoundingMode.HALF_UP).toDouble()
        val grossVariancePercent = if (priorGross != 0.0) {
            BigDecimal.valueOf((grossVariance / priorGross) * 100.0)
                .setScale(2, RoundingMode.HALF_UP).toDouble()
        } else 0.0

        val currentNet = currentRes.netPay.toDouble()
        val priorNet = priorRes?.netPay?.toDouble() ?: currentNet
        val netVariance = BigDecimal.valueOf(currentNet - priorNet).setScale(2, RoundingMode.HALF_UP).toDouble()
        val netVariancePercent = if (priorNet != 0.0) {
            BigDecimal.valueOf((netVariance / priorNet) * 100.0)
                .setScale(2, RoundingMode.HALF_UP).toDouble()
        } else 0.0

        // Itemized comparison lines
        val currentLines = payrollResultLineRepository.findAllByPayrollResultId(id)
        val priorLines = priorRes?.let { payrollResultLineRepository.findAllByPayrollResultId(it.id) } ?: emptyList()

        val itemCodes = (currentLines.map { it.itemCode } + priorLines.map { it.itemCode }).distinct()

        val comparisonLines = itemCodes.map { code ->
            val curr = currentLines.find { it.itemCode == code }
            val prev = priorLines.find { it.itemCode == code }

            val itemName = curr?.itemName ?: prev?.itemName ?: code
            val category = (curr?.lineCategory ?: prev?.lineCategory ?: LineCategory.EARNING).name
            val cAmt = curr?.amount?.toDouble() ?: 0.0
            val pAmt = prev?.amount?.toDouble() ?: 0.0
            val varAmt = BigDecimal.valueOf(cAmt - pAmt).setScale(2, RoundingMode.HALF_UP).toDouble()
            val varPct = if (pAmt != 0.0) {
                BigDecimal.valueOf((varAmt / pAmt) * 100.0)
                    .setScale(2, RoundingMode.HALF_UP).toDouble()
            } else if (cAmt != 0.0) 100.0 else 0.0

            PayslipComparisonLineResponse(
                itemCode = code,
                itemName = itemName,
                category = category,
                currentAmount = cAmt,
                priorAmount = pAmt,
                varianceAmount = varAmt,
                variancePercent = varPct,
            )
        }

        return PayslipComparisonResponse(
            currentPeriodCode = currentPeriodCode,
            priorPeriodCode = priorPeriodCode,
            currentGross = currentGross,
            priorGross = priorGross,
            grossVariance = grossVariance,
            grossVariancePercent = grossVariancePercent,
            currentNet = currentNet,
            priorNet = priorNet,
            netVariance = netVariance,
            netVariancePercent = netVariancePercent,
            lines = comparisonLines,
        )
    }

    private fun resolveEmployeeId(jwt: Jwt?): UUID {
        if (jwt != null) {
            val caller = runCatching { Caller.from(jwt) }.getOrNull()
            if (caller?.employeeId != null) {
                return caller.employeeId
            }
        }
        throw NotFoundException("NO_EMPLOYEE_RECORD", "This account is not linked to an employee record")
    }

    private fun formatPeriodName(startDate: LocalDate, endDate: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        return startDate.format(formatter)
    }

    private fun PayrollResultLine.toResponse(): PayslipLineItemResponse {
        return PayslipLineItemResponse(
            id = this.id.toString(),
            category = this.lineCategory.name,
            itemCode = this.itemCode,
            itemName = this.itemName,
            amount = this.amount.toDouble(),
            rate = extractRateFromTrace(this.calculationTrace),
            hours = extractHoursFromTrace(this.calculationTrace),
            isStatutory = this.isStatutory,
            calculationTrace = this.calculationTrace,
        )
    }

    private fun extractRateFromTrace(trace: String?): Double? {
        if (trace == null) return null
        return when {
            trace.contains("1.50x") || trace.contains("1.5x") -> 1.5
            trace.contains("2.00x") || trace.contains("2.0x") -> 2.0
            trace.contains("8%") || trace.contains("0.08") -> 0.08
            trace.contains("12%") || trace.contains("0.12") -> 0.12
            trace.contains("3%") || trace.contains("0.03") -> 0.03
            else -> null
        }
    }

    private fun extractHoursFromTrace(trace: String?): Double? {
        if (trace == null) return null
        val match = Regex("""(\d+(\.\d+)?)\s*hrs?""").find(trace)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }
}
