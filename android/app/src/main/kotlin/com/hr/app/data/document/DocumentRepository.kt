package com.hr.app.data.document

import com.hr.client.api.DocumentsApi
import com.hr.client.api.SignaturesApi
import com.hr.client.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository
    @Inject
    constructor(
        private val documentsApi: DocumentsApi,
        private val signaturesApi: SignaturesApi,
    ) {
        private val _folders = MutableStateFlow<List<DocumentFolderItem>>(emptyList())
        val folders: StateFlow<List<DocumentFolderItem>> = _folders.asStateFlow()

        private val _documents = MutableStateFlow<List<CompanyDocumentItem>>(emptyList())
        val documents: StateFlow<List<CompanyDocumentItem>> = _documents.asStateFlow()

        private val _templates = MutableStateFlow<List<DocumentTemplateItem>>(emptyList())
        val templates: StateFlow<List<DocumentTemplateItem>> = _templates.asStateFlow()

        private val _letterRequests = MutableStateFlow<List<LetterRequestItem>>(emptyList())
        val letterRequests: StateFlow<List<LetterRequestItem>> = _letterRequests.asStateFlow()

        private val _signatureRequests = MutableStateFlow<List<SignatureRequestItem>>(emptyList())
        val signatureRequests: StateFlow<List<SignatureRequestItem>> = _signatureRequests.asStateFlow()

        private val _selectedSignatureDetail = MutableStateFlow<SignatureRequestDetailResponse?>(null)
        val selectedSignatureDetail: StateFlow<SignatureRequestDetailResponse?> = _selectedSignatureDetail.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        suspend fun loadFolders(): Result<List<DocumentFolderItem>> =
            runCatching {
                val response = documentsApi.listDocumentFolders()
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load folders: ${response.code()}")
                val list = body.folders
                _folders.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load folders"
            }

        suspend fun loadDocuments(
            folderId: UUID? = null,
            category: String? = null,
            search: String? = null,
        ): Result<List<CompanyDocumentItem>> =
            runCatching {
                _isLoading.value = true
                val response = documentsApi.listDocuments(folderId = folderId, category = category, search = search)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load documents: ${response.code()}")
                val list = body.documents
                _documents.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load documents"
            }.also {
                _isLoading.value = false
            }

        suspend fun loadTemplates(): Result<List<DocumentTemplateItem>> =
            runCatching {
                val response = documentsApi.listDocumentTemplates()
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load templates: ${response.code()}")
                val list = body.templates
                _templates.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load templates"
            }

        suspend fun loadLetterRequests(
            employeeId: UUID? = null,
            status: String? = null,
        ): Result<List<LetterRequestItem>> =
            runCatching {
                val response = documentsApi.listLetterRequests(employeeId = employeeId, status = status)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load letter requests: ${response.code()}")
                val list = body.requests
                _letterRequests.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load letter requests"
            }

        suspend fun submitLetterRequest(
            templateId: UUID,
            employeeId: UUID,
            reason: String,
            recipientAddress: String? = null,
        ): Result<LetterRequestItem> =
            runCatching {
                val request = LetterRequestCreateRequest(
                    templateId = templateId,
                    employeeId = employeeId,
                    reason = reason,
                    recipientAddress = recipientAddress,
                )
                val response = documentsApi.createLetterRequest(request)
                val created = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to submit letter request: ${response.code()}")
                _letterRequests.update { listOf(created) + it }
                created
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to submit letter request"
            }

        suspend fun loadSignatureRequests(
            status: String? = null,
            signerEmployeeId: UUID? = null,
        ): Result<List<SignatureRequestItem>> =
            runCatching {
                _isLoading.value = true
                val response = signaturesApi.listSignatureRequests(status = status, signerEmployeeId = signerEmployeeId)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load signature requests: ${response.code()}")
                val list = body.requests
                _signatureRequests.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load signature requests"
            }.also {
                _isLoading.value = false
            }

        suspend fun loadSignatureDetail(id: UUID): Result<SignatureRequestDetailResponse> =
            runCatching {
                val response = signaturesApi.getSignatureRequestDetails(id)
                val detail = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load signature detail: ${response.code()}")
                _selectedSignatureDetail.value = detail
                detail
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load signature detail"
            }

        suspend fun signDocument(
            requestId: UUID,
            signerEmployeeId: UUID? = null,
            signatureType: String,
            signatureData: String,
            consentConfirmed: Boolean,
        ): Result<SignatureRequestDetailResponse> =
            runCatching {
                val req = SignatureSignRequest(
                    signerEmployeeId = signerEmployeeId,
                    signatureType = signatureType,
                    signatureData = signatureData,
                    consentConfirmed = consentConfirmed,
                )
                val response = signaturesApi.signDocument(requestId, req)
                val updated = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to sign document: ${response.code()}")
                _selectedSignatureDetail.value = updated
                // Update in list
                _signatureRequests.update { list ->
                    list.map { if (it.id == requestId) updated.request else it }
                }
                updated
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to sign document"
            }

        fun clearError() {
            _error.value = null
        }
    }
