package com.hr.attendance.internal

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Standard ZKTeco ADMS (Automatic Data Master Server) Push Protocol Controller.
 *
 * Biometric attendance terminals configured with ADMS cloud push point to this server
 * to push real-time clock transactions, heartbeat status, and fetch server commands.
 */
@RestController
@RequestMapping("/iclock")
class ZkTecoAdmsController(
    private val biometricDeviceService: BiometricDeviceService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Handshake and terminal option negotiation.
     * ZKTeco terminals issue a GET request to /iclock/cdata?SN={serialNumber}&options=all on boot.
     */
    @GetMapping("/cdata", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun handleHandshake(
        @RequestParam(name = "SN", required = false) sn: String?,
        @RequestParam(name = "options", required = false) options: String?,
    ): ResponseEntity<String> {
        val serialNumber = sn?.trim() ?: "ZK-UNKNOWN"
        log.info("ZKTeco ADMS Handshake from serial number: $serialNumber (options: $options)")
        biometricDeviceService.registerHeartbeat(serialNumber)

        val responseBody = buildString {
            appendLine("GET OPTION FROM: $serialNumber")
            appendLine("Stamp=9999")
            appendLine("OpStamp=9999")
            appendLine("ErrorDelay=60")
            appendLine("Delay=30")
            appendLine("ResStamp=9999")
            appendLine("TransTimes=00:00;14:05")
            appendLine("TransInterval=1")
            appendLine("TransFlag=1111000000")
            appendLine("TimeZone=5.5")
            appendLine("Realtime=1")
            appendLine("Encrypt=0")
        }
        return ResponseEntity.ok(responseBody)
    }

    /**
     * Ingestion of raw attendance log transactions pushed by the terminal.
     * ZKTeco terminals POST tab-separated log records with table=ATTLOG.
     */
    @PostMapping("/cdata", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun handleAttendanceLogPush(
        @RequestParam(name = "SN", required = false) sn: String?,
        @RequestParam(name = "table", required = false) table: String?,
        @RequestBody(required = false) rawBody: String?,
    ): ResponseEntity<String> {
        val serialNumber = sn?.trim() ?: "ZK-UNKNOWN"
        val body = rawBody?.trim().orEmpty()

        if (table.equals("ATTLOG", ignoreCase = true) || body.isNotEmpty()) {
            val accepted = biometricDeviceService.processAdmsAttendanceLogs(serialNumber, body)
            log.info("ZKTeco ADMS Ingested $accepted punches from terminal $serialNumber")
            return ResponseEntity.ok("OK: $accepted")
        }

        biometricDeviceService.registerHeartbeat(serialNumber)
        return ResponseEntity.ok("OK")
    }

    /**
     * Terminal heartbeat polling and command delivery.
     * Terminals poll this every N seconds to notify server they are online and check for commands.
     */
    @GetMapping("/getrequest", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun handleGetRequest(
        @RequestParam(name = "SN", required = false) sn: String?,
    ): ResponseEntity<String> {
        val serialNumber = sn?.trim() ?: "ZK-UNKNOWN"
        biometricDeviceService.registerHeartbeat(serialNumber)
        return ResponseEntity.ok("OK")
    }

    /**
     * Terminal execution report for previously dispatched commands.
     */
    @PostMapping("/devicecmd", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun handleDeviceCommandAck(
        @RequestParam(name = "SN", required = false) sn: String?,
        @RequestBody(required = false) body: String?,
    ): ResponseEntity<String> {
        val serialNumber = sn?.trim() ?: "ZK-UNKNOWN"
        log.info("ZKTeco Command Ack from $serialNumber: ${body?.take(100)}")
        return ResponseEntity.ok("OK")
    }
}
