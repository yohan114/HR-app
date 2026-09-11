package com.hr.benefit.internal

import com.hr.benefit.*
import com.hr.identity.Caller
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/benefits")
class BenefitController(
    private val benefitService: BenefitService,
) {

    @GetMapping("/catalogue")
    fun getBenefitCatalogue(
        @AuthenticationPrincipal jwt: Jwt?,
    ): BenefitCatalogueResponse {
        val employeeId = resolveEmployeeId(jwt)
        return benefitService.getBenefitCatalogue(employeeId)
    }

    @GetMapping("/my-benefits")
    fun getMyBenefits(
        @AuthenticationPrincipal jwt: Jwt?,
    ): MyBenefitsResponse {
        val employeeId = resolveEmployeeId(jwt)
        return benefitService.getMyBenefits(employeeId)
    }

    @GetMapping("/claims")
    fun getMyBenefitClaims(
        @RequestParam(value = "status", required = false) status: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): BenefitClaimsResponse {
        val employeeId = resolveEmployeeId(jwt)
        return benefitService.getMyBenefitClaims(employeeId, status)
    }

    @PostMapping("/claims")
    @ResponseStatus(HttpStatus.CREATED)
    fun submitBenefitClaim(
        @RequestBody request: BenefitClaimSubmitRequest,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): BenefitClaimItemResponse {
        val employeeId = resolveEmployeeId(jwt)
        return benefitService.submitBenefitClaim(employeeId, request)
    }

    @PostMapping("/claims/{id}/cancel")
    fun cancelBenefitClaim(
        @PathVariable("id") id: UUID,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody(required = false) request: CancelBenefitClaimRequest?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): BenefitClaimItemResponse {
        val employeeId = resolveEmployeeId(jwt)
        return benefitService.cancelBenefitClaim(employeeId, id, request?.cancellationReason)
    }

    private fun resolveEmployeeId(jwt: Jwt?): UUID {
        if (jwt != null) {
            runCatching {
                Caller.from(jwt).employeeId
            }.getOrNull()?.let { return it }
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
