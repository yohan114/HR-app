package com.hr.app.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hr.app.data.training.TrainingRepository
import com.hr.client.model.TrainingCourseItem
import com.hr.client.model.TrainingEnrollmentItem
import com.hr.client.model.TrainingCertificateItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TrainingViewModel
    @Inject
    constructor(
        private val repository: TrainingRepository,
    ) : ViewModel() {

        private val _uiState = MutableStateFlow(TrainingUiState())
        val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

        val defaultEmployeeId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")

        init {
            observeRepository()
            refreshAll()
        }

        private fun observeRepository() {
            viewModelScope.launch {
                repository.courses.collect { courses ->
                    _uiState.update { it.copy(courses = courses) }
                }
            }
            viewModelScope.launch {
                repository.schedules.collect { schedules ->
                    _uiState.update { it.copy(schedules = schedules) }
                }
            }
            viewModelScope.launch {
                repository.enrollments.collect { enrollments ->
                    _uiState.update { it.copy(enrollments = enrollments) }
                }
            }
            viewModelScope.launch {
                repository.certificates.collect { certificates ->
                    _uiState.update { it.copy(certificates = certificates) }
                }
            }
            viewModelScope.launch {
                repository.trainingNeeds.collect { needs ->
                    _uiState.update { it.copy(trainingNeeds = needs) }
                }
            }
            viewModelScope.launch {
                repository.selectedCourseDetail.collect { detail ->
                    _uiState.update { it.copy(selectedCourseDetail = detail) }
                }
            }
            viewModelScope.launch {
                repository.isLoading.collect { loading ->
                    _uiState.update { it.copy(isLoading = loading) }
                }
            }
            viewModelScope.launch {
                repository.error.collect { err ->
                    _uiState.update { it.copy(error = err) }
                }
            }
        }

        fun refreshAll() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                repository.loadCourses(
                    category = _uiState.value.selectedCategory,
                    search = _uiState.value.searchQuery.takeIf { it.isNotBlank() },
                )
                repository.loadSchedules()
                repository.loadEnrollments(employeeId = defaultEmployeeId)
                repository.loadCertificates(employeeId = defaultEmployeeId)
                repository.loadTrainingNeeds(employeeId = defaultEmployeeId)
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        fun selectTab(tab: TrainingTab) {
            _uiState.update { it.copy(selectedTab = tab) }
            if (tab == TrainingTab.CATALOG) {
                viewModelScope.launch {
                    repository.loadCourses(
                        category = _uiState.value.selectedCategory,
                        search = _uiState.value.searchQuery.takeIf { it.isNotBlank() },
                    )
                }
            }
        }

        fun selectCategory(category: String?) {
            _uiState.update { it.copy(selectedCategory = category) }
            viewModelScope.launch {
                repository.loadCourses(
                    category = category,
                    search = _uiState.value.searchQuery.takeIf { it.isNotBlank() },
                )
            }
        }

        fun onSearchQueryChanged(query: String) {
            _uiState.update { it.copy(searchQuery = query) }
            viewModelScope.launch {
                repository.loadCourses(
                    category = _uiState.value.selectedCategory,
                    search = query.takeIf { it.isNotBlank() },
                )
            }
        }

        fun openCourseDetail(course: TrainingCourseItem) {
            viewModelScope.launch {
                repository.loadCourseDetails(course.id)
            }
        }

        fun closeCourseDetail() {
            _uiState.update { it.copy(selectedCourseDetail = null) }
        }

        fun openEnrollDialog(course: TrainingCourseItem) {
            viewModelScope.launch {
                repository.loadCourseDetails(course.id)
                _uiState.update { it.copy(isEnrollingCourse = course) }
            }
        }

        fun closeEnrollDialog() {
            _uiState.update { it.copy(isEnrollingCourse = null) }
        }

        fun enrollInBatch(scheduleId: UUID) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                repository.enroll(
                    scheduleId = scheduleId,
                    employeeId = defaultEmployeeId,
                    enrollmentType = "SELF_ENROLLED",
                ).onSuccess {
                    _uiState.update {
                        it.copy(
                            isEnrollingCourse = null,
                            userMessage = "Successfully enrolled in batch!",
                            isLoading = false,
                        )
                    }
                    repository.loadEnrollments(employeeId = defaultEmployeeId)
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "Failed to enroll in course",
                            isLoading = false,
                        )
                    }
                }
            }
        }

        fun checkInAttendance(enrollmentId: UUID) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                repository.checkInAttendance(
                    enrollmentId = enrollmentId,
                    sessionDate = LocalDate.now(),
                    remarks = "Mobile self check-in",
                ).onSuccess {
                    _uiState.update {
                        it.copy(
                            userMessage = "Attendance confirmed for today's session!",
                            isLoading = false,
                        )
                    }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "Failed to check in attendance",
                            isLoading = false,
                        )
                    }
                }
            }
        }

        fun openEvaluationDialog(enrollment: TrainingEnrollmentItem) {
            _uiState.update { it.copy(isEvaluatingEnrollment = enrollment) }
        }

        fun closeEvaluationDialog() {
            _uiState.update { it.copy(isEvaluatingEnrollment = null) }
        }

        fun submitEvaluation(
            enrollmentId: UUID,
            ratingScore: Int,
            contentRating: Int,
            instructorRating: Int,
            feedback: String,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                repository.submitEvaluation(
                    enrollmentId = enrollmentId,
                    ratingScore = ratingScore,
                    contentRating = contentRating,
                    instructorRating = instructorRating,
                    feedbackComments = feedback.takeIf { it.isNotBlank() },
                ).onSuccess {
                    _uiState.update {
                        it.copy(
                            isEvaluatingEnrollment = null,
                            userMessage = "Course evaluation submitted! Digital certificate issued.",
                            isLoading = false,
                        )
                    }
                    repository.loadCertificates(employeeId = defaultEmployeeId)
                    repository.loadEnrollments(employeeId = defaultEmployeeId)
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "Failed to submit evaluation",
                            isLoading = false,
                        )
                    }
                }
            }
        }

        fun openCertificateViewer(cert: TrainingCertificateItem) {
            _uiState.update { it.copy(isViewingCertificate = cert) }
        }

        fun closeCertificateViewer() {
            _uiState.update { it.copy(isViewingCertificate = null) }
        }

        fun clearUserMessage() {
            _uiState.update { it.copy(userMessage = null) }
        }

        fun clearError() {
            repository.clearError()
            _uiState.update { it.copy(error = null) }
        }
    }
