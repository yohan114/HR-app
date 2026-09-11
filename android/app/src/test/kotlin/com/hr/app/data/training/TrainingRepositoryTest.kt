package com.hr.app.data.training

import com.hr.client.api.TrainingApi
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class TrainingRepositoryTest {

    private val trainingApi = mockk<TrainingApi>()
    private lateinit var repository: TrainingRepository

    private val courseId = UUID.randomUUID()
    private val scheduleId = UUID.randomUUID()
    private val enrollmentId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()

    @Before
    fun setUp() {
        repository = TrainingRepository(trainingApi)
    }

    @Test
    fun `loadCourses updates courses flow on success`() = runTest {
        val course = TrainingCourseItem(
            id = courseId,
            courseCode = "SEC-101",
            title = "Zero Trust Security",
            category = "COMPLIANCE",
            deliveryMode = "ONLINE_LIVE",
            durationHours = java.math.BigDecimal("6.0"),
            maxCapacity = 40,
            isActive = true,
        )
        coEvery { trainingApi.listTrainingCourses(any(), any(), any()) } returns Response.success(
            CourseListResponse(listOf(course))
        )

        val result = repository.loadCourses()

        assertTrue(result.isSuccess)
        assertEquals(1, repository.courses.value.size)
        assertEquals("Zero Trust Security", repository.courses.value[0].title)
    }

    @Test
    fun `loadSchedules updates schedules flow on success`() = runTest {
        val schedule = TrainingScheduleItem(
            id = scheduleId,
            courseId = courseId,
            courseTitle = "Zero Trust Security",
            batchCode = "SEC-BATCH-1",
            startDate = OffsetDateTime.now(),
            endDate = OffsetDateTime.now().plusDays(2),
            totalSeats = 30,
            enrolledSeats = 5,
            status = "SCHEDULED",
            costPerParticipant = java.math.BigDecimal.ZERO,
        )
        coEvery { trainingApi.listTrainingSchedules(any(), any()) } returns Response.success(
            TrainingScheduleListResponse(listOf(schedule))
        )

        val result = repository.loadSchedules()

        assertTrue(result.isSuccess)
        assertEquals(1, repository.schedules.value.size)
        assertEquals("SEC-BATCH-1", repository.schedules.value[0].batchCode)
    }

    @Test
    fun `enroll creates enrollment and reloads enrollments`() = runTest {
        val enrollment = TrainingEnrollmentItem(
            id = enrollmentId,
            scheduleId = scheduleId,
            courseTitle = "Zero Trust Security",
            batchCode = "SEC-BATCH-1",
            startDate = OffsetDateTime.now(),
            endDate = OffsetDateTime.now().plusDays(2),
            employeeId = employeeId,
            employeeName = "Kasun Mendis",
            enrollmentType = "SELF_ENROLLED",
            status = "ENROLLED",
            createdAt = OffsetDateTime.now(),
        )
        coEvery { trainingApi.createTrainingEnrollment(any()) } returns Response.success(enrollment)
        coEvery { trainingApi.listTrainingEnrollments(any(), any(), any()) } returns Response.success(
            TrainingEnrollmentListResponse(listOf(enrollment))
        )

        val result = repository.enroll(scheduleId, employeeId)

        assertTrue(result.isSuccess)
        assertEquals("ENROLLED", result.getOrNull()?.status)
        assertEquals(1, repository.enrollments.value.size)
    }

    @Test
    fun `checkInAttendance records check-in successfully`() = runTest {
        val item = TrainingAttendanceItem(
            id = UUID.randomUUID(),
            enrollmentId = enrollmentId,
            sessionDate = LocalDate.now(),
            checkInTime = OffsetDateTime.now(),
            attendanceStatus = "PRESENT",
            remarks = "Mobile check-in",
        )
        coEvery { trainingApi.checkInTrainingAttendance(eq(enrollmentId), any()) } returns Response.success(item)

        val result = repository.checkInAttendance(enrollmentId, LocalDate.now(), "Mobile check-in")

        assertTrue(result.isSuccess)
        assertEquals("PRESENT", result.getOrNull()?.attendanceStatus)
    }

    @Test
    fun `submitEvaluation submits Kirkpatrick reaction and triggers certificate reload`() = runTest {
        val eval = TraineeEvaluationItem(
            id = UUID.randomUUID(),
            enrollmentId = enrollmentId,
            ratingScore = 5,
            contentRating = 5,
            instructorRating = 5,
            feedbackComments = "Excellent",
            submittedAt = OffsetDateTime.now(),
        )
        val cert = TrainingCertificateItem(
            id = UUID.randomUUID(),
            enrollmentId = enrollmentId,
            courseTitle = "Zero Trust Security",
            employeeName = "Kasun Mendis",
            certificateNumber = "CERT-TRN-12345678",
            issuedDate = LocalDate.now(),
            verificationHash = "abcdef1234567890",
        )
        coEvery { trainingApi.submitTraineeEvaluation(eq(enrollmentId), any()) } returns Response.success(eval)
        coEvery { trainingApi.listTrainingCertificates(any()) } returns Response.success(
            TrainingCertificateListResponse(listOf(cert))
        )
        coEvery { trainingApi.listTrainingEnrollments(any(), any(), any()) } returns Response.success(
            TrainingEnrollmentListResponse(emptyList())
        )

        val result = repository.submitEvaluation(enrollmentId, 5, 5, 5, "Excellent")

        assertTrue(result.isSuccess)
        assertEquals(1, repository.certificates.value.size)
        assertEquals("CERT-TRN-12345678", repository.certificates.value[0].certificateNumber)
    }
}
