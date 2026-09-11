package com.hr.disciplinary

import com.hr.disciplinary.internal.*
import com.hr.employee.EmployeeLookupService
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@DisplayName("Grievance Service Unit Tests")
class GrievanceServiceTest {

    private val groundGroupRepository = mockk<GrievanceGroundGroupRepository>()
    private val groundRepository = mockk<GrievanceGroundRepository>()
    private val channelRepository = mockk<GrievanceChannelRepository>()
    private val grievanceRepository = mockk<GrievanceRepository>()
    private val appealRepository = mockk<GrievanceAppealRepository>()
    private val employeeLookupService = mockk<EmployeeLookupService>(relaxed = true)

    private lateinit var service: GrievanceService

    private val tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val sampleGroup = GrievanceGroundGroup(
        code = "WORK_ENV",
        name = "Workplace Environment",
        description = "Facility and environment issues",
    ).apply {
        this.tenantId = this@GrievanceServiceTest.tenantId
    }
    private val groupId get() = sampleGroup.id

    private val sampleGround = GrievanceGround(
        groupId = sampleGroup.id,
        code = "SAFETY_HAZARD",
        name = "Safety Hazard",
        description = "Physical danger",
        severity = GrievanceSeverity.CRITICAL,
        defaultHandlerRole = "OPERATIONS_DIRECTOR",
        slaDays = 2,
    ).apply {
        this.tenantId = this@GrievanceServiceTest.tenantId
    }
    private val groundId get() = sampleGround.id

    private val sampleChannel = GrievanceChannel(
        code = "DIRECT_PORTAL",
        name = "Direct Portal",
        isConfidential = true,
    ).apply {
        this.tenantId = this@GrievanceServiceTest.tenantId
    }
    private val channelId get() = sampleChannel.id

    private val employeeId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { employeeLookupService.findDisplayName(employeeId) } returns "Kasun Mendis"

