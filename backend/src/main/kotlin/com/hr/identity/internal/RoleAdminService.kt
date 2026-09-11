package com.hr.identity.internal

import com.hr.shared.api.BadRequestException
import com.hr.shared.api.ConflictException
import com.hr.shared.api.ErrorCode
import com.hr.shared.api.NotFoundException
import com.hr.tenancy.TenantContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RoleAdminService(
    private val jdbc: JdbcTemplate,
    private val permissionResolver: PermissionResolver,
) {
    @Transactional(readOnly = true)
    fun listRoles(): List<RoleSummaryDto> {
        val tenantId = TenantContext.currentId()
        val roles = jdbc.query(
            """
            SELECT r.id, r.key, r.name, r.description, r.is_system,
                   COUNT(DISTINCT ur.user_id) as assigned_count
            FROM role r
            LEFT JOIN user_role ur ON ur.role_id = r.id
            WHERE r.tenant_id = ?
            GROUP BY r.id, r.key, r.name, r.description, r.is_system
            ORDER BY r.is_system DESC, r.name ASC
            """,
            { rs, _ ->
                RoleRow(
                    id = rs.getObject("id", UUID::class.java),
                    key = rs.getString("key"),
                    name = rs.getString("name"),
                    description = rs.getString("description") ?: "",
                    isSystem = rs.getBoolean("is_system"),
                    assignedCount = rs.getInt("assigned_count"),
                )
            },
            tenantId,
        )

        val permissionsByRole = getPermissionsByRoleForTenant(tenantId)

        return roles.map { role ->
            RoleSummaryDto(
                id = role.id,
                code = role.key,
                name = role.name,
                description = role.description,
                isSystem = role.isSystem,
                permissions = permissionsByRole[role.id] ?: emptyList(),
                assignedUserCount = role.assignedCount,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getRole(id: UUID): RoleSummaryDto {
        val tenantId = TenantContext.currentId()
        val role = jdbc.query(
            """
            SELECT r.id, r.key, r.name, r.description, r.is_system,
                   COUNT(DISTINCT ur.user_id) as assigned_count
            FROM role r
            LEFT JOIN user_role ur ON ur.role_id = r.id
            WHERE r.tenant_id = ? AND r.id = ?
            GROUP BY r.id, r.key, r.name, r.description, r.is_system
            """,
            { rs, _ ->
                RoleRow(
                    id = rs.getObject("id", UUID::class.java),
                    key = rs.getString("key"),
                    name = rs.getString("name"),
                    description = rs.getString("description") ?: "",
                    isSystem = rs.getBoolean("is_system"),
                    assignedCount = rs.getInt("assigned_count"),
                )
            },
            tenantId,
            id,
        ).firstOrNull() ?: throw NotFoundException(ErrorCode.NOT_FOUND, "Role not found: $id")

        val permissions = getPermissionsForRole(id)
        return RoleSummaryDto(
            id = role.id,
            code = role.key,
            name = role.name,
            description = role.description,
            isSystem = role.isSystem,
            permissions = permissions,
            assignedUserCount = role.assignedCount,
        )
    }

    @Transactional
    fun createRole(payload: CreateRolePayloadDto): RoleSummaryDto {
        val tenantId = TenantContext.currentId()
        val key = payload.code.trim().uppercase()

        val existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM role WHERE tenant_id = ? AND key = ?",
            Int::class.java,
            tenantId,
            key,
        ) ?: 0
        if (existing > 0) {
            throw ConflictException(ErrorCode.CONFLICT, "Role with code '$key' already exists")
        }

        val roleId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO role (id, tenant_id, key, name, description, is_system, created_at, updated_at, version)
            VALUES (?, ?, ?, ?, ?, false, now(), now(), 0)
            """,
            roleId,
            tenantId,
            key,
            payload.name.trim(),
            payload.description.trim(),
        )

        payload.permissions.distinct().forEach { permKey ->
            jdbc.update(
                """
                INSERT INTO role_permission (tenant_id, role_id, permission_key)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                tenantId,
                roleId,
                permKey,
            )
        }

        permissionResolver.evictAll()
        return getRole(roleId)
    }

    @Transactional
    fun updateRole(id: UUID, payload: UpdateRolePayloadDto): RoleSummaryDto {
        val tenantId = TenantContext.currentId()
        val role = getRole(id)

        val updatedName = payload.name?.trim()?.ifBlank { null } ?: role.name
        val updatedDesc = payload.description?.trim() ?: role.description

        jdbc.update(
            """
            UPDATE role
               SET name = ?, description = ?, updated_at = now()
             WHERE tenant_id = ? AND id = ?
            """,
            updatedName,
            updatedDesc,
            tenantId,
            id,
        )

        if (payload.permissions != null) {
            jdbc.update("DELETE FROM role_permission WHERE tenant_id = ? AND role_id = ?", tenantId, id)
            payload.permissions.distinct().forEach { permKey ->
                jdbc.update(
                    """
                    INSERT INTO role_permission (tenant_id, role_id, permission_key)
                    VALUES (?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """,
                    tenantId,
                    id,
                    permKey,
                )
            }
        }

        permissionResolver.evictAll()
        return getRole(id)
    }

    @Transactional
    fun deleteRole(id: UUID): RoleActionSuccessDto {
        val tenantId = TenantContext.currentId()
        val role = getRole(id)
        if (role.isSystem) {
            throw BadRequestException(ErrorCode.BUSINESS_RULE_VIOLATION, "System roles cannot be deleted")
        }
        if (role.assignedUserCount > 0) {
            throw BadRequestException(
                ErrorCode.BUSINESS_RULE_VIOLATION,
                "Cannot delete role assigned to ${role.assignedUserCount} users",
            )
        }

        jdbc.update("DELETE FROM role_permission WHERE tenant_id = ? AND role_id = ?", tenantId, id)
        jdbc.update("DELETE FROM role WHERE tenant_id = ? AND id = ?", tenantId, id)
        permissionResolver.evictAll()
        return RoleActionSuccessDto(success = true)
    }

    @Transactional(readOnly = true)
    fun listPermissions(): List<PermissionItemDto> =
        jdbc.query(
            "SELECT key, module, description FROM permission ORDER BY module ASC, key ASC",
            { rs, _ ->
                val key = rs.getString("key")
                val module = rs.getString("module")
                val desc = rs.getString("description")
                PermissionItemDto(
                    key = key,
                    domain = module,
                    label = formatPermissionLabel(key),
                    description = desc,
                )
            },
        )

    private fun formatPermissionLabel(key: String): String {
        val parts = key.split('.')
        val action = parts.getOrNull(1) ?: parts[0]
        val entity = parts.getOrNull(0) ?: ""
        return "${action.replace('_', ' ').replaceFirstChar { it.uppercase() }} ${entity.replace('_', ' ').replaceFirstChar { it.uppercase() }}"
    }

    private fun getPermissionsByRoleForTenant(tenantId: UUID): Map<UUID, List<String>> {
        val map = mutableMapOf<UUID, MutableList<String>>()
        jdbc.query(
            "SELECT role_id, permission_key FROM role_permission WHERE tenant_id = ? ORDER BY permission_key ASC",
            { rs ->
                val roleId = rs.getObject("role_id", UUID::class.java)
                val permKey = rs.getString("permission_key")
                map.computeIfAbsent(roleId) { mutableListOf() }.add(permKey)
            },
            tenantId,
        )
        return map
    }

    private fun getPermissionsForRole(roleId: UUID): List<String> =
        jdbc.queryForList(
            "SELECT permission_key FROM role_permission WHERE role_id = ? ORDER BY permission_key ASC",
            String::class.java,
            roleId,
        )

    private data class RoleRow(
        val id: UUID,
        val key: String,
        val name: String,
        val description: String,
        val isSystem: Boolean,
        val assignedCount: Int,
    )
}
