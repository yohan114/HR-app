package com.hr.app.ui.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.document.DocumentRepository
import com.hr.client.model.DocumentTemplateItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DocumentViewModel
    @Inject
    constructor(
        private val repository: DocumentRepository,
    ) : ViewModel() {

        private val _uiState = MutableStateFlow(DocumentUiState())
        val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

        val defaultEmployeeId = UUID.fromString("00000000-0000-0000-0000-000000000010")

        init {
            observeRepository()
            refreshAll()
        }

        private fun observeRepository() {
            viewModelScope.launch {
                repository.folders.collect { folders ->
                    _uiState.update { it.copy(folders = folders) }
                }
            }
            viewModelScope.launch {
                repository.documents.collect { docs ->
                    _uiState.update { it.copy(documents = docs) }
                }
            }
            viewModelScope.launch {
                repository.templates.collect { templates ->
                    _uiState.update { it.copy(templates = templates) }
                }
            }
            viewModelScope.launch {
                repository.letterRequests.collect { letters ->
                    _uiState.update { it.copy(letterRequests = letters) }
                }
            }
            viewModelScope.launch {
                repository.signatureRequests.collect { signatures ->
                    _uiState.update { it.copy(signatureRequests = signatures) }
                }
            }
            viewModelScope.launch {
                repository.selectedSignatureDetail.collect { sigDetail ->
                    _uiState.update { it.copy(selectedSignatureDetail = sigDetail) }
                }
            }
            viewModelScope.launch {
                repository.isLoading.collect { loading ->
                    _uiState.update { it.copy(isLoading = loading) }
                }
            }
            viewModelScope.launch {
                repository.error.collect { err ->
                    _uiState.update { it.copy(errorMessage = err) }
                }
            }
        }

        fun refreshAll() {
            viewModelScope.launch {
                repository.loadFolders()
                repository.loadDocuments()
                repository.loadTemplates()
                repository.loadLetterRequests(defaultEmployeeId)
                repository.loadSignatureRequests(signerEmployeeId = defaultEmployeeId)
            }
        }

        fun setTab(tab: DocumentTab) {
            _uiState.update { it.copy(selectedTab = tab) }
        }

        fun selectFolder(folderId: UUID?) {
            _uiState.update { it.copy(selectedFolderId = folderId) }
            viewModelScope.launch {
                repository.loadDocuments(folderId = folderId, category = _uiState.value.selectedCategory, search = _uiState.value.searchQuery.takeIf { it.isNotBlank() })
            }
        }

        fun selectCategory(category: String?) {
            _uiState.update { it.copy(selectedCategory = category) }
            viewModelScope.launch {
                repository.loadDocuments(folderId = _uiState.value.selectedFolderId, category = category, search = _uiState.value.searchQuery.takeIf { it.isNotBlank() })
            }
        }

        fun setSearchQuery(query: String) {
            _uiState.update { it.copy(searchQuery = query) }
            viewModelScope.launch {
                repository.loadDocuments(
                    folderId = _uiState.value.selectedFolderId,
                    category = _uiState.value.selectedCategory,
                    search = query.takeIf { it.isNotBlank() },
                )
            }
        }

        // --- Letter Request Dialog ---

        fun openLetterRequestDialog(template: DocumentTemplateItem? = null) {
            _uiState.update {
                it.copy(
                    isLetterRequestDialogVisible = true,
                    selectedTemplateForRequest = template ?: it.templates.firstOrNull(),
                    letterRequestReason = "",
                    letterRequestRecipient = "",
                )
            }
        }

        fun closeLetterRequestDialog() {
            _uiState.update { it.copy(isLetterRequestDialogVisible = false) }
        }

        fun setLetterRequestTemplate(template: DocumentTemplateItem) {
            _uiState.update { it.copy(selectedTemplateForRequest = template) }
        }

        fun setLetterRequestReason(reason: String) {
            _uiState.update { it.copy(letterRequestReason = reason) }
        }

        fun setLetterRequestRecipient(recipient: String) {
            _uiState.update { it.copy(letterRequestRecipient = recipient) }
        }

        fun submitLetterRequest() {
            val template = _uiState.value.selectedTemplateForRequest ?: return
            val reason = _uiState.value.letterRequestReason
            if (reason.isBlank()) return

            viewModelScope.launch {
                repository.submitLetterRequest(
                    templateId = template.id,
                    employeeId = defaultEmployeeId,
                    reason = reason,
                    recipientAddress = _uiState.value.letterRequestRecipient.takeIf { it.isNotBlank() },
                ).onSuccess {
                    _uiState.update {
                        it.copy(
                            isLetterRequestDialogVisible = false,
                            successMessage = "Letter request submitted successfully for ${template.name}",
                        )
                    }
                }.onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Submission failed") }
                }
            }
        }

        // --- Digital Signature Dialog ---

        fun openSignatureDialog(requestId: UUID) {
            _uiState.update {
                it.copy(
                    signingRequestId = requestId,
                    signatureMode = SignatureInputMode.CANVAS_DRAW,
                    typedSignatureName = "",
                    canvasStrokePaths = emptyList(),
                    legalConsentChecked = false,
                )
            }
            viewModelScope.launch {
                repository.loadSignatureDetail(requestId)
            }
        }

        fun closeSignatureDialog() {
            _uiState.update { it.copy(signingRequestId = null) }
        }

        fun setSignatureMode(mode: SignatureInputMode) {
            _uiState.update { it.copy(signatureMode = mode) }
        }

        fun setTypedSignatureName(name: String) {
            _uiState.update { it.copy(typedSignatureName = name) }
        }

        fun addCanvasStrokePath(stroke: List<Pair<Float, Float>>) {
            _uiState.update { it.copy(canvasStrokePaths = it.canvasStrokePaths + listOf(stroke)) }
        }

        fun clearCanvasStrokes() {
            _uiState.update { it.copy(canvasStrokePaths = emptyList()) }
        }

        fun setLegalConsent(checked: Boolean) {
            _uiState.update { it.copy(legalConsentChecked = checked) }
        }

        fun submitDigitalSignature() {
            val requestId = _uiState.value.signingRequestId ?: return
            val mode = _uiState.value.signatureMode
            val consent = _uiState.value.legalConsentChecked
            if (!consent) {
                _uiState.update { it.copy(errorMessage = "You must confirm legal consent to sign") }
                return
            }

            val (signatureType, signatureData) = when (mode) {
                SignatureInputMode.CANVAS_DRAW -> {
                    val paths = _uiState.value.canvasStrokePaths
                    if (paths.isEmpty()) {
                        _uiState.update { it.copy(errorMessage = "Please draw your signature") }
                        return
                    }
                    val pathData = paths.joinToString(";") { stroke ->
                        stroke.joinToString(",") { "${it.first},${it.second}" }
                    }
                    "CANVAS_DRAWING" to pathData
                }
                SignatureInputMode.TYPED_NAME -> {
                    val name = _uiState.value.typedSignatureName
                    if (name.isBlank()) {
                        _uiState.update { it.copy(errorMessage = "Please type your full legal name") }
                        return
                    }
                    "TYPED_NAME" to name
                }
            }

            viewModelScope.launch {
                _uiState.update { it.copy(isSigningInProgress = true) }
                repository.signDocument(
                    requestId = requestId,
                    signerEmployeeId = defaultEmployeeId,
                    signatureType = signatureType,
                    signatureData = signatureData,
                    consentConfirmed = true,
                ).onSuccess {
                    _uiState.update {
                        it.copy(
                            signingRequestId = null,
                            isSigningInProgress = false,
                            successMessage = "Document signed successfully with legal audit seal!",
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSigningInProgress = false,
                            errorMessage = err.message ?: "Failed to complete signature",
                        )
                    }
                }
            }
        }

        fun clearMessages() {
            _uiState.update { it.copy(errorMessage = null, successMessage = null) }
            repository.clearError()
        }
    }
