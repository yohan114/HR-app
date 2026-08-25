package com.hr.identity.internal

import com.hr.identity.FieldAccess
import com.hr.identity.FieldAccessContext
import com.hr.identity.FieldPermissions
import com.hr.tenancy.TenantContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves which fields a caller may see, and how.
 *
 * ## The default matters more than the rules
 *
 * Almost every field has no explicit rule, so what happens in the absence of one
 * decides the security posture of the whole feature. Neither uniform answer works:
 *
 * - **Deny everything by default** is safe and unusable. Every field of every
 *   entity would need explicit configuration before anyone could see anything,
 *   and the first thing every customer would do is grant everything to everyone
 *   to make the product work.
 *
 * - **Allow everything by default** is usable and unsafe twice over: a newly
 *   added sensitive column is exposed to everyone until somebody remembers to
 *   restrict it, and — less obvious, more damaging — an ordinary employee with
 *   no configured rules could rewrite their own join date or department.
 *
 * So the default is split three ways, and the split is the design:
 *
 * | | reading | writing |
 * |---|---|---|
 * | ordinary field | READ | only with `canManage`, or self-service on [SELF_WRITABLE] |
 * | sensitive field ([ALWAYS_SENSITIVE]) | HIDDEN — READ on your own record | never, without an explicit grant |
 *
 * Two consequences worth stating plainly. **Write is never the default.**
 * Reading is permissive because a colleague's department is not a secret;
 * writing is not, because an unintended write is silent and corrupts data that
 * later feeds payroll. And **`employee.manage` does not confer sight of
 * sensitive fields** — an HR administrator can edit a profile without being
 * shown national identity numbers, which is what lets a customer separate
 * "maintains records" from "handles identity documents".
 *
 * [ALWAYS_SENSITIVE] lives in code rather than configuration deliberately: a
 * tenant should not be able to make their employees' identity numbers
 * world-readable by clearing a row.
 */
