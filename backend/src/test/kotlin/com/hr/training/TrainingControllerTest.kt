package com.hr.training

import com.hr.training.internal.TrainingController
import com.hr.training.internal.TrainingService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@DisplayName("Training Controller Unit Tests")
class TrainingControllerTest {

    private val trainingService = mockk<TrainingService>()
    private lateinit var trainingController: TrainingController

    private val courseId = UUID.randomUUID()
    private val scheduleId = UUID.randomUUID()
    private val enrollmentId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        trainingController = TrainingController(trainingService)
    }

    @Test
    fun `listCourses returns courses list`() {
        val course = TrainingCourseItem(
            id = courseId,
            courseCode = "SEC-101",
            title = "Zero Trust Security",
            category = "COMPLIANCE",
            deliveryMode = "ONLINE_LIVE",
            durationHours = 6.0,
            maxCapacity = 40,
            isActive = true
        )
        every { trainingService.listCourses(null, null, null) } returns CourseListResponse(listOf(course))

        val response = trainingController.listCourses(null, null, null)

        assertEquals(1, response.courses.size)
        assertEquals("Zero Trust Security", response.courses[0].title)
    }

    @Test
    fun `createCourse returns HTTP 201 Created`() {
        val req = CourseCreateRequest(
            courseCode = "SEC-101",
            title = "Zero Trust Security",
            category = "COMPLIANCE",
            deliveryMode = "ONLINE_LIVE",
            durationHours = 6.0,
            maxCapacity = 40
        )
        val course = TrainingCourseItem(
            id = courseId,
            courseCode = req.courseCode,
            title = req.title,
            category = req.category,
            deliveryMode = req.deliveryMode,
            durationHours = req.durationHours,
            maxCapacity = req.maxCapacity,
            isActive = true
        )
        every { trainingService.createCourse(req) } returns CourseDetailResponse(course = course)

        val res = trainingController.createCourse(req)

        assertEquals(HttpStatus.CREATED, res.statusCode)
        assertEquals("Zero Trust Security", res.body?.course?.title)
    }

    @Test
    fun `createSchedule returns HTTP 201 Created`() {
        val req = TrainingScheduleCreateRequest(
            courseId = courseId,
            batchCode = "BATCH-01",
            startDate = OffsetDateTime.now(),
            endDate = OffsetDateTime.now().plusDays(2),
            totalSeats = 25
        )
        val item = TrainingScheduleItem(
            id = scheduleId,
            courseId = courseId,
            courseTitle = "Zero Trust Security",
            batchCode = "BATCH-01",
            startDate = req.startDate,
            endDate = req.endDate,
            totalSeats = 25,
            enrolledSeats = 0,
            status = "SCHEDULED",
            costPerParticipant = 0.0
        )
        every { trainingService.createSchedule(req) } returns item

        val res = trainingController.createSchedule(req)

        assertEquals(HttpStatus.CREATED, res.statusCode)
        assertEquals("BATCH-01", res.body?.batchCode)
    }

    @Test
    fun `createEnrollment returns HTTP 201 Created`() {
        val req = TrainingEnrollmentCreateRequest(
            scheduleId = scheduleId,
            employeeId = employeeId,
            enrollmentType = "SELF_ENROLLED"
        )
        val item = TrainingEnrollmentItem(
            id = enrollmentId,
            scheduleId = scheduleId,
            courseTitle = "Zero Trust Security",
            batchCode = "BATCH-01",
            startDate = OffsetDateTime.now(),
            endDate = OffsetDateTime.now().plusDays(2),
            employeeId = employeeId,
            employeeName = "Kasun Perera",
            enrollmentType = "SELF_ENROLLED",
            status = "ENROLLED",
            createdAt = OffsetDateTime.now()
        )
        every { trainingService.createEnrollment(req) } returns item

        val res = trainingController.createEnrollment(req)

        assertEquals(HttpStatus.CREATED, res.statusCode)
        assertEquals("ENROLLED", res.body?.status)
    }

    @Test
    fun `checkInAttendance returns check in response`() {
        val req = TrainingAttendanceCheckInRequest(sessionDate = LocalDate.now(), remarks = "Present")
        val item = TrainingAttendanceItem(
            id = UUID.randomUUID(),
            enrollmentId = enrollmentId,
            sessionDate = LocalDate.now(),
            checkInTime = OffsetDateTime.now(),
            attendanceStatus = "PRESENT",
            remarks = "Present"
        )
        every { trainingService.checkInAttendance(enrollmentId, req) } returns item

        val res = trainingController.checkInAttendance(enrollmentId, req)

        assertEquals("PRESENT", res.attendanceStatus)
    }
}
