package com.hr.expense.internal

import com.hr.expense.*
import com.hr.identity.Caller
import com.hr.shared.api.NotFoundException
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/expenses")
@PreAuthorize("isAuthenticated()")
class ExpenseController(
    private val expenseService: ExpenseService,
) {

    @GetMapping("/categories")
    fun getExpenseCategories(): ExpenseCategoriesResponse {
        return expenseService.getExpenseCategories()
    }

    @GetMapping("/claims")
    fun getMyExpenseClaims(
        @RequestParam(value = "status", required = false) status: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ExpenseClaimsResponse {
        val employeeId = resolveEmployeeId(jwt)
        return expenseService.getMyExpenseClaims(employeeId, status)
    }

    @GetMapping("/claims/{id}")
    fun getMyExpenseClaimDetails(
        @PathVariable("id") id: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ExpenseClaimDetailResponse {
        val employeeId = resolveEmployeeId(jwt)
        return expenseService.getMyExpenseClaimDetails(employeeId, id)
    }

    @PostMapping("/claims")
    @ResponseStatus(HttpStatus.CREATED)
    fun submitExpenseClaim(
        @RequestBody request: ExpenseClaimSubmitRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ExpenseClaimDetailResponse {
        val employeeId = resolveEmployeeId(jwt)
        return expenseService.submitExpenseClaim(employeeId, request)
    }

    @PostMapping("/claims/{id}/cancel")
    fun cancelExpenseClaim(
        @PathVariable("id") id: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody(required = false) request: CancelExpenseClaimRequest?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ExpenseClaimItemResponse {
        val employeeId = resolveEmployeeId(jwt)
        return expenseService.cancelExpenseClaim(employeeId, id, request?.cancellationReason)
    }

    @PostMapping("/ocr")
    fun scanReceiptOcr(
        @RequestBody request: ReceiptOcrRequest,
    ): ReceiptOcrResponse {
        return expenseService.scanReceiptOcr(request)
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
