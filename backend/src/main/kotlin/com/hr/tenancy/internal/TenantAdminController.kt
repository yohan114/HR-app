package com.hr.tenancy.internal

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/v1/tenants")
class TenantAdminController(
    private val service: TenantAdminService,
) {
    @GetMapping
    fun listTenants(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) plan: String?,
    ): TenantListResponseDto =
        TenantListResponseDto(service.listTenants(q = q, status = status, plan = plan))

    @GetMapping("/{id}")
    fun getTenant(
        @PathVariable id: UUID,
    ): TenantDetailDto = service.getTenant(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTenant(
        @RequestBody payload: CreateTenantPayloadDto,
    ): TenantDetailDto = service.createTenant(payload)

    @PatchMapping("/{id}")
    fun updateTenant(
        @PathVariable id: UUID,
        @RequestBody payload: UpdateTenantPayloadDto,
    ): TenantDetailDto = service.updateTenant(id, payload)

    @PostMapping("/{id}/archive")
    fun archiveTenant(
        @PathVariable id: UUID,
    ): TenantDetailDto = service.archiveTenant(id)

    @PutMapping("/{id}/modules")
    fun updateTenantModules(
        @PathVariable id: UUID,
        @RequestBody payload: UpdateTenantModulesPayloadDto,
    ): TenantModulesResponseDto =
        TenantModulesResponseDto(service.updateTenantModules(id, payload.modules))
}
