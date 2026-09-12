package com.hr.attendance.internal

import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class BiometricDeviceListResponseDto(
    val devices: List<BiometricDeviceDto>,
)

data class BiometricLiveStreamResponseDto(
    val punches: List<BiometricLivePunchDto>,
)

@RestController
@RequestMapping("/v1/attendance/devices")
@PreAuthorize("hasAnyAuthority('biometric.device.view', 'biometric.device.manage', 'attendance.device.view', 'attendance.device.manage')")
class BiometricDeviceAdminController(
    private val service: BiometricDeviceService,
) {
    @GetMapping
    fun listDevices(): BiometricDeviceListResponseDto =
        BiometricDeviceListResponseDto(service.listDevices())

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDevice(
        @RequestBody payload: CreateBiometricDevicePayload,
    ): BiometricDeviceDto =
        service.createDevice(payload)

    @GetMapping("/{id}")
    fun getDevice(
        @PathVariable id: UUID,
    ): BiometricDeviceDto =
        service.getDevice(id)

    @PutMapping("/{id}")
    fun updateDevice(
        @PathVariable id: UUID,
        @RequestBody payload: UpdateBiometricDevicePayload,
    ): BiometricDeviceDto =
        service.updateDevice(id, payload)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDevice(
        @PathVariable id: UUID,
    ) {
        service.deleteDevice(id)
    }

    @PostMapping("/{id}/ping")
    fun pingDevice(
        @PathVariable id: UUID,
    ): BiometricDeviceDto =
        service.pingDevice(id)

    @PostMapping("/sync")
    fun syncBatchPunches(
        @RequestBody batch: HardwarePunchBatchPayload,
    ): HardwarePunchResult =
        service.processBatchPunches(batch)

    @PostMapping("/simulate-punch")
    fun simulateHardwarePunch(
        @RequestBody payload: HardwarePunchPayload,
    ): HardwarePunchResult =
        service.simulateHardwarePunch(payload)

    @GetMapping("/live-stream")
    fun getLiveStream(
        @RequestParam(name = "limit", required = false, defaultValue = "20") limit: Int,
    ): BiometricLiveStreamResponseDto =
        BiometricLiveStreamResponseDto(service.getLiveStream(limit))
}