@Service
class FieldPermissionResolver(
    private val jdbc: JdbcTemplate,
) : FieldPermissions {
    private val cache = ConcurrentHashMap<CacheKey, CachedPermissions>()

    /**
     * Explicitly granted access per field. Fields with no rule are absent.
     *
     * Callers use [accessFor], which applies the defaults above.
     */
    @Transactional(readOnly = true)
    fun explicitGrants(
        userId: UUID,
        entityType: String,
    ): Map<String, FieldAccess> {
        val key = CacheKey(TenantContext.currentId(), userId, entityType)
        cache[key]?.takeIf { it.isFresh }?.let { return it.permissions }

        val rows =
            jdbc.query(
                """
                SELECT fp.field_key, fp.access
                FROM field_permission fp
                JOIN user_role ur ON ur.role_id = fp.role_id
                WHERE ur.user_id = ?
                  AND fp.entity_type = ?
                  AND ur.valid_from <= CURRENT_DATE
                  AND (ur.valid_to IS NULL OR ur.valid_to >= CURRENT_DATE)
                """.trimIndent(),
                { rs, _ -> rs.getString("field_key") to FieldAccess.valueOf(rs.getString("access")) },
                userId,
                entityType,
            )

        // Roles are additive, so a field granted by any role is granted.
        val resolved =
            rows.groupBy({ it.first }, { it.second })
                .mapValues { (_, grants) -> grants.reduce(FieldAccess::mostPermissive) }

        cache[key] = CachedPermissions(resolved, Instant.now())
        return resolved
    }

    override fun accessFor(
        context: FieldAccessContext,
        fieldKey: String,
    ): FieldAccess {
        // An explicit grant is a deliberate decision by whoever configured the
        // role, so it wins outright — including over the sensitive-field
        // default, which exists precisely to force that decision to be made.
        explicitGrants(context.userId, context.entityType)[fieldKey]?.let { return it }

        val sensitive = isAlwaysSensitive(context.entityType, fieldKey)

        return when {
            // Checked before sensitivity: a personal email address is sensitive
            // to a colleague and routine to its owner, and someone who cannot
            // update their own phone number will phone HR to do it.
            context.subjectIsSelf && isSelfWritable(context.entityType, fieldKey) -> FieldAccess.WRITE
            context.subjectIsSelf && sensitive -> FieldAccess.READ
            sensitive -> FieldAccess.HIDDEN
            context.canManage -> FieldAccess.WRITE
            else -> FieldAccess.READ
        }
    }

    override fun readableFields(
        context: FieldAccessContext,
        candidateFields: Collection<String>,
    ): Set<String> = candidateFields.filterTo(mutableSetOf()) { accessFor(context, it).canRead }

    override fun writableFields(
        context: FieldAccessContext,
        candidateFields: Collection<String>,
    ): Set<String> = candidateFields.filterTo(mutableSetOf()) { accessFor(context, it).canWrite }

    fun evictUser(userId: UUID) {
        val tenantId = TenantContext.currentIdOrNull() ?: return
        cache.keys.removeIf { it.tenantId == tenantId && it.userId == userId }
    }

    fun evictAll() = cache.clear()

    private fun isAlwaysSensitive(
        entityType: String,
        fieldKey: String,
    ): Boolean = ALWAYS_SENSITIVE[entityType]?.contains(fieldKey) == true

    private fun isSelfWritable(
        entityType: String,
        fieldKey: String,
    ): Boolean = SELF_WRITABLE[entityType]?.contains(fieldKey) == true

    private data class CacheKey(
        val tenantId: UUID,
        val userId: UUID,
        val entityType: String,
    )

    private class CachedPermissions(
        val permissions: Map<String, FieldAccess>,
        val loadedAt: Instant,
    ) {
        val isFresh: Boolean get() = Duration.between(loadedAt, Instant.now()) < TTL
    }

    companion object {
        /**
         * Fields hidden from everyone except their owner unless explicitly granted.
         *
         * Everything here would cause real harm if shown to the wrong colleague:
         * pay, identity documents, bank details, home address, date of birth.
         *
         * Add a field here in the same change that adds the column. Until a role
         * is granted access it simply does not appear in any response, which is
         * the correct behaviour for a column nobody has yet decided who should
         * see. Getting this wrong in the other direction — forgetting to add a
         * new sensitive column — is a silent leak with no expiry.
         */
        val ALWAYS_SENSITIVE: Map<String, Set<String>> =
            mapOf(
                "employee" to
                    setOf(
                        "nationalId",
                        "nationalIdEnc",
                        "dateOfBirth",
                        "personalEmail",
                        "permanentAddress",
                        "currentAddress",
                        "maritalStatusId",
                        "religionId",
                        "raceId",
                        "bloodGroupId",
                        "salaryGradeId",
                        "basicSalary",
                        "resignDate",
                        "lastWorkingDate",
                    ),
                "employeeBankAccount" to setOf("accountNo", "accountNoEnc", "bankId", "branchId"),
                "employeeDocument" to setOf("docNumber", "docNumberEnc"),
            )

        /**
         * Fields a person may change on their own record without any grant.
         *
         * Deliberately short. Everything here is something the employee is the
         * authoritative source for and that carries no statutory consequence —
         * how to reach them, what to call them, what they look like.
         *
         * Notably absent: name, date of birth, national identity number, join
         * date. Those appear on statutory filings and payslips, so changing one
         * is a request with evidence attached, not a text field. Letting someone
         * edit their own name directly would also make the audit trail of who
         * approved a change meaningless.
         */
        val SELF_WRITABLE: Map<String, Set<String>> =
            mapOf(
                "employee" to
                    setOf(
                        "preferredName",
                        "personalEmail",
                        "mobile",
                        "currentAddress",
                        "photoKey",
                        "bloodGroupId",
                    ),
            )

        /**
         * Short TTL, same reasoning as [PermissionResolver]: explicit eviction
         * handles the common case, and this bounds how long a *withdrawn* grant
         * can linger when something is missed.
         */
        private val TTL: Duration = Duration.ofSeconds(60)
    }
}
