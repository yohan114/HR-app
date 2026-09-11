package com.hr.training

import com.hr.employee.EmployeeLookupService
import com.hr.training.internal.*
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@DisplayName("Training Service Unit Tests")
class TrainingServiceTest {

    private val providerRepository = mockk<TrainingProviderRepository>()
    private val trainerRepository = mockk<ResourcePersonRepository>()
    private val courseRepository = mockk<TrainingCourseRepository>()
    private val scheduleRepository = mockk<TrainingScheduleRepository>()
    private val enrollmentRepository = mockk<TrainingEnrollmentRepository>()
    private val attendanceRepository = mockk<TrainingAttendanceRepository>()
    private val evaluationRepository = mockk<TraineeEvaluationRepository>()
    private val certificateRepository = mockk<TrainingCertificateRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)

    private lateinit var trainingService: TrainingService

    private val courseId = UUID.randomUUID()
    private val scheduleId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()
    private val enrollmentId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        trainingService = TrainingService(
            providerRepository = providerRepository,
            trainerRepository = trainerRepository,
            courseRepository = courseRepository,
            scheduleRepository = scheduleRepository,
            enrollmentRepository = enrollmentRepository,
            attendanceRepository = attendanceRepository,
            evaluationRepository = evaluationRepository,
            certificateRepository = certificateRepository,
            employeeLookupService = employeeLookupService,
        )
    }

    @Test
    fun `listCourses filters by category`() {
        val course = TrainingCourseEntity(
            courseCode = "SEC-101",
            title = "Zero Trust Security",
            category = "COMPLIANCE",
            deliveryMode = "ONLINE_LIVE",
            durationHours = 6.0,
            maxCapacity = 40
        )
        every { courseRepository.findByCategoryAndIsActiveTrue("COMPLIANCE") } returns listOf(course)
        every { providerRepository.findById(any()) } returns Optional.empty()

        val response = trainingService.listCourses("COMPLIANCE", null, null)

        assertEquals(1, response.courses.size)
        assertEquals("Zero Trust Security", response.courses[0].title)
    }

    @Test
    fun `createCourse saves course and returns details`() {
        val request = CourseCreateRequest(
            courseCode = "KOT-201",
            title = "Modern Kotlin Backend Architecture",
            category = "TECHNICAL",
            deliveryMode = "CLASSROOM",
            durationHours = 16.0,
            maxCapacity = 25
        )
        val savedEntity = TrainingCourseEntity(
            courseCode = request.courseCode,
            title = request.title,
            category = request.category,
            deliveryMode = request.deliveryMode,
            durationHours = request.durationHours,
            maxCapacity = request.maxCapacity
        )
        every { courseRepository.save(any()) } returns savedEntity

        val response = trainingService.createCourse(request)

        assertNotNull(response.course)
        assertEquals("Modern Kotlin Backend Architecture", response.course.title)
        verify { courseRepository.save(any()) }
    }

    @Test
    fun `createSchedule saves new batch schedule`() {
        val course = TrainingCourseEntity(
            courseCode = "LDR-300",
            title = "Executive Coaching",
            category = "LEADERSHIP",
            deliveryMode = "BLENDED",
            durationHours = 20.0,
            maxCapacity = 15
        )
        every { courseRepository.findById(courseId) } returns Optional.of(course)

        val scheduleEntity = TrainingScheduleEntity(
            courseId = courseId,
            batchCode = "LDR-2026-B1",
            startDate = Instant.now(),
            endDate = Instant.now().plusSeconds(86400 * 3),
            totalSeats = 15,
            status = "SCHEDULED"
        )
        every { scheduleRepository.save(any()) } returns scheduleEntity
        every { trainerRepository.findById(any()) } returns Optional.empty()

        val request = TrainingScheduleCreateRequest(
            courseId = courseId,
            batchCode = "LDR-2026-B1",
            startDate = OffsetDateTime.now(),
            endDate = OffsetDateTime.now().plusDays(3),
            totalSeats = 15
        )
        val item = trainingService.createSchedule(request)

        assertEquals("LDR-2026-B1", item.batchCode)
        assertEquals("Executive Coaching", item.courseTitle)
    }

    @Test
    fun `createEnrollment increments enrolled seats when seats available`() {
        val schedule = TrainingScheduleEntity(
            courseId = courseId,
            batchCode = "BATCH-01",
            startDate = Instant.now(),
            endDate = Instant.now().plusSeconds(86400),
            totalSeats = 20,
            enrolledSeats = 5
        )
        val course = TrainingCourseEntity(
            courseCode = "SEC-101",
            title = "Security Fundamentals",
            category = "COMPLIANCE",
            deliveryMode = "CLASSROOM",
            durationHours = 4.0,
            maxCapacity = 20
        )
        every { scheduleRepository.findById(scheduleId) } returns Optional.of(schedule)
        every { courseRepository.findById(courseId) } returns Optional.of(course)
        every { employeeLookupService.findDisplayName(employeeId) } returns "Kasun Perera"

        val enrollmentEntity = TrainingEnrollmentEntity(
            scheduleId = scheduleId,
            employeeId = employeeId,
            enrollmentType = "SELF_ENROLLED",
            status = "ENROLLED"
        )
        every { enrollmentRepository.save(any()) } returns enrollmentEntity
        every { scheduleRepository.save(any()) } returns schedule

        val request = TrainingEnrollmentCreateRequest(
            scheduleId = scheduleId,
            employeeId = employeeId,
            enrollmentType = "SELF_ENROLLED"
        )
        val item = trainingService.createEnrollment(request)

        assertEquals("ENROLLED", item.status)
        assertEquals(6, schedule.enrolledSeats)
        verify { scheduleRepository.save(schedule) }
    }

    @Test
    fun `createEnrollment throws exception when schedule is full`() {
        val schedule = TrainingScheduleEntity(
            courseId = courseId,
            batchCode = "FULL-01",
            startDate = Instant.now(),
            endDate = Instant.now().plusSeconds(86400),
            totalSeats = 10,
            enrolledSeats = 10
        )
        every { scheduleRepository.findById(scheduleId) } returns Optional.of(schedule)

        val request = TrainingEnrollmentCreateRequest(
            scheduleId = scheduleId,
            employeeId = employeeId,
            enrollmentType = "SELF_ENROLLED"
        )

        assertThrows(IllegalStateException::class.java) {
            trainingService.createEnrollment(request)
        }
    }

    @Test
    fun `approveEnrollment updates status and records approval remarks`() {
        val enrollment = TrainingEnrollmentEntity(
            scheduleId = scheduleId,
            employeeId = employeeId,
            enrollmentType = "MANAGER_NOMINATED",
            status = "REQUESTED"
        )
        val schedule = TrainingScheduleEntity(
            courseId = courseId,
            batchCode = "NOM-01",
            startDate = Instant.now(),
            endDate = Instant.now().plusSeconds(86400),
            totalSeats = 20
        )
        val course = TrainingCourseEntity(
            courseCode = "AI-101",
            title = "Generative AI at Work",
            category = "TECHNICAL",
            deliveryMode = "ONLINE_LIVE",
            durationHours = 8.0,
            maxCapacity = 20
        )
        every { enrollmentRepository.findById(enrollmentId) } returns Optional.of(enrollment)
        every { enrollmentRepository.save(any()) } returns enrollment
        every { scheduleRepository.findById(scheduleId) } returns Optional.of(schedule)
        every { courseRepository.findById(courseId) } returns Optional.of(course)

        val request = TrainingEnrollmentApproveRequest(decision = "APPROVED", remarks = "Approved for Q3 upskilling")
        val item = trainingService.approveEnrollment(enrollmentId, request)

        assertEquals("ENROLLED", item.status)
        assertEquals("Approved for Q3 upskilling", enrollment.approvalRemarks)
        assertNotNull(enrollment.approvedAt)
    }

    @Test
    fun `checkInAttendance records check-in for trainee`() {
        val enrollment = TrainingEnrollmentEntity(
            scheduleId = scheduleId,
            employeeId = employeeId,
            enrollmentType = "SELF_ENROLLED",
            status = "ENROLLED"
        )
        every { enrollmentRepository.findById(enrollmentId) } returns Optional.of(enrollment)

        val attendanceEntity = TrainingAttendanceEntity(
            enrollmentId = enrollmentId,
            sessionDate = LocalDate.now(),
            checkInTime = Instant.now(),
            attendanceStatus = "PRESENT",
            remarks = "On time"
        )
        every { attendanceRepository.save(any()) } returns attendanceEntity

        val request = TrainingAttendanceCheckInRequest(sessionDate = LocalDate.now(), remarks = "On time")
        val item = trainingService.checkInAttendance(enrollmentId, request)

        assertEquals("PRESENT", item.attendanceStatus)
        assertEquals(LocalDate.now(), item.sessionDate)
    }

    @Test
    fun `submitEvaluation completes enrollment and automatically issues certificate for passing score`() {
        val enrollment = TrainingEnrollmentEntity(
            scheduleId = scheduleId,
            employeeId = employeeId,
            enrollmentType = "SELF_ENROLLED",
            status = "ENROLLED"
        )
        every { enrollmentRepository.findById(enrollmentId) } returns Optional.of(enrollment)
        every { enrollmentRepository.save(any()) } returns enrollment

        val evalEntity = TraineeEvaluationEntity(
            enrollmentId = enrollmentId,
            ratingScore = 5,
            contentRating = 5,
            instructorRating = 5,
            feedbackComments = "Outstanding hands-on session"
        )
        every { evaluationRepository.save(any()) } returns evalEntity
        every { certificateRepository.findByEnrollmentId(any()) } returns null

        val certSlot = slot<TrainingCertificateEntity>()
        every { certificateRepository.save(capture(certSlot)) } answers { certSlot.captured }

        val request = TraineeEvaluationSubmitRequest(
            ratingScore = 5,
            contentRating = 5,
            instructorRating = 5,
            feedbackComments = "Outstanding hands-on session"
        )
        val item = trainingService.submitEvaluation(enrollmentId, request)

        assertEquals(5, item.ratingScore)
        assertEquals("COMPLETED", enrollment.status)
        verify { certificateRepository.save(any()) }
        assertTrue(certSlot.captured.certificateNumber.startsWith("CERT-TRN-"))
        assertNotNull(certSlot.captured.verificationHash)
    }

    @Test
    fun `getTrainingNeeds returns competency gap analysis`() {
        every { courseRepository.findByIsActiveTrue() } returns emptyList()

        val response = trainingService.getTrainingNeeds(employeeId)

        assertTrue(response.needs.isNotEmpty())
        assertEquals("Enterprise Architecture & Microservices", response.needs[0].competencyName)
        assertEquals(1.5, response.needs[0].gap)
    }
}
