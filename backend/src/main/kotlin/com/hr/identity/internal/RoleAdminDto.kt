package com.hr.identity.internal

import java.util.UUID

data class RoleSummaryDto(
    val id: UUID,
    val code: String,
    val name: String,
    val description: String,
    val isSystem: Boolean,
    val permissions: List<String>,
    val assignedUserCount: Int = 0,
)

data class RoleListResponseDto(
    val roles: List<RoleSummaryDto>,
)

data class PermissionItemDto(
    val key: String,
    val domain: String,
    val label: String,
    val description: String,
)

data class PermissionListResponseDto(
    val permissions: List<PermissionItemDto>,
)

data class CreateRolePayloadDto(
    val name: String,
    val code: String,
    val description: String = "",
    val permissions: List<String> = emptyList(),
)

data class UpdateRolePayloadDto(
    val name: String? = null,
    val description: String? = null,
    val permissions: List<String>? = null,
)

data class RoleActionSuccessDto(
    val success: Boolean,
)
