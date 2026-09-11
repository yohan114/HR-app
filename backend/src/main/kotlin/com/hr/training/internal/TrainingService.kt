package com.hr.training.internal

import com.hr.employee.EmployeeLookupService
import com.hr.training.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
@Transactional
class TrainingService(
    private val providerRepository: TrainingProviderRepository,
    private val trainerRepository: ResourcePersonRepository,
    private val courseRepository: TrainingCourseRepository,
    private val scheduleRepository: TrainingScheduleRepository,
    private val enrollmentRepository: TrainingEnrollmentRepository,
    private val attendanceRepository: TrainingAttendanceRepository,
    private val evaluationRepository: TraineeEvaluationRepository,
    private val certificateRepository: TrainingCertificateRepository,
    private val employeeLookupService: EmployeeLookupService,
) {

    // -------------------------------------------------------------------------
    // Courses
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun listCourses(category: String?, competencyId: UUID?, search: String?): CourseListResponse {
        val allCourses = when {
            category != null -> courseRepository.findByCategoryAndIsActiveTrue(category)
            competencyId != null -> courseRepository.findByCompetencyIdAndIsActiveTrue(competencyId)
            else -> courseRepository.findByIsActiveTrue()
        }

        val filtered = if (!search.isNullOrBlank()) {
            val q = search.trim().lowercase()
            allCourses.filter {
                it.title.lowercase().contains(q) ||
                    (it.description?.lowercase()?.contains(q) == true) ||
                    it.courseCode.lowercase().contains(q)
            }
        } else {
            allCourses
        }

        val items = filtered.map { course ->
            val provider = course.providerId?.let { providerRepository.findById(it).orElse(null) }
            course.toItem(provider?.providerName)
        }
        return CourseListResponse(items)
    }

    fun createCourse(request: CourseCreateRequest): CourseDetailResponse {
        val entity = TrainingCourseEntity(
            providerId = request.providerId,
            courseCode = request.courseCode,
            title = request.title,
            description = request.description,
            category = request.category,
            deliveryMode = request.deliveryMode,
            durationHours = request.durationHours,
            targetAudience = request.targetAudience,
            prerequisites = request.prerequisites,
            maxCapacity = request.maxCapacity,
            competencyId = request.competencyId,
            isActive = true
        )
        val saved = courseRepository.save(entity)
        val provider = saved.providerId?.let { providerRepository.findById(it).orElse(null) }
        return CourseDetailResponse(
            course = saved.toItem(provider?.providerName),
            provider = provider?.toItem(),
            schedules = emptyList()
        )
    }

    @Transactional(readOnly = true)
    fun getCourseDetails(courseId: UUID): CourseDetailResponse {
        val course = courseRepository.findById(courseId).orElseThrow {
            IllegalArgumentException("Course not found: $courseId")
        }
        val provider = course.providerId?.let { providerRepository.findById(it).orElse(null) }
        val schedules = scheduleRepository.findByCourseId(course.id).map { sched ->
            val trainer = sched.trainerId?.let { trainerRepository.findById(it).orElse(null) }
            sched.toItem(course.title, trainer?.fullName)
        }
        return CourseDetailResponse(
            course = course.toItem(provider?.providerName),
            provider = provider?.toItem(),
            schedules = schedules
        )
    }

    // -------------------------------------------------------------------------
    // Schedules
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun listSchedules(courseId: UUID?, status: String?): TrainingScheduleListResponse {
        val schedules = when {
            courseId != null && status != null -> scheduleRepository.findByCourseIdAndStatus(courseId, status)
            courseId != null -> scheduleRepository.findByCourseId(courseId)
            status != null -> scheduleRepository.findByStatus(status)
            else -> scheduleRepository.findAll()
        }

        val items = schedules.map { sched ->
            val course = courseRepository.findById(sched.courseId).orElse(null)
            val trainer = sched.trainerId?.let { trainerRepository.findById(it).orElse(null) }
            sched.toItem(course?.title ?: "Unknown Course", trainer?.fullName)
        }
        return TrainingScheduleListResponse(items)
    }

    fun createSchedule(request: TrainingScheduleCreateRequest): TrainingScheduleItem {
        val course = courseRepository.findById(request.courseId).orElseThrow {
            IllegalArgumentException("Course not found: ${request.courseId}")
        }
        val entity = TrainingScheduleEntity(
            courseId = request.courseId,
            trainerId = request.trainerId,
            batchCode = request.batchCode,
            startDate = request.startDate.toInstant(),
            endDate = request.endDate.toInstant(),
            venueName = request.venueName,
            virtualMeetingUrl = request.virtualMeetingUrl,
            totalSeats = request.totalSeats,
            enrolledSeats = 0,
            status = ScheduleStatus.SCHEDULED.name,
            costPerParticipant = request.costPerParticipant ?: 0.0
        )
        val saved = scheduleRepository.save(entity)
        val trainer = saved.trainerId?.let { trainerRepository.findById(it).orElse(null) }
        return saved.toItem(course.title, trainer?.fullName)
    }

    // -------------------------------------------------------------------------
    // Enrollments
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun listEnrollments(employeeId: UUID?, scheduleId: UUID?, status: String?): TrainingEnrollmentListResponse {
        val enrollments = when {
            employeeId != null && status != null -> enrollmentRepository.findByEmployeeIdAndStatus(employeeId, status)
            employeeId != null -> enrollmentRepository.findByEmployeeId(employeeId)
            scheduleId != null -> enrollmentRepository.findByScheduleId(scheduleId)
            status != null -> enrollmentRepository.findByStatus(status)
            else -> enrollmentRepository.findAll()
        }

        val items = enrollments.map { enr ->
            val sched = scheduleRepository.findById(enr.scheduleId).orElse(null)
            val course = sched?.courseId?.let { courseRepository.findById(it).orElse(null) }
            val empName = employeeLookupService.findDisplayName(enr.employeeId) ?: "Employee"
            enr.toItem(
                courseTitle = course?.title ?: "Enterprise Training",
                batchCode = sched?.batchCode ?: "BATCH",
                startDate = (sched?.startDate ?: Instant.now()).atOffset(ZoneOffset.UTC),
                endDate = (sched?.endDate ?: Instant.now()).atOffset(ZoneOffset.UTC),
                employeeName = empName
            )
        }
        return TrainingEnrollmentListResponse(items)
    }

    fun createEnrollment(request: TrainingEnrollmentCreateRequest): TrainingEnrollmentItem {
        val schedule = scheduleRepository.findById(request.scheduleId).orElseThrow {
            IllegalArgumentException("Schedule not found: ${request.scheduleId}")
        }
        if (schedule.enrolledSeats >= schedule.totalSeats) {
            throw IllegalStateException("Schedule is fully booked (Capacity: ${schedule.totalSeats})")
        }

        val course = courseRepository.findById(schedule.courseId).orElse(null)
        val empName = employeeLookupService.findDisplayName(request.employeeId) ?: "Employee"

        val initialStatus = when (request.enrollmentType) {
            EnrollmentType.MANAGER_NOMINATED.name -> EnrollmentStatus.REQUESTED.name
            else -> EnrollmentStatus.ENROLLED.name
        }

        val entity = TrainingEnrollmentEntity(
            scheduleId = request.scheduleId,
            employeeId = request.employeeId,
            enrollmentType = request.enrollmentType,
            status = initialStatus,
            nominatedBy = request.nominatedBy
        )
        val saved = enrollmentRepository.save(entity)

        schedule.enrolledSeats += 1
        scheduleRepository.save(schedule)

        return saved.toItem(
            courseTitle = course?.title ?: "Enterprise Training",
            batchCode = schedule.batchCode,
            startDate = schedule.startDate.atOffset(ZoneOffset.UTC),
            endDate = schedule.endDate.atOffset(ZoneOffset.UTC),
            employeeName = empName
        )
    }

    fun approveEnrollment(enrollmentId: UUID, request: TrainingEnrollmentApproveRequest): TrainingEnrollmentItem {
        val enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow {
            IllegalArgumentException("Enrollment not found: $enrollmentId")
        }

        if (request.decision.equals("APPROVED", ignoreCase = true)) {
            enrollment.status = EnrollmentStatus.ENROLLED.name
            enrollment.approvedAt = Instant.now()
            enrollment.approvalRemarks = request.remarks
        } else {
            enrollment.status = EnrollmentStatus.REJECTED.name
            enrollment.cancellationReason = request.remarks
        }
        val saved = enrollmentRepository.save(enrollment)

        val sched = scheduleRepository.findById(saved.scheduleId).orElse(null)
        val course = sched?.courseId?.let { courseRepository.findById(it).orElse(null) }
        val empName = employeeLookupService.findDisplayName(saved.employeeId) ?: "Employee"

        return saved.toItem(
            courseTitle = course?.title ?: "Enterprise Training",
            batchCode = sched?.batchCode ?: "BATCH",
            startDate = (sched?.startDate ?: Instant.now()).atOffset(ZoneOffset.UTC),
            endDate = (sched?.endDate ?: Instant.now()).atOffset(ZoneOffset.UTC),
            employeeName = empName
        )
    }

    // -------------------------------------------------------------------------
    // Attendance & Evaluations
    // -------------------------------------------------------------------------

    fun checkInAttendance(enrollmentId: UUID, request: TrainingAttendanceCheckInRequest): TrainingAttendanceItem {
        val enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow {
            IllegalArgumentException("Enrollment not found: $enrollmentId")
        }
        val entity = TrainingAttendanceEntity(
            enrollmentId = enrollment.id,
            sessionDate = request.sessionDate,
            checkInTime = Instant.now(),
            attendanceStatus = AttendanceStatus.PRESENT.name,
            remarks = request.remarks
        )
        val saved = attendanceRepository.save(entity)
        return saved.toItem()
    }

    fun submitEvaluation(enrollmentId: UUID, request: TraineeEvaluationSubmitRequest): TraineeEvaluationItem {
        val enrollment = enrollmentRepository.findById(enrollmentId).orElseThrow {
            IllegalArgumentException("Enrollment not found: $enrollmentId")
        }

        val eval = TraineeEvaluationEntity(
            enrollmentId = enrollment.id,
            ratingScore = request.ratingScore,
            contentRating = request.contentRating,
            instructorRating = request.instructorRating,
            feedbackComments = request.feedbackComments,
            submittedAt = Instant.now()
        )
        val saved = evaluationRepository.save(eval)

        enrollment.status = EnrollmentStatus.COMPLETED.name
        enrollmentRepository.save(enrollment)

        // Automatically issue digital certificate if rating score >= 3
        if (request.ratingScore >= 3 && certificateRepository.findByEnrollmentId(enrollment.id) == null) {
            val certNumber = "CERT-TRN-${UUID.randomUUID().toString().take(8).uppercase()}"
            val rawData = "${enrollment.id}:${enrollment.employeeId}:$certNumber:${Instant.now()}"
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(rawData.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            val cert = TrainingCertificateEntity(
                enrollmentId = enrollment.id,
                certificateNumber = certNumber,
                issuedDate = LocalDate.now(),
                expiryDate = LocalDate.now().plusYears(2),
                verificationHash = hash,
                fileUrl = "https://documents.hrapp.io/certs/$certNumber.pdf"
            )
            certificateRepository.save(cert)
        }

        return saved.toItem()
    }

    // -------------------------------------------------------------------------
    // Certificates
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun listCertificates(employeeId: UUID?): TrainingCertificateListResponse {
        val certificates = if (employeeId != null) {
            val enrollments = enrollmentRepository.findByEmployeeId(employeeId)
            certificateRepository.findByEnrollmentIdIn(enrollments.map { it.id })
        } else {
            certificateRepository.findAll()
        }

        val items = certificates.map { cert ->
            val enrollment = enrollmentRepository.findById(cert.enrollmentId).orElse(null)
            val sched = enrollment?.scheduleId?.let { scheduleRepository.findById(it).orElse(null) }
            val course = sched?.courseId?.let { courseRepository.findById(it).orElse(null) }
            val empName = enrollment?.employeeId?.let { employeeLookupService.findDisplayName(it) } ?: "Employee"

            cert.toItem(
                courseTitle = course?.title ?: "Enterprise Professional Certification",
                employeeName = empName
            )
        }
        return TrainingCertificateListResponse(items)
    }

    // -------------------------------------------------------------------------
    // Training Needs Analysis (Competency Gap Closure)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun getTrainingNeeds(employeeId: UUID?): TrainingNeedsResponse {
        val activeCourses = courseRepository.findByIsActiveTrue()

        val mockNeeds = listOf(
            TrainingNeedItem(
                competencyId = UUID.fromString("c0000000-0000-0000-0000-000000000001"),
                competencyName = "Enterprise Architecture & Microservices",
                currentProficiency = 2.5,
                targetProficiency = 4.0,
                gap = 1.5,
                recommendedCourses = activeCourses.filter { it.category == "TECHNICAL" }.map { it.toItem("Acme Academy") }
            ),
            TrainingNeedItem(
                competencyId = UUID.fromString("c0000000-0000-0000-0000-000000000002"),
                competencyName = "Leadership & Strategic Communication",
                currentProficiency = 3.0,
                targetProficiency = 4.5,
                gap = 1.5,
                recommendedCourses = activeCourses.filter { it.category == "LEADERSHIP" }.map { it.toItem("Acme Academy") }
            ),
            TrainingNeedItem(
                competencyId = UUID.fromString("c0000000-0000-0000-0000-000000000003"),
                competencyName = "Information Security & Zero Trust Architecture",
                currentProficiency = 3.2,
                targetProficiency = 4.0,
                gap = 0.8,
                recommendedCourses = activeCourses.filter { it.category == "COMPLIANCE" || it.category == "TECHNICAL" }.map { it.toItem("Acme Academy") }
            )
        )
        return TrainingNeedsResponse(mockNeeds)
    }

    // -------------------------------------------------------------------------
    // Mappers
    // -------------------------------------------------------------------------

    private fun TrainingProviderEntity.toItem(): TrainingProviderItem = TrainingProviderItem(
        id = id,
        providerName = providerName,
        providerType = providerType,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        websiteUrl = websiteUrl,
        isAccredited = isAccredited
    )

    private fun TrainingCourseEntity.toItem(providerName: String?): TrainingCourseItem = TrainingCourseItem(
        id = id,
        providerId = providerId,
        providerName = providerName,
        courseCode = courseCode,
        title = title,
        description = description,
        category = category,
        deliveryMode = deliveryMode,
        durationHours = durationHours,
        targetAudience = targetAudience,
        prerequisites = prerequisites,
        maxCapacity = maxCapacity,
        competencyId = competencyId,
        isActive = isActive
    )

    private fun TrainingScheduleEntity.toItem(courseTitle: String, trainerName: String?): TrainingScheduleItem = TrainingScheduleItem(
        id = id,
        courseId = courseId,
        courseTitle = courseTitle,
        trainerId = trainerId,
        trainerName = trainerName,
        batchCode = batchCode,
        startDate = startDate.atOffset(ZoneOffset.UTC),
        endDate = endDate.atOffset(ZoneOffset.UTC),
        venueName = venueName,
        virtualMeetingUrl = virtualMeetingUrl,
        totalSeats = totalSeats,
        enrolledSeats = enrolledSeats,
        status = status,
        costPerParticipant = costPerParticipant
    )

    private fun TrainingEnrollmentEntity.toItem(
        courseTitle: String,
        batchCode: String,
        startDate: OffsetDateTime,
        endDate: OffsetDateTime,
        employeeName: String,
    ): TrainingEnrollmentItem = TrainingEnrollmentItem(
        id = id,
        scheduleId = scheduleId,
        courseTitle = courseTitle,
        batchCode = batchCode,
        startDate = startDate,
        endDate = endDate,
        employeeId = employeeId,
        employeeName = employeeName,
        enrollmentType = enrollmentType,
        status = status,
        nominatedBy = nominatedBy,
        approvalRemarks = approvalRemarks,
        approvedAt = approvedAt?.atOffset(ZoneOffset.UTC),
        cancellationReason = cancellationReason,
        createdAt = (createdAt ?: Instant.now()).atOffset(ZoneOffset.UTC)
    )

    private fun TrainingAttendanceEntity.toItem(): TrainingAttendanceItem = TrainingAttendanceItem(
        id = id,
        enrollmentId = enrollmentId,
        sessionDate = sessionDate,
        checkInTime = checkInTime.atOffset(ZoneOffset.UTC),
        attendanceStatus = attendanceStatus,
        remarks = remarks
    )

    private fun TraineeEvaluationEntity.toItem(): TraineeEvaluationItem = TraineeEvaluationItem(
        id = id,
        enrollmentId = enrollmentId,
        ratingScore = ratingScore,
        contentRating = contentRating,
        instructorRating = instructorRating,
        feedbackComments = feedbackComments,
        submittedAt = submittedAt.atOffset(ZoneOffset.UTC)
    )

    private fun TrainingCertificateEntity.toItem(
        courseTitle: String,
        employeeName: String,
    ): TrainingCertificateItem = TrainingCertificateItem(
        id = id,
        enrollmentId = enrollmentId,
        courseTitle = courseTitle,
        employeeName = employeeName,
        certificateNumber = certificateNumber,
        issuedDate = issuedDate,
        expiryDate = expiryDate,
        verificationHash = verificationHash,
        fileUrl = fileUrl
    )
}
