package com.hr.loan.internal

import com.hr.employee.EmployeeLookupService
import com.hr.loan.AmortizationCalculationResult
import com.hr.loan.EmployeeLoanDetailResponse
import com.hr.loan.EmployeeLoanItemResponse
import com.hr.loan.EmployeeLoansResponse
import com.hr.loan.InterestMethod
import com.hr.loan.LoanApplicationRequest
import com.hr.loan.LoanEligibilityRequest
import com.hr.loan.LoanEligibilityResponse
import com.hr.loan.LoanRepaymentScheduleItemResponse
import com.hr.loan.LoanSettlementRequest
import com.hr.loan.LoanSettlementResponse
import com.hr.loan.LoanStatus
import com.hr.loan.LoanTypeItemResponse
import com.hr.loan.LoanTypesResponse
import com.hr.loan.ScheduleStatus
import com.hr.payroll.PayrollLookupService
import com.hr.shared.api.BadRequestException
import com.hr.shared.api.BusinessRuleException
import com.hr.shared.api.ErrorCode
import com.hr.shared.api.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@Service
class LoanService(
    private val loanTypeRepository: LoanTypeRepository,
    private val employeeLoanRepository: EmployeeLoanRepository,
    private val loanRepaymentScheduleRepository: LoanRepaymentScheduleRepository,
    private val employeeLookupService: EmployeeLookupService,
    private val payrollLookupService: PayrollLookupService,
    private val calculator: LoanScheduleCalculator,
    private val eligibilityEngine: LoanEligibilityEngine,
) {

    @Transactional
    fun getLoanTypes(): LoanTypesResponse {
        var types = loanTypeRepository.findAllByIsActiveTrueOrderByCodeAsc()
        if (types.isEmpty()) {
            types = seedDefaultLoanTypes()
        }
        return LoanTypesResponse(types = types.map { it.toResponse() })
    }

    @Transactional(readOnly = true)
    fun getMyLoans(employeeId: UUID): EmployeeLoansResponse {
        val loans = employeeLoanRepository.findAllByEmployeeIdOrderByCreatedAtDesc(employeeId)
        val loanTypesMap = loanTypeRepository.findAll().associateBy { it.id }
        val mappedLoans = loans.map { loan ->
            val type = loanTypesMap[loan.loanTypeId]
            loan.toResponse(type)
        }
        val activeLoans = mappedLoans.filter { it.status == LoanStatus.ACTIVE }
        val totalOutstanding = activeLoans.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.remainingBalance) }

        return EmployeeLoansResponse(
            totalOutstandingBalance = totalOutstanding,
            activeLoansCount = activeLoans.size,
            loans = mappedLoans,
        )
    }


    @Transactional(readOnly = true)
    fun getMyLoanDetails(employeeId: UUID, loanId: UUID): EmployeeLoanDetailResponse {
        val loan = employeeLoanRepository.findByIdAndEmployeeId(loanId, employeeId)
            ?: throw NotFoundException(ErrorCode.NOT_FOUND, "Loan not found: $loanId")

        val loanType = loanTypeRepository.findById(loan.loanTypeId).orElse(null)
        val scheduleItems = loanRepaymentScheduleRepository.findAllByLoanIdOrderByInstallmentNumberAsc(loanId)

        return EmployeeLoanDetailResponse(
            loan = loan.toResponse(loanType),
            schedule = scheduleItems.map { it.toResponse() },
        )
    }

    @Transactional(readOnly = true)
    fun checkEligibility(employeeId: UUID, request: LoanEligibilityRequest): LoanEligibilityResponse {
        val joinDate = employeeLookupService.findById(employeeId)?.joinDate ?: LocalDate.now().minusYears(2)
        val loanType = loanTypeRepository.findById(request.loanTypeId).orElseThrow {
            NotFoundException(ErrorCode.NOT_FOUND, "Loan type not found: ${request.loanTypeId}")
        }

        val basicSalary = resolveBasicSalary(employeeId)
        val activeLoansCount = employeeLoanRepository.countByEmployeeIdAndStatusIn(
            employeeId,
            listOf(LoanStatus.SUBMITTED, LoanStatus.APPROVED, LoanStatus.ACTIVE),
        ).toInt()

        return eligibilityEngine.evaluate(
            joinDate = joinDate,
            loanType = loanType,
            requestedPrincipal = request.principalAmount,
            tenureMonths = request.tenureMonths,
            basicSalary = basicSalary,
            activeLoansCount = activeLoansCount,
        )
    }


    @Transactional
    fun applyForLoan(employeeId: UUID, request: LoanApplicationRequest): EmployeeLoanDetailResponse {
        val joinDate = employeeLookupService.findById(employeeId)?.joinDate ?: LocalDate.now().minusYears(2)
        val loanType = loanTypeRepository.findById(request.loanTypeId).orElseThrow {
            NotFoundException(ErrorCode.NOT_FOUND, "Loan type not found: ${request.loanTypeId}")
        }

        val basicSalary = resolveBasicSalary(employeeId)
        val activeLoansCount = employeeLoanRepository.countByEmployeeIdAndStatusIn(
            employeeId,
            listOf(LoanStatus.SUBMITTED, LoanStatus.APPROVED, LoanStatus.ACTIVE),
        ).toInt()

        val eligibility = eligibilityEngine.evaluate(
            joinDate = joinDate,
            loanType = loanType,
            requestedPrincipal = request.principalAmount,
            tenureMonths = request.tenureMonths,
            basicSalary = basicSalary,
            activeLoansCount = activeLoansCount,
        )

        if (!eligibility.eligible) {
            val firstReason = eligibility.reasons.firstOrNull() ?: "Loan eligibility criteria not met"
            throw BusinessRuleException(
                code = ErrorCode.BUSINESS_RULE_VIOLATION,
                message = firstReason,
                details = mapOf(
                    "reasons" to eligibility.reasons,
                    "maxAllowedPrincipal" to eligibility.maxAllowedPrincipal,
                ),
            )
        }

        val calculation = calculator.calculateSchedule(
            principal = request.principalAmount,
            annualInterestRate = loanType.annualInterestRate,
            tenureMonths = request.tenureMonths,
            method = loanType.interestMethod,
            startDate = LocalDate.now(),
        )

        val loanCode = "LN-${LocalDate.now().year}-${UUID.randomUUID().toString().take(6).uppercase()}"

        val employeeLoan = EmployeeLoan(
            employeeId = employeeId,
            loanTypeId = loanType.id,
            loanCode = loanCode,
            principalAmount = request.principalAmount.setScale(2, RoundingMode.HALF_UP),
            interestMethod = loanType.interestMethod,
            annualInterestRate = loanType.annualInterestRate,
            tenureMonths = request.tenureMonths,
            monthlyInstallment = calculation.monthlyInstallment,
            totalInterest = calculation.totalInterest,
            totalRepayable = calculation.totalRepayable,
            totalRepaid = BigDecimal.ZERO.setScale(2),
            remainingBalance = calculation.totalRepayable,
            reason = request.reason,
            status = LoanStatus.ACTIVE,
            disbursedDate = LocalDate.now(),
        )

        val savedLoan = employeeLoanRepository.save(employeeLoan)

        val scheduleEntities = calculation.schedule.map { line ->
            LoanRepaymentSchedule(
                loanId = savedLoan.id,
                installmentNumber = line.installmentNumber,
                dueDate = line.dueDate,
                principalAmount = line.principalAmount,
                interestAmount = line.interestAmount,
                totalInstallment = line.totalInstallment,
                status = ScheduleStatus.PENDING,
            )
        }

        val savedSchedule = loanRepaymentScheduleRepository.saveAll(scheduleEntities)

        return EmployeeLoanDetailResponse(
            loan = savedLoan.toResponse(loanType),
            schedule = savedSchedule.map { it.toResponse() },
        )
    }

    @Transactional
    fun settleLoan(employeeId: UUID, loanId: UUID, request: LoanSettlementRequest?): LoanSettlementResponse {
        val loan = employeeLoanRepository.findByIdAndEmployeeId(loanId, employeeId)
            ?: throw NotFoundException(ErrorCode.NOT_FOUND, "Loan not found: $loanId")

        if (loan.status != LoanStatus.ACTIVE) {
            throw BadRequestException(
                code = ErrorCode.BUSINESS_RULE_VIOLATION,
                message = "Only active loans can be settled early. Current status: ${loan.status}",
            )
        }

        val pendingSchedules = loanRepaymentScheduleRepository.findAllByLoanIdAndStatus(loanId, ScheduleStatus.PENDING)

        // Settlement amount is the sum of remaining unpaid principal
        val payoffPrincipal = pendingSchedules.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(item.principalAmount)
        }.setScale(2, RoundingMode.HALF_UP)

        // Waived future interest is the sum of remaining unaccrued interest
        val waivedInterest = pendingSchedules.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(item.interestAmount)
        }.setScale(2, RoundingMode.HALF_UP)

        // Mark remaining schedules as WAIVED
        pendingSchedules.forEach { item ->
            item.status = ScheduleStatus.WAIVED
            item.deductedDate = LocalDate.now()
        }
        loanRepaymentScheduleRepository.saveAll(pendingSchedules)

        // Update loan status to SETTLED
        loan.totalRepaid = loan.totalRepaid.add(payoffPrincipal).setScale(2, RoundingMode.HALF_UP)
        loan.remainingBalance = BigDecimal.ZERO.setScale(2)
        loan.status = LoanStatus.SETTLED
        loan.settledDate = LocalDate.now()
        employeeLoanRepository.save(loan)

        return LoanSettlementResponse(
            loanId = loan.id,
            settledDate = LocalDate.now(),
            settlementAmountPaid = payoffPrincipal,
            remainingBalance = BigDecimal.ZERO.setScale(2),
            status = LoanStatus.SETTLED.name,
        )

    }

    private fun resolveBasicSalary(employeeId: UUID): BigDecimal {
        return payrollLookupService.findLatestBasicSalary(employeeId) ?: BigDecimal("50000.00")
    }

    private fun seedDefaultLoanTypes(): List<LoanType> {
        val festivalAdvance = LoanType(
            code = "FESTIVAL_ADVANCE",
            name = "Festival Advance",
            description = "Zero-interest advance for cultural and seasonal festivals",
            interestMethod = InterestMethod.ZERO_INTEREST,
            annualInterestRate = BigDecimal("0.00"),
            minTenureMonths = 1,
            maxTenureMonths = 10,
            minPrincipal = BigDecimal("5000.00"),
            maxPrincipal = BigDecimal("100000.00"),
            salaryMultipleLimit = BigDecimal("1.00"),
            minServiceMonths = 3,
            maxActiveLoansPerEmployee = 1,
            isActive = true,
        )

        val distressLoan = LoanType(
            code = "DISTRESS_LOAN",
            name = "Emergency Distress Loan",
            description = "Low-interest financial relief for medical emergencies or personal hardship",
            interestMethod = InterestMethod.REDUCING_BALANCE,
            annualInterestRate = BigDecimal("4.50"),
            minTenureMonths = 6,
            maxTenureMonths = 36,
            minPrincipal = BigDecimal("10000.00"),
            maxPrincipal = BigDecimal("300000.00"),
            salaryMultipleLimit = BigDecimal("3.00"),
            minServiceMonths = 6,
            maxActiveLoansPerEmployee = 1,
            isActive = true,
        )

        val educationLoan = LoanType(
            code = "EDUCATION_LOAN",
            name = "Higher Education Loan",
            description = "Professional development and higher education course support",
            interestMethod = InterestMethod.FLAT_RATE,
            annualInterestRate = BigDecimal("6.00"),
            minTenureMonths = 12,
            maxTenureMonths = 48,
            minPrincipal = BigDecimal("20000.00"),
            maxPrincipal = BigDecimal("500000.00"),
            salaryMultipleLimit = BigDecimal("4.00"),
            minServiceMonths = 12,
            maxActiveLoansPerEmployee = 1,
            isActive = true,
        )

        return loanTypeRepository.saveAll(listOf(festivalAdvance, distressLoan, educationLoan))
    }

    private fun LoanType.toResponse(): LoanTypeItemResponse = LoanTypeItemResponse(
        id = this.id,
        code = this.code,
        name = this.name,
        interestMethod = this.interestMethod,
        annualInterestRate = this.annualInterestRate,
        minTenureMonths = this.minTenureMonths,
        maxTenureMonths = this.maxTenureMonths,
        minPrincipal = this.minPrincipal,
        maxPrincipal = this.maxPrincipal,
        salaryMultipleLimit = this.salaryMultipleLimit,
        minServiceMonths = this.minServiceMonths,
        maxActiveLoans = this.maxActiveLoansPerEmployee,
        description = this.description,
    )

    private fun EmployeeLoan.toResponse(loanType: LoanType?): EmployeeLoanItemResponse = EmployeeLoanItemResponse(
        id = this.id,
        loanCode = this.loanCode,
        loanTypeId = this.loanTypeId,
        loanTypeCode = loanType?.code ?: "LOAN",
        loanTypeName = loanType?.name ?: "Employee Loan",
        principalAmount = this.principalAmount,
        interestMethod = this.interestMethod,
        annualInterestRate = this.annualInterestRate,
        tenureMonths = this.tenureMonths,
        monthlyInstallment = this.monthlyInstallment,
        totalInterest = this.totalInterest,
        totalRepayable = this.totalRepayable,
        totalRepaid = this.totalRepaid,
        remainingBalance = this.remainingBalance,
        reason = this.reason,
        status = this.status,
        disbursedDate = this.disbursedDate,
        settledDate = this.settledDate,
    )

    private fun LoanRepaymentSchedule.toResponse(): LoanRepaymentScheduleItemResponse = LoanRepaymentScheduleItemResponse(
        installmentNumber = this.installmentNumber,
        dueDate = this.dueDate,
        principalAmount = this.principalAmount,
        interestAmount = this.interestAmount,
        totalInstallment = this.totalInstallment,
        status = this.status,
        deductedDate = this.deductedDate,
    )
}
