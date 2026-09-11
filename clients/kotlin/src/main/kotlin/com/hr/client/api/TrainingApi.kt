package com.hr.client.api

import com.hr.client.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.CourseCreateRequest
import com.hr.client.model.CourseDetailResponse
import com.hr.client.model.CourseListResponse
import com.hr.client.model.TraineeEvaluationItem
import com.hr.client.model.TraineeEvaluationSubmitRequest
import com.hr.client.model.TrainingAttendanceCheckInRequest
import com.hr.client.model.TrainingAttendanceItem
import com.hr.client.model.TrainingCertificateListResponse
import com.hr.client.model.TrainingEnrollmentApproveRequest
import com.hr.client.model.TrainingEnrollmentCreateRequest
import com.hr.client.model.TrainingEnrollmentItem
import com.hr.client.model.TrainingEnrollmentListResponse
import com.hr.client.model.TrainingNeedsResponse
import com.hr.client.model.TrainingScheduleCreateRequest
import com.hr.client.model.TrainingScheduleItem
import com.hr.client.model.TrainingScheduleListResponse

interface TrainingApi {
    /**
     * POST v1/training/enrollments/{id}/approve
     * Approve or reject training enrollment
     * Approves or rejects a pending employee training nomination.
     * Responses:
     *  - 200: Enrollment decision recorded
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param trainingEnrollmentApproveRequest 
     * @return [TrainingEnrollmentItem]
     */
    @POST("v1/training/enrollments/{id}/approve")
    suspend fun approveTrainingEnrollment(@Path("id") id: java.util.UUID, @Body trainingEnrollmentApproveRequest: TrainingEnrollmentApproveRequest): Response<TrainingEnrollmentItem>

    /**
     * POST v1/training/enrollments/{id}/attendance
     * Check in attendance for training session
     * Ingests an employee attendance check-in for a scheduled training session.
     * Responses:
     *  - 200: Attendance checked in successfully
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param trainingAttendanceCheckInRequest 
     * @return [TrainingAttendanceItem]
     */
    @POST("v1/training/enrollments/{id}/attendance")
    suspend fun checkInTrainingAttendance(@Path("id") id: java.util.UUID, @Body trainingAttendanceCheckInRequest: TrainingAttendanceCheckInRequest): Response<TrainingAttendanceItem>

    /**
     * POST v1/training/courses
     * Create training course
     * Registers a new course in the training catalogue.
     * Responses:
     *  - 201: Training course created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *
     * @param courseCreateRequest 
     * @return [CourseDetailResponse]
     */
    @POST("v1/training/courses")
    suspend fun createTrainingCourse(@Body courseCreateRequest: CourseCreateRequest): Response<CourseDetailResponse>

    /**
     * POST v1/training/enrollments
     * Enrol or nominate employee for training
     * Enrols an employee into a training schedule (self-enrolled or manager-nominated).
     * Responses:
     *  - 201: Training enrollment created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *
     * @param trainingEnrollmentCreateRequest 
     * @return [TrainingEnrollmentItem]
     */
    @POST("v1/training/enrollments")
    suspend fun createTrainingEnrollment(@Body trainingEnrollmentCreateRequest: TrainingEnrollmentCreateRequest): Response<TrainingEnrollmentItem>

    /**
     * POST v1/training/schedules
     * Create training schedule
     * Schedules a new training batch or session offering.
     * Responses:
     *  - 201: Training schedule created
     *  - 400: Malformed request, or a field value that could not be interpreted
     *
     * @param trainingScheduleCreateRequest 
     * @return [TrainingScheduleItem]
     */
    @POST("v1/training/schedules")
    suspend fun createTrainingSchedule(@Body trainingScheduleCreateRequest: TrainingScheduleCreateRequest): Response<TrainingScheduleItem>

    /**
     * GET v1/training/courses/{id}
     * Get training course details
     * Retrieves detailed course information, syllabus, provider, and upcoming schedules.
     * Responses:
     *  - 200: Course details
     *  - 404: Not found
     *
     * @param id 
     * @return [CourseDetailResponse]
     */
    @GET("v1/training/courses/{id}")
    suspend fun getTrainingCourseDetails(@Path("id") id: java.util.UUID): Response<CourseDetailResponse>

    /**
     * GET v1/training/needs
     * Get recommended courses based on competency gaps
     * Analyzes performance appraisal ratings and recommends courses closing competency gaps.
     * Responses:
     *  - 200: Personalized course recommendations
     *
     * @param employeeId  (optional)
     * @return [TrainingNeedsResponse]
     */
    @GET("v1/training/needs")
    suspend fun getTrainingNeeds(@Query("employeeId") employeeId: java.util.UUID? = null): Response<TrainingNeedsResponse>

    /**
     * GET v1/training/certificates
     * List training certificates
     * Retrieves verified digital certificates issued to an employee.
     * Responses:
     *  - 200: List of certificates
     *
     * @param employeeId  (optional)
     * @return [TrainingCertificateListResponse]
     */
    @GET("v1/training/certificates")
    suspend fun listTrainingCertificates(@Query("employeeId") employeeId: java.util.UUID? = null): Response<TrainingCertificateListResponse>

    /**
     * GET v1/training/courses
     * List training courses
     * Retrieves courses filtered by category, competency, or search query.
     * Responses:
     *  - 200: List of training courses
     *
     * @param category  (optional)
     * @param competencyId  (optional)
     * @param search  (optional)
     * @return [CourseListResponse]
     */
    @GET("v1/training/courses")
    suspend fun listTrainingCourses(@Query("category") category: kotlin.String? = null, @Query("competencyId") competencyId: java.util.UUID? = null, @Query("search") search: kotlin.String? = null): Response<CourseListResponse>

    /**
     * GET v1/training/enrollments
     * List training enrollments
     * Retrieves employee training enrollments filtered by employee, schedule, or status.
     * Responses:
     *  - 200: List of training enrollments
     *
     * @param employeeId  (optional)
     * @param scheduleId  (optional)
     * @param status  (optional)
     * @return [TrainingEnrollmentListResponse]
     */
    @GET("v1/training/enrollments")
    suspend fun listTrainingEnrollments(@Query("employeeId") employeeId: java.util.UUID? = null, @Query("scheduleId") scheduleId: java.util.UUID? = null, @Query("status") status: kotlin.String? = null): Response<TrainingEnrollmentListResponse>

    /**
     * GET v1/training/schedules
     * List training schedules
     * Retrieves scheduled training sessions and batches.
     * Responses:
     *  - 200: List of training schedules
     *
     * @param courseId  (optional)
     * @param status  (optional)
     * @return [TrainingScheduleListResponse]
     */
    @GET("v1/training/schedules")
    suspend fun listTrainingSchedules(@Query("courseId") courseId: java.util.UUID? = null, @Query("status") status: kotlin.String? = null): Response<TrainingScheduleListResponse>

    /**
     * POST v1/training/enrollments/{id}/evaluate
     * Submit trainee feedback and evaluation
     * Submits participant course feedback ratings, reviews, and automatically issues certificate if eligible.
     * Responses:
     *  - 200: Evaluation submitted and processed
     *  - 400: Malformed request, or a field value that could not be interpreted
     *  - 404: Not found
     *
     * @param id 
     * @param traineeEvaluationSubmitRequest 
     * @return [TraineeEvaluationItem]
     */
    @POST("v1/training/enrollments/{id}/evaluate")
    suspend fun submitTraineeEvaluation(@Path("id") id: java.util.UUID, @Body traineeEvaluationSubmitRequest: TraineeEvaluationSubmitRequest): Response<TraineeEvaluationItem>

}
