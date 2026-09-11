package com.hr.app.ui.document

import com.hr.client.model.*
import java.util.UUID

enum class DocumentTab {
    VAULT,
    LETTERS,
    SIGNATURES,
    UPLOADS
}

enum class SignatureInputMode {
    CANVAS_DRAW,
    TYPED_NAME
}

data class DocumentUiState(
    val selectedTab: DocumentTab = DocumentTab.VAULT,
    val folders: List<DocumentFolderItem> = emptyList(),
    val selectedFolderId: UUID? = null,
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val documents: List<CompanyDocumentItem> = emptyList(),
    val templates: List<DocumentTemplateItem> = emptyList(),
    val letterRequests: List<LetterRequestItem> = emptyList(),
    val signatureRequests: List<SignatureRequestItem> = emptyList(),
    val selectedSignatureDetail: SignatureRequestDetailResponse? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,

    // Letter Request Modal State
    val isLetterRequestDialogVisible: Boolean = false,
    val selectedTemplateForRequest: DocumentTemplateItem? = null,
    val letterRequestReason: String = "",
    val letterRequestRecipient: String = "",

    // Digital E-Signature Modal State
    val signingRequestId: UUID? = null,
    val signatureMode: SignatureInputMode = SignatureInputMode.CANVAS_DRAW,
    val typedSignatureName: String = "",
    val canvasStrokePaths: List<List<Pair<Float, Float>>> = emptyList(),
    val legalConsentChecked: Boolean = false,
    val isSigningInProgress: Boolean = false,
)
