package com.hr.app.data.training

import com.hr.client.api.TrainingApi
import com.hr.client.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainingRepository
    @Inject
    constructor(
        private val trainingApi: TrainingApi,
    ) {
        private val _courses = MutableStateFlow<List<TrainingCourseItem>>(emptyList())
        val courses: StateFlow<List<TrainingCourseItem>> = _courses.asStateFlow()

        private val _schedules = MutableStateFlow<List<TrainingScheduleItem>>(emptyList())
        val schedules: StateFlow<List<TrainingScheduleItem>> = _schedules.asStateFlow()

        private val _enrollments = MutableStateFlow<List<TrainingEnrollmentItem>>(emptyList())
        val enrollments: StateFlow<List<TrainingEnrollmentItem>> = _enrollments.asStateFlow()

        private val _certificates = MutableStateFlow<List<TrainingCertificateItem>>(emptyList())
        val certificates: StateFlow<List<TrainingCertificateItem>> = _certificates.asStateFlow()

        private val _trainingNeeds = MutableStateFlow<List<TrainingNeedItem>>(emptyList())
        val trainingNeeds: StateFlow<List<TrainingNeedItem>> = _trainingNeeds.asStateFlow()

        private val _selectedCourseDetail = MutableStateFlow<CourseDetailResponse?>(null)
        val selectedCourseDetail: StateFlow<CourseDetailResponse?> = _selectedCourseDetail.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        suspend fun loadCourses(
            category: String? = null,
            competencyId: UUID? = null,
            search: String? = null,
        ): Result<List<TrainingCourseItem>> =
            runCatching {
                val response = trainingApi.listTrainingCourses(category, competencyId, search)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load courses: ${response.code()}")
                val list = body.courses
                _courses.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load courses"
            }

        suspend fun loadCourseDetails(id: UUID): Result<CourseDetailResponse> =
            runCatching {
                val response = trainingApi.getTrainingCourseDetails(id)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load course details: ${response.code()}")
                _selectedCourseDetail.value = body
                body
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load course details"
            }

        suspend fun loadSchedules(
            courseId: UUID? = null,
            status: String? = null,
        ): Result<List<TrainingScheduleItem>> =
            runCatching {
                val response = trainingApi.listTrainingSchedules(courseId, status)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load schedules: ${response.code()}")
                val list = body.schedules
                _schedules.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load schedules"
            }

        suspend fun loadEnrollments(
            employeeId: UUID? = null,
            scheduleId: UUID? = null,
            status: String? = null,
        ): Result<List<TrainingEnrollmentItem>> =
            runCatching {
                val response = trainingApi.listTrainingEnrollments(employeeId, scheduleId, status)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load enrollments: ${response.code()}")
                val list = body.enrollments
                _enrollments.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load enrollments"
            }

        suspend fun loadCertificates(employeeId: UUID? = null): Result<List<TrainingCertificateItem>> =
            runCatching {
                val response = trainingApi.listTrainingCertificates(employeeId)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load certificates: ${response.code()}")
                val list = body.certificates
                _certificates.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load certificates"
            }

        suspend fun loadTrainingNeeds(employeeId: UUID? = null): Result<List<TrainingNeedItem>> =
            runCatching {
                val response = trainingApi.getTrainingNeeds(employeeId)
                val body = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to load training needs: ${response.code()}")
                val list = body.needs
                _trainingNeeds.value = list
                list
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to load training needs"
            }

        suspend fun enroll(
            scheduleId: UUID,
            employeeId: UUID,
            enrollmentType: String = "SELF_ENROLLED",
            nominatedBy: UUID? = null,
        ): Result<TrainingEnrollmentItem> =
            runCatching {
                val request = TrainingEnrollmentCreateRequest(
                    scheduleId = scheduleId,
                    employeeId = employeeId,
                    enrollmentType = enrollmentType,
                    nominatedBy = nominatedBy,
                )
                val response = trainingApi.createTrainingEnrollment(request)
                val item = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to create enrollment: ${response.code()}")
                // Refresh enrollments
                loadEnrollments(employeeId = employeeId)
                item
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to enroll in training"
            }

        suspend fun checkInAttendance(
            enrollmentId: UUID,
            sessionDate: LocalDate,
            remarks: String? = null,
        ): Result<TrainingAttendanceItem> =
            runCatching {
                val request = TrainingAttendanceCheckInRequest(
                    sessionDate = sessionDate,
                    remarks = remarks,
                )
                val response = trainingApi.checkInTrainingAttendance(enrollmentId, request)
                val item = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to check in attendance: ${response.code()}")
                item
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to check in attendance"
            }

        suspend fun submitEvaluation(
            enrollmentId: UUID,
            ratingScore: Int,
            contentRating: Int,
            instructorRating: Int,
            feedbackComments: String? = null,
        ): Result<TraineeEvaluationItem> =
            runCatching {
                val request = TraineeEvaluationSubmitRequest(
                    ratingScore = ratingScore,
                    contentRating = contentRating,
                    instructorRating = instructorRating,
                    feedbackComments = feedbackComments,
                )
                val response = trainingApi.submitTraineeEvaluation(enrollmentId, request)
                val item = response.body().takeIf { response.isSuccessful }
                    ?: error("Failed to submit evaluation: ${response.code()}")
                // Reload certificates and enrollments as this triggers auto-issuing of certificate
                loadCertificates()
                loadEnrollments()
                item
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to submit evaluation"
            }

        fun clearError() {
            _error.value = null
        }
    }
