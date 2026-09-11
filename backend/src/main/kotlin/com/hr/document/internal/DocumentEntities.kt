package com.hr.document.internal

import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "document_folder")
class DocumentFolderEntity(
    @Column(name = "parent_id")
    var parentId: UUID? = null,
    @Column(name = "folder_name", nullable = false, length = 100)
    var folderName: String,
    @Column(name = "path", nullable = false, length = 500)
    var path: String,
    @Column(name = "access_scope", nullable = false, length = 50)
    var accessScope: String = "PUBLIC",
    @Column(name = "is_system", nullable = false)
    var isSystem: Boolean = false,
) : TenantScopedEntity()

@Entity
@Table(name = "document_tag")
class DocumentTagEntity(
    @Column(name = "tag_name", nullable = false, length = 50)
    var tagName: String,
    @Column(name = "color", nullable = false, length = 20)
    var color: String = "#1976D2",
) : TenantScopedEntity()

@Entity
@Table(name = "company_document")
class CompanyDocumentEntity(
    @Column(name = "folder_id")
    var folderId: UUID? = null,
    @Column(name = "employee_id")
    var employeeId: UUID? = null,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Column(name = "description", columnDefinition = "text")
    var description: String? = null,
    @Column(name = "document_category", nullable = false, length = 50)
    var documentCategory: String = "GENERAL",
    @Column(name = "current_version_number", nullable = false)
    var currentVersionNumber: Int = 1,
    @Column(name = "file_name", nullable = false, length = 255)
    var fileName: String,
    @Column(name = "file_size_bytes", nullable = false)
    var fileSizeBytes: Long = 0L,
    @Column(name = "mime_type", nullable = false, length = 100)
    var mimeType: String = "application/pdf",
    @Column(name = "storage_key", nullable = false, length = 500)
    var storageKey: String,
    @Column(name = "checksum_sha256", length = 64)
    var checksumSha256: String? = null,
    @Column(name = "status", nullable = false, length = 50)
    var status: String = "PUBLISHED",
    @Column(name = "is_confidential", nullable = false)
    var isConfidential: Boolean = false,
    @Column(name = "retention_until")
    var retentionUntil: LocalDate? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "document_version")
class DocumentVersionEntity(
    @Column(name = "document_id", nullable = false)
    var documentId: UUID,
    @Column(name = "version_number", nullable = false)
    var versionNumber: Int,
    @Column(name = "file_name", nullable = false, length = 255)
    var fileName: String,
    @Column(name = "file_size_bytes", nullable = false)
    var fileSizeBytes: Long,
    @Column(name = "storage_key", nullable = false, length = 500)
    var storageKey: String,
    @Column(name = "checksum_sha256", length = 64)
    var checksumSha256: String? = null,
    @Column(name = "changelog", columnDefinition = "text")
    var changelog: String? = null,
    @Column(name = "uploaded_by")
    var uploadedBy: UUID? = null,
) : TenantScopedEntity()

data class DocumentTagMappingId(
    var tenantId: UUID? = null,
    var documentId: UUID? = null,
    var tagId: UUID? = null,
) : Serializable

@Entity
@Table(name = "document_tag_mapping")
@IdClass(DocumentTagMappingId::class)
class DocumentTagMappingEntity(
    @Id
    @Column(name = "tenant_id", nullable = false)
    var tenantId: UUID,
    @Id
    @Column(name = "document_id", nullable = false)
    var documentId: UUID,
    @Id
    @Column(name = "tag_id", nullable = false)
    var tagId: UUID,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "document_template")
class DocumentTemplateEntity(
    @Column(name = "template_code", nullable = false, length = 100)
    var templateCode: String,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Column(name = "category", nullable = false, length = 50)
    var category: String = "HR_LETTER",
    @Column(name = "content_template", nullable = false, columnDefinition = "text")
    var contentTemplate: String,
    @Column(name = "placeholders", nullable = false, columnDefinition = "jsonb")
    var placeholders: String = "[]",
    @Column(name = "requires_signature", nullable = false)
    var requiresSignature: Boolean = true,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "letter_request")
class LetterRequestEntity(
    @Column(name = "request_number", nullable = false, length = 50)
    var requestNumber: String,
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "template_id", nullable = false)
    var templateId: UUID,
    @Column(name = "purpose", nullable = false, length = 255)
    var purpose: String,
    @Column(name = "addressee", nullable = false, length = 255)
    var addressee: String,
    @Column(name = "specific_instructions", columnDefinition = "text")
    var specificInstructions: String? = null,
    @Column(name = "status", nullable = false, length = 50)
    var status: String = "SUBMITTED",
    @Column(name = "generated_document_id")
    var generatedDocumentId: UUID? = null,
    @Column(name = "approver_id")
    var approverId: UUID? = null,
    @Column(name = "approval_remarks", columnDefinition = "text")
    var approvalRemarks: String? = null,
    @Column(name = "approved_at")
    var approvedAt: Instant? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "signature_request")
class SignatureRequestEntity(
    @Column(name = "document_id", nullable = false)
    var documentId: UUID,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Column(name = "workflow_type", nullable = false, length = 50)
    var workflowType: String = "PARALLEL",
    @Column(name = "status", nullable = false, length = 50)
    var status: String = "PENDING",
    @Column(name = "due_date")
    var dueDate: LocalDate? = null,
    @Column(name = "requested_by", nullable = false)
    var requestedBy: UUID,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "signature_signer")
class SignatureSignerEntity(
    @Column(name = "request_id", nullable = false)
    var requestId: UUID,
    @Column(name = "signer_employee_id")
    var signerEmployeeId: UUID? = null,
    @Column(name = "signer_name", nullable = false, length = 255)
    var signerName: String,
    @Column(name = "signer_email", nullable = false, length = 255)
    var signerEmail: String,
    @Column(name = "signing_order", nullable = false)
    var signingOrder: Int = 1,
    @Column(name = "status", nullable = false, length = 50)
    var status: String = "PENDING",
    @Column(name = "signature_method", length = 50)
    var signatureMethod: String? = null,
    @Column(name = "signature_data", columnDefinition = "text")
    var signatureData: String? = null,
    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null,
    @Column(name = "user_agent", length = 255)
    var userAgent: String? = null,
    @Column(name = "signed_at")
    var signedAt: Instant? = null,
    @Column(name = "decline_reason", columnDefinition = "text")
    var declineReason: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "signature_audit_log")
class SignatureAuditLogEntity(
    @Column(name = "request_id", nullable = false)
    var requestId: UUID,
    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: String,
    @Column(name = "actor_id")
    var actorId: UUID? = null,
    @Column(name = "actor_name", nullable = false, length = 255)
    var actorName: String,
    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null,
    @Column(name = "document_hash_sha256", nullable = false, length = 64)
    var documentHashSha256: String,
    @Column(name = "details", columnDefinition = "jsonb")
    var details: String? = null,
) : TenantScopedEntity()
