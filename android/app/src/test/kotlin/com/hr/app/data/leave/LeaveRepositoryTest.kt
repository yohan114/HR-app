package com.hr.app.data.leave

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.LeaveApi
import com.hr.client.model.CancelLeaveRequest
import com.hr.client.model.LeaveApplicationItem
import com.hr.client.model.LeaveApplicationRequest
import com.hr.client.model.LeaveApplicationsResponse
import com.hr.client.model.LeaveBalanceItem
import com.hr.client.model.LeaveBalancesResponse
import com.hr.client.model.LeaveEligibilityRequest
import com.hr.client.model.LeaveEligibilityResponse
import com.hr.client.model.LeaveLedgerEntryItem
import com.hr.client.model.LeaveLedgerResponse
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class LeaveRepositoryTest {

    private val leaveApi = mockk<LeaveApi>()
    private val outbox = mockk<Outbox>(relaxed = true)
    private val clock = Clock { 1772870000000L }
    private val json = Json {
        serializersModule = com.hr.client.infrastructure.Serializer.kotlinxSerializationAdapters
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val pendingCountFlow = MutableStateFlow(0)
    private lateinit var repository: LeaveRepository

    private val sampleBalances = listOf(
        LeaveBalanceItem(
            leaveTypeId = "annual-id",
            leaveTypeCode = "ANNUAL",
            leaveTypeName = "Annual Leave",
            color = "#0284c7",
            entitledDays = BigDecimal("14.0"),
            accruedDays = BigDecimal("14.0"),
            takenDays = BigDecimal("2.0"),
            pendingDays = BigDecimal("1.0"),
            availableDays = BigDecimal("11.0"),
        ),
        LeaveBalanceItem(
            leaveTypeId = "casual-id",
            leaveTypeCode = "CASUAL",
            leaveTypeName = "Casual Leave",
            color = "#16a34a",
            entitledDays = BigDecimal("7.0"),
            accruedDays = BigDecimal("7.0"),
            takenDays = BigDecimal("1.0"),
            pendingDays = BigDecimal.ZERO,
            availableDays = BigDecimal("6.0"),
        ),
    )

    private val sampleApplication = LeaveApplicationItem(
        id = UUID.fromString("00000000-0000-0000-0000-000000000101"),
        employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        employeeName = "Kasun Perera",
        leaveTypeId = "annual-id",
        leaveTypeCode = "ANNUAL",
        leaveTypeName = "Annual Leave",
        startDate = LocalDate.of(2026, 9, 14),
        endDate = LocalDate.of(2026, 9, 15),
        dayPortion = LeaveApplicationItem.DayPortion.FULL_DAY,
        totalDays = BigDecimal("2.0"),
        reason = "Family trip",
        status = LeaveApplicationItem.Status.SUBMITTED,
        submittedAt = OffsetDateTime.parse("2026-09-07T08:00:00Z"),
        approvedAt = null,
        days = emptyList(),
    )

    @Before
    fun setUp() {
        every { outbox.pendingCount } returns pendingCountFlow
        coEvery { leaveApi.getMyLeaveBalances() } returns Response.success(
            LeaveBalancesResponse(leaveYear = "2026", balances = sampleBalances)
        )
        coEvery { leaveApi.getMyLeaveApplications(any()) } returns Response.success(
            LeaveApplicationsResponse(applications = listOf(sampleApplication))
        )
        coEvery { leaveApi.getMyLeaveLedger(any()) } returns Response.success(
            LeaveLedgerResponse(
                ledger = listOf(
                    LeaveLedgerEntryItem(
                        id = "ledger-1",
                        date = LocalDate.of(2026, 1, 1),
                        leaveTypeId = "annual-id",
                        leaveTypeCode = "ANNUAL",
                        leaveTypeName = "Annual Leave",
                        eventType = LeaveLedgerEntryItem.EventType.OPENING,
                        daysCredited = BigDecimal("14.0"),
                        daysDebited = BigDecimal.ZERO,
                        balanceAfter = BigDecimal("14.0"),
                        referenceId = "2026",
                        notes = "Opening balance",
                    )
                )
            )
        )
        coEvery { outbox.enqueue(any(), any(), any(), any(), any()) } returns "mock-outbox-id"

        repository = LeaveRepository(
            leaveApi = leaveApi,
            outbox = outbox,
            json = json,
            clock = clock,
        )
    }

    @Test
    fun `refreshBalances fetches from API and updates state flows`() = runTest {
        val result = repository.refreshBalances()
        assertTrue(result.isSuccess)
        assertEquals(2, repository.balances.value.size)
        assertEquals("2026", repository.leaveYear.value)
        assertEquals(11.0, repository.balances.value.first().availableDays.toDouble(), 0.001)
    }

    @Test
    fun `refreshApplications populates applications state`() = runTest {
        val result = repository.refreshApplications()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.applications.value.size)
        assertEquals(LeaveApplicationItem.Status.SUBMITTED, repository.applications.value.first().status)
    }

    @Test
    fun `refreshLedger populates ledger state`() = runTest {
        val result = repository.refreshLedger()
        assertTrue(result.isSuccess)
        assertEquals(1, repository.ledger.value.size)
        assertEquals(LeaveLedgerEntryItem.EventType.OPENING, repository.ledger.value.first().eventType)
    }

    @Test
    fun `checkEligibility falls back to offline working days calculation`() = runTest {
        repository.refreshBalances()
        coEvery { leaveApi.checkLeaveEligibility(any()) } throws RuntimeException("Offline")

        // Friday 2026-09-11 to Monday 2026-09-14 (Fri, Sat, Sun, Mon = 2 working days)
        val result = repository.checkEligibility(
            leaveTypeId = "annual-id",
            startDate = LocalDate.of(2026, 9, 11),
            endDate = LocalDate.of(2026, 9, 14),
            dayPortion = "FULL_DAY",
        )

        assertTrue(result.isSuccess)
        val eligibility = result.getOrThrow()
        assertTrue(eligibility.eligible)
        assertEquals(2.0, eligibility.workingDaysRequested.toDouble(), 0.001)
        assertEquals(11.0, eligibility.balanceAvailable.toDouble(), 0.001)
        assertEquals(9.0, eligibility.remainingAfter.toDouble(), 0.001)
    }

    @Test
    fun `submitApplication optimistically updates balances and enqueues outbox mutation`() = runTest {
        repository.refreshBalances()
        coEvery { leaveApi.submitLeaveApplication(any()) } returns Response.success(sampleApplication)

        val result = repository.submitApplication(
            leaveTypeId = "annual-id",
            startDate = LocalDate.of(2026, 9, 14),
            endDate = LocalDate.of(2026, 9, 15),
            dayPortion = "FULL_DAY",
            reason = "Family trip",
        )

        assertTrue(result.isSuccess)
        assertEquals(1, repository.applications.value.size)

        val annual = repository.balances.value.first { it.leaveTypeId == "annual-id" }
        assertEquals(9.0, annual.availableDays.toDouble(), 0.001)
        assertEquals(3.0, annual.pendingDays.toDouble(), 0.001)

        val typeSlot = slot<String>()
        val pathSlot = slot<String>()
        val payloadSlot = slot<String>()

        coVerify {
            outbox.enqueue(
                aggregateType = capture(typeSlot),
                aggregateId = any(),
                httpMethod = eq("POST"),
                path = capture(pathSlot),
                payload = capture(payloadSlot),
            )
        }

        assertEquals("LEAVE_APPLICATION", typeSlot.captured)
        assertEquals("/v1/leave/applications", pathSlot.captured)
        assertTrue(payloadSlot.captured.contains("\"leaveTypeId\":\"annual-id\""))
        assertTrue(payloadSlot.captured.contains("Family trip"))
    }

    @Test
    fun `cancelApplication updates state, restores balance, and queues outbox`() = runTest {
        repository.refreshBalances()
        repository.refreshApplications()

        val appId = sampleApplication.id
        coEvery { leaveApi.cancelLeaveApplication(appId, any()) } returns Response.success(
            sampleApplication.copy(status = LeaveApplicationItem.Status.CANCELLED)
        )

        val result = repository.cancelApplication(
            applicationId = appId,
            reason = "Changed travel plans",
        )

        assertTrue(result.isSuccess)
        val app = repository.applications.value.first { it.id == appId }
        assertEquals(LeaveApplicationItem.Status.CANCELLED, app.status)

        val typeSlot = slot<String>()
        val pathSlot = slot<String>()

        coVerify {
            outbox.enqueue(
                aggregateType = capture(typeSlot),
                aggregateId = eq(appId.toString()),
                httpMethod = eq("POST"),
                path = capture(pathSlot),
                payload = any(),
            )
        }

        assertEquals("LEAVE_CANCELLATION", typeSlot.captured)
        assertEquals("/v1/leave/applications/$appId/cancel", pathSlot.captured)
    }
}
