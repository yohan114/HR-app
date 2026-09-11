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
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
@Transactional
class DocumentService(
    private val folderRepository: DocumentFolderRepository,
    private val tagRepository: DocumentTagRepository,
    private val companyDocumentRepository: CompanyDocumentRepository,
    private val versionRepository: DocumentVersionRepository,
    private val tagMappingRepository: DocumentTagMappingRepository,
    private val templateRepository: DocumentTemplateRepository,
    private val letterRequestRepository: LetterRequestRepository,
    private val employeeLookupService: EmployeeLookupService,
) {

    // -------------------------------------------------------------------------
    // Folders
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun listFolders(): DocumentFolderListResponse {
        val folders = folderRepository.findAllByOrderByFolderNameAsc().map { folder ->
            val count = companyDocumentRepository.countByFolderId(folder.id)
            folder.toItem(count.toInt())
        }
        return DocumentFolderListResponse(folders)
    }

    fun createFolder(request: DocumentFolderCreateRequest): DocumentFolderItem {
        val path = if (request.parentFolderId != null) {
            val parent = folderRepository.findById(request.parentFolderId).orElse(null)
            val parentPath = parent?.path ?: ""
            "$parentPath/${request.name.lowercase().replace(" ", "-")}"
        } else {
            "/${request.name.lowercase().replace(" ", "-")}"
        }

        val folder = DocumentFolderEntity(
            parentId = request.parentFolderId,
            folderName = request.name,
            path = path,
            accessScope = request.accessLevel,
            isSystem = false,
        )
        val saved = folderRepository.save(folder)
        return saved.toItem(0)
    }

    // -------------------------------------------------------------------------
    // Company Documents & Versions
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun listDocuments(folderId: UUID?, category: String?, search: String?): DocumentListResponse {
        val trimmedSearch = search?.trim()?.takeIf { it.isNotEmpty() }
        val searchPattern = trimmedSearch?.let { "%${it.lowercase()}%" }
        val trimmedCategory = category?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()

        val documents = if (folderId == null && trimmedCategory == null && searchPattern == null) {
            companyDocumentRepository.findAllByOrderByCreatedAtDesc()
        } else {
            companyDocumentRepository.searchDocuments(folderId, trimmedCategory, searchPattern)
        }
        val items = documents.map { it.toItem() }
        return DocumentListResponse(items)
    }

    fun createDocument(request: DocumentCreateRequest, userId: UUID?): CompanyDocumentItem {
        val document = CompanyDocumentEntity(
            folderId = request.folderId,
            title = request.title,
            description = request.description,
            documentCategory = request.category,
            currentVersionNumber = 1,
            fileName = request.fileName,
            fileSizeBytes = request.fileSizeBytes,
            mimeType = request.mimeType,
            storageKey = request.fileUrl,
            checksumSha256 = request.checksumSha256,
            status = "PUBLISHED",
        )
        val savedDoc = companyDocumentRepository.save(document)

        // Save initial version
        val version = DocumentVersionEntity(
            documentId = savedDoc.id,
            versionNumber = 1,
            fileName = request.fileName,
            fileSizeBytes = request.fileSizeBytes,
            storageKey = request.fileUrl,
            checksumSha256 = request.checksumSha256,
            changelog = "Initial document upload",
            uploadedBy = userId,
        )
        versionRepository.save(version)

        return savedDoc.toItem()
    }

    @Transactional(readOnly = true)
    fun getDocumentDetails(documentId: UUID): DocumentDetailResponse {
        val doc = companyDocumentRepository.findById(documentId)
            .orElseThrow { NoSuchElementException("Document not found: $documentId") }
        val versions = versionRepository.findAllByDocumentIdOrderByVersionNumberDesc(documentId)
            .map { it.toItem() }
        return DocumentDetailResponse(
            document = doc.toItem(),
            versions = versions,
        )
    }

    fun createDocumentVersion(documentId: UUID, request: DocumentVersionCreateRequest, userId: UUID?): DocumentVersionItem {
        val doc = companyDocumentRepository.findById(documentId)
            .orElseThrow { NoSuchElementException("Document not found: $documentId") }

        val newVersionNumber = doc.currentVersionNumber + 1
        doc.currentVersionNumber = newVersionNumber
        doc.fileName = request.fileName
        doc.fileSizeBytes = request.fileSizeBytes
        doc.mimeType = request.mimeType
        doc.storageKey = request.fileUrl
        doc.checksumSha256 = request.checksumSha256
        companyDocumentRepository.save(doc)

        val version = DocumentVersionEntity(
            documentId = doc.id,
            versionNumber = newVersionNumber,
            fileName = request.fileName,
            fileSizeBytes = request.fileSizeBytes,
            storageKey = request.fileUrl,
            checksumSha256 = request.checksumSha256,
            changelog = request.changeNotes ?: "Version $newVersionNumber uploaded",
            uploadedBy = userId,
        )
        val savedVersion = versionRepository.save(version)
        return savedVersion.toItem()
    }

    // -------------------------------------------------------------------------
    // Templates
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun listTemplates(): DocumentTemplateListResponse {
        val templates = templateRepository.findAllByIsActiveTrueOrderByTitleAsc().map { it.toItem() }
        return DocumentTemplateListResponse(templates)
    }

    @Transactional(readOnly = true)
    fun getTemplate(id: UUID): DocumentTemplateItem {
        val template = templateRepository.findById(id)
            .orElseThrow { NoSuchElementException("Template not found: $id") }
        return template.toItem()
    }

    // -------------------------------------------------------------------------
    // Letter Requests
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun listLetterRequests(employeeId: UUID?, status: String?): LetterRequestListResponse {
        val requests = when {
            employeeId != null && status != null -> letterRequestRepository.findAllByEmployeeIdAndStatusOrderByCreatedAtDesc(employeeId, status)
            employeeId != null -> letterRequestRepository.findAllByEmployeeIdOrderByCreatedAtDesc(employeeId)
            status != null -> letterRequestRepository.findAllByStatusOrderByCreatedAtDesc(status)
            else -> letterRequestRepository.findAllByOrderByCreatedAtDesc()
        }
        val items = requests.map { it.toItem() }
        return LetterRequestListResponse(items)
    }

    fun createLetterRequest(request: LetterRequestCreateRequest): LetterRequestItem {
        val template = templateRepository.findById(request.templateId)
            .orElseThrow { NoSuchElementException("Template not found: ${request.templateId}") }
        val employee = employeeLookupService.findById(request.employeeId)
            ?: throw NoSuchElementException("Employee not found: ${request.employeeId}")

        val count = letterRequestRepository.count()
        val requestNumber = "LTR-2026-%03d".format(count + 1)

        val entity = LetterRequestEntity(
            requestNumber = requestNumber,
            employeeId = employee.id,
            templateId = template.id,
            purpose = request.reason,
            addressee = request.recipientAddress ?: "To Whom It May Concern",
            specificInstructions = null,
            status = "SUBMITTED",
        )
        val saved = letterRequestRepository.save(entity)
        return saved.toItem()
    }

    fun approveLetterRequest(requestId: UUID, request: LetterRequestApproveRequest, approverId: UUID?): LetterRequestItem {
        val letterReq = letterRequestRepository.findById(requestId)
            .orElseThrow { NoSuchElementException("Letter request not found: $requestId") }

        if (!request.approved) {
            letterReq.status = "REJECTED"
            letterReq.approvalRemarks = request.rejectionReason ?: "Request declined by HR"
            letterReq.approverId = approverId
            letterReq.approvedAt = Instant.now()
            val saved = letterRequestRepository.save(letterReq)
            return saved.toItem()
        }

        // Generate letter content
        val template = templateRepository.findById(letterReq.templateId).orElse(null)
        val employee = employeeLookupService.findById(letterReq.employeeId)

        val currentDate = DateTimeFormatter.ISO_LOCAL_DATE.format(OffsetDateTime.now())
        var interpolatedContent = template?.contentTemplate ?: "Official HR Letter for ${employee?.displayName ?: "Employee"}"
        if (employee != null) {
            interpolatedContent = interpolatedContent
                .replace("{{employee.name}}", employee.displayName)
                .replace("{{employee.code}}", employee.employeeCode)
                .replace("{{employee.joinDate}}", employee.joinDate.toString())
                .replace("{{currentDate}}", currentDate)
                .replace("{{addressee}}", letterReq.addressee)
                .replace("{{purpose}}", letterReq.purpose)
                .replace("{{company.name}}", "Acme Corporation")
        }

        val docChecksum = calculateSha256(interpolatedContent)
        val fileName = "${letterReq.requestNumber}_${employee?.employeeCode ?: "EMP"}.pdf"
        val storageKey = "https://storage.acmecorp.com/letters/$fileName"

        val doc = CompanyDocumentEntity(
            folderId = null,
            employeeId = letterReq.employeeId,
            title = "${template?.title ?: "HR Letter"} - ${employee?.displayName ?: "Employee"}",
            description = "Auto-generated official letter for request ${letterReq.requestNumber}",
            documentCategory = "LETTER",
            currentVersionNumber = 1,
            fileName = fileName,
            fileSizeBytes = interpolatedContent.toByteArray(StandardCharsets.UTF_8).size.toLong(),
            mimeType = "application/pdf",
            storageKey = storageKey,
            checksumSha256 = docChecksum,
            status = "PUBLISHED",
        )
        val savedDoc = companyDocumentRepository.save(doc)

        val version = DocumentVersionEntity(
            documentId = savedDoc.id,
            versionNumber = 1,
            fileName = fileName,
            fileSizeBytes = savedDoc.fileSizeBytes,
            storageKey = storageKey,
            checksumSha256 = docChecksum,
            changelog = "Auto-generated letter approved by HR",
            uploadedBy = approverId,
        )
        versionRepository.save(version)

        letterReq.status = "APPROVED"
        letterReq.generatedDocumentId = savedDoc.id
        letterReq.approverId = approverId
        letterReq.approvedAt = Instant.now()
        letterReq.approvalRemarks = "Approved and issued"
        val saved = letterRequestRepository.save(letterReq)

        return saved.toItem(generatedDocUrl = storageKey, generatedContent = interpolatedContent)
    }

    fun rejectLetterRequest(id: UUID, reason: String, approverId: UUID): LetterRequestItem {
        val letterReq = letterRequestRepository.findById(id).orElseThrow {
            IllegalArgumentException("Letter request $id not found")
        }
        letterReq.status = "REJECTED"
        letterReq.approverId = approverId
        letterReq.approvedAt = Instant.now()
        letterReq.approvalRemarks = reason
        val saved = letterRequestRepository.save(letterReq)
        return saved.toItem()
    }

    // -------------------------------------------------------------------------
    // Mappers & Helpers
    // -------------------------------------------------------------------------

    private fun DocumentFolderEntity.toItem(count: Int = 0): DocumentFolderItem {
        return DocumentFolderItem(
            id = id,
            name = folderName,
            description = null,
            icon = when {
                path.contains("policies") -> "gavel"
                path.contains("contracts") -> "handshake"
                path.contains("letters") -> "mail"
                else -> "folder"
            },
            parentFolderId = parentId,
            accessLevel = accessScope,
            documentCount = count,
            createdAt = (createdAt ?: Instant.now()).atOffset(ZoneOffset.UTC),
        )
    }

    private fun CompanyDocumentEntity.toItem(): CompanyDocumentItem {
        val folder = folderId?.let { folderRepository.findById(it).orElse(null) }
        return CompanyDocumentItem(
            id = id,
            folderId = folderId,
            folderName = folder?.folderName,
            title = title,
            description = description,
            category = documentCategory,
            documentType = mimeType,
            accessLevel = if (isConfidential) "CONFIDENTIAL" else "PUBLIC",
            currentVersionNumber = currentVersionNumber,
            fileUrl = storageKey,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            mimeType = mimeType,
            checksumSha256 = checksumSha256 ?: "",
            tags = emptyList(),
            createdAt = (createdAt ?: Instant.now()).atOffset(ZoneOffset.UTC),
            updatedAt = (updatedAt ?: Instant.now()).atOffset(ZoneOffset.UTC),
        )
    }

    private fun DocumentVersionEntity.toItem(): DocumentVersionItem {
        val uploaderName = uploadedBy?.let { employeeLookupService.findDisplayName(it) }
        return DocumentVersionItem(
            id = id,
            versionNumber = versionNumber,
            fileUrl = storageKey,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            mimeType = "application/pdf",
            checksumSha256 = checksumSha256 ?: "",
            changeNotes = changelog,
            uploadedBy = uploadedBy,
            uploadedByName = uploaderName,
            uploadedAt = (createdAt ?: Instant.now()).atOffset(ZoneOffset.UTC),
        )
    }

    private fun DocumentTemplateEntity.toItem(): DocumentTemplateItem {
        val vars = listOf("employee.name", "employee.code", "employee.joinDate", "currentDate", "company.name")
        return DocumentTemplateItem(
            id = id,
            templateCode = templateCode,
            name = title,
            category = category,
            description = null,
            bodyTemplate = contentTemplate,
            variables = vars,
            isActive = isActive,
            createdAt = (createdAt ?: Instant.now()).atOffset(ZoneOffset.UTC),
        )
    }

    private fun LetterRequestEntity.toItem(
        generatedDocUrl: String? = null,
        generatedContent: String? = null,
    ): LetterRequestItem {
        val template = templateRepository.findById(templateId).orElse(null)
        val employeeName = employeeLookupService.findDisplayName(employeeId) ?: "Employee"
        val generatedDoc = generatedDocumentId?.let { companyDocumentRepository.findById(it).orElse(null) }

        return LetterRequestItem(
            id = id,
            templateId = templateId,
            templateCode = template?.templateCode ?: "CUSTOM",
            templateName = template?.title ?: "Letter",
            employeeId = employeeId,
            employeeName = employeeName,
            reason = purpose,
            recipientAddress = addressee,
            status = status,
            generatedDocumentId = generatedDocumentId,
            generatedDocumentUrl = generatedDocUrl ?: generatedDoc?.storageKey,
            generatedContent = generatedContent,
            rejectionReason = if (status == "REJECTED") approvalRemarks else null,
            approvedBy = approverId,
            approvedAt = approvedAt?.atOffset(ZoneOffset.UTC),
            createdAt = (createdAt ?: Instant.now()).atOffset(ZoneOffset.UTC),
        )
    }

    private fun calculateSha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
