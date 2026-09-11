package com.hr.attendance.internal

import com.hr.attendance.PunchIngestResult
import com.hr.attendance.PunchIngestionService
import com.hr.attendance.RawPunchInput
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
@Transactional
class DefaultPunchIngestionService(
    private val rawPunchRepository: RawPunchRepository,
) : PunchIngestionService {

    override fun ingestPunch(input: RawPunchInput): PunchIngestResult {
        // Idempotency check: prevent duplicate ingestion of same offline or network-retried punch
        if (input.clientIdempotencyKey != null) {
            val existing = rawPunchRepository.findByClientIdempotencyKey(input.clientIdempotencyKey)
            if (existing != null) {
                return PunchIngestResult(
                    id = existing.id ?: UUID.randomUUID(),
                    employeeId = existing.employeeId,
                    punchedAt = existing.punchedAt,
                    punchType = existing.punchType,
                    isDuplicate = true,
                    statusMessage = "Duplicate punch ignored via idempotency key: ${input.clientIdempotencyKey}",
                )
            }
        }

        val entity = RawPunch(
            employeeId = input.employeeId,
            punchedAt = input.punchedAt,
            punchType = input.punchType,
            source = input.source,
            deviceId = input.deviceId,
            locationId = input.locationId,
            geoLat = input.geoLat,
            geoLng = input.geoLng,
            geoAccuracyM = input.geoAccuracyM,
            geofenceStatus = input.geofenceStatus,
            isMockLocation = input.isMockLocation,
            clientIdempotencyKey = input.clientIdempotencyKey,
            recordedOffline = input.recordedOffline,
        )

        val saved = rawPunchRepository.save(entity)
        return PunchIngestResult(
            id = saved.id ?: UUID.randomUUID(),
            employeeId = saved.employeeId,
            punchedAt = saved.punchedAt,
            punchType = saved.punchType,
            isDuplicate = false,
            statusMessage = "Punch recorded successfully",
        )
    }

    override fun ingestBatch(inputs: List<RawPunchInput>): List<PunchIngestResult> =
        inputs.map { ingestPunch(it) }

    @Transactional(readOnly = true)
    override fun getPunches(
        employeeId: UUID,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<RawPunchInput> {
        val startInstant = startDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        val endInstant = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        return rawPunchRepository
            .findAllByEmployeeIdAndPunchedAtBetweenOrderByPunchedAtAsc(employeeId, startInstant, endInstant)
            .map { it.toDto() }
    }

    private fun RawPunch.toDto() = RawPunchInput(
        employeeId = employeeId,
        punchedAt = punchedAt,
        punchType = punchType,
        source = source,
        deviceId = deviceId,
        locationId = locationId,
        geoLat = geoLat,
        geoLng = geoLng,
        geoAccuracyM = geoAccuracyM,
        geofenceStatus = geofenceStatus,
        isMockLocation = isMockLocation,
        clientIdempotencyKey = clientIdempotencyKey,
        recordedOffline = recordedOffline,
    )
}
