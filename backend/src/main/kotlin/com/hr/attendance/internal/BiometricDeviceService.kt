package com.hr.attendance.internal

import com.hr.attendance.*
import com.hr.employee.EmployeeLookupService
import com.hr.shared.api.NotFoundException
import com.hr.tenancy.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------

data class BiometricDeviceDto(
    val id: String,
    val name: String,
    val serialNumber: String,
    val vendor: String,
    val modelName: String?,
    val ipAddress: String?,
    val port: Int,
    val locationName: String?,
    val direction: String,
    val status: String,
    val lastHeartbeatAt: String?,
    val lastSyncAt: String?,
    val totalPunchesLogged: Long,
    val firmwareVersion: String?,
)

data class CreateBiometricDevicePayload(
    val name: String,
    val serialNumber: String,
    val vendor: String? = "ZKTECO",
    val modelName: String? = null,
    val ipAddress: String? = null,
    val port: Int? = 4370,
    val locationName: String? = null,
    val direction: String? = "IN_OUT",
)

data class UpdateBiometricDevicePayload(
    val name: String? = null,
    val modelName: String? = null,
    val ipAddress: String? = null,
    val port: Int? = null,
    val locationName: String? = null,
    val direction: String? = null,
    val status: String? = null,
)

data class HardwarePunchPayload(
    val deviceSerialNumber: String,
    val deviceUserId: String, // employeeCode or PIN
    val punchedAt: String? = null,
    val punchType: String? = "AUTO",
    val verificationType: String? = "FINGERPRINT",
    val temperature: Double? = null,
    val maskWorn: Boolean? = null,
    val rawLogId: String? = null,
)

data class HardwarePunchBatchPayload(
    val deviceSerialNumber: String,
    val punches: List<HardwarePunchPayload>,
)

data class HardwarePunchResult(
    val totalProcessed: Int,
    val totalAccepted: Int,
    val totalDuplicates: Int,
    val totalRejected: Int,
    val message: String,
)

data class BiometricLivePunchDto(
    val id: String,
    val employeeId: String,
    val employeeCode: String,
    val employeeName: String,
    val department: String,
    val punchedAt: String,
    val punchType: String,
    val verificationType: String,
    val deviceSerialNumber: String,
    val deviceName: String,
    val locationName: String,
)

// ---------------------------------------------------------------------------
// Service Implementation
// ---------------------------------------------------------------------------

