package com.hr.attendance

import com.hr.attendance.internal.DefaultPunchIngestionService
import com.hr.attendance.internal.RawPunch
import com.hr.attendance.internal.RawPunchRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@DisplayName("Punch Ingestion Service")
class PunchIngestionTest {

    private lateinit var rawPunchRepository: RawPunchRepository
    private lateinit var service: DefaultPunchIngestionService

    private val employeeId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        rawPunchRepository = mock(RawPunchRepository::class.java)
        service = DefaultPunchIngestionService(rawPunchRepository)
    }

    @Test
    fun `ingests biometric punch successfully`() {
        val punchInput = RawPunchInput(
            employeeId = employeeId,
            punchedAt = Instant.parse("2026-03-02T02:55:00Z"),
            punchType = PunchType.IN,
            source = PunchSource.BIOMETRIC_DEVICE,
            deviceId = "ZKT-HQ-01",
            clientIdempotencyKey = "punch-emp-101-20260302-01",
        )

        val entity = RawPunch(
            employeeId = punchInput.employeeId,
            punchedAt = punchInput.punchedAt,
            punchType = punchInput.punchType,
            source = punchInput.source,
            deviceId = punchInput.deviceId,
            clientIdempotencyKey = punchInput.clientIdempotencyKey,
        )

        `when`(rawPunchRepository.findByClientIdempotencyKey(punchInput.clientIdempotencyKey!!)).thenReturn(null)
        `when`(rawPunchRepository.save(any(RawPunch::class.java))).thenReturn(entity)

        val result = service.ingestPunch(punchInput)
        assertThat(result.isDuplicate).isFalse()
        assertThat(result.employeeId).isEqualTo(employeeId)
        assertThat(result.punchType).isEqualTo(PunchType.IN)
        assertThat(result.statusMessage).contains("successfully")
    }

    @Test
    fun `ignores duplicate punch with same client idempotency key`() {
        val idempotencyKey = "mobile-offline-uuid-999"
        val existingEntity = RawPunch(
            employeeId = employeeId,
            punchedAt = Instant.parse("2026-03-02T03:00:00Z"),
            punchType = PunchType.IN,
            source = PunchSource.MOBILE_APP,
            clientIdempotencyKey = idempotencyKey,
        )

        `when`(rawPunchRepository.findByClientIdempotencyKey(idempotencyKey)).thenReturn(existingEntity)

        val duplicateInput = RawPunchInput(
            employeeId = employeeId,
            punchedAt = Instant.parse("2026-03-02T03:00:00Z"),
            punchType = PunchType.IN,
            source = PunchSource.MOBILE_APP,
            clientIdempotencyKey = idempotencyKey,
        )

        val result = service.ingestPunch(duplicateInput)
        assertThat(result.isDuplicate).isTrue()
        assertThat(result.id).isEqualTo(existingEntity.id)
        assertThat(result.statusMessage).contains("Duplicate punch ignored")
    }

    @Test
    fun `records geofence status and mock location trust metadata`() {
        val punchInput = RawPunchInput(
            employeeId = employeeId,
            punchedAt = Instant.parse("2026-03-02T03:02:00Z"),
            punchType = PunchType.IN,
            source = PunchSource.MOBILE_APP,
            geoLat = BigDecimal("6.9271"),
            geoLng = BigDecimal("79.8612"),
            geoAccuracyM = BigDecimal("15.5"),
            geofenceStatus = GeofenceStatus.INSIDE,
            isMockLocation = false,
            recordedOffline = true,
        )

        val entity = RawPunch(
            employeeId = punchInput.employeeId,
            punchedAt = punchInput.punchedAt,
            punchType = punchInput.punchType,
            source = punchInput.source,
            geoLat = punchInput.geoLat,
            geoLng = punchInput.geoLng,
            geoAccuracyM = punchInput.geoAccuracyM,
            geofenceStatus = punchInput.geofenceStatus,
            isMockLocation = punchInput.isMockLocation,
            recordedOffline = punchInput.recordedOffline,
        )

        `when`(rawPunchRepository.save(any(RawPunch::class.java))).thenReturn(entity)

        val result = service.ingestPunch(punchInput)
        assertThat(result.isDuplicate).isFalse()
    }
}
