package com.hr.identity.internal

import java.time.Instant
import java.util.UUID

data class UserSummaryDto(
    val id: UUID,
    val username: String,
    val email: String? = null,
    val employeeCode: String? = null,
    val employeeName: String? = null,
    val role: String,
    val status: String,
    val mfaEnabled: Boolean = false,
    val lastLoginAt: Instant? = null,
    val createdAt: Instant,
    val mustChangePassword: Boolean = false,
)

data class UserListResponseDto(
    val users: List<UserSummaryDto>,
)

data class CreateUserPayloadDto(
    val username: String,
    val email: String? = null,
    val role: String = "EMPLOYEE",
    val employeeCode: String? = null,
    val mustChangePassword: Boolean = true,
    val temporaryPassword: String? = null,
)

data class UpdateUserPayloadDto(
    val status: String? = null,
    val role: String? = null,
)

data class ResetPasswordPayloadDto(
    val temporaryPassword: String? = null,
    val mustChangePassword: Boolean? = true,
)

data class ResetPasswordResponseDto(
    val success: Boolean,
    val message: String,
)

data class UserDeviceSummaryDto(
    val id: UUID,
    val deviceType: String? = null,
    val name: String? = null,
    val ipAddress: String? = null,
    val lastSeenAt: Instant? = null,
    val isCurrent: Boolean = false,
    val mfaEnrolled: Boolean = false,
)

data class UserDeviceListResponseDto(
    val devices: List<UserDeviceSummaryDto>,
)

data class RevokeDeviceResponseDto(
    val success: Boolean,
)

data class RevokeAllDevicesResponseDto(
    val success: Boolean,
    val revokedCount: Int,
)
