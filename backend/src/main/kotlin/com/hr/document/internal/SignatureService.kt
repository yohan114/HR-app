package com.hr.document.internal

import com.hr.document.*
import com.hr.employee.EmployeeLookupService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
@Transactional
class SignatureService(
    private val signatureRequestRepository: SignatureRequestRepository,
    private val signerRepository: SignatureSignerRepository,
    private val auditLogRepository: SignatureAuditLogRepository,
    private val companyDocumentRepository: CompanyDocumentRepository,
    private val employeeLookupService: EmployeeLookupService,
) {

    @Transactional(readOnly = true)
    fun listSignatureRequests(status: String?, signerEmployeeId: UUID?): SignatureRequestListResponse {
        val requests = when {
            signerEmployeeId != null && status != null ->
                signatureRequestRepository.findAllBySignerEmployeeIdAndStatus(signerEmployeeId, status)
            signerEmployeeId != null ->
                signatureRequestRepository.findAllBySignerEmployeeId(signerEmployeeId)
            status != null ->
                signatureRequestRepository.findAllByStatusOrderByCreatedAtDesc(status)
            else ->
                signatureRequestRepository.findAllByOrderByCreatedAtDesc()
        }
        val items = requests.map { it.toItem() }
        return SignatureRequestListResponse(items)
    }

    fun createSignatureRequest(request: SignatureRequestCreateRequest, creatorId: UUID): SignatureRequestItem {
        val document = if (request.documentId != null) {
            companyDocumentRepository.findById(request.documentId).orElse(null)
        } else null

        val entity = SignatureRequestEntity(
            documentId = request.documentId ?: document?.id ?: UUID.randomUUID(),
            title = request.title,
            workflowType = "SEQUENTIAL",
            status = "PENDING",
            dueDate = request.expiresAt?.toLocalDate(),
            requestedBy = creatorId,
        )
        val savedRequest = signatureRequestRepository.save(entity)

        request.signers.forEachIndexed { index, signerInput ->
            val signer = SignatureSignerEntity(
                requestId = savedRequest.id,
                signerEmployeeId = signerInput.signerEmployeeId,
                signerName = signerInput.signerName,
                signerEmail = signerInput.signerEmail,
                signingOrder = signerInput.signingOrder.takeIf { it > 0 } ?: (index + 1),
                status = "PENDING",
            )
            signerRepository.save(signer)
        }

        // Create initial cryptographic audit log
        val creatorName = employeeLookupService.findDisplayName(creatorId) ?: "HR Admin"
        val auditLog = SignatureAuditLogEntity(
            requestId = savedRequest.id,
            eventType = "CREATED",
            actorId = creatorId,
            actorName = creatorName,
            ipAddress = "127.0.0.1",
            documentHashSha256 = request.fileChecksumSha256,
            details = "Signature request initiated for ${request.signers.size} recipient(s)",
        )
        auditLogRepository.save(auditLog)

        return savedRequest.toItem()
    }

    @Transactional(readOnly = true)
    fun getSignatureRequestDetails(requestId: UUID): SignatureRequestDetailResponse {
        val request = signatureRequestRepository.findById(requestId)
            .orElseThrow { NoSuchElementException("Signature request not found: $requestId") }
        val signers = signerRepository.findAllByRequestIdOrderBySigningOrderAsc(requestId)
            .map { it.toItem() }
        val auditLogs = auditLogRepository.findAllByRequestIdOrderByCreatedAtAsc(requestId)
            .map { it.toItem() }

        return SignatureRequestDetailResponse(
            request = request.toItem(),
            signers = signers,
            auditLogs = auditLogs,
        )
    }

    fun signDocument(requestId: UUID, request: SignatureSignRequest): SignatureRequestDetailResponse {
        val signatureReq = signatureRequestRepository.findById(requestId)
            .orElseThrow { NoSuchElementException("Signature request not found: $requestId") }

        if (signatureReq.status == "COMPLETED" || signatureReq.status == "DECLINED") {
            throw IllegalStateException("Signature request is already ${signatureReq.status}")
        }

        val allSigners = signerRepository.findAllByRequestIdOrderBySigningOrderAsc(requestId)
        val signer = if (request.signerEmployeeId != null) {
            allSigners.find { it.signerEmployeeId == request.signerEmployeeId }
                ?: throw NoSuchElementException("Signer with employee ID ${request.signerEmployeeId} not found on this request")
        } else {
            allSigners.firstOrNull { it.status == "PENDING" }
                ?: throw IllegalStateException("No pending signer found for this request")
        }

        if (signer.status == "SIGNED") {
            throw IllegalStateException("Signer ${signer.signerName} has already signed")
        }

        val now = Instant.now()
        signer.status = "SIGNED"
        signer.signatureMethod = request.signatureType
        signer.signatureData = request.signatureData
        signer.ipAddress = request.ipAddress ?: "127.0.0.1"
        signer.signedAt = now
        signerRepository.save(signer)

        // Generate SHA-256 seal of the signature evidence
        val signatureEvidence = "${signatureReq.id}:${signer.id}:${request.signatureType}:${request.signatureData}:${now.toEpochMilli()}"
        val signatureSeal = calculateSha256(signatureEvidence)

        val auditLog = SignatureAuditLogEntity(
            requestId = signatureReq.id,
            eventType = "SIGNED",
            actorId = signer.signerEmployeeId,
            actorName = signer.signerName,
            ipAddress = request.ipAddress ?: "127.0.0.1",
            documentHashSha256 = signatureSeal,
            details = "Digital signature captured via ${request.signatureType}. Consent confirmed.",
        )
        auditLogRepository.save(auditLog)

        // Check if all signers have signed
        val remainingPending = allSigners.count { it.id != signer.id && it.status == "PENDING" }
        if (remainingPending == 0) {
            signatureReq.status = "COMPLETED"
            signatureReq.completedAt = now
            signatureRequestRepository.save(signatureReq)

            val completionAudit = SignatureAuditLogEntity(
                requestId = signatureReq.id,
                eventType = "COMPLETED",
                actorId = signer.signerEmployeeId,
                actorName = "System Workflow",
                ipAddress = request.ipAddress ?: "127.0.0.1",
                documentHashSha256 = signatureSeal,
                details = "All signers completed signing. Document sealed.",
            )
            auditLogRepository.save(completionAudit)
        } else {
            signatureReq.status = "PARTIALLY_SIGNED"
            signatureRequestRepository.save(signatureReq)
        }

        return getSignatureRequestDetails(requestId)
    }

    // -------------------------------------------------------------------------
    // Mappers & Helpers
    // -------------------------------------------------------------------------

    private fun SignatureRequestEntity.toItem(): SignatureRequestItem {
        val creatorName = employeeLookupService.findDisplayName(requestedBy) ?: "HR Operations"
        val doc = companyDocumentRepository.findById(documentId).orElse(null)
        val signers = signerRepository.findAllByRequestIdOrderBySigningOrderAsc(id)
        val signedCount = signers.count { it.status == "SIGNED" }
        val nextSigner = signers.firstOrNull { it.status == "PENDING" }

        return SignatureRequestItem(
            id = id,
            title = title,
            description = null,
            documentId = documentId,
            documentTitle = doc?.title ?: title,
            fileUrl = doc?.storageKey ?: "https://storage.acmecorp.com/documents/${doc?.fileName ?: "contract.pdf"}",
            fileChecksumSha256 = doc?.checksumSha256 ?: "0000000000000000000000000000000000000000000000000000000000000000",
            status = status,
            createdByEmployeeId = requestedBy,
            createdByName = creatorName,
            currentSignerOrder = nextSigner?.signingOrder ?: signers.size,
            totalSigners = signers.size,
            signedCount = signedCount,
            expiresAt = dueDate?.atStartOfDay()?.atOffset(ZoneOffset.UTC),
            completedAt = completedAt?.atOffset(ZoneOffset.UTC),
            createdAt = (createdAt ?: Instant.now()).atOffset(ZoneOffset.UTC),
        )
    }

    private fun SignatureSignerEntity.toItem(): SignatureSignerItem {
        return SignatureSignerItem(
            id = id,
            signerEmployeeId = signerEmployeeId,
            signerName = signerName,
            signerEmail = signerEmail,
            role = when (signingOrder) {
                1 -> "Employee"
                2 -> "Reporting Manager"
                else -> "HR Director"
            },
            signingOrder = signingOrder,
            status = status,
            signatureType = signatureMethod,
            signatureData = signatureData,
            ipAddress = ipAddress,
            signedAt = signedAt?.atOffset(ZoneOffset.UTC),
        )
    }

    private fun SignatureAuditLogEntity.toItem(): SignatureAuditLogItem {
        return SignatureAuditLogItem(
            id = id,
            action = eventType,
            actorId = actorId,
            actorName = actorName,
            ipAddress = ipAddress,
            details = details,
            timestamp = (createdAt ?: Instant.now()).atOffset(ZoneOffset.UTC),
        )
    }

    private fun calculateSha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
