package com.hr.training.internal

import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "training_provider")
class TrainingProviderEntity(
    @Column(name = "provider_name", nullable = false, length = 150)
    var providerName: String,
    @Column(name = "provider_type", nullable = false, length = 50)
    var providerType: String = "INTERNAL",
    @Column(name = "contact_email", length = 100)
    var contactEmail: String? = null,
    @Column(name = "contact_phone", length = 50)
    var contactPhone: String? = null,
    @Column(name = "website_url", length = 255)
    var websiteUrl: String? = null,
    @Column(name = "is_accredited", nullable = false)
    var isAccredited: Boolean = true
) : TenantScopedEntity()

@Entity
@Table(name = "resource_person")
class ResourcePersonEntity(
    @Column(name = "provider_id")
    var providerId: UUID? = null,
    @Column(name = "full_name", nullable = false, length = 150)
    var fullName: String,
    @Column(name = "email", length = 100)
    var email: String? = null,
    @Column(name = "bio", columnDefinition = "text")
    var bio: String? = null,
    @Column(name = "specialization", length = 200)
    var specialization: String? = null,
    @Column(name = "internal_employee_id")
    var internalEmployeeId: UUID? = null
) : TenantScopedEntity()

@Entity
@Table(name = "training_course")
class TrainingCourseEntity(
    @Column(name = "provider_id")
    var providerId: UUID? = null,
    @Column(name = "course_code", nullable = false, length = 50)
    var courseCode: String,
    @Column(name = "title", nullable = false, length = 200)
    var title: String,
    @Column(name = "description", columnDefinition = "text")
    var description: String? = null,
    @Column(name = "category", nullable = false, length = 50)
    var category: String = "TECHNICAL",
    @Column(name = "delivery_mode", nullable = false, length = 50)
    var deliveryMode: String = "CLASSROOM",
    @Column(name = "duration_hours", nullable = false)
    var durationHours: Double = 8.0,
    @Column(name = "target_audience", columnDefinition = "text")
    var targetAudience: String? = null,
    @Column(name = "prerequisites", columnDefinition = "text")
    var prerequisites: String? = null,
    @Column(name = "max_capacity", nullable = false)
    var maxCapacity: Int = 30,
    @Column(name = "competency_id")
    var competencyId: UUID? = null,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true
) : TenantScopedEntity()

@Entity
@Table(name = "training_schedule")
class TrainingScheduleEntity(
    @Column(name = "course_id", nullable = false)
    var courseId: UUID,
    @Column(name = "trainer_id")
    var trainerId: UUID? = null,
    @Column(name = "batch_code", nullable = false, length = 50)
    var batchCode: String,
    @Column(name = "start_date", nullable = false)
    var startDate: Instant,
    @Column(name = "end_date", nullable = false)
    var endDate: Instant,
    @Column(name = "venue_name", length = 200)
    var venueName: String? = null,
    @Column(name = "virtual_meeting_url", length = 500)
    var virtualMeetingUrl: String? = null,
    @Column(name = "total_seats", nullable = false)
    var totalSeats: Int = 25,
    @Column(name = "enrolled_seats", nullable = false)
    var enrolledSeats: Int = 0,
    @Column(name = "status", nullable = false, length = 50)
    var status: String = "SCHEDULED",
    @Column(name = "cost_per_participant", nullable = false)
    var costPerParticipant: Double = 0.0
) : TenantScopedEntity()

@Entity
@Table(name = "training_enrollment")
class TrainingEnrollmentEntity(
    @Column(name = "schedule_id", nullable = false)
    var scheduleId: UUID,
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "enrollment_type", nullable = false, length = 50)
    var enrollmentType: String = "SELF_ENROLLED",
    @Column(name = "status", nullable = false, length = 50)
    var status: String = "ENROLLED",
    @Column(name = "nominated_by")
    var nominatedBy: UUID? = null,
    @Column(name = "approval_remarks", columnDefinition = "text")
    var approvalRemarks: String? = null,
    @Column(name = "approved_at")
    var approvedAt: Instant? = null,
    @Column(name = "cancellation_reason", columnDefinition = "text")
    var cancellationReason: String? = null
) : TenantScopedEntity()

@Entity
@Table(name = "training_attendance")
class TrainingAttendanceEntity(
    @Column(name = "enrollment_id", nullable = false)
    var enrollmentId: UUID,
    @Column(name = "session_date", nullable = false)
    var sessionDate: LocalDate,
    @Column(name = "check_in_time", nullable = false)
    var checkInTime: Instant = Instant.now(),
    @Column(name = "attendance_status", nullable = false, length = 50)
    var attendanceStatus: String = "PRESENT",
    @Column(name = "remarks", columnDefinition = "text")
    var remarks: String? = null
) : TenantScopedEntity()

@Entity
@Table(name = "trainee_evaluation")
class TraineeEvaluationEntity(
    @Column(name = "enrollment_id", nullable = false)
    var enrollmentId: UUID,
    @Column(name = "rating_score", nullable = false)
    var ratingScore: Int,
    @Column(name = "content_rating", nullable = false)
    var contentRating: Int = 5,
    @Column(name = "instructor_rating", nullable = false)
    var instructorRating: Int = 5,
    @Column(name = "feedback_comments", columnDefinition = "text")
    var feedbackComments: String? = null,
    @Column(name = "submitted_at", nullable = false)
    var submittedAt: Instant = Instant.now()
) : TenantScopedEntity()

@Entity
@Table(name = "training_certificate")
class TrainingCertificateEntity(
    @Column(name = "enrollment_id", nullable = false)
    var enrollmentId: UUID,
    @Column(name = "certificate_number", nullable = false, length = 100)
    var certificateNumber: String,
    @Column(name = "issued_date", nullable = false)
    var issuedDate: LocalDate = LocalDate.now(),
    @Column(name = "expiry_date")
    var expiryDate: LocalDate? = null,
    @Column(name = "verification_hash", nullable = false, length = 64)
    var verificationHash: String,
    @Column(name = "file_url", length = 500)
    var fileUrl: String? = null
) : TenantScopedEntity()
