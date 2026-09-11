package com.hr.app.ui.recruitment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.recruitment.RecruitmentRepository
import com.hr.client.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecruitmentViewModel
    @Inject
    constructor(
        private val repository: RecruitmentRepository,
    ) : ViewModel() {

        private val _state = MutableStateFlow(RecruitmentState())
        val state: StateFlow<RecruitmentState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                repository.vacancies.collect { list ->
                    _state.update { it.copy(vacancies = list) }
                }
            }
            viewModelScope.launch {
                repository.candidates.collect { list ->
                    _state.update { it.copy(candidates = list) }
                }
            }
            viewModelScope.launch {
                repository.applications.collect { list ->
                    _state.update { it.copy(applications = list) }
                }
            }
            viewModelScope.launch {
                repository.interviews.collect { list ->
                    _state.update { it.copy(interviews = list) }
                }
            }
            viewModelScope.launch {
                repository.selectedOffer.collect { offer ->
                    _state.update { it.copy(selectedOffer = offer) }
                }
            }
            refreshAll()
        }

        fun selectTab(tab: RecruitmentTab) {
            _state.update { it.copy(selectedTab = tab) }
        }

        fun setSearchQuery(query: String) {
            _state.update { it.copy(searchQuery = query) }
        }

        fun setStageFilter(stage: ApplicationItem.Stage?) {
            _state.update { it.copy(selectedStageFilter = stage) }
        }

        fun selectVacancy(vacancy: VacancyItem?) {
            _state.update { it.copy(selectedVacancy = vacancy) }
        }

        fun selectApplication(application: ApplicationItem?) {
            _state.update { it.copy(selectedApplication = application) }
            if (application != null) {
                viewModelScope.launch {
                    repository.getApplicationOffer(application.id)
                }
            }
        }

        fun selectInterview(interview: InterviewItem?) {
            _state.update { it.copy(selectedInterview = interview) }
        }

        fun toggleCreateVacancyDialog(show: Boolean) {
            _state.update { it.copy(showCreateVacancyDialog = show) }
        }

        fun toggleScheduleInterviewDialog(show: Boolean) {
            _state.update { it.copy(showScheduleInterviewDialog = show) }
        }

        fun toggleScorecardDialog(show: Boolean) {
            _state.update { it.copy(showScorecardDialog = show) }
        }

        fun toggleCreateOfferDialog(show: Boolean) {
            _state.update { it.copy(showCreateOfferDialog = show) }
        }

        fun clearMessages() {
            _state.update { it.copy(errorMessage = null, successMessage = null) }
        }

        fun refreshAll() {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                repository.refreshVacancies()
                repository.refreshCandidates()
                repository.refreshApplications()
                repository.refreshInterviews()
                _state.update { it.copy(isLoading = false) }
            }
        }

        fun createVacancy(
            jobCode: String,
            title: String,
            description: String,
            minSalary: BigDecimal?,
            maxSalary: BigDecimal?,
        ) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                val req = VacancyCreateRequest(
                    jobCode = jobCode,
                    title = title,
                    location = "Colombo, Sri Lanka",
                    employmentType = "FULL_TIME",
                    experienceLevel = "SENIOR_LEVEL",
                    currency = "LKR",
                    description = description,
                    openPositions = 1,
                    minSalary = minSalary,
                    maxSalary = maxSalary,
                )
                repository.createVacancy(req)
                    .onSuccess {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                showCreateVacancyDialog = false,
                                successMessage = "Vacancy '$title' posted successfully",
                            )
                        }
                    }
                    .onFailure { err ->
                        _state.update {
                            it.copy(isLoading = false, errorMessage = err.message ?: "Failed to post vacancy")
                        }
                    }
            }
        }

        fun updateApplicationStage(
            applicationId: UUID,
            stage: ApplicationStageUpdateRequest.Stage,
        ) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                val req = ApplicationStageUpdateRequest(stage = stage)
                repository.updateApplicationStage(applicationId, req)
                    .onSuccess { updated ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                selectedApplication = updated,
                                successMessage = "Candidate moved to ${stage.value.replace('_', ' ')}",
                            )
                        }
                    }
                    .onFailure { err ->
                        _state.update {
                            it.copy(isLoading = false, errorMessage = err.message ?: "Failed to update stage")
                        }
                    }
            }
        }

        fun scheduleInterview(
            applicationId: UUID,
            title: String,
            type: InterviewScheduleRequest.InterviewType,
            scheduledStart: OffsetDateTime,
            scheduledEnd: OffsetDateTime,
            meetingLink: String?,
        ) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                val req = InterviewScheduleRequest(
                    applicationId = applicationId,
                    interviewRound = 1,
                    title = title,
                    interviewType = type,
                    scheduledStart = scheduledStart,
                    scheduledEnd = scheduledEnd,
                    locationOrLink = "Google Meet",
                    meetingLink = meetingLink ?: "https://meet.google.com/hiring-panel",
                    interviewerEmployeeIds = listOf(UUID.fromString("e0000000-0000-0000-0000-000000000010")),
                    notes = "Conducted via HR Mobile App Portal",
                )
                repository.scheduleInterview(req)
                    .onSuccess {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                showScheduleInterviewDialog = false,
                                successMessage = "Interview '$title' scheduled",
                            )
                        }
                    }
                    .onFailure { err ->
                        _state.update {
                            it.copy(isLoading = false, errorMessage = err.message ?: "Failed to schedule interview")
                        }
                    }
            }
        }

        fun submitScorecard(
            interviewId: UUID,
            rec: ScorecardSubmitRequest.OverallRecommendation,
            tech: BigDecimal?,
            comm: BigDecimal?,
            problem: BigDecimal?,
            fit: BigDecimal?,
            strengths: String?,
            weaknesses: String?,
            notes: String?,
        ) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                val req = ScorecardSubmitRequest(
                    overallRecommendation = rec,
                    technicalSkillRating = tech,
                    communicationRating = comm,
                    problemSolvingRating = problem,
                    culturalFitRating = fit,
                    strengths = strengths,
                    weaknesses = weaknesses,
                    summaryNotes = notes,
                )
                repository.submitScorecard(interviewId, req)
                    .onSuccess {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                showScorecardDialog = false,
                                successMessage = "Evaluation scorecard submitted (${rec.value.replace('_', ' ')})",
                            )
                        }
                    }
                    .onFailure { err ->
                        _state.update {
                            it.copy(isLoading = false, errorMessage = err.message ?: "Failed to submit scorecard")
                        }
                    }
            }
        }

        fun createOffer(
            applicationId: UUID,
            baseSalary: BigDecimal,
            bonus: BigDecimal?,
            startDate: LocalDate,
            expiryDate: LocalDate,
        ) {
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true) }
                val req = OfferCreateRequest(
                    baseSalary = baseSalary,
                    variableBonus = bonus,
                    currency = "LKR",
                    startDate = startDate,
                    expiryDate = expiryDate,
                    decisionNotes = "Standard enterprise compensation package",
                )
                repository.createOffer(applicationId, req)
                    .onSuccess { offer ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                showCreateOfferDialog = false,
                                selectedOffer = offer,
                                successMessage = "Formal employment offer extended (${offer.offerNumber})",
                            )
                        }
                    }
                    .onFailure { err ->
                        _state.update {
                            it.copy(isLoading = false, errorMessage = err.message ?: "Failed to extend offer")
                        }
                    }
            }
        }
    }
