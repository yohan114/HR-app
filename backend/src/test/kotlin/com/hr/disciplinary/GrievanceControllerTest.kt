package com.hr.disciplinary

import com.hr.disciplinary.internal.GrievanceController
import com.hr.disciplinary.internal.GrievanceService
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@DisplayName("Grievance Controller Unit Tests")
class GrievanceControllerTest {

    private val service = mockk<GrievanceService>()
    private lateinit var controller: GrievanceController

    private val sampleGround = GrievanceGroundItem(
        id = UUID.randomUUID(),
        groupId = UUID.randomUUID(),
        groupName = "Environment",
        code = "SAFETY",
        name = "Safety Hazard",
        description = "Hazard",
        severity = GrievanceSeverity.CRITICAL,
        defaultHandlerRole = "HR_MANAGER",
        slaDays = 2,
    )

    private val sampleChannel = GrievanceChannelItem(
        id = UUID.randomUUID(),
        code = "DIRECT_PORTAL",
        name = "Portal",
        isConfidential = true,
    )

    private val sampleDetail = GrievanceDetailResponse(
        id = UUID.randomUUID(),
        grievanceNumber = "GRV-2026-001",
        title = "Test",
        description = "Test desc",
        ground = sampleGround,
        channel = sampleChannel,
        anonymous = false,
        raisedByEmployeeName = "Kasun Mendis",
        status = GrievanceStatus.SUBMITTED,
        raisedAt = Instant.now(),
        targetResolutionDate = Instant.now().plusSeconds(86400),
        resolvedAt = null,
        resolution = null,
        satisfactionRating = null,
        appeal = null,
    )

    @BeforeEach
    fun setUp() {
        controller = GrievanceController(service)
    }

    @Test
    fun `getGrievanceGrounds delegates to service`() {
        every { service.getGrounds() } returns GrievanceGroundsResponse(emptyList(), listOf(sampleGround))
        val res = controller.getGrievanceGrounds()
        assertEquals(1, res.grounds.size)
    }

    @Test
    fun `getGrievanceChannels delegates to service`() {
        every { service.getChannels() } returns GrievanceChannelsResponse(listOf(sampleChannel))
        val res = controller.getGrievanceChannels()
        assertEquals(1, res.channels.size)
    }

    @Test
    fun `getMyGrievances delegates to service`() {
        every { service.getMyGrievances(employeeId = any(), status = null) } returns GrievanceListResponse(0, 0, emptyList())
        val res = controller.getMyGrievances(null, null)
        assertEquals(0, res.totalCount)
    }

    @Test
    fun `getGrievanceById delegates to service`() {
        val id = UUID.randomUUID()
        every { service.getGrievanceById(id = id) } returns sampleDetail
        val res = controller.getGrievanceById(id)
        assertEquals("GRV-2026-001", res.grievanceNumber)
    }

    @Test
    fun `submitGrievance delegates to service`() {
        val req = GrievanceSubmitRequest(
            groundId = sampleGround.id,
            channelId = sampleChannel.id,
            title = "Test",
            description = "Test desc",
        )
        every { service.submitGrievance(employeeId = any(), request = req) } returns sampleDetail
        val res = controller.submitGrievance(req, null, null)
        assertEquals("GRV-2026-001", res.grievanceNumber)
    }

    @Test
    fun `appealGrievance delegates to service`() {
        val id = UUID.randomUUID()
        val req = GrievanceAppealRequest("Not satisfied")
        every { service.appealGrievance(grievanceId = id, employeeId = any(), request = req) } returns sampleDetail
        val res = controller.appealGrievance(id, req, null, null)
        assertEquals("GRV-2026-001", res.grievanceNumber)
    }
}
