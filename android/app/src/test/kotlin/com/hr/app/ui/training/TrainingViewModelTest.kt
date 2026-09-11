package com.hr.app.ui.training

import com.hr.app.data.training.TrainingRepository
import com.hr.client.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingViewModelTest {

    private val repository = mockk<TrainingRepository>()
    private val testDispatcher = StandardTestDispatcher()

    private val coursesFlow = MutableStateFlow<List<TrainingCourseItem>>(emptyList())
    private val schedulesFlow = MutableStateFlow<List<TrainingScheduleItem>>(emptyList())
    private val enrollmentsFlow = MutableStateFlow<List<TrainingEnrollmentItem>>(emptyList())
    private val certificatesFlow = MutableStateFlow<List<TrainingCertificateItem>>(emptyList())
    private val trainingNeedsFlow = MutableStateFlow<List<TrainingNeedItem>>(emptyList())
    private val selectedCourseDetailFlow = MutableStateFlow<CourseDetailResponse?>(null)
    private val isLoadingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    private val courseId = UUID.randomUUID()
    private val scheduleId = UUID.randomUUID()
    private val enrollmentId = UUID.randomUUID()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { repository.courses } returns coursesFlow
        coEvery { repository.schedules } returns schedulesFlow
        coEvery { repository.enrollments } returns enrollmentsFlow
        coEvery { repository.certificates } returns certificatesFlow
        coEvery { repository.trainingNeeds } returns trainingNeedsFlow
        coEvery { repository.selectedCourseDetail } returns selectedCourseDetailFlow
        coEvery { repository.isLoading } returns isLoadingFlow
        coEvery { repository.error } returns errorFlow

        coEvery { repository.loadCourses(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { repository.loadSchedules(any(), any()) } returns Result.success(emptyList())
        coEvery { repository.loadEnrollments(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { repository.loadCertificates(any()) } returns Result.success(emptyList())
        coEvery { repository.loadTrainingNeeds(any()) } returns Result.success(emptyList())
        coEvery { repository.loadCourseDetails(any()) } returns Result.success(mockk())
        coEvery { repository.clearError() } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads courses, schedules, enrollments, certificates and training needs`() = runTest(testDispatcher) {
        val viewModel = TrainingViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.loadCourses(any(), any(), any()) }
        coVerify { repository.loadSchedules(any(), any()) }
        coVerify { repository.loadEnrollments(any(), any(), any()) }
        coVerify { repository.loadCertificates(any()) }
        coVerify { repository.loadTrainingNeeds(any()) }
    }

    @Test
    fun `selectTab updates selectedTab in uiState`() = runTest(testDispatcher) {
        val viewModel = TrainingViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectTab(TrainingTab.MY_LEARNING)
        assertEquals(TrainingTab.MY_LEARNING, viewModel.uiState.value.selectedTab)

        viewModel.selectTab(TrainingTab.CERTIFICATES)
        assertEquals(TrainingTab.CERTIFICATES, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun `selectCategory filters courses`() = runTest(testDispatcher) {
        val viewModel = TrainingViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectCategory("TECHNICAL")
        assertEquals("TECHNICAL", viewModel.uiState.value.selectedCategory)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.loadCourses(category = "TECHNICAL", search = null) }
    }

    @Test
    fun `enrollInBatch calls repository and sets user message on success`() = runTest(testDispatcher) {
        val enrollment = TrainingEnrollmentItem(
            id = enrollmentId,
            scheduleId = scheduleId,
            courseTitle = "Zero Trust Security",
            batchCode = "BATCH-01",
            startDate = OffsetDateTime.now(),
            endDate = OffsetDateTime.now().plusDays(2),
            employeeId = UUID.randomUUID(),
            employeeName = "Kasun Mendis",
            enrollmentType = "SELF_ENROLLED",
            status = "ENROLLED",
            createdAt = OffsetDateTime.now(),
        )
        coEvery { repository.enroll(scheduleId, any(), any(), any()) } returns Result.success(enrollment)

        val viewModel = TrainingViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.enrollInBatch(scheduleId)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.enroll(scheduleId, any(), any(), any()) }
        assertEquals("Successfully enrolled in batch!", viewModel.uiState.value.userMessage)
    }

    @Test
    fun `checkInAttendance calls repository and updates user message`() = runTest(testDispatcher) {
        val item = TrainingAttendanceItem(
            id = UUID.randomUUID(),
            enrollmentId = enrollmentId,
            sessionDate = LocalDate.now(),
            checkInTime = OffsetDateTime.now(),
            attendanceStatus = "PRESENT",
        )
        coEvery { repository.checkInAttendance(eq(enrollmentId), any(), any()) } returns Result.success(item)

        val viewModel = TrainingViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.checkInAttendance(enrollmentId)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.checkInAttendance(eq(enrollmentId), any(), any()) }
        assertEquals("Attendance confirmed for today's session!", viewModel.uiState.value.userMessage)
    }

    @Test
    fun `submitEvaluation calls repository and updates message`() = runTest(testDispatcher) {
        val eval = TraineeEvaluationItem(
            id = UUID.randomUUID(),
            enrollmentId = enrollmentId,
            ratingScore = 5,
            contentRating = 5,
            instructorRating = 5,
            submittedAt = OffsetDateTime.now(),
        )
        coEvery { repository.submitEvaluation(eq(enrollmentId), any(), any(), any(), any()) } returns Result.success(eval)

        val viewModel = TrainingViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.submitEvaluation(enrollmentId, 5, 5, 5, "Great class")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.submitEvaluation(eq(enrollmentId), 5, 5, 5, "Great class") }
        assertEquals("Course evaluation submitted! Digital certificate issued.", viewModel.uiState.value.userMessage)
    }
}
