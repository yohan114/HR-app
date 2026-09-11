package com.hr.training

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

enum class ProviderType {
    INTERNAL, EXTERNAL, ACADEMIC, ONLINE_PLATFORM
}

enum class CourseCategory {
    TECHNICAL, LEADERSHIP, COMPLIANCE, SOFT_SKILLS, SECURITY
}

enum class DeliveryMode {
    CLASSROOM, ONLINE_SELF_PACED, ONLINE_LIVE, BLENDED
}

enum class ScheduleStatus {
    SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
}

enum class EnrollmentType {
    SELF_ENROLLED, MANAGER_NOMINATED, MANDATORY
}

enum class EnrollmentStatus {
    REQUESTED, APPROVED, REJECTED, ENROLLED, COMPLETED, CANCELLED, NO_SHOW
}

enum class AttendanceStatus {
    PRESENT, LATE, ABSENT, EXCUSED
}

data class TrainingProviderItem(
    val id: UUID,
    val providerName: String,
    val providerType: String,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val websiteUrl: String? = null,
    val isAccredited: Boolean = true
)

data class ResourcePersonItem(
    val id: UUID,
    val providerId: UUID? = null,
    val fullName: String,
    val email: String? = null,
    val bio: String? = null,
    val specialization: String? = null,
    val internalEmployeeId: UUID? = null
)

data class TrainingCourseItem(
    val id: UUID,
    val providerId: UUID? = null,
    val providerName: String? = null,
    val courseCode: String,
    val title: String,
    val description: String? = null,
    val category: String,
    val deliveryMode: String,
    val durationHours: Double,
    val targetAudience: String? = null,
    val prerequisites: String? = null,
    val maxCapacity: Int,
    val competencyId: UUID? = null,
    val isActive: Boolean = true
)

data class CourseListResponse(
    val courses: List<TrainingCourseItem>
)

data class CourseCreateRequest(
    val providerId: UUID? = null,
    val courseCode: String,
    val title: String,
    val description: String? = null,
    val category: String,
    val deliveryMode: String,
    val durationHours: Double,
    val targetAudience: String? = null,
    val prerequisites: String? = null,
    val maxCapacity: Int,
    val competencyId: UUID? = null
)

data class CourseDetailResponse(
    val course: TrainingCourseItem,
    val provider: TrainingProviderItem? = null,
    val schedules: List<TrainingScheduleItem> = emptyList()
)

data class TrainingScheduleItem(
    val id: UUID,
    val courseId: UUID,
    val courseTitle: String,
    val trainerId: UUID? = null,
    val trainerName: String? = null,
    val batchCode: String,
    val startDate: OffsetDateTime,
    val endDate: OffsetDateTime,
    val venueName: String? = null,
    val virtualMeetingUrl: String? = null,
    val totalSeats: Int,
    val enrolledSeats: Int,
    val status: String,
    val costPerParticipant: Double = 0.0
)

data class TrainingScheduleListResponse(
    val schedules: List<TrainingScheduleItem>
)

data class TrainingScheduleCreateRequest(
    val courseId: UUID,
    val trainerId: UUID? = null,
    val batchCode: String,
    val startDate: OffsetDateTime,
    val endDate: OffsetDateTime,
    val venueName: String? = null,
    val virtualMeetingUrl: String? = null,
    val totalSeats: Int,
    val costPerParticipant: Double? = 0.0
)

data class TrainingEnrollmentItem(
    val id: UUID,
    val scheduleId: UUID,
    val courseTitle: String,
    val batchCode: String,
    val startDate: OffsetDateTime,
    val endDate: OffsetDateTime,
    val employeeId: UUID,
    val employeeName: String,
    val enrollmentType: String,
    val status: String,
    val nominatedBy: UUID? = null,
    val approvalRemarks: String? = null,
    val approvedAt: OffsetDateTime? = null,
    val cancellationReason: String? = null,
    val createdAt: OffsetDateTime
)

data class TrainingEnrollmentListResponse(
    val enrollments: List<TrainingEnrollmentItem>
)

data class TrainingEnrollmentCreateRequest(
    val scheduleId: UUID,
    val employeeId: UUID,
    val enrollmentType: String,
    val nominatedBy: UUID? = null
)

data class TrainingEnrollmentApproveRequest(
    val decision: String,
    val remarks: String? = null
)

data class TrainingAttendanceItem(
    val id: UUID,
    val enrollmentId: UUID,
    val sessionDate: LocalDate,
    val checkInTime: OffsetDateTime,
    val attendanceStatus: String,
    val remarks: String? = null
)

data class TrainingAttendanceCheckInRequest(
    val sessionDate: LocalDate,
    val remarks: String? = null
)

data class TraineeEvaluationItem(
    val id: UUID,
    val enrollmentId: UUID,
    val ratingScore: Int,
    val contentRating: Int,
    val instructorRating: Int,
    val feedbackComments: String? = null,
    val submittedAt: OffsetDateTime
)

data class TraineeEvaluationSubmitRequest(
    val ratingScore: Int,
    val contentRating: Int,
    val instructorRating: Int,
    val feedbackComments: String? = null
)

data class TrainingCertificateItem(
    val id: UUID,
    val enrollmentId: UUID,
    val courseTitle: String,
    val employeeName: String,
    val certificateNumber: String,
    val issuedDate: LocalDate,
    val expiryDate: LocalDate? = null,
    val verificationHash: String,
    val fileUrl: String? = null
)

data class TrainingCertificateListResponse(
    val certificates: List<TrainingCertificateItem>
)

data class TrainingNeedItem(
    val competencyId: UUID,
    val competencyName: String,
    val currentProficiency: Double,
    val targetProficiency: Double,
    val gap: Double,
    val recommendedCourses: List<TrainingCourseItem>
)

data class TrainingNeedsResponse(
    val needs: List<TrainingNeedItem>
)
