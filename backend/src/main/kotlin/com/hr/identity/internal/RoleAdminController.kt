package com.hr.identity.internal

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/v1")
class RoleAdminController(
    private val service: RoleAdminService,
) {
    @GetMapping("/roles")
    fun listRoles(): RoleListResponseDto =
        RoleListResponseDto(service.listRoles())

    @GetMapping("/roles/{id}")
    fun getRole(
        @PathVariable id: UUID,
    ): RoleSummaryDto = service.getRole(id)

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRole(
        @RequestBody payload: CreateRolePayloadDto,
    ): RoleSummaryDto = service.createRole(payload)

    @PutMapping("/roles/{id}")
    fun updateRole(
        @PathVariable id: UUID,
        @RequestBody payload: UpdateRolePayloadDto,
    ): RoleSummaryDto = service.updateRole(id, payload)

    @DeleteMapping("/roles/{id}")
    fun deleteRole(
        @PathVariable id: UUID,
    ): RoleActionSuccessDto = service.deleteRole(id)

    @GetMapping("/permissions")
    fun listPermissions(): PermissionListResponseDto =
        PermissionListResponseDto(service.listPermissions())
}
