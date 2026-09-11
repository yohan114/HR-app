package com.hr.app.ui.disciplinary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.disciplinary.DisciplinaryRepository
import com.hr.app.data.disciplinary.GrievanceRepository
import com.hr.client.model.GrievanceSubmitRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DisciplinaryViewModel
    @Inject
    constructor(
        private val grievanceRepository: GrievanceRepository,
        private val disciplinaryRepository: DisciplinaryRepository,
    ) : ViewModel() {

        private val _uiState = MutableStateFlow(DisciplinaryGrievanceUiState())
        val uiState: StateFlow<DisciplinaryGrievanceUiState> = _uiState.asStateFlow()

        val grounds = grievanceRepository.grounds
        val channels = grievanceRepository.channels
        val grievances = grievanceRepository.grievances
        val selectedGrievance = grievanceRepository.selectedGrievance
        val pendingGrievanceCount = grievanceRepository.pendingCount

        val incidentTypes = disciplinaryRepository.types
        val incidents = disciplinaryRepository.incidents
        val selectedIncident = disciplinaryRepository.selectedIncident
        val openIncidentCount = disciplinaryRepository.openCount

        init {
            refreshAll()
        }

        fun refreshAll() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                grievanceRepository.refreshGrounds()
                grievanceRepository.refreshChannels()
                grievanceRepository.refreshMyGrievances()
                disciplinaryRepository.refreshTypes()
                disciplinaryRepository.refreshIncidents()
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        fun selectTab(tab: DisciplinaryGrievanceTab) {
            _uiState.update {
                it.copy(
                    activeTab = tab,
                    errorBanner = null,
                    successBanner = null,
                )
            }
        }

        fun updateGrievanceForm(
            title: String? = null,
            description: String? = null,
            groundId: UUID? = null,
            channelId: UUID? = null,
        ) {
            _uiState.update { current ->
                current.copy(
                    grievanceTitle = title ?: current.grievanceTitle,
                    grievanceDescription = description ?: current.grievanceDescription,
                    selectedGroundId = groundId ?: current.selectedGroundId,
                    selectedChannelId = channelId ?: current.selectedChannelId,
                )
            }
        }

        fun toggleAnonymous(isAnonymous: Boolean) {
            _uiState.update { it.copy(isAnonymous = isAnonymous) }
        }

        fun submitGrievance() {
            val state = _uiState.value
            if (state.grievanceTitle.isBlank()) {
                _uiState.update { it.copy(errorBanner = "Please specify a grievance title") }
                return
            }
            if (state.grievanceDescription.isBlank()) {
                _uiState.update { it.copy(errorBanner = "Please enter grievance description details") }
                return
            }
            val groundId = state.selectedGroundId
                ?: grounds.value?.grounds?.firstOrNull()?.id
                ?: UUID.fromString("d1000000-0000-0000-0000-000000000001")
            val channelId = state.selectedChannelId
                ?: channels.value.firstOrNull()?.id
                ?: UUID.fromString("d2000000-0000-0000-0000-000000000001")

            viewModelScope.launch {
                _uiState.update { it.copy(isSubmittingGrievance = true, errorBanner = null) }
                val result = grievanceRepository.submitGrievance(
                    GrievanceSubmitRequest(
                        groundId = groundId,
                        channelId = channelId,
                        title = state.grievanceTitle.trim(),
                        description = state.grievanceDescription.trim(),
                        anonymous = state.isAnonymous,
                    ),
                )
                result.fold(
                    onSuccess = { created ->
                        _uiState.update {
                            it.copy(
                                isSubmittingGrievance = false,
                                successBanner = "Grievance ${created.grievanceNumber} filed successfully.",
                                grievanceTitle = "",
                                grievanceDescription = "",
                                isAnonymous = false,
                                activeTab = DisciplinaryGrievanceTab.MY_GRIEVANCES,
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isSubmittingGrievance = false,
                                errorBanner = error.message ?: "Failed to submit grievance",
                            )
                        }
                    },
                )
            }
        }

        fun openGrievanceDetail(id: UUID) {
            viewModelScope.launch {
                grievanceRepository.loadGrievanceDetail(id)
                _uiState.update { it.copy(showGrievanceDetailDialog = true) }
            }
        }

        fun closeGrievanceDetail() {
            _uiState.update { it.copy(showGrievanceDetailDialog = false) }
        }

        fun openGrievanceAppeal(id: UUID) {
            _uiState.update {
                it.copy(
                    showGrievanceAppealDialog = true,
                    appealGrievanceId = id,
                    appealReasonText = "",
                )
            }
        }

        fun closeGrievanceAppeal() {
            _uiState.update { it.copy(showGrievanceAppealDialog = false, appealGrievanceId = null) }
        }

        fun updateGrievanceAppealReason(text: String) {
            _uiState.update { it.copy(appealReasonText = text) }
        }

        fun submitGrievanceAppeal() {
            val state = _uiState.value
            val grievanceId = state.appealGrievanceId ?: return
            if (state.appealReasonText.isBlank()) {
                _uiState.update { it.copy(errorBanner = "Please specify grounds for appeal") }
                return
            }

            viewModelScope.launch {
                _uiState.update { it.copy(isSubmittingAppeal = true, errorBanner = null) }
                val result = grievanceRepository.appealGrievance(grievanceId, state.appealReasonText.trim())
                result.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isSubmittingAppeal = false,
                                showGrievanceAppealDialog = false,
                                appealGrievanceId = null,
                                successBanner = "Appeal lodged successfully for investigation review.",
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isSubmittingAppeal = false,
                                errorBanner = error.message ?: "Failed to lodge appeal",
                            )
                        }
                    },
                )
            }
        }

        fun openActionResponse(actionId: UUID) {
            _uiState.update {
                it.copy(
                    showActionResponseDialog = true,
                    respondingActionId = actionId,
                    responseExplanationText = "",
                )
            }
        }

        fun closeActionResponse() {
            _uiState.update { it.copy(showActionResponseDialog = false, respondingActionId = null) }
        }

        fun updateResponseExplanation(text: String) {
            _uiState.update { it.copy(responseExplanationText = text) }
        }

        fun submitActionResponse() {
            val state = _uiState.value
            val actionId = state.respondingActionId ?: return
            if (state.responseExplanationText.isBlank()) {
                _uiState.update { it.copy(errorBanner = "Please enter your explanation before submitting") }
                return
            }

            viewModelScope.launch {
                _uiState.update { it.copy(isSubmittingResponse = true, errorBanner = null) }
                val result = disciplinaryRepository.respondToCorrectiveAction(actionId, state.responseExplanationText.trim())
                result.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isSubmittingResponse = false,
                                showActionResponseDialog = false,
                                respondingActionId = null,
                                successBanner = "Response to show cause notice submitted successfully.",
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isSubmittingResponse = false,
                                errorBanner = error.message ?: "Failed to submit response",
                            )
                        }
                    },
                )
            }
        }

        fun openActionAppeal(actionId: UUID) {
            _uiState.update {
                it.copy(
                    showActionAppealDialog = true,
                    appealingActionId = actionId,
                    actionAppealReasonText = "",
                )
            }
        }

        fun closeActionAppeal() {
            _uiState.update { it.copy(showActionAppealDialog = false, appealingActionId = null) }
        }

        fun updateActionAppealReason(text: String) {
            _uiState.update { it.copy(actionAppealReasonText = text) }
        }

        fun submitActionAppeal() {
            val state = _uiState.value
            val actionId = state.appealingActionId ?: return
            if (state.actionAppealReasonText.isBlank()) {
                _uiState.update { it.copy(errorBanner = "Please state your reasons for appeal") }
                return
            }

            viewModelScope.launch {
                _uiState.update { it.copy(isSubmittingActionAppeal = true, errorBanner = null) }
                val result = disciplinaryRepository.appealCorrectiveAction(actionId, state.actionAppealReasonText.trim())
                result.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isSubmittingActionAppeal = false,
                                showActionAppealDialog = false,
                                appealingActionId = null,
                                successBanner = "Appeal lodged against disciplinary action.",
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isSubmittingActionAppeal = false,
                                errorBanner = error.message ?: "Failed to lodge action appeal",
                            )
                        }
                    },
                )
            }
        }

        fun openIncidentDetail(id: UUID) {
            viewModelScope.launch {
                disciplinaryRepository.loadIncidentDetails(id)
                _uiState.update { it.copy(showIncidentDetailSheet = true, selectedIncidentId = id) }
            }
        }

        fun closeIncidentDetail() {
            _uiState.update { it.copy(showIncidentDetailSheet = false, selectedIncidentId = null) }
        }

        fun dismissBanners() {
            _uiState.update { it.copy(errorBanner = null, successBanner = null) }
        }
    }