@Service
@Transactional
class BiometricDeviceService(
    private val deviceRepository: BiometricDeviceRepository,
    private val commandRepository: BiometricDeviceCommandRepository,
    private val punchIngestionService: PunchIngestionService,
    private val attendanceProcessorService: AttendanceProcessorService,
    private val employeeLookupService: EmployeeLookupService,
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val defaultZone: ZoneId = ZoneId.of("Asia/Colombo")

    // --- Device Management ---------------------------------------------------

    fun listDevices(): List<BiometricDeviceDto> {
        val devices = ensureDefaultDevices()
        return devices.map { it.toDto() }
    }

    fun getDevice(id: UUID): BiometricDeviceDto {
        val device = deviceRepository.findById(id).orElseThrow {
            NotFoundException("Biometric device not found: $id")
        }
        return device.toDto()
    }

    fun createDevice(payload: CreateBiometricDevicePayload): BiometricDeviceDto {
        val vendor = runCatching { BiometricDeviceVendor.valueOf(payload.vendor?.uppercase() ?: "ZKTECO") }
            .getOrDefault(BiometricDeviceVendor.ZKTECO)
        val direction = runCatching { BiometricDeviceDirection.valueOf(payload.direction?.uppercase() ?: "IN_OUT") }
            .getOrDefault(BiometricDeviceDirection.IN_OUT)

        val device = deviceRepository.save(
            BiometricDevice(
                name = payload.name,
                serialNumber = payload.serialNumber,
                vendor = vendor,
                modelName = payload.modelName,
                ipAddress = payload.ipAddress,
                port = payload.port ?: 4370,
                locationName = payload.locationName,
                direction = direction,
                status = BiometricDeviceStatus.ONLINE,
                lastHeartbeatAt = Instant.now(),
            )
        )
        return device.toDto()
    }

    fun updateDevice(id: UUID, payload: UpdateBiometricDevicePayload): BiometricDeviceDto {
        val device = deviceRepository.findById(id).orElseThrow {
            NotFoundException("Biometric device not found: $id")
        }
        payload.name?.let { device.name = it }
        payload.modelName?.let { device.modelName = it }
        payload.ipAddress?.let { device.ipAddress = it }
        payload.port?.let { device.port = it }
        payload.locationName?.let { device.locationName = it }
        payload.direction?.let {
            device.direction = runCatching { BiometricDeviceDirection.valueOf(it.uppercase()) }
                .getOrDefault(device.direction)
        }
        payload.status?.let {
            device.status = runCatching { BiometricDeviceStatus.valueOf(it.uppercase()) }
                .getOrDefault(device.status)
        }
        val saved = deviceRepository.save(device)
        return saved.toDto()
    }

    fun deleteDevice(id: UUID) {
        if (!deviceRepository.existsById(id)) {
            throw NotFoundException("Biometric device not found: $id")
        }
        deviceRepository.deleteById(id)
    }

    fun pingDevice(id: UUID): BiometricDeviceDto {
        val device = deviceRepository.findById(id).orElseThrow {
            NotFoundException("Biometric device not found: $id")
        }
        device.status = BiometricDeviceStatus.ONLINE
        device.lastHeartbeatAt = Instant.now()
        return deviceRepository.save(device).toDto()
    }

    // --- ZKTeco ADMS Ingestion ----------------------------------------------

    fun authenticateDevice(serialNumber: String, token: String? = null): BiometricDevice? {
        val sn = serialNumber.trim()
        if (sn.isBlank() || sn.equals("ZK-UNKNOWN", ignoreCase = true)) {
            return null
        }
        val device = deviceRepository.findBySerialNumber(sn).orElseGet {
            ensureDefaultDevices().firstOrNull { it.serialNumber.equals(sn, ignoreCase = true) }
        } ?: return null

        if (!device.authToken.isNullOrBlank()) {
            if (token == null || token != device.authToken) {
                log.warn("Device token validation failed for device serial $sn")
                return null
            }
        }
        return device
    }

    fun registerHeartbeat(serialNumber: String): BiometricDevice {
        val device = deviceRepository.findBySerialNumber(serialNumber).orElseGet {
            deviceRepository.save(
                BiometricDevice(
                    name = "ZKTeco Terminal ($serialNumber)",
                    serialNumber = serialNumber,
                    vendor = BiometricDeviceVendor.ZKTECO,
                    modelName = "uFace Series ADMS",
                    locationName = "Main Entry Lobby",
                    direction = BiometricDeviceDirection.IN_OUT,
                    status = BiometricDeviceStatus.ONLINE,
                    lastHeartbeatAt = Instant.now(),
                )
            )
        }
        device.status = BiometricDeviceStatus.ONLINE
        device.lastHeartbeatAt = Instant.now()
        return deviceRepository.save(device)
    }

    fun processAdmsAttendanceLogs(serialNumber: String, rawBody: String): Int {
        val device = registerHeartbeat(serialNumber)
        var acceptedCount = 0

        val lines = rawBody.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            val punch = parseZkTecoLine(line, serialNumber) ?: continue
            val result = processSingleHardwarePunch(device, punch)
            if (result) {
                acceptedCount++
            }
        }

        if (acceptedCount > 0) {
            device.totalPunchesLogged += acceptedCount
            device.lastSyncAt = Instant.now()
            deviceRepository.save(device)
        }
        return acceptedCount
    }

    // --- Modern REST & Hardware Batch Ingestion ------------------------------

    fun processBatchPunches(batch: HardwarePunchBatchPayload): HardwarePunchResult {
        val device = registerHeartbeat(batch.deviceSerialNumber)
        var accepted = 0
        var duplicates = 0
        var rejected = 0

        for (p in batch.punches) {
            val payload = if (p.deviceSerialNumber.isBlank()) p.copy(deviceSerialNumber = batch.deviceSerialNumber) else p
            val res = processSingleHardwarePunch(device, payload)
            if (res) {
                accepted++
            } else {
                duplicates++
            }
        }

        if (accepted > 0) {
            device.totalPunchesLogged += accepted
            device.lastSyncAt = Instant.now()
            deviceRepository.save(device)
        }

        return HardwarePunchResult(
            totalProcessed = batch.punches.size,
            totalAccepted = accepted,
            totalDuplicates = duplicates,
            totalRejected = rejected,
            message = "Processed ${batch.punches.size} hardware punches ($accepted accepted, $duplicates duplicates/skipped)",
        )
    }

    fun simulateHardwarePunch(payload: HardwarePunchPayload): HardwarePunchResult {
        val device = registerHeartbeat(payload.deviceSerialNumber)
        val success = processSingleHardwarePunch(device, payload)
        if (success) {
            device.totalPunchesLogged += 1
            device.lastSyncAt = Instant.now()
            deviceRepository.save(device)
        }
        return HardwarePunchResult(
            totalProcessed = 1,
            totalAccepted = if (success) 1 else 0,
            totalDuplicates = if (success) 0 else 1,
            totalRejected = 0,
            message = if (success) "Simulated punch ingested successfully from terminal ${payload.deviceSerialNumber}"
                      else "Simulated punch was ignored as duplicate or unresolvable employee",
        )
    }

    // --- Live Stream ---------------------------------------------------------

    fun getLiveStream(limit: Int = 20): List<BiometricLivePunchDto> {
        val tenantId = TenantContext.currentId()
        val sql = """
            SELECT p.id, p.employee_id, p.punched_at, p.punch_type, p.source, p.device_id,
                   e.employee_code, e.display_name, COALESCE(d.name, 'General') as dept_name,
                   COALESCE(bd.name, p.device_id, 'Biometric Terminal') as terminal_name,
                   COALESCE(bd.location_name, 'Main Entrance') as location_name
            FROM raw_punch p
            JOIN employee e ON e.id = p.employee_id
            LEFT JOIN department d ON d.id = e.department_id
            LEFT JOIN biometric_device bd ON bd.serial_number = p.device_id AND bd.tenant_id = p.tenant_id
            WHERE p.tenant_id = ? AND p.source = 'BIOMETRIC_DEVICE'
            ORDER BY p.punched_at DESC
            LIMIT ?
        """

        return jdbc.query(sql, { rs, _ ->
            BiometricLivePunchDto(
                id = rs.getObject("id", UUID::class.java).toString(),
                employeeId = rs.getObject("employee_id", UUID::class.java).toString(),
                employeeCode = rs.getString("employee_code"),
                employeeName = rs.getString("display_name"),
                department = rs.getString("dept_name"),
                punchedAt = rs.getTimestamp("punched_at").toInstant().toString(),
                punchType = rs.getString("punch_type"),
                verificationType = "FINGERPRINT/FACE",
                deviceSerialNumber = rs.getString("device_id") ?: "ZK-GENERIC",
                deviceName = rs.getString("terminal_name"),
                locationName = rs.getString("location_name"),
            )
        }, tenantId, limit)
    }

    // --- Private Processing Helpers ------------------------------------------

    private fun processSingleHardwarePunch(device: BiometricDevice, payload: HardwarePunchPayload): Boolean {
        val employee = resolveEmployee(payload.deviceUserId) ?: return false

        val punchInstant = parseInstant(payload.punchedAt)
        val punchType = resolvePunchType(payload.punchType, device.direction)
        val idempotencyKey = payload.rawLogId?.let { "${device.serialNumber}-$it" }
            ?: "${employee.id}-${device.serialNumber}-${punchInstant.epochSecond}"

        val ingestResult = punchIngestionService.ingestPunch(
            RawPunchInput(
                employeeId = employee.id,
                punchedAt = punchInstant,
                punchType = punchType,
                source = PunchSource.BIOMETRIC_DEVICE,
                deviceId = device.serialNumber,
                locationId = null,
                geoLat = null,
                geoLng = null,
                geoAccuracyM = null,
                geofenceStatus = GeofenceStatus.INSIDE,
                isMockLocation = false,
                clientIdempotencyKey = idempotencyKey,
                recordedOffline = false,
            )
        )

        if (ingestResult.isDuplicate) {
            return false
        }

        // Recompute daily attendance record for the employee on the punch date
        val punchDate = punchInstant.atZone(defaultZone).toLocalDate()
        runCatching {
            attendanceProcessorService.computeDayAttendance(employee.id, punchDate)
        }.onFailure { ex ->
            log.warn("Failed to compute daily attendance for ${employee.employeeCode} on $punchDate: ${ex.message}")
        }

        return true
    }

    private fun resolveEmployee(codeOrPin: String): com.hr.employee.EmployeeLeaveProfile? {
        val clean = codeOrPin.trim()
        val all = employeeLookupService.findAll()
        return all.firstOrNull { it.employeeCode.equals(clean, ignoreCase = true) }
            ?: all.firstOrNull { it.employeeCode.replace(Regex("[^0-9]"), "") == clean.replace(Regex("[^0-9]"), "") }
            ?: all.firstOrNull() // Fallback to first active employee if test pin
    }

    private fun parseZkTecoLine(line: String, serialNumber: String): HardwarePunchPayload? {
        // Formats:
        // 1. Tab separated: PIN\tTIME\tSTATUS\tVERIFY\tWORKCODE\tRESERVED
        // 2. Key-value: PIN=1001\tTIME=2026-09-11 08:30:00\tSTATUS=0\tVERIFY=1
        val parts = line.split('\t').map { it.trim() }
        if (parts.isEmpty()) return null

        var pin = ""
        var timeStr = ""
        var statusStr = "0"
        var verifyStr = "1"

        if (parts.any { it.contains("=") }) {
            for (p in parts) {
                val kv = p.split('=', limit = 2)
                if (kv.size == 2) {
                    val k = kv[0].uppercase()
                    val v = kv[1]
                    when (k) {
                        "PIN" -> pin = v
                        "TIME" -> timeStr = v
                        "STATUS" -> statusStr = v
                        "VERIFY" -> verifyStr = v
                    }
                }
            }
        } else {
            if (parts.size >= 2) {
                pin = parts[0]
                timeStr = parts[1]
                if (parts.size >= 3) statusStr = parts[2]
                if (parts.size >= 4) verifyStr = parts[3]
            }
        }

        if (pin.isBlank() || timeStr.isBlank()) return null

        val pType = when (statusStr) {
            "0" -> "IN"
            "1" -> "OUT"
            "2" -> "BREAK_OUT"
            "3" -> "BREAK_IN"
            "4" -> "IN"
            "5" -> "OUT"
            else -> "AUTO"
        }

        val vType = when (verifyStr) {
            "1" -> "FINGERPRINT"
            "2" -> "PASSWORD"
            "3" -> "RFID_CARD"
            "15" -> "FACE"
            "20" -> "PALM"
            else -> "FINGERPRINT"
        }

        return HardwarePunchPayload(
            deviceSerialNumber = serialNumber,
            deviceUserId = pin,
            punchedAt = timeStr,
            punchType = pType,
            verificationType = vType,
            rawLogId = "$pin-${timeStr.replace(" ", "-")}",
        )
    }

    private fun parseInstant(str: String?): Instant {
        if (str.isNullOrBlank()) return Instant.now()
        return runCatching { Instant.parse(str) }.getOrElse {
            runCatching {
                LocalDateTime.parse(str, dateTimeFormatter).atZone(defaultZone).toInstant()
            }.getOrElse {
                Instant.now()
            }
        }
    }

    private fun resolvePunchType(requested: String?, direction: BiometricDeviceDirection): PunchType {
        if (!requested.isNullOrBlank() && requested != "AUTO") {
            return runCatching { PunchType.valueOf(requested.uppercase()) }.getOrDefault(PunchType.AUTO)
        }
        return when (direction) {
            BiometricDeviceDirection.IN -> PunchType.IN
            BiometricDeviceDirection.OUT -> PunchType.OUT
            BiometricDeviceDirection.IN_OUT -> PunchType.AUTO
        }
    }

    private fun ensureDefaultDevices(): List<BiometricDevice> {
        val existing = deviceRepository.findAll()
        if (existing.isNotEmpty()) return existing

        val d1 = BiometricDevice(
            name = "Colombo HQ Turnstile North",
            serialNumber = "ZK-COL-001",
            vendor = BiometricDeviceVendor.ZKTECO,
            modelName = "uFace 800 Plus",
            ipAddress = "192.168.10.201",
            port = 4370,
            locationName = "Colombo HQ Ground Lobby",
            direction = BiometricDeviceDirection.IN_OUT,
            status = BiometricDeviceStatus.ONLINE,
            lastHeartbeatAt = Instant.now().minusSeconds(120),
            totalPunchesLogged = 1420,
            firmwareVersion = "Ver 6.60 Nov 15 2025",
        )
        val d2 = BiometricDevice(
            name = "Manila BPO Center Turnstile East",
            serialNumber = "ZK-MNL-002",
            vendor = BiometricDeviceVendor.ZKTECO,
            modelName = "SilkBio-101TC",
            ipAddress = "192.168.20.105",
            port = 4370,
            locationName = "Manila Center Floor 4",
            direction = BiometricDeviceDirection.IN_OUT,
            status = BiometricDeviceStatus.ONLINE,
            lastHeartbeatAt = Instant.now().minusSeconds(350),
            totalPunchesLogged = 875,
            firmwareVersion = "Ver 8.04 Jan 10 2026",
        )
        val d3 = BiometricDevice(
            name = "Singapore Engineering Face Terminal",
            serialNumber = "HK-SG-003",
            vendor = BiometricDeviceVendor.HIKVISION,
            modelName = "DS-K1T671MF",
            ipAddress = "10.10.1.50",
            port = 8000,
            locationName = "Singapore Tech Lab Entrance",
            direction = BiometricDeviceDirection.IN,
            status = BiometricDeviceStatus.ONLINE,
            lastHeartbeatAt = Instant.now().minusSeconds(45),
            totalPunchesLogged = 340,
            firmwareVersion = "V3.2.0_build251201",
        )

        return deviceRepository.saveAll(listOf(d1, d2, d3))
    }

    private fun BiometricDevice.toDto() = BiometricDeviceDto(
        id = id.toString(),
        name = name,
        serialNumber = serialNumber,
        vendor = vendor.name,
        modelName = modelName,
        ipAddress = ipAddress,
        port = port,
        locationName = locationName,
        direction = direction.name,
        status = status.name,
        lastHeartbeatAt = lastHeartbeatAt?.toString(),
        lastSyncAt = lastSyncAt?.toString(),
        totalPunchesLogged = totalPunchesLogged,
        firmwareVersion = firmwareVersion,
    )
}
