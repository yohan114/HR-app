package com.hr.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.client.api.EmployeesApi
import com.hr.client.model.EmployeeDocumentItem
import com.hr.client.model.EmployeeProfile
import com.hr.client.model.FormSchema
import com.hr.client.model.RenewDocumentRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * One employee profile, rendered from the server's form schema alongside statutory compliance documents.
 *
 * Both profile and compliance document requests are loaded asynchronously for own profile views.
 */
@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val employeesApi: EmployeesApi,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ProfileState())
        val state: StateFlow<ProfileState> = _state.asStateFlow()

        private var loadedId: UUID? = null

        /** @param employeeId null loads the caller's own profile, via `/v1/employees/me`. */
        fun load(employeeId: UUID?) {
            loadedId = employeeId
            _state.update { it.copy(loading = true, error = null) }

            viewModelScope.launch {
                runCatching {
                    val profile = async { if (employeeId == null) employeesApi.getOwnEmployeeProfile() else employeesApi.getEmployeeProfile(employeeId) }
                    val schema = async { employeeId?.let { employeesApi.getEmployeeEditForm(it) } }
                    val documents = async {
                        if (employeeId == null) {
                            runCatching {
                                val resp = employeesApi.getOwnDocuments()
                                if (resp.isSuccessful) resp.body().orEmpty() else emptyList()
                            }.getOrElse { emptyList() }
                        } else {
                            emptyList()
                        }
                    }

                    listOf(profile, schema, documents).awaitAll()

                    val profileResponse = profile.await()
                    val body =
                        profileResponse.body().takeIf { profileResponse.isSuccessful }
                            ?: error("profile ${profileResponse.code()}")

                    Triple(
                        body,
                        schema.await()?.let { if (it.isSuccessful) it.body() else null },
                        documents.await(),
                    )
                }.onSuccess { (profile, schema, docs) ->
                    _state.update {
                        it.copy(
                            loading = false,
                            profile = profile,
                            schema = schema,
                            documents = docs,
                            error = null,
                        )
                    }
                }.onFailure { cause ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error =
                                if (cause is java.io.IOException) {
                                    "No connection. Check your network and try again."
                                } else {
                                    "That profile is not available."
                                },
                        )
                    }
                }
            }
        }

        fun retry() = load(loadedId)

        fun openRenewalDialog(doc: EmployeeDocumentItem?) {
            _state.update { it.copy(selectedDocumentForRenewal = doc) }
        }

        fun openRenewalDialogById(documentId: String) {
            val matching = _state.value.documents.find { it.id.toString() == documentId }
            _state.update { it.copy(selectedDocumentForRenewal = matching) }
        }

        fun closeRenewalDialog() {
            _state.update { it.copy(selectedDocumentForRenewal = null) }
        }

        fun clearRenewalMessage() {
            _state.update { it.copy(renewalMessage = null) }
        }

        fun submitRenewal(
            docType: String,
            docNumber: String,
            expiryDate: LocalDate,
            issueDate: LocalDate? = null,
            issuingCountry: String? = null,
            attachmentKey: String? = null,
        ) {
            val prevDoc = _state.value.selectedDocumentForRenewal
            _state.update { it.copy(renewalInProgress = true) }

            viewModelScope.launch {
                runCatching {
                    val request = RenewDocumentRequest(
                        docType = docType,
                        docNumber = docNumber,
                        issueDate = issueDate,
                        expiryDate = expiryDate,
                        issuingCountry = issuingCountry,
                        attachmentKey = attachmentKey,
                        previousDocumentId = prevDoc?.id,
                    )
                    val response = employeesApi.renewOwnDocument(request)
                    if (response.isSuccessful && response.body() != null) {
                        response.body()!!
                    } else {
                        error("Renewal failed: ${response.code()}")
                    }
                }.onSuccess { resp ->
                    val updatedDocs = _state.value.documents
                        .filter { it.id != prevDoc?.id }
                        .toMutableList()
                        .apply { add(0, resp.document) }

                    _state.update {
                        it.copy(
                            renewalInProgress = false,
                            selectedDocumentForRenewal = null,
                            documents = updatedDocs,
                            renewalMessage = resp.message.ifBlank { "Document renewed successfully." },
                        )
                    }
                }.onFailure { err ->
                    _state.update {
                        it.copy(
                            renewalInProgress = false,
                            renewalMessage = "Failed to submit renewal: ${err.message ?: "Unknown error"}",
                        )
                    }
                }
            }
        }
    }

data class ProfileState(
    val profile: EmployeeProfile? = null,
    val schema: FormSchema? = null,
    val documents: List<EmployeeDocumentItem> = emptyList(),
    val selectedDocumentForRenewal: EmployeeDocumentItem? = null,
    val renewalInProgress: Boolean = false,
    val renewalMessage: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)
