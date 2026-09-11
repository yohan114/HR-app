package com.hr.document

import com.hr.document.internal.*
import com.hr.employee.EmployeeLeaveProfile
import com.hr.employee.EmployeeLookupService
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Document Service Unit Tests")
class DocumentServiceTest {

    private val folderRepository = mockk<DocumentFolderRepository>()
    private val tagRepository = mockk<DocumentTagRepository>()
    private val companyDocumentRepository = mockk<CompanyDocumentRepository>()
    private val versionRepository = mockk<DocumentVersionRepository>()
    private val tagMappingRepository = mockk<DocumentTagMappingRepository>()
    private val templateRepository = mockk<DocumentTemplateRepository>()
    private val letterRequestRepository = mockk<LetterRequestRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)

    private lateinit var documentService: DocumentService

    private val employeeId = UUID.randomUUID()
    private val approverId = UUID.randomUUID()
    private val folderId = UUID.randomUUID()
    private val docId = UUID.randomUUID()
    private val templateId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        documentService = DocumentService(
            folderRepository = folderRepository,
            tagRepository = tagRepository,
            companyDocumentRepository = companyDocumentRepository,
            versionRepository = versionRepository,
            tagMappingRepository = tagMappingRepository,
            templateRepository = templateRepository,
            letterRequestRepository = letterRequestRepository,
            employeeLookupService = employeeLookupService,
        )
    }

    @Test
    fun `listFolders returns all folders with document counts`() {
        val folder = DocumentFolderEntity(
            folderName = "HR Policies",
            path = "/policies",
            accessScope = "PUBLIC",
        )
        every { folderRepository.findAllByOrderByFolderNameAsc() } returns listOf(folder)
        every { companyDocumentRepository.countByFolderId(folder.id) } returns 3L

        val response = documentService.listFolders()

        assertEquals(1, response.folders.size)
        assertEquals("HR Policies", response.folders[0].name)
        assertEquals(3, response.folders[0].documentCount)
    }

    @Test
    fun `createFolder generates correct hierarchical path`() {
        val parentFolder = DocumentFolderEntity(
            folderName = "Policies",
            path = "/policies",
            accessScope = "PUBLIC",
        )
        every { folderRepository.findById(parentFolder.id) } returns Optional.of(parentFolder)
        every { folderRepository.save(any()) } answers { firstArg() }

        val request = DocumentFolderCreateRequest(
            name = "Security Policies",
            parentFolderId = parentFolder.id,
            accessLevel = "CONFIDENTIAL",
        )

        val created = documentService.createFolder(request)

        assertEquals("Security Policies", created.name)
        assertEquals("CONFIDENTIAL", created.accessLevel)
        verify { folderRepository.save(match { it.path == "/policies/security-policies" }) }
    }

    @Test
    fun `createDocument registers document and initial version`() {
        every { companyDocumentRepository.save(any()) } answers { firstArg() }
        every { versionRepository.save(any()) } answers { firstArg() }
        every { folderRepository.findById(folderId) } returns Optional.empty()

        val request = DocumentCreateRequest(
            folderId = folderId,
            title = "Employee Handbook 2026",
            category = "POLICY",
            documentType = "application/pdf",
            accessLevel = "PUBLIC",
            fileUrl = "https://storage.acmecorp.com/docs/handbook.pdf",
            fileName = "handbook_2026.pdf",
            fileSizeBytes = 2048576L,
            mimeType = "application/pdf",
            checksumSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        )

        val created = documentService.createDocument(request, approverId)

        assertEquals("Employee Handbook 2026", created.title)
        assertEquals(1, created.currentVersionNumber)
        verify { versionRepository.save(match { it.versionNumber == 1 && it.uploadedBy == approverId }) }
    }

    @Test
    fun `createDocumentVersion increments version number and updates document metadata`() {
        val existingDoc = CompanyDocumentEntity(
            title = "IT Security Policy",
            documentCategory = "POLICY",
            currentVersionNumber = 1,
            fileName = "infosec_v1.pdf",
            fileSizeBytes = 1024L,
            mimeType = "application/pdf",
            storageKey = "https://storage.acmecorp.com/docs/infosec_v1.pdf",
        )
        every { companyDocumentRepository.findById(existingDoc.id) } returns Optional.of(existingDoc)
        every { companyDocumentRepository.save(any()) } answers { firstArg() }
        every { versionRepository.save(any()) } answers { firstArg() }
        every { employeeLookupService.findById(approverId) } returns null

        val request = DocumentVersionCreateRequest(
            fileUrl = "https://storage.acmecorp.com/docs/infosec_v2.pdf",
            fileName = "infosec_v2.pdf",
            fileSizeBytes = 2048L,
            mimeType = "application/pdf",
            checksumSha256 = "abcd1234efgh5678",
            changeNotes = "Added remote work security protocols",
        )

        val version = documentService.createDocumentVersion(existingDoc.id, request, approverId)

        assertEquals(2, version.versionNumber)
        assertEquals(2, existingDoc.currentVersionNumber)
        assertEquals("infosec_v2.pdf", existingDoc.fileName)
        assertEquals("Added remote work security protocols", version.changeNotes)
    }

    @Test
    fun `approveLetterRequest generates interpolated document upon approval`() {
        val employee = EmployeeLeaveProfile(
            id = UUID.randomUUID(),
            tenantId = UUID.randomUUID(),
            employeeCode = "EMP-001",
            firstName = "Kasun",
            lastName = "Mendis",
            displayName = "Kasun Mendis",
            joinDate = LocalDate.of(2022, 1, 15),
            status = "ACTIVE",
        )
        val template = DocumentTemplateEntity(
            templateCode = "SERVICE_LETTER",
            title = "Service & Employment Letter",
            category = "HR_LETTER",
            contentTemplate = "This is to certify that {{employee.name}} (ID: {{employee.code}}) has been employed since {{employee.joinDate}}.",
            placeholders = "[]",
        )
        val letterReq = LetterRequestEntity(
            requestNumber = "LTR-2026-001",
            employeeId = employee.id,
            templateId = template.id,
            purpose = "Visa Application",
            addressee = "Embassy of France",
            status = "SUBMITTED",
        )

        every { letterRequestRepository.findById(letterReq.id) } returns Optional.of(letterReq)
        every { templateRepository.findById(template.id) } returns Optional.of(template)
        every { employeeLookupService.findById(employee.id) } returns employee
        every { companyDocumentRepository.save(any()) } answers { firstArg() }
        every { companyDocumentRepository.findById(any()) } returns Optional.empty()
        every { versionRepository.save(any()) } answers { firstArg() }
        every { letterRequestRepository.save(any()) } answers { firstArg() }

        val response = documentService.approveLetterRequest(
            requestId = letterReq.id,
            request = LetterRequestApproveRequest(approved = true),
            approverId = approverId,
        )

        assertEquals("APPROVED", response.status)
        assertNotNull(response.generatedDocumentId)
        assertTrue(response.generatedContent!!.contains("Kasun Mendis"))
        assertTrue(response.generatedContent!!.contains("EMP-001"))
        verify { companyDocumentRepository.save(match { it.documentCategory == "LETTER" }) }
    }
}
