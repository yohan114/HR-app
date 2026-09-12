package com.hr.loan.internal

import com.hr.identity.Caller
import com.hr.loan.EmployeeLoanDetailResponse
import com.hr.loan.EmployeeLoansResponse
import com.hr.loan.LoanApplicationRequest
import com.hr.loan.LoanEligibilityRequest
import com.hr.loan.LoanEligibilityResponse
import com.hr.loan.LoanSettlementRequest
import com.hr.loan.LoanSettlementResponse
import com.hr.loan.LoanTypesResponse
import com.hr.shared.api.NotFoundException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/v1/loans")
@PreAuthorize("isAuthenticated()")
class LoanController(
    private val loanService: LoanService,
) {

    @GetMapping("/types")
    fun getLoanTypes(): LoanTypesResponse {
        return loanService.getLoanTypes()
    }

    @GetMapping("/me")
    fun getMyLoans(
        @AuthenticationPrincipal jwt: Jwt?,
    ): EmployeeLoansResponse {
        val employeeId = resolveEmployeeId(jwt)
        return loanService.getMyLoans(employeeId)
    }

    @GetMapping("/me/{id}")
    fun getMyLoanDetails(
        @PathVariable("id") id: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): EmployeeLoanDetailResponse {
        val employeeId = resolveEmployeeId(jwt)
        return loanService.getMyLoanDetails(employeeId, id)
    }

    @PostMapping("/eligibility")
    fun checkLoanEligibility(
        @RequestBody request: LoanEligibilityRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): LoanEligibilityResponse {
        val employeeId = resolveEmployeeId(jwt)
        return loanService.checkEligibility(employeeId, request)
    }

    @PostMapping("/applications")
    fun submitLoanApplication(
        @RequestBody request: LoanApplicationRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): EmployeeLoanDetailResponse {
        val employeeId = resolveEmployeeId(jwt)
        return loanService.applyForLoan(employeeId, request)
    }

    @PostMapping("/me/{id}/settle")
    fun requestLoanSettlement(
        @PathVariable("id") id: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody(required = false) request: LoanSettlementRequest?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): LoanSettlementResponse {
        val employeeId = resolveEmployeeId(jwt)
        return loanService.settleLoan(employeeId, id, request)
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
}
