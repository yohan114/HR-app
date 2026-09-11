package com.hr.document

import com.hr.document.internal.DocumentController
import com.hr.document.internal.DocumentService
import com.hr.document.internal.SignatureController
import com.hr.document.internal.SignatureService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.util.UUID

@DisplayName("Document and Signature Controller Unit Tests")
class DocumentSignatureControllerTest {

    private val documentService = mockk<DocumentService>()
    private val signatureService = mockk<SignatureService>()

    private lateinit var documentController: DocumentController
    private lateinit var signatureController: SignatureController

    @BeforeEach
    fun setUp() {
        documentController = DocumentController(documentService)
        signatureController = SignatureController(signatureService)
    }

    @Test
    fun `listFolders returns HTTP 200 with folder response`() {
        val folder = DocumentFolderItem(
            id = UUID.randomUUID(),
            name = "Policies",
            accessLevel = "PUBLIC",
            documentCount = 5,
            createdAt = OffsetDateTime.now(),
        )
        every { documentService.listFolders() } returns DocumentFolderListResponse(listOf(folder))

        val response = documentController.listFolders()

        assertEquals(1, response.folders.size)
        assertEquals("Policies", response.folders[0].name)
    }

    @Test
    fun `createDocument returns HTTP 201 Created`() {
        val req = DocumentCreateRequest(
            title = "Code of Conduct",
            category = "POLICY",
            documentType = "application/pdf",
            accessLevel = "PUBLIC",
            fileUrl = "https://storage.acmecorp.com/docs/conduct.pdf",
            fileName = "conduct.pdf",
            fileSizeBytes = 1024L,
            mimeType = "application/pdf",
            checksumSha256 = "abcd",
        )
        val item = CompanyDocumentItem(
            id = UUID.randomUUID(),
            title = "Code of Conduct",
            category = "POLICY",
            documentType = "application/pdf",
            accessLevel = "PUBLIC",
            currentVersionNumber = 1,
            fileUrl = req.fileUrl,
            fileName = req.fileName,
            fileSizeBytes = req.fileSizeBytes,
            mimeType = req.mimeType,
            checksumSha256 = req.checksumSha256,
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now(),
        )
        every { documentService.createDocument(any(), any()) } returns item

        val response = documentController.createDocument(req, null)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("Code of Conduct", response.body?.title)
    }

    @Test
    fun `signDocument executes digital sign off`() {
        val reqId = UUID.randomUUID()
        val signRequest = SignatureSignRequest(
            signatureType = "CANVAS_DRAWING",
            signatureData = "stroke_path_data",
            consentConfirmed = true,
        )
        val sigItem = SignatureRequestItem(
            id = reqId,
            title = "Employment Agreement",
            fileUrl = "https://storage.acmecorp.com/contracts/ea.pdf",
            fileChecksumSha256 = "1234",
            status = "COMPLETED",
            createdByEmployeeId = UUID.randomUUID(),
            createdByName = "Amanda Perera",
            currentSignerOrder = 1,
            totalSigners = 1,
            signedCount = 1,
            createdAt = OffsetDateTime.now(),
        )
        val detailResponse = SignatureRequestDetailResponse(
            request = sigItem,
            signers = emptyList(),
            auditLogs = emptyList(),
        )
        every { signatureService.signDocument(reqId, signRequest) } returns detailResponse

        val response = signatureController.signDocument(reqId, signRequest)

        assertEquals("COMPLETED", response.request.status)
        assertEquals("Employment Agreement", response.request.title)
    }
}
