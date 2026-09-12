package com.hr.identity.internal

import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/v1/users")
@PreAuthorize("hasAnyAuthority('identity.user.view', 'identity.user.manage', 'ADMIN', 'ROLE_ADMIN', 'ROLE_HR_ADMIN')")
class UserAdminController(
    private val service: UserAdminService,
) {
    @GetMapping
    fun listUsers(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) role: String?,
    ): UserListResponseDto =
        UserListResponseDto(service.listUsers(q = q, status = status, role = role))

    @GetMapping("/{id}")
    fun getUser(
        @PathVariable id: UUID,
    ): UserSummaryDto = service.getUser(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(
        @RequestBody payload: CreateUserPayloadDto,
    ): UserSummaryDto = service.createUser(payload)

    @PatchMapping("/{id}")
    fun updateUser(
        @PathVariable id: UUID,
        @RequestBody payload: UpdateUserPayloadDto,
    ): UserSummaryDto = service.updateUser(id, payload)

    @PostMapping("/{id}/reset-password")
    fun resetPassword(
        @PathVariable id: UUID,
        @RequestBody(required = false) payload: ResetPasswordPayloadDto?,
    ): ResetPasswordResponseDto =
        service.resetPassword(id, payload ?: ResetPasswordPayloadDto())

    @GetMapping("/{userId}/devices")
    fun listDevices(
        @PathVariable userId: UUID,
    ): UserDeviceListResponseDto =
        UserDeviceListResponseDto(service.listDevices(userId))

    @DeleteMapping("/{userId}/devices/{deviceId}")
    fun revokeDevice(
        @PathVariable userId: UUID,
        @PathVariable deviceId: UUID,
    ): RevokeDeviceResponseDto =
        RevokeDeviceResponseDto(service.revokeDevice(userId, deviceId))

    @PostMapping("/{userId}/devices/revoke-all")
    fun revokeAllDevices(
        @PathVariable userId: UUID,
    ): RevokeAllDevicesResponseDto {
        val count = service.revokeAllDevices(userId)
        return RevokeAllDevicesResponseDto(success = true, revokedCount = count)
    }
}
