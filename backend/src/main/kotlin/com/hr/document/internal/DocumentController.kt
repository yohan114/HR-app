package com.hr.document.internal

import com.hr.document.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/documents")
class DocumentController(
    private val documentService: DocumentService,
) {

    // --- Folders -------------------------------------------------------------

    @GetMapping("/folders")
    fun listFolders(): DocumentFolderListResponse {
        return documentService.listFolders()
    }

    @PostMapping("/folders")
    fun createFolder(
        @RequestBody request: DocumentFolderCreateRequest,
    ): ResponseEntity<DocumentFolderItem> {
        val created = documentService.createFolder(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    // --- Documents & Versions ------------------------------------------------

    @GetMapping
    fun listDocuments(
        @RequestParam(name = "folderId", required = false) folderId: UUID?,
        @RequestParam(name = "category", required = false) category: String?,
        @RequestParam(name = "search", required = false) search: String?,
    ): DocumentListResponse {
        return documentService.listDocuments(folderId, category, search)
    }

    @PostMapping
    fun createDocument(
        @RequestBody request: DocumentCreateRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<CompanyDocumentItem> {
        val actorId = resolveEmployeeId(jwt)
        val created = documentService.createDocument(request, actorId)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping("/{id}")
    fun getDocumentDetails(
        @PathVariable("id") id: UUID,
    ): DocumentDetailResponse {
        return documentService.getDocumentDetails(id)
    }

    @PostMapping("/{id}/versions")
    fun createDocumentVersion(
        @PathVariable("id") id: UUID,
        @RequestBody request: DocumentVersionCreateRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<DocumentVersionItem> {
        val actorId = resolveEmployeeId(jwt)
        val created = documentService.createDocumentVersion(id, request, actorId)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    // --- Templates -----------------------------------------------------------

    @GetMapping("/templates")
    fun listTemplates(): DocumentTemplateListResponse {
        return documentService.listTemplates()
    }

    @GetMapping("/templates/{id}")
    fun getTemplate(
        @PathVariable("id") id: UUID,
    ): DocumentTemplateItem {
        return documentService.getTemplate(id)
    }

    // --- Letter Requests -----------------------------------------------------

    @GetMapping("/letter-requests")
    fun listLetterRequests(
        @RequestParam(name = "employeeId", required = false) employeeId: UUID?,
        @RequestParam(name = "status", required = false) status: String?,
    ): LetterRequestListResponse {
        return documentService.listLetterRequests(employeeId, status)
    }

    @PostMapping("/letter-requests")
    fun createLetterRequest(
        @RequestBody request: LetterRequestCreateRequest,
    ): ResponseEntity<LetterRequestItem> {
        val created = documentService.createLetterRequest(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @PostMapping("/letter-requests/{id}/approve")
    fun approveLetterRequest(
        @PathVariable("id") id: UUID,
        @RequestBody request: LetterRequestApproveRequest,
        @AuthenticationPrincipal jwt: Jwt?,
    ): LetterRequestItem {
        val actorId = resolveEmployeeId(jwt)
        return documentService.approveLetterRequest(id, request, actorId)
    }

    @PostMapping("/letter-requests/{id}/reject")
    fun rejectLetterRequest(
        @PathVariable("id") id: UUID,
        @RequestBody(required = false) request: Map<String, String>?,
        @AuthenticationPrincipal jwt: Jwt?,
    ): LetterRequestItem {
        val actorId = resolveEmployeeId(jwt)
        val reason = request?.get("reason") ?: request?.get("remarks") ?: "Rejected by HR Administrator"
        return documentService.rejectLetterRequest(id, reason, actorId)
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
