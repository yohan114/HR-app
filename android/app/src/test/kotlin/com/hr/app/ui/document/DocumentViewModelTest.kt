package com.hr.app.ui.document

import com.hr.app.data.document.DocumentRepository
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentViewModelTest {

    private val repository = mockk<DocumentRepository>()
    private val testDispatcher = StandardTestDispatcher()

    private val foldersFlow = MutableStateFlow<List<DocumentFolderItem>>(emptyList())
    private val documentsFlow = MutableStateFlow<List<CompanyDocumentItem>>(emptyList())
    private val templatesFlow = MutableStateFlow<List<DocumentTemplateItem>>(emptyList())
    private val letterRequestsFlow = MutableStateFlow<List<LetterRequestItem>>(emptyList())
    private val signatureRequestsFlow = MutableStateFlow<List<SignatureRequestItem>>(emptyList())
    private val selectedSignatureDetailFlow = MutableStateFlow<SignatureRequestDetailResponse?>(null)
    private val isLoadingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    private val reqId = UUID.randomUUID()
    private val tplId = UUID.randomUUID()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.folders } returns foldersFlow
        coEvery { repository.documents } returns documentsFlow
        coEvery { repository.templates } returns templatesFlow
        coEvery { repository.letterRequests } returns letterRequestsFlow
        coEvery { repository.signatureRequests } returns signatureRequestsFlow
        coEvery { repository.selectedSignatureDetail } returns selectedSignatureDetailFlow
        coEvery { repository.isLoading } returns isLoadingFlow
        coEvery { repository.error } returns errorFlow

        coEvery { repository.loadFolders() } returns Result.success(emptyList())
        coEvery { repository.loadDocuments(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { repository.loadTemplates() } returns Result.success(emptyList())
        coEvery { repository.loadLetterRequests(any(), any()) } returns Result.success(emptyList())
        coEvery { repository.loadSignatureRequests(any(), any()) } returns Result.success(emptyList())
        coEvery { repository.loadSignatureDetail(any()) } returns Result.success(mockk())
        coEvery { repository.clearError() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setTab updates selectedTab in UI state`() = runTest {
        val viewModel = DocumentViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(DocumentTab.VAULT, viewModel.uiState.value.selectedTab)
        viewModel.setTab(DocumentTab.SIGNATURES)
        assertEquals(DocumentTab.SIGNATURES, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun `openLetterRequestDialog populates dialog fields`() = runTest {
        val viewModel = DocumentViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        val template = DocumentTemplateItem(
            id = tplId,
            templateCode = "SERVICE_LETTER",
            name = "Service Letter",
            category = "HR_LETTER",
            bodyTemplate = "Content",
            variables = emptyList(),
            isActive = true,
            createdAt = OffsetDateTime.now(),
        )

        viewModel.openLetterRequestDialog(template)
        assertTrue(viewModel.uiState.value.isLetterRequestDialogVisible)
        assertEquals(template, viewModel.uiState.value.selectedTemplateForRequest)

        viewModel.closeLetterRequestDialog()
        assertFalse(viewModel.uiState.value.isLetterRequestDialogVisible)
    }

    @Test
    fun `openSignatureDialog sets target request and resets canvas`() = runTest {
        val viewModel = DocumentViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openSignatureDialog(reqId)
        assertEquals(reqId, viewModel.uiState.value.signingRequestId)
        assertEquals(SignatureInputMode.CANVAS_DRAW, viewModel.uiState.value.signatureMode)
        assertTrue(viewModel.uiState.value.canvasStrokePaths.isEmpty())
        assertFalse(viewModel.uiState.value.legalConsentChecked)
    }

    @Test
    fun `submitDigitalSignature requires legal consent confirmed`() = runTest {
        val viewModel = DocumentViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.openSignatureDialog(reqId)
        viewModel.addCanvasStrokePath(listOf(0f to 0f, 10f to 10f))
        // Consent not checked yet
        viewModel.submitDigitalSignature()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("legal consent"))
        coVerify(exactly = 0) { repository.signDocument(any(), any(), any(), any(), any()) }
    }
}