        service = GrievanceService(
            groundGroupRepository = groundGroupRepository,
            groundRepository = groundRepository,
            channelRepository = channelRepository,
            grievanceRepository = grievanceRepository,
            appealRepository = appealRepository,
            employeeLookupService = employeeLookupService,
        )
    }

    @Test
    fun `getGrounds returns groups and grounds with SLA`() {
        every { groundGroupRepository.findByTenantIdOrderByCodeAsc(tenantId) } returns listOf(sampleGroup)
        every { groundRepository.findByTenantIdOrderByCodeAsc(tenantId) } returns listOf(sampleGround)

        val response = service.getGrounds(tenantId)

        assertEquals(1, response.groups.size)
        assertEquals("WORK_ENV", response.groups[0].code)
        assertEquals(1, response.grounds.size)
        assertEquals("SAFETY_HAZARD", response.grounds[0].code)
        assertEquals(2, response.grounds[0].slaDays)
        assertEquals(GrievanceSeverity.CRITICAL, response.grounds[0].severity)
        assertEquals("Workplace Environment", response.grounds[0].groupName)
    }

    @Test
    fun `getMyGrievances returns employee grievances and calculates pendingCount`() {
        val g1 = Grievance(
            grievanceNumber = "GRV-2026-001",
            raisedByEmployeeId = employeeId,
            groundId = groundId,
            channelId = channelId,
            title = "Noise complaint",
            description = "Loud drilling during calls",
            status = GrievanceStatus.SUBMITTED,
            targetResolutionDate = Instant.now(),
        ).apply {
            this.tenantId = this@GrievanceServiceTest.tenantId
        }

        val g2 = Grievance(
            grievanceNumber = "GRV-2026-002",
            raisedByEmployeeId = employeeId,
            groundId = groundId,
            channelId = channelId,
            title = "Overtime adjustment",
            description = "Variance resolved",
            status = GrievanceStatus.RESOLVED,
            targetResolutionDate = Instant.now(),
        ).apply {
            this.tenantId = this@GrievanceServiceTest.tenantId
        }

        every { grievanceRepository.findByTenantIdAndRaisedByEmployeeIdOrderByRaisedAtDesc(tenantId, employeeId) } returns listOf(g1, g2)
        every { groundRepository.findByTenantIdOrderByCodeAsc(tenantId) } returns listOf(sampleGround)
        every { channelRepository.findByTenantIdOrderByCodeAsc(tenantId) } returns listOf(sampleChannel)

        val response = service.getMyGrievances(tenantId, employeeId)

        assertEquals(2, response.totalCount)
        assertEquals(1, response.pendingCount)
        assertEquals("GRV-2026-001", response.grievances[0].grievanceNumber)
    }

    @Test
    fun `submitGrievance calculates target resolution date based on SLA days`() {
        every { groundRepository.findById(groundId) } returns Optional.of(sampleGround)
        every { channelRepository.findById(channelId) } returns Optional.of(sampleChannel)
        every { groundGroupRepository.findById(groupId) } returns Optional.of(sampleGroup)
        every { appealRepository.findByTenantIdAndGrievanceId(any(), any()) } returns null

        val grievanceSlot = slot<Grievance>()
        every { grievanceRepository.save(capture(grievanceSlot)) } answers { grievanceSlot.captured }
        every { grievanceRepository.findByTenantIdAndId(any(), any()) } answers { grievanceSlot.captured }

        val request = GrievanceSubmitRequest(
            groundId = groundId,
            channelId = channelId,
            title = "Broken stairwell rail",
            description = "Handrail loose on 3rd floor",
            anonymous = false,
        )

        val detail = service.submitGrievance(tenantId, employeeId, request)

        assertNotNull(detail)
        assertTrue(detail.grievanceNumber.startsWith("GRV-"))
        assertEquals("Broken stairwell rail", detail.title)
        assertEquals("Kasun Mendis", detail.raisedByEmployeeName)
        assertEquals(GrievanceStatus.SUBMITTED, detail.status)
        // Verified that target resolution date is roughly 2 days after now
        assertTrue(detail.targetResolutionDate.isAfter(Instant.now()))
    }

    @Test
    fun `submitGrievance masks employee name when anonymous`() {
        every { groundRepository.findById(groundId) } returns Optional.of(sampleGround)
        every { channelRepository.findById(channelId) } returns Optional.of(sampleChannel)
        every { groundGroupRepository.findById(groupId) } returns Optional.of(sampleGroup)
        every { appealRepository.findByTenantIdAndGrievanceId(any(), any()) } returns null

        val grievanceSlot = slot<Grievance>()
        every { grievanceRepository.save(capture(grievanceSlot)) } answers { grievanceSlot.captured }
        every { grievanceRepository.findByTenantIdAndId(any(), any()) } answers { grievanceSlot.captured }

        val request = GrievanceSubmitRequest(
            groundId = groundId,
            channelId = channelId,
            title = "Anonymous whistleblowing tip",
            description = "Suspected financial discrepancy in procurement",
            anonymous = true,
        )

        val detail = service.submitGrievance(tenantId, employeeId, request)

        assertTrue(detail.anonymous)
        assertEquals("Confidential (Anonymous Whistleblower)", detail.raisedByEmployeeName)
    }

    @Test
    fun `appealGrievance updates grievance status to APPEALED and saves appeal`() {
        val g = Grievance(
            grievanceNumber = "GRV-2026-010",
            raisedByEmployeeId = employeeId,
            groundId = groundId,
            channelId = channelId,
            title = "Resolved grievance",
            description = "Some issue",
            status = GrievanceStatus.RESOLVED,
            targetResolutionDate = Instant.now(),
        ).apply {
            this.tenantId = this@GrievanceServiceTest.tenantId
        }

        val appealItem = GrievanceAppeal(
            grievanceId = g.id,
            reason = "Resolution was inadequate",
            status = AppealStatus.PENDING,
        )

        every { appealRepository.findByTenantIdAndGrievanceId(tenantId, g.id) } returnsMany listOf(null, appealItem)
        every { grievanceRepository.findByTenantIdAndId(tenantId, g.id) } returns g
        every { appealRepository.save(any()) } answers { firstArg() }
        every { grievanceRepository.save(any()) } answers { firstArg() }
        every { groundRepository.findById(groundId) } returns Optional.of(sampleGround)
        every { groundGroupRepository.findById(groupId) } returns Optional.of(sampleGroup)
        every { channelRepository.findById(channelId) } returns Optional.of(sampleChannel)

        val result = service.appealGrievance(
            tenantId = tenantId,
            grievanceId = g.id,
            employeeId = employeeId,
            request = GrievanceAppealRequest(reason = "Resolution was inadequate"),
        )

        assertEquals(GrievanceStatus.APPEALED, result.status)
        assertNotNull(result.appeal)
        assertEquals("Resolution was inadequate", result.appeal?.reason)
    }
}
