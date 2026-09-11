package com.hr.attendance.internal

import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class BiometricDeviceVendor {
    ZKTECO,
    HIKVISION,
    SUPREMA,
    ANVIZ,
    GENERIC_HTTP,
}

enum class BiometricDeviceDirection {
    IN,
    OUT,
    IN_OUT,
}

enum class BiometricDeviceStatus {
    ONLINE,
    OFFLINE,
    SYNCING,
    ERROR,
}

@Entity
@Table(name = "biometric_device")
class BiometricDevice(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "serial_number", nullable = false, length = 64)
    var serialNumber: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "vendor", nullable = false, length = 32)
    var vendor: BiometricDeviceVendor = BiometricDeviceVendor.ZKTECO,

    @Column(name = "model_name", length = 64)
    var modelName: String? = null,

    @Column(name = "ip_address", length = 64)
    var ipAddress: String? = null,

    @Column(name = "port", nullable = false)
    var port: Int = 4370,

    @Column(name = "location_name", length = 100)
    var locationName: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    var direction: BiometricDeviceDirection = BiometricDeviceDirection.IN_OUT,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: BiometricDeviceStatus = BiometricDeviceStatus.ONLINE,

    @Column(name = "last_heartbeat_at")
    var lastHeartbeatAt: Instant? = null,

    @Column(name = "last_sync_at")
    var lastSyncAt: Instant? = null,

    @Column(name = "total_punches_logged", nullable = false)
    var totalPunchesLogged: Long = 0,

    @Column(name = "firmware_version", length = 64)
    var firmwareVersion: String? = null,

    @Column(name = "auth_token", length = 128)
    var authToken: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "biometric_device_command")
class BiometricDeviceCommand(
    @Column(name = "device_id", nullable = false)
    var deviceId: UUID,

    @Column(name = "command_type", nullable = false, length = 32)
    var commandType: String,

    @Column(name = "command_content", nullable = false)
    var commandContent: String,

    @Column(name = "status", nullable = false, length = 32)
    var status: String = "PENDING",

    @Column(name = "sent_at")
    var sentAt: Instant? = null,

    @Column(name = "executed_at")
    var executedAt: Instant? = null,
) : TenantScopedEntity()
