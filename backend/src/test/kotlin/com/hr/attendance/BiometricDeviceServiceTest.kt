package com.hr.attendance

import com.hr.attendance.internal.*
import com.hr.employee.EmployeeLeaveProfile
import com.hr.employee.EmployeeLookupService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.LocalDate
import java.util.*

@DisplayName("Biometric Device Service & Ingestion Tests")
class BiometricDeviceServiceTest {

    private lateinit var deviceRepository: BiometricDeviceRepository
    private lateinit var commandRepository: BiometricDeviceCommandRepository
    private lateinit var punchIngestionService: PunchIngestionService
    private lateinit var attendanceProcessorService: AttendanceProcessorService
    private lateinit var employeeLookupService: EmployeeLookupService
    private lateinit var jdbc: JdbcTemplate
    private lateinit var service: BiometricDeviceService

    private val employeeId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val testEmployee = EmployeeLeaveProfile(
        id = employeeId,
        tenantId = UUID.randomUUID(),
        employeeCode = "LK010",
        displayName = "Kasun Mendis",
        joinDate = LocalDate.of(2022, 1, 15),
        status = "ACTIVE",
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyObject(): T {
        Mockito.any<T>()
        return null as T
    }

    @BeforeEach
    fun setUp() {
        deviceRepository = mock(BiometricDeviceRepository::class.java)
        commandRepository = mock(BiometricDeviceCommandRepository::class.java)
        punchIngestionService = mock(PunchIngestionService::class.java)
        attendanceProcessorService = mock(AttendanceProcessorService::class.java)
        employeeLookupService = mock(EmployeeLookupService::class.java)
        jdbc = mock(JdbcTemplate::class.java)

        service = BiometricDeviceService(
            deviceRepository,
            commandRepository,
            punchIngestionService,
            attendanceProcessorService,
            employeeLookupService,
            jdbc,
        )

        `when`(employeeLookupService.findAll()).thenReturn(listOf(testEmployee))
        `when`(deviceRepository.save(anyObject())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `createDevice persists device with vendor and serial number`() {
        val payload = CreateBiometricDevicePayload(
            name = "Colombo HQ Main Turnstile",
            serialNumber = "ZK-COL-001",
            vendor = "ZKTECO",
            modelName = "uFace 800 Plus",
            ipAddress = "192.168.1.100",
            port = 4370,
            locationName = "Lobby Entrance",
            direction = "IN_OUT",
        )

        val created = service.createDevice(payload)
        assertThat(created.name).isEqualTo("Colombo HQ Main Turnstile")
        assertThat(created.serialNumber).isEqualTo("ZK-COL-001")
        assertThat(created.vendor).isEqualTo("ZKTECO")
        assertThat(created.status).isEqualTo("ONLINE")
    }

    @Test
    fun `pingDevice updates device status to online and records heartbeat`() {
        val deviceId = UUID.randomUUID()
        val device = BiometricDevice(
            name = "Gate Terminal",
            serialNumber = "ZK-GATE-01",
            vendor = BiometricDeviceVendor.ZKTECO,
            status = BiometricDeviceStatus.OFFLINE,
        )
        `when`(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device))

        val pinged = service.pingDevice(deviceId)
        assertThat(pinged.status).isEqualTo("ONLINE")
        assertThat(pinged.lastHeartbeatAt).isNotNull()
    }

    @Test
    fun `processAdmsAttendanceLogs parses tab-separated ZKTeco raw lines and ingests punches`() {
        val sn = "ZK-TEST-001"
        val device = BiometricDevice(
            name = "Test Terminal",
            serialNumber = sn,
            vendor = BiometricDeviceVendor.ZKTECO,
        )
        `when`(deviceRepository.findBySerialNumber(sn)).thenReturn(Optional.of(device))

        `when`(punchIngestionService.ingestPunch(anyObject())).thenReturn(
            PunchIngestResult(
                id = UUID.randomUUID(),
                employeeId = employeeId,
                punchedAt = Instant.now(),
                punchType = PunchType.IN,
                isDuplicate = false,
                statusMessage = "Success",
            )
        )

        // Raw body simulating ZKTeco terminal ATTLOG push:
        // LK010 \t 2026-09-11 08:30:15 \t 0 \t 1 \t 0 \t 0
        val rawBody = "LK010\t2026-09-11 08:30:15\t0\t1\t0\t0"

        val count = service.processAdmsAttendanceLogs(sn, rawBody)
        assertThat(count).isEqualTo(1)
        assertThat(device.totalPunchesLogged).isEqualTo(1)

        // Verify daily attendance was recomputed
        verify(attendanceProcessorService, atLeastOnce()).computeDayAttendance(anyObject(), anyObject())
    }

    @Test
    fun `processAdmsAttendanceLogs parses key-value formatted ZKTeco line`() {
        val sn = "ZK-TEST-002"
        val device = BiometricDevice(
            name = "Test Terminal 2",
            serialNumber = sn,
            vendor = BiometricDeviceVendor.ZKTECO,
        )
        `when`(deviceRepository.findBySerialNumber(sn)).thenReturn(Optional.of(device))

        `when`(punchIngestionService.ingestPunch(anyObject())).thenReturn(
            PunchIngestResult(
                id = UUID.randomUUID(),
                employeeId = employeeId,
                punchedAt = Instant.now(),
                punchType = PunchType.OUT,
                isDuplicate = false,
                statusMessage = "Success",
            )
        )

        val rawBody = "PIN=LK010\tTIME=2026-09-11 17:35:00\tSTATUS=1\tVERIFY=15"

        val count = service.processAdmsAttendanceLogs(sn, rawBody)
        assertThat(count).isEqualTo(1)
        assertThat(device.totalPunchesLogged).isEqualTo(1)
    }

    @Test
    fun `simulateHardwarePunch correctly processes test clock event`() {
        val sn = "ZK-SIM-001"
        val device = BiometricDevice(
            name = "Sim Terminal",
            serialNumber = sn,
            vendor = BiometricDeviceVendor.ZKTECO,
        )
        `when`(deviceRepository.findBySerialNumber(sn)).thenReturn(Optional.of(device))

        `when`(punchIngestionService.ingestPunch(anyObject())).thenReturn(
            PunchIngestResult(
                id = UUID.randomUUID(),
                employeeId = employeeId,
                punchedAt = Instant.now(),
                punchType = PunchType.IN,
                isDuplicate = false,
                statusMessage = "Success",
            )
        )

        val payload = HardwarePunchPayload(
            deviceSerialNumber = sn,
            deviceUserId = "LK010",
            punchType = "IN",
            verificationType = "FACE",
        )

        val result = service.simulateHardwarePunch(payload)
        assertThat(result.totalAccepted).isEqualTo(1)
        assertThat(result.message).contains("Simulated punch ingested successfully")
        assertThat(device.totalPunchesLogged).isEqualTo(1)
    }

    @Test
    fun `processBatchPunches handles list of hardware punches`() {
        val sn = "ZK-BATCH-001"
        val device = BiometricDevice(
            name = "Batch Terminal",
            serialNumber = sn,
            vendor = BiometricDeviceVendor.ZKTECO,
        )
        `when`(deviceRepository.findBySerialNumber(sn)).thenReturn(Optional.of(device))

        `when`(punchIngestionService.ingestPunch(anyObject())).thenReturn(
            PunchIngestResult(
                id = UUID.randomUUID(),
                employeeId = employeeId,
                punchedAt = Instant.now(),
                punchType = PunchType.IN,
                isDuplicate = false,
                statusMessage = "Success",
            )
        )

        val batch = HardwarePunchBatchPayload(
            deviceSerialNumber = sn,
            punches = listOf(
                HardwarePunchPayload(
                    deviceSerialNumber = sn,
                    deviceUserId = "LK010",
                    punchType = "IN",
                ),
                HardwarePunchPayload(
                    deviceSerialNumber = sn,
                    deviceUserId = "LK010",
                    punchType = "OUT",
                ),
            )
        )

        val res = service.processBatchPunches(batch)
        assertThat(res.totalProcessed).isEqualTo(2)
        assertThat(res.totalAccepted).isEqualTo(2)
        assertThat(device.totalPunchesLogged).isEqualTo(2)
    }
}
