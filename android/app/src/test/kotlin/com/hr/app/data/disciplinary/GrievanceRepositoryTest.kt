package com.hr.app.data.disciplinary

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.GrievanceApi
import com.hr.client.model.GrievanceAppealItem
import com.hr.client.model.GrievanceChannelItem
import com.hr.client.model.GrievanceChannelsResponse
import com.hr.client.model.GrievanceDetailResponse
import com.hr.client.model.GrievanceGroundItem
import com.hr.client.model.GrievanceGroundsResponse
import com.hr.client.model.GrievanceItem
import com.hr.client.model.GrievanceListResponse
import com.hr.client.model.GrievanceSubmitRequest
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.OffsetDateTime
import java.util.UUID

class GrievanceRepositoryTest {

    private val grievanceApi = mockk<GrievanceApi>()
    private val outbox = mockk<Outbox>(relaxed = true)
    private val clock = Clock { 1772870000000L }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val pendingCountFlow = MutableStateFlow(0)
    private lateinit var repository: GrievanceRepository

    private val sampleGround = GrievanceGroundItem(
        id = UUID.randomUUID(),
        groupId = UUID.randomUUID(),
        code = "SAFETY",
        name = "Workplace Safety Hazard",
        description = "Physical safety",
        severity = GrievanceGroundItem.Severity.HIGH,
        slaDays = 3,
        defaultHandlerRole = "HR_DIRECTOR",
        groupName = "Environment",
    )

    private val sampleChannel = GrievanceChannelItem(
        id = UUID.randomUUID(),
        code = "DIRECT",
        name = "Confidential Direct Portal",
        isConfidential = true,
    )

    private val sampleGrievance = GrievanceItem(
        id = UUID.randomUUID(),
        grievanceNumber = "GRV-2026-0001",
        title = "Noise and fumes on floor 3",
        groundCode = "SAFETY",
        groundName = "Workplace Safety Hazard",
        channelName = "Confidential Direct Portal",
        severity = GrievanceItem.Severity.HIGH,
        anonymous = false,
        status = GrievanceItem.Status.SUBMITTED,
        raisedAt = OffsetDateTime.now(),
        targetResolutionDate = OffsetDateTime.now().plusDays(3),
        resolvedAt = null,
        resolution = null,
        satisfactionRating = null,
    )

    @Before
    fun setUp() {
        coEvery { outbox.pendingCount } returns pendingCountFlow
        repository = GrievanceRepository(
            grievanceApi = grievanceApi,
            outbox = outbox,
            json = json,
            clock = clock,
        )
    }

    @Test
    fun `refreshGrounds updates StateFlow on successful API response`() = runTest {
        val groundsResponse = GrievanceGroundsResponse(
            groups = emptyList(),
            grounds = listOf(sampleGround),
        )
        coEvery { grievanceApi.getGrievanceGrounds() } returns Response.success(groundsResponse)

        val result = repository.refreshGrounds()

        assertTrue(result.isSuccess)
        val state = repository.grounds.value
        assertNotNull(state)
        assertEquals(1, state?.grounds?.size)
        assertEquals("SAFETY", state?.grounds?.first()?.code)
    }

    @Test
    fun `refreshGrounds falls back to offline default grounds on error`() = runTest {
        coEvery { grievanceApi.getGrievanceGrounds() } throws RuntimeException("Network timeout")

        val result = repository.refreshGrounds()

        assertTrue(result.isFailure)
        val fallback = repository.grounds.value
        assertNotNull(fallback)
        assertTrue(fallback!!.grounds.isNotEmpty())
    }

    @Test
    fun `refreshChannels updates channels StateFlow`() = runTest {
        coEvery { grievanceApi.getGrievanceChannels() } returns Response.success(
            GrievanceChannelsResponse(channels = listOf(sampleChannel)),
        )

        val result = repository.refreshChannels()

        assertTrue(result.isSuccess)
        val channels = repository.channels.value
        assertEquals(1, channels.size)
        assertEquals("Confidential Direct Portal", channels[0].name)
    }

    @Test
    fun `refreshMyGrievances updates grievance list and pendingCount`() = runTest {
        val listResponse = GrievanceListResponse(
            grievances = listOf(sampleGrievance),
            totalCount = 1,
            pendingCount = 1,
        )
        coEvery { grievanceApi.getMyGrievances(any()) } returns Response.success(listResponse)

        val result = repository.refreshMyGrievances()

        assertTrue(result.isSuccess)
        assertEquals(1, repository.grievances.value.size)
        assertEquals(1, repository.pendingCount.value)
        assertEquals("GRV-2026-0001", repository.grievances.value.first().grievanceNumber)
    }

    @Test
    fun `submitGrievance performs optimistic update and increments pending count`() = runTest {
        val request = GrievanceSubmitRequest(
            groundId = sampleGround.id,
            channelId = sampleChannel.id,
            title = "Overtime calculation variance",
            description = "Unpaid hours in pay run",
            anonymous = true,
        )

        val detailResponse = GrievanceDetailResponse(
            id = UUID.randomUUID(),
            grievanceNumber = "GRV-2026-0002",
            ground = sampleGround,
            channel = sampleChannel,
            title = request.title,
            description = request.description,
            anonymous = true,
            status = GrievanceDetailResponse.Status.SUBMITTED,
            raisedAt = OffsetDateTime.now(),
            targetResolutionDate = OffsetDateTime.now().plusDays(3),
            raisedByEmployeeName = "Confidential (Anonymous Whistleblower)",
            resolution = null,
            appeal = null,
        )

        coEvery {
            grievanceApi.submitGrievance(any(), any())
        } returns Response.success(detailResponse)

        val result = repository.submitGrievance(request)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.grievances.value.size)
        assertEquals("GRV-2026-0002", repository.grievances.value.first().grievanceNumber)
        assertEquals(1, repository.pendingCount.value)
    }
}
