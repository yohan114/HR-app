package com.hr.document

import java.time.OffsetDateTime
import java.util.UUID

enum class DocumentCategory {
    POLICY,
    CONTRACT,
    LETTER,
    CERTIFICATE,
    FORM,
    GENERAL
}

enum class DocumentStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED,
    TRASHED
}

enum class LetterRequestStatus {
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    GENERATED,
    REJECTED
}

enum class SignatureWorkflowType {
    PARALLEL,
    SEQUENTIAL
}

enum class SignatureRequestStatus {
    PENDING,
    PARTIALLY_SIGNED,
    COMPLETED,
    DECLINED,
    EXPIRED
}

enum class SignatureSignerStatus {
    PENDING,
    SIGNED,
    DECLINED
}

enum class SignatureMethod {
    DRAWN,
    TYPED,
    CERTIFICATE
}

enum class SignatureAuditEventType {
    CREATED,
    VIEWED,
    SIGNED,
    DECLINED,
    COMPLETED
}

// -----------------------------------------------------------------------------
// Folder DTOs
// -----------------------------------------------------------------------------

data class DocumentFolderItem(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val icon: String? = null,
    val parentFolderId: UUID? = null,
    val accessLevel: String,
    val documentCount: Int,
    val createdAt: OffsetDateTime,
)

data class DocumentFolderListResponse(
    val folders: List<DocumentFolderItem>,
)

data class DocumentFolderCreateRequest(
    val name: String,
    val description: String? = null,
    val icon: String? = null,
    val parentFolderId: UUID? = null,
    val accessLevel: String = "PUBLIC",
)

// -----------------------------------------------------------------------------
// Document Tag DTOs
// -----------------------------------------------------------------------------

data class DocumentTagItem(
    val id: UUID,
    val name: String,
    val colorHex: String? = null,
)

// -----------------------------------------------------------------------------
// Document & Version DTOs
// -----------------------------------------------------------------------------

data class DocumentVersionItem(
    val id: UUID,
    val versionNumber: Int,
    val fileUrl: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val checksumSha256: String,
    val changeNotes: String? = null,
    val uploadedBy: UUID? = null,
    val uploadedByName: String? = null,
    val uploadedAt: OffsetDateTime,
)

data class CompanyDocumentItem(
    val id: UUID,
    val folderId: UUID? = null,
    val folderName: String? = null,
    val title: String,
    val description: String? = null,
    val category: String,
    val documentType: String,
    val accessLevel: String,
    val currentVersionNumber: Int,
    val fileUrl: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val checksumSha256: String,
    val tags: List<String> = emptyList(),
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class DocumentListResponse(
    val documents: List<CompanyDocumentItem>,
)

data class DocumentCreateRequest(
    val folderId: UUID? = null,
    val title: String,
    val description: String? = null,
    val category: String,
    val documentType: String,
    val accessLevel: String,
    val fileUrl: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val checksumSha256: String,
    val tags: List<String>? = null,
)

data class DocumentVersionCreateRequest(
    val fileUrl: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val checksumSha256: String,
    val changeNotes: String? = null,
)

data class DocumentDetailResponse(
    val document: CompanyDocumentItem,
    val versions: List<DocumentVersionItem>,
)

// -----------------------------------------------------------------------------
// Template & Letter Request DTOs
// -----------------------------------------------------------------------------

data class DocumentTemplateItem(
    val id: UUID,
    val templateCode: String,
    val name: String,
    val category: String,
    val description: String? = null,
    val bodyTemplate: String,
    val variables: List<String>,
    val isActive: Boolean,
    val createdAt: OffsetDateTime,
)

data class DocumentTemplateListResponse(
    val templates: List<DocumentTemplateItem>,
)

data class LetterRequestItem(
    val id: UUID,
    val templateId: UUID,
    val templateCode: String,
    val templateName: String,
    val employeeId: UUID,
    val employeeName: String,
    val reason: String,
    val recipientAddress: String? = null,
    val status: String,
    val generatedDocumentId: UUID? = null,
    val generatedDocumentUrl: String? = null,
    val generatedContent: String? = null,
    val rejectionReason: String? = null,
    val approvedBy: UUID? = null,
    val approvedAt: OffsetDateTime? = null,
    val createdAt: OffsetDateTime,
)

data class LetterRequestListResponse(
    val requests: List<LetterRequestItem>,
)

data class LetterRequestCreateRequest(
    val templateId: UUID,
    val employeeId: UUID,
    val reason: String,
    val recipientAddress: String? = null,
)

data class LetterRequestApproveRequest(
    val approved: Boolean,
    val rejectionReason: String? = null,
)

// -----------------------------------------------------------------------------
// Digital E-Signature DTOs
// -----------------------------------------------------------------------------

data class SignatureSignerItem(
    val id: UUID,
    val signerEmployeeId: UUID? = null,
    val signerName: String,
    val signerEmail: String,
    val role: String,
    val signingOrder: Int,
    val status: String,
    val signatureType: String? = null,
    val signatureData: String? = null,
    val ipAddress: String? = null,
    val signedAt: OffsetDateTime? = null,
)

data class SignatureAuditLogItem(
    val id: UUID,
    val action: String,
    val actorId: UUID? = null,
    val actorName: String,
    val ipAddress: String? = null,
    val details: String? = null,
    val timestamp: OffsetDateTime,
)

data class SignatureRequestItem(
    val id: UUID,
    val title: String,
    val description: String? = null,
    val documentId: UUID? = null,
    val documentTitle: String? = null,
    val fileUrl: String,
    val fileChecksumSha256: String,
    val status: String,
    val createdByEmployeeId: UUID,
    val createdByName: String,
    val currentSignerOrder: Int,
    val totalSigners: Int,
    val signedCount: Int,
    val expiresAt: OffsetDateTime? = null,
    val completedAt: OffsetDateTime? = null,
    val createdAt: OffsetDateTime,
)

data class SignatureRequestDetailResponse(
    val request: SignatureRequestItem,
    val signers: List<SignatureSignerItem>,
    val auditLogs: List<SignatureAuditLogItem>,
)

data class SignatureRequestListResponse(
    val requests: List<SignatureRequestItem>,
)

data class SignatureSignerInput(
    val signerEmployeeId: UUID? = null,
    val signerName: String,
    val signerEmail: String,
    val role: String,
    val signingOrder: Int,
)

data class SignatureRequestCreateRequest(
    val title: String,
    val description: String? = null,
    val documentId: UUID? = null,
    val fileUrl: String,
    val fileChecksumSha256: String,
    val expiresAt: OffsetDateTime? = null,
    val signers: List<SignatureSignerInput>,
)

data class SignatureSignRequest(
    val signerEmployeeId: UUID? = null,
    val signatureType: String,
    val signatureData: String,
    val consentConfirmed: Boolean,
    val ipAddress: String? = null,
)
