package com.hr.onboarding

import com.hr.onboarding.internal.OffboardingController
import com.hr.onboarding.internal.OffboardingService
import com.hr.onboarding.internal.OnboardingController
import com.hr.onboarding.internal.OnboardingService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@DisplayName("Onboarding and Offboarding Controllers Unit Tests")
class OnboardingOffboardingControllerTest {

    private val onboardingService = mockk<OnboardingService>()
    private val offboardingService = mockk<OffboardingService>()

    private lateinit var onboardingController: OnboardingController
    private lateinit var offboardingController: OffboardingController

    private val employeeId = UUID.randomUUID()
    private val profileId = UUID.randomUUID()
    private val instanceId = UUID.randomUUID()
    private val noticeId = UUID.randomUUID()
    private val taskId = UUID.randomUUID()

    private val mockJwt = mockk<Jwt> {
        every { getClaimAsString("employee_id") } returns employeeId.toString()
    }

    @BeforeEach
    fun setUp() {
        onboardingController = OnboardingController(onboardingService)
        offboardingController = OffboardingController(offboardingService)
    }

    @Test
    fun `getOnboardingProfiles delegates to onboardingService`() {
        val response = OnboardingProfileListResponse(items = emptyList(), totalCount = 0)
        every { onboardingService.getProfiles() } returns response

        val result = onboardingController.getOnboardingProfiles()

        assertEquals(0, result.totalCount)
        verify { onboardingService.getProfiles() }
    }

    @Test
    fun `createOnboardingInstance delegates to onboardingService`() {
        val req = OnboardingInstanceCreateRequest(
            employeeId = employeeId,
            profileId = profileId,
            joinDate = LocalDate.now(),
        )
        val detail = mockk<OnboardingInstanceDetail>()
        every { onboardingService.createInstance(req) } returns detail

        val result = onboardingController.createOnboardingInstance(req)

        assertEquals(detail, result)
        verify { onboardingService.createInstance(req) }
    }

    @Test
    fun `completeOnboardingTask delegates to onboardingService with actor id`() {
        val req = OnboardingTaskCompleteRequest(notes = "Completed")
        val item = mockk<OnboardingTaskItem>()
        every { onboardingService.completeTask(taskId, req, employeeId) } returns item

        val result = onboardingController.completeOnboardingTask(taskId, req, mockJwt)

        assertEquals(item, result)
        verify { onboardingService.completeTask(taskId, req, employeeId) }
    }

    @Test
    fun `getExitTypes delegates to offboardingService`() {
        val response = ExitTypeListResponse(items = emptyList())
        every { offboardingService.getExitTypes() } returns response

        val result = offboardingController.getExitTypes()

        assertEquals(0, result.items.size)
        verify { offboardingService.getExitTypes() }
    }

    @Test
    fun `createExitNotice delegates to offboardingService with caller employee id`() {
        val req = ExitNoticeCreateRequest(
            exitTypeId = UUID.randomUUID(),
            requestedLastWorkingDate = LocalDate.now().plusDays(30),
        )
        val item = mockk<ExitNoticeItem>()
        every { offboardingService.createExitNotice(employeeId, req) } returns item

        val result = offboardingController.createExitNotice(req, mockJwt)

        assertEquals(item, result)
        verify { offboardingService.createExitNotice(employeeId, req) }
    }

    @Test
    fun `approveExitNotice delegates to offboardingService`() {
        val req = ExitNoticeApproveRequest(
            approvedLastWorkingDate = LocalDate.now().plusDays(30),
        )
        val item = mockk<ExitNoticeItem>()
        every { offboardingService.approveExitNotice(noticeId, req, employeeId) } returns item

        val result = offboardingController.approveExitNotice(noticeId, req, mockJwt)

        assertEquals(item, result)
        verify { offboardingService.approveExitNotice(noticeId, req, employeeId) }
    }

    @Test
    fun `getExitNoticeClearance delegates to offboardingService`() {
        val response = mockk<ClearanceDetailResponse>()
        every { offboardingService.getClearance(noticeId) } returns response

        val result = offboardingController.getExitNoticeClearance(noticeId)

        assertEquals(response, result)
        verify { offboardingService.getClearance(noticeId) }
    }

    @Test
    fun `updateClearanceTaskStatus delegates to offboardingService`() {
        val req = ClearanceTaskStatusUpdateRequest(
            status = ClearanceTaskStatus.CLEARED,
            remarks = "Hardware returned",
            recoverableAmount = BigDecimal.ZERO,
        )
        val item = mockk<ClearanceTaskItem>()
        every { offboardingService.updateClearanceTaskStatus(taskId, req) } returns item

        val result = offboardingController.updateClearanceTaskStatus(taskId, req)

        assertEquals(item, result)
        verify { offboardingService.updateClearanceTaskStatus(taskId, req) }
    }
}
