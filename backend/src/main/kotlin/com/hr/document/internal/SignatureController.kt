package com.hr.document.internal

import com.hr.document.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/signatures")
@PreAuthorize("isAuthenticated()")
class SignatureController(
    private val signatureService: SignatureService,
) {

    @GetMapping("/requests")
    fun listSignatureRequests(
        @RequestParam(name = "status", required = false) status: String?,
        @RequestParam(name = "signerEmployeeId", required = false) signerEmployeeId: UUID?,
    ): SignatureRequestListResponse {
        return signatureService.listSignatureRequests(status, signerEmployeeId)
    }

    @PostMapping("/requests")
    fun createSignatureRequest(
        @RequestBody request: SignatureRequestCreateRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<SignatureRequestItem> {
        val actorId = resolveEmployeeId(jwt)
        val created = signatureService.createSignatureRequest(request, actorId)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping("/requests/{id}")
    fun getSignatureRequestDetails(
        @PathVariable("id") id: UUID,
    ): SignatureRequestDetailResponse {
        return signatureService.getSignatureRequestDetails(id)
    }

    @PostMapping("/requests/{id}/sign")
    fun signDocument(
        @PathVariable("id") id: UUID,
        @RequestBody request: SignatureSignRequest,
    ): SignatureRequestDetailResponse {
        return signatureService.signDocument(id, request)
    }

    private fun resolveEmployeeId(jwt: Jwt?): UUID {
        val claim = jwt?.getClaimAsString("employee_id")
        return if (!claim.isNullOrBlank()) {
            UUID.fromString(claim)
        } else {
            UUID.fromString("00000000-0000-0000-0000-000000000010")
        }
    }
}
