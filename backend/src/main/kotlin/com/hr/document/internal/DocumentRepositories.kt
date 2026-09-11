package com.hr.document.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DocumentFolderRepository : JpaRepository<DocumentFolderEntity, UUID> {
    fun findAllByOrderByFolderNameAsc(): List<DocumentFolderEntity>
    fun findByIdAndTenantId(id: UUID, tenantId: UUID): DocumentFolderEntity?
}

@Repository
interface DocumentTagRepository : JpaRepository<DocumentTagEntity, UUID> {
    fun findAllByOrderByTagNameAsc(): List<DocumentTagEntity>
    fun findByTagNameIgnoreCase(tagName: String): DocumentTagEntity?
}

@Repository
interface CompanyDocumentRepository : JpaRepository<CompanyDocumentEntity, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<CompanyDocumentEntity>
    fun findAllByFolderIdOrderByCreatedAtDesc(folderId: UUID): List<CompanyDocumentEntity>
    fun findAllByDocumentCategoryOrderByCreatedAtDesc(category: String): List<CompanyDocumentEntity>
    fun countByFolderId(folderId: UUID): Long

    @Query("""
        SELECT d FROM CompanyDocumentEntity d
        WHERE (:folderId IS NULL OR d.folderId = :folderId)
          AND (:category IS NULL OR d.documentCategory = :category)
          AND (:search IS NULL OR LOWER(d.title) LIKE :search OR LOWER(d.fileName) LIKE :search)
        ORDER BY d.createdAt DESC
    """)
    fun searchDocuments(
        @Param("folderId") folderId: UUID?,
        @Param("category") category: String?,
        @Param("search") search: String?,
    ): List<CompanyDocumentEntity>
}

@Repository
interface DocumentVersionRepository : JpaRepository<DocumentVersionEntity, UUID> {
    fun findAllByDocumentIdOrderByVersionNumberDesc(documentId: UUID): List<DocumentVersionEntity>
    fun findTopByDocumentIdOrderByVersionNumberDesc(documentId: UUID): DocumentVersionEntity?
}

@Repository
interface DocumentTagMappingRepository : JpaRepository<DocumentTagMappingEntity, DocumentTagMappingId> {
    fun findAllByDocumentId(documentId: UUID): List<DocumentTagMappingEntity>
    fun deleteByDocumentId(documentId: UUID)
}

@Repository
interface DocumentTemplateRepository : JpaRepository<DocumentTemplateEntity, UUID> {
    fun findAllByIsActiveTrueOrderByTitleAsc(): List<DocumentTemplateEntity>
    fun findByTemplateCode(templateCode: String): DocumentTemplateEntity?
}

@Repository
interface LetterRequestRepository : JpaRepository<LetterRequestEntity, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<LetterRequestEntity>
    fun findAllByEmployeeIdOrderByCreatedAtDesc(employeeId: UUID): List<LetterRequestEntity>
    fun findAllByStatusOrderByCreatedAtDesc(status: String): List<LetterRequestEntity>
    fun findAllByEmployeeIdAndStatusOrderByCreatedAtDesc(employeeId: UUID, status: String): List<LetterRequestEntity>
}

@Repository
interface SignatureRequestRepository : JpaRepository<SignatureRequestEntity, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<SignatureRequestEntity>
    fun findAllByStatusOrderByCreatedAtDesc(status: String): List<SignatureRequestEntity>

    @Query("""
        SELECT DISTINCT r FROM SignatureRequestEntity r
        JOIN SignatureSignerEntity s ON s.requestId = r.id
        WHERE s.signerEmployeeId = :signerEmployeeId
        ORDER BY r.createdAt DESC
    """)
    fun findAllBySignerEmployeeId(@Param("signerEmployeeId") signerEmployeeId: UUID): List<SignatureRequestEntity>

    @Query("""
        SELECT DISTINCT r FROM SignatureRequestEntity r
        JOIN SignatureSignerEntity s ON s.requestId = r.id
        WHERE s.signerEmployeeId = :signerEmployeeId
          AND r.status = :status
        ORDER BY r.createdAt DESC
    """)
    fun findAllBySignerEmployeeIdAndStatus(
        @Param("signerEmployeeId") signerEmployeeId: UUID,
        @Param("status") status: String,
    ): List<SignatureRequestEntity>
}

@Repository
interface SignatureSignerRepository : JpaRepository<SignatureSignerEntity, UUID> {
    fun findAllByRequestIdOrderBySigningOrderAsc(requestId: UUID): List<SignatureSignerEntity>
    fun findByRequestIdAndSignerEmployeeId(requestId: UUID, signerEmployeeId: UUID): SignatureSignerEntity?
}

@Repository
interface SignatureAuditLogRepository : JpaRepository<SignatureAuditLogEntity, UUID> {
    fun findAllByRequestIdOrderByCreatedAtAsc(requestId: UUID): List<SignatureAuditLogEntity>
}
