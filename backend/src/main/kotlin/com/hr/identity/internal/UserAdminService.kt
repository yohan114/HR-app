package com.hr.identity.internal

import com.hr.shared.api.ConflictException
import com.hr.shared.api.ErrorCode
import com.hr.shared.api.NotFoundException
import com.hr.tenancy.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Service
class UserAdminService(
    private val appUserRepository: AppUserRepository,
    private val deviceRepository: UserDeviceRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val permissionResolver: PermissionResolver,
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    @Transactional(readOnly = true)
    fun listUsers(
        q: String? = null,
        status: String? = null,
        role: String? = null,
    ): List<UserSummaryDto> {
        val tenantId = TenantContext.currentId()
        val allUsers = jdbc.query(
            """
            SELECT u.id, u.username, u.email, u.status, u.mfa_enabled, u.last_login_at,
                   u.created_at, u.must_change_password, u.locked_until,
                   e.employee_code, e.first_name, e.last_name, e.display_name,
                   r.key as role_key
            FROM app_user u
            LEFT JOIN employee e ON e.id = u.employee_id
            LEFT JOIN user_role ur ON ur.user_id = u.id AND ur.valid_from <= CURRENT_DATE
                                  AND (ur.valid_to IS NULL OR ur.valid_to >= CURRENT_DATE)
            LEFT JOIN role r ON r.id = ur.role_id
            WHERE u.tenant_id = ?
            ORDER BY u.created_at DESC
            """,
            { rs, _ ->
                val lockedUntil = rs.getTimestamp("locked_until")?.toInstant()
                val isLocked = lockedUntil != null && lockedUntil.isAfter(Instant.now())
                val rawStatus = rs.getString("status")
                val effectiveStatus = if (isLocked) "LOCKED" else rawStatus

                val firstName = rs.getString("first_name")
                val lastName = rs.getString("last_name")
                val displayName = rs.getString("display_name")
                val fullName = displayName ?: listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { null }

                UserSummaryDto(
                    id = rs.getObject("id", UUID::class.java),
                    username = rs.getString("username"),
                    email = rs.getString("email"),
                    employeeCode = rs.getString("employee_code"),
                    employeeName = fullName,
                    role = rs.getString("role_key") ?: "EMPLOYEE",
                    status = effectiveStatus,
                    mfaEnabled = rs.getBoolean("mfa_enabled"),
                    lastLoginAt = rs.getTimestamp("last_login_at")?.toInstant(),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    mustChangePassword = rs.getBoolean("must_change_password"),
                )
            },
            tenantId,
        )

        return allUsers.filter { user ->
            val matchesQ = q.isNullOrBlank() ||
                user.username.contains(q, ignoreCase = true) ||
                (user.email?.contains(q, ignoreCase = true) == true) ||
                (user.employeeName?.contains(q, ignoreCase = true) == true) ||
                (user.employeeCode?.contains(q, ignoreCase = true) == true)
            val matchesStatus = status.isNullOrBlank() || user.status.equals(status, ignoreCase = true)
            val matchesRole = role.isNullOrBlank() || user.role.equals(role, ignoreCase = true)
            matchesQ && matchesStatus && matchesRole
        }
    }

    @Transactional(readOnly = true)
    fun getUser(id: UUID): UserSummaryDto {
        val tenantId = TenantContext.currentId()
        return jdbc.query(
            """
            SELECT u.id, u.username, u.email, u.status, u.mfa_enabled, u.last_login_at,
                   u.created_at, u.must_change_password, u.locked_until,
                   e.employee_code, e.first_name, e.last_name, e.display_name,
                   r.key as role_key
            FROM app_user u
            LEFT JOIN employee e ON e.id = u.employee_id
            LEFT JOIN user_role ur ON ur.user_id = u.id AND ur.valid_from <= CURRENT_DATE
                                  AND (ur.valid_to IS NULL OR ur.valid_to >= CURRENT_DATE)
            LEFT JOIN role r ON r.id = ur.role_id
            WHERE u.tenant_id = ? AND u.id = ?
            """,
            { rs, _ ->
                val lockedUntil = rs.getTimestamp("locked_until")?.toInstant()
                val isLocked = lockedUntil != null && lockedUntil.isAfter(Instant.now())
                val rawStatus = rs.getString("status")
                val effectiveStatus = if (isLocked) "LOCKED" else rawStatus

                val firstName = rs.getString("first_name")
                val lastName = rs.getString("last_name")
                val displayName = rs.getString("display_name")
                val fullName = displayName ?: listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { null }

                UserSummaryDto(
                    id = rs.getObject("id", UUID::class.java),
                    username = rs.getString("username"),
                    email = rs.getString("email"),
                    employeeCode = rs.getString("employee_code"),
                    employeeName = fullName,
                    role = rs.getString("role_key") ?: "EMPLOYEE",
                    status = effectiveStatus,
                    mfaEnabled = rs.getBoolean("mfa_enabled"),
                    lastLoginAt = rs.getTimestamp("last_login_at")?.toInstant(),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    mustChangePassword = rs.getBoolean("must_change_password"),
                )
            },
            tenantId,
            id,
        ).firstOrNull() ?: throw NotFoundException(ErrorCode.NOT_FOUND, "User not found: $id")
    }

    @Transactional
    fun createUser(payload: CreateUserPayloadDto): UserSummaryDto {
        val tenantId = TenantContext.currentId()
        val username = payload.username.trim()
        val existing = appUserRepository.findByUsername(username)
        if (existing != null) {
            throw ConflictException(ErrorCode.CONFLICT, "Username '$username' is already taken")
        }

        val employeeId = payload.employeeCode?.trim()?.takeIf { it.isNotBlank() }?.let { code ->
            jdbc.query(
                "SELECT id FROM employee WHERE employee_code = ? AND tenant_id = ?",
                { rs, _ -> rs.getObject("id", UUID::class.java) },
                code,
                tenantId,
            ).firstOrNull()
        }

        val initialPassword = payload.temporaryPassword?.takeIf { it.isNotBlank() } ?: generateTemporaryPassword()
        val encodedHash = passwordEncoder.encode(initialPassword)

        val user = AppUser(
            username = username,
            email = payload.email?.trim()?.takeIf { it.isNotBlank() },
            passwordHash = encodedHash,
            employeeId = employeeId,
            status = UserStatus.ACTIVE,
        ).apply {
            mustChangePassword = payload.mustChangePassword
            passwordChangedAt = Instant.now()
        }

        val saved = appUserRepository.save(user)

        // Assign role
        val roleKey = payload.role.trim().uppercase()
        val roleId = jdbc.query(
            "SELECT id FROM role WHERE tenant_id = ? AND key = ?",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            tenantId,
            roleKey,
        ).firstOrNull()

        if (roleId != null) {
            jdbc.update(
                """
                INSERT INTO user_role (tenant_id, user_id, role_id, valid_from)
                VALUES (?, ?, ?, CURRENT_DATE)
                ON CONFLICT (user_id, role_id) DO NOTHING
                """,
                tenantId,
                saved.id,
                roleId,
            )
        }

        log.info("Created user {} ({}) in tenant {}", username, saved.id, tenantId)
        return getUser(saved.id)
    }

    @Transactional
    fun updateUser(id: UUID, payload: UpdateUserPayloadDto): UserSummaryDto {
        val tenantId = TenantContext.currentId()
        val user = appUserRepository.findById(id).orElse(null)
            ?: throw NotFoundException(ErrorCode.NOT_FOUND, "User not found: $id")

        payload.status?.let { newStatus ->
            when (newStatus.uppercase()) {
                "ACTIVE" -> {
                    user.status = UserStatus.ACTIVE
                    user.lockedUntil = null
                    user.failedAttempts = 0
                }
                "DISABLED" -> user.status = UserStatus.DISABLED
                "LOCKED" -> user.lockedUntil = Instant.now().plusSeconds(86400 * 365) // 1 year
                "PENDING_MFA", "PENDING_ACTIVATION" -> user.status = UserStatus.PENDING_ACTIVATION
            }
        }
        appUserRepository.save(user)

        payload.role?.let { roleName ->
            val roleKey = roleName.trim().uppercase()
            val roleId = jdbc.query(
                "SELECT id FROM role WHERE tenant_id = ? AND key = ?",
                { rs, _ -> rs.getObject("id", UUID::class.java) },
                tenantId,
                roleKey,
            ).firstOrNull()

            if (roleId != null) {
                jdbc.update("DELETE FROM user_role WHERE tenant_id = ? AND user_id = ?", tenantId, id)
                jdbc.update(
                    """
                    INSERT INTO user_role (tenant_id, user_id, role_id, valid_from)
                    VALUES (?, ?, ?, CURRENT_DATE)
                    ON CONFLICT (user_id, role_id) DO NOTHING
                    """,
                    tenantId,
                    id,
                    roleId,
                )
            }
        }

        permissionResolver.evictUser(id)
        return getUser(id)
    }

    @Transactional
    fun resetPassword(id: UUID, payload: ResetPasswordPayloadDto): ResetPasswordResponseDto {
        val user = appUserRepository.findById(id).orElse(null)
            ?: throw NotFoundException(ErrorCode.NOT_FOUND, "User not found: $id")

        val newPassword = payload.temporaryPassword?.takeIf { it.isNotBlank() } ?: generateTemporaryPassword()
        user.passwordHash = passwordEncoder.encode(newPassword)
        user.mustChangePassword = payload.mustChangePassword ?: true
        user.passwordChangedAt = Instant.now()
        user.failedAttempts = 0
        user.lockedUntil = null
        appUserRepository.save(user)

        refreshTokenRepository.revokeAllForUser(id, "PASSWORD_RESET", Instant.now())
        log.info("Reset password for user {}", id)
        return ResetPasswordResponseDto(success = true, message = "Password reset successfully")
    }

    @Transactional(readOnly = true)
    fun listDevices(userId: UUID): List<UserDeviceSummaryDto> {
        val devices = deviceRepository.findActiveByUserId(userId)
        return devices.map { device ->
            UserDeviceSummaryDto(
                id = device.id,
                deviceType = device.platform.name,
                name = device.model ?: device.deviceId,
                ipAddress = null,
                lastSeenAt = device.lastSeenAt,
                isCurrent = false,
                mfaEnrolled = device.biometricEnrolled,
            )
        }
    }

    @Transactional
    fun revokeDevice(userId: UUID, deviceId: UUID): Boolean {
        val device = deviceRepository.findById(deviceId).orElse(null)
            ?: throw NotFoundException(ErrorCode.NOT_FOUND, "Device not found: $deviceId")
        if (device.userId != userId) {
            throw NotFoundException(ErrorCode.NOT_FOUND, "Device not found: $deviceId")
        }
        device.revoke(RevocationReason.ADMIN_REVOKED)
        deviceRepository.save(device)
        refreshTokenRepository.revokeAllForDevice(device.id, RevocationReason.DEVICE_REVOKED, Instant.now())
        return true
    }

    @Transactional
    fun revokeAllDevices(userId: UUID): Int {
        val active = deviceRepository.findActiveByUserId(userId)
        active.forEach { d ->
            d.revoke(RevocationReason.ADMIN_REVOKED)
            deviceRepository.save(d)
        }
        refreshTokenRepository.revokeAllForUser(userId, RevocationReason.ADMIN_REVOKED, Instant.now())
        return active.size
    }

    private fun generateTemporaryPassword(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%"
        return (1..14).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}
