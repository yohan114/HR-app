package com.hr.app.data.approval

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.ApprovalsApi
import com.hr.client.model.ApprovalItem
import com.hr.client.model.LeaveApprovalDetails
import com.hr.client.model.PendingApprovalsResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class ApprovalsRepositoryTest {

    private val approvalsApi = mockk<ApprovalsApi>()
    private val outbox = mockk<Outbox>(relaxed = true)
    private val clock = Clock { 1772870000000L }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val pendingCountFlow = MutableStateFlow(0)

    private lateinit var repository: ApprovalsRepository

    private val sampleItem = ApprovalItem(
        id = "leave-app-001",
        type = "LEAVE",
        requesterId = UUID.fromString("00000000-0000-0000-0000-000000000004"),
        requesterName = "Kasun Perera",
        requesterDesignation = "Senior Software Engineer",
        departmentName = "Engineering",
        submittedAt = OffsetDateTime.parse("2026-09-07T08:30:00Z"),
        title = "Annual Leave (2.0 days)",
        summary = "Family function in Kandy",
        status = "PENDING",
        urgency = ApprovalItem.Urgency.URGENT,
        leaveDetails = LeaveApprovalDetails(
            leaveTypeName = "Annual Leave",
            startDate = LocalDate.of(2026, 9, 15),
            endDate = LocalDate.of(2026, 9, 16),
            workingDays = BigDecimal.valueOf(2.0),
            reason = "Family function in Kandy",
            employeeBalanceDays = BigDecimal.valueOf(14.0),
            teamCoverageWarning = "1 other team member on leave",
        ),
    )

    @Before
    fun setUp() {
        every { outbox.pendingCount } returns pendingCountFlow
        repository = ApprovalsRepository(
            approvalsApi = approvalsApi,
            outbox = outbox,
            json = json,
            clock = clock,
        )
    }

    @Test
    fun `refresh populates pending approvals state on successful response`() = runTest {
        coEvery { approvalsApi.listPendingApprovals() } returns Response.success(
            PendingApprovalsResponse(
                items = listOf(sampleItem),
                totalCount = 1,
            ),
        )

        val result = repository.refresh()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.pendingApprovals.value.size)
        assertEquals("leave-app-001", repository.pendingApprovals.value.first().id)
    }

    @Test
    fun `submitDecision removes item optimistically and enqueues to outbox with aggregateType leaveApplication`() = runTest {
        coEvery { approvalsApi.listPendingApprovals() } returns Response.success(
            PendingApprovalsResponse(
                items = listOf(sampleItem),
                totalCount = 1,
            ),
        )
        repository.refresh()
        assertEquals(1, repository.pendingApprovals.value.size)

        val aggTypeSlot = slot<String>()
        val aggIdSlot = slot<String>()
        val methodSlot = slot<String>()
        val pathSlot = slot<String>()

        coEvery {
            outbox.enqueue(
                aggregateType = capture(aggTypeSlot),
                aggregateId = capture(aggIdSlot),
                httpMethod = capture(methodSlot),
                path = capture(pathSlot),
                payload = any(),
            )
        } returns "outbox-entry-1"

        val response = repository.submitDecision(
            itemId = "leave-app-001",
            itemType = "LEAVE",
            decision = "APPROVE",
            remarks = null,
        )

        // Optimistic removal: in-memory list is immediately empty
        assertTrue(repository.pendingApprovals.value.isEmpty())

        // Outbox enqueue verification
        assertEquals("leaveApplication", aggTypeSlot.captured)
        assertEquals("leave-app-001", aggIdSlot.captured)
        assertEquals("POST", methodSlot.captured)
        assertEquals("/v1/approvals/leave-app-001/decision", pathSlot.captured)
        assertEquals("APPROVED", response.status)
    }

    @Test
    fun `submitDecision with ATTENDANCE type enqueues with aggregateType attendanceRegularisation`() = runTest {
        val aggTypeSlot = slot<String>()

        coEvery {
            outbox.enqueue(
                aggregateType = capture(aggTypeSlot),
                aggregateId = any(),
                httpMethod = any(),
                path = any(),
                payload = any(),
            )
        } returns "outbox-entry-2"

        val response = repository.submitDecision(
            itemId = "att-reg-002",
            itemType = "ATTENDANCE",
            decision = "REJECT",
            remarks = "Insufficient justification",
        )

        assertEquals("attendanceRegularisation", aggTypeSlot.captured)
        assertEquals("REJECTED", response.status)
    }
}
