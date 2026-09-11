package com.hr.app.data.document

import com.hr.client.api.DocumentsApi
import com.hr.client.api.SignaturesApi
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.OffsetDateTime
import java.util.UUID

class DocumentRepositoryTest {

    private val documentsApi = mockk<DocumentsApi>()
    private val signaturesApi = mockk<SignaturesApi>()

    private lateinit var repository: DocumentRepository

    private val docId = UUID.randomUUID()
    private val folderId = UUID.randomUUID()
    private val reqId = UUID.randomUUID()

    @Before
    fun setUp() {
        repository = DocumentRepository(documentsApi, signaturesApi)
    }

    @Test
    fun `loadFolders updates folders flow on success`() = runTest {
        val folder = DocumentFolderItem(
            id = folderId,
            name = "Policies",
            accessLevel = "PUBLIC",
            documentCount = 4,
            createdAt = OffsetDateTime.now(),
        )
        coEvery { documentsApi.listDocumentFolders() } returns Response.success(DocumentFolderListResponse(listOf(folder)))

        val result = repository.loadFolders()

        assertTrue(result.isSuccess)
        assertEquals(1, repository.folders.value.size)
        assertEquals("Policies", repository.folders.value[0].name)
    }

    @Test
    fun `loadDocuments updates documents flow on success`() = runTest {
        val doc = CompanyDocumentItem(
            id = docId,
            title = "Employee Handbook",
            category = "POLICY",
            documentType = "application/pdf",
            accessLevel = "PUBLIC",
            currentVersionNumber = 1,
            fileUrl = "https://storage.acmecorp.com/docs/handbook.pdf",
            fileName = "handbook.pdf",
            fileSizeBytes = 1024L,
            mimeType = "application/pdf",
            checksumSha256 = "abcd",
            tags = listOf("HANDBOOK", "POLICY"),
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now(),
        )
        coEvery { documentsApi.listDocuments(any(), any(), any()) } returns Response.success(DocumentListResponse(listOf(doc)))

        val result = repository.loadDocuments()

        assertTrue(result.isSuccess)
        assertEquals(1, repository.documents.value.size)
        assertEquals("Employee Handbook", repository.documents.value[0].title)
    }

    @Test
    fun `signDocument invokes API and updates signatureRequests flow`() = runTest {
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
        val detail = SignatureRequestDetailResponse(
            request = sigItem,
            signers = emptyList(),
            auditLogs = emptyList(),
        )
        coEvery { signaturesApi.signDocument(reqId, any()) } returns Response.success(detail)

        val result = repository.signDocument(
            requestId = reqId,
            signatureType = "CANVAS_DRAWING",
            signatureData = "stroke_path",
            consentConfirmed = true,
        )

        assertTrue(result.isSuccess)
        assertEquals("COMPLETED", repository.selectedSignatureDetail.value?.request?.status)
    }
}
