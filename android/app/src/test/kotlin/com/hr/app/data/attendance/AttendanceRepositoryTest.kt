package com.hr.app.data.attendance

import com.hr.app.data.sync.Clock
import com.hr.app.data.sync.Outbox
import com.hr.client.api.AttendanceApi
import com.hr.client.model.AttendancePunchItem
import com.hr.client.model.AttendancePunchResponse
import com.hr.client.model.AttendanceShiftInfo
import com.hr.client.model.BranchGeofenceInfo
import com.hr.client.model.TodayAttendanceResponse
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
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AttendanceRepositoryTest {

    private val attendanceApi = mockk<AttendanceApi>()
    private val outbox = mockk<Outbox>(relaxed = true)
    private val clock = Clock { 1772870000000L }
    private val json = Json {
        serializersModule = com.hr.client.infrastructure.Serializer.kotlinxSerializationAdapters
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val pendingCountFlow = MutableStateFlow(0)
    private lateinit var repository: AttendanceRepository

    private val sampleShift = AttendanceShiftInfo(
        id = "shift-gen-0830",
        code = "GEN_0830",
        name = "General Day Shift",
        startTime = "08:30:00",
        endTime = "17:30:00",
        graceInMinutes = 15,
        breakMinutes = 60,
    )

    private val sampleGeofence = BranchGeofenceInfo(
        branchName = "Colombo Head Office",
        latitude = BigDecimal.valueOf(6.9271),
        longitude = BigDecimal.valueOf(79.8612),
        radiusMeters = BigDecimal.valueOf(200.0),
    )

    private val sampleTodayAttendance = TodayAttendanceResponse(
        employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        workDate = LocalDate.of(2026, 9, 7),
        dayStatus = "NOT_CHECKED_IN",
        currentStatus = "NOT_CHECKED_IN",
        workedMinutes = 0,
        punches = emptyList(),
        geofence = sampleGeofence,
        shift = sampleShift,
    )

    @Before
    fun setUp() {
        every { outbox.pendingCount } returns pendingCountFlow
        coEvery { attendanceApi.getMyTodayAttendance() } returns Response.success(sampleTodayAttendance)
        coEvery { outbox.enqueue(any(), any(), any(), any(), any()) } returns "mock-outbox-id"

        repository = AttendanceRepository(
            attendanceApi = attendanceApi,
            outbox = outbox,
            json = json,
            clock = clock,
        )
    }

    @Test
    fun `refresh populates todayAttendance from API`() = runTest {
        val result = repository.refresh()
        assertTrue(result.isSuccess)
        val state = repository.todayAttendance.value
        assertNotNull(state)
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), state?.employeeId)
        assertEquals("GEN_0830", state?.shift?.code)
        assertEquals(BigDecimal.valueOf(200.0), state?.geofence?.radiusMeters)
    }

    @Test
    fun `recordPunch optimistically updates state and enqueues to outbox`() = runTest {
        repository.refresh()

        // Clock In at HQ (lat: 6.9272, lng: 79.8613) ~ 15m away, inside 200m radius
        val punch = repository.recordPunch(
            punchType = "IN",
            geoLat = 6.9272,
            geoLng = 79.8613,
            geoAccuracyM = 5.0,
            isMockLocation = false,
            locationName = "Colombo HQ Main Gate",
        )

        assertEquals(AttendancePunchItem.PunchType.IN, punch.punchType)
        assertEquals(AttendancePunchItem.GeofenceStatus.INSIDE, punch.geofenceStatus)
        assertFalse(punch.isMockLocation)

        // Verify optimistic state update
        val updated = repository.todayAttendance.value
        assertEquals("CHECKED_IN", updated?.currentStatus)
        assertEquals("PRESENT", updated?.dayStatus)
        assertEquals(1, updated?.punches?.size)
        assertEquals(AttendancePunchItem.PunchType.IN, updated?.punches?.first()?.punchType)

        // Verify outbox enqueue call with correct aggregate type and path
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

        assertEquals("ATTENDANCE_PUNCH", typeSlot.captured)
        assertEquals("/v1/attendance/punches", pathSlot.captured)
        assertTrue(payloadSlot.captured.contains("\"punchType\":\"IN\""))
        assertTrue(payloadSlot.captured.contains("6.9272"))
    }

    @Test
    fun `recordPunch evaluates OUTSIDE when coordinates exceed branch radius`() = runTest {
        repository.refresh()

        // 1.8km away (Town hall lat: 6.9147, lng: 79.8653)
        val punch = repository.recordPunch(
            punchType = "IN",
            geoLat = 6.9147,
            geoLng = 79.8653,
            geoAccuracyM = 10.0,
            isMockLocation = false,
            locationName = null,
        )

        assertEquals(AttendancePunchItem.GeofenceStatus.OUTSIDE, punch.geofenceStatus)
        assertTrue(punch.locationName?.contains("Outside Office Boundary") == true)
    }

    @Test
    fun `recordPunch flags mock location provider`() = runTest {
        repository.refresh()

        val punch = repository.recordPunch(
            punchType = "IN",
            geoLat = 6.9271,
            geoLng = 79.8612,
            geoAccuracyM = 2.0,
            isMockLocation = true,
            locationName = "Spoofed Location",
        )

        assertTrue(punch.isMockLocation)
        assertEquals("Spoofed Location", punch.locationName)
    }

    @Test
    fun `haversine calculation accurately computes distance`() {
        // Point A: Colombo HQ (6.9271, 79.8612)
        // Point B: ~150 meters away (6.9280, 79.8620)
        val dist = AttendanceRepository.calculateHaversineMeters(
            6.9271, 79.8612,
            6.9280, 79.8620,
        )
        assertTrue(dist in 120.0..180.0)
    }
}
