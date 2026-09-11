package com.hr.training.internal

import com.hr.training.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/v1/training")
class TrainingController(
    private val trainingService: TrainingService,
) {

    // --- Courses -------------------------------------------------------------

    @GetMapping("/courses")
    fun listCourses(
        @RequestParam(name = "category", required = false) category: String?,
        @RequestParam(name = "competencyId", required = false) competencyId: UUID?,
        @RequestParam(name = "search", required = false) search: String?,
    ): CourseListResponse {
        return trainingService.listCourses(category, competencyId, search)
    }

    @PostMapping("/courses")
    fun createCourse(
        @RequestBody request: CourseCreateRequest,
    ): ResponseEntity<CourseDetailResponse> {
        val created = trainingService.createCourse(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping("/courses/{id}")
    fun getCourseDetails(
        @PathVariable("id") id: UUID,
    ): CourseDetailResponse {
        return trainingService.getCourseDetails(id)
    }

    // --- Schedules -----------------------------------------------------------

    @GetMapping("/schedules")
    fun listSchedules(
        @RequestParam(name = "courseId", required = false) courseId: UUID?,
        @RequestParam(name = "status", required = false) status: String?,
    ): TrainingScheduleListResponse {
        return trainingService.listSchedules(courseId, status)
    }

    @PostMapping("/schedules")
    fun createSchedule(
        @RequestBody request: TrainingScheduleCreateRequest,
    ): ResponseEntity<TrainingScheduleItem> {
        val created = trainingService.createSchedule(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    // --- Enrollments ---------------------------------------------------------

    @GetMapping("/enrollments")
    fun listEnrollments(
        @RequestParam(name = "employeeId", required = false) employeeId: UUID?,
        @RequestParam(name = "scheduleId", required = false) scheduleId: UUID?,
        @RequestParam(name = "status", required = false) status: String?,
    ): TrainingEnrollmentListResponse {
        return trainingService.listEnrollments(employeeId, scheduleId, status)
    }

    @PostMapping("/enrollments")
    fun createEnrollment(
        @RequestBody request: TrainingEnrollmentCreateRequest,
    ): ResponseEntity<TrainingEnrollmentItem> {
        val created = trainingService.createEnrollment(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @PostMapping("/enrollments/{id}/approve")
    fun approveEnrollment(
        @PathVariable("id") id: UUID,
        @RequestBody request: TrainingEnrollmentApproveRequest,
    ): TrainingEnrollmentItem {
        return trainingService.approveEnrollment(id, request)
    }

    // --- Attendance & Evaluation ---------------------------------------------

    @PostMapping("/enrollments/{id}/attendance")
    fun checkInAttendance(
        @PathVariable("id") id: UUID,
        @RequestBody request: TrainingAttendanceCheckInRequest,
    ): TrainingAttendanceItem {
        return trainingService.checkInAttendance(id, request)
    }

    @PostMapping("/enrollments/{id}/evaluate")
    fun submitEvaluation(
        @PathVariable("id") id: UUID,
        @RequestBody request: TraineeEvaluationSubmitRequest,
    ): TraineeEvaluationItem {
        return trainingService.submitEvaluation(id, request)
    }

    // --- Certificates & Needs ------------------------------------------------

    @GetMapping("/certificates")
    fun listCertificates(
        @RequestParam(name = "employeeId", required = false) employeeId: UUID?,
    ): TrainingCertificateListResponse {
        return trainingService.listCertificates(employeeId)
    }

    @GetMapping("/needs")
    fun getTrainingNeeds(
        @RequestParam(name = "employeeId", required = false) employeeId: UUID?,
    ): TrainingNeedsResponse {
        return trainingService.getTrainingNeeds(employeeId)
    }
}
