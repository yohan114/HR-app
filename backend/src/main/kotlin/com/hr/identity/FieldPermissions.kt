package com.hr.identity

import java.util.UUID

/**
 * The three facts that decide field access, stated explicitly at every call site.
 *
 * Bundled into an object rather than passed as loose parameters because
 * omitting one is the whole risk: a call that forgets [subjectIsSelf] hides an
 * employee's own date of birth from them, and a call that assumes [canManage]
 * lets them rewrite their own join date. Both are silent.
 */
data class FieldAccessContext(
    val userId: UUID,
    val entityType: String,
    /**
     * Whether the record being accessed is the caller's own.
     *
     * Self-access is authorised by ownership, not by a permission grant —
     * otherwise every employee would need a permission that reads, in effect,
     * "may view employees", and that permission would then also be the one
     * gating access to everyone else's records.
     */
    val subjectIsSelf: Boolean,
    /**
     * Whether the caller holds the entity's manage permission (`employee.manage`).
     *
     * This is the RBAC half. Field permissions narrow what a manager may touch;
     * they are not what makes someone a manager in the first place.
     */
    val canManage: Boolean,
)

/**
 * The identity module's answer to "may this person see this field?".
 *
 * A published interface rather than the resolver itself, because every other
 * module needs the answer and none of them should be able to reach into how it
 * is computed. `ModuleStructureTest` enforces that.
 *
 * ## This is half of the authorisation model, and the other half is not optional
 *
 * This answers *which fields*. It does **not** answer *may this caller see this
 * record at all* — that is `EmployeeService.assertVisible`, which checks
 * ownership, then `employee.manage` / `employee.view.all`, then the
 * reporting-line subtree. Anything reading employee data must apply **both**.
 *
 * The failure is easy to reach and does not look like one. Consider a
 * work-anniversary card gated only on `accessFor(..., "joinDate")`. `joinDate`
 * is not in [FieldPermissionResolver.ALWAYS_SENSITIVE][com.hr.identity.internal.FieldPermissionResolver],
 * so it resolves to `READ` for every colleague and the check passes — for every
 * active employee in the tenant. But `V8__employee_access.sql` grants the
 * default `EMPLOYEE` role only `employee.directory`, so those same users get a
 * 404 from `GET /v1/employees/{id}` on the very records the card just listed.
 *
 * A per-field check that passes is not evidence the caller may see the record.
 * It is only evidence that *if* they may, they may see that field of it. The
 * sensitive-field defaults do not rescue this: they protect the fields somebody
 * classified, and this leak is made of ordinary ones.
 *
 * See docs/home-composite.md §5.
 */
interface FieldPermissions {
    /** Access for one field, with defaults applied. Never returns null. */
    fun accessFor(
        context: FieldAccessContext,
        fieldKey: String,
    ): FieldAccess

    fun readableFields(
        context: FieldAccessContext,
        candidateFields: Collection<String>,
    ): Set<String>

    fun writableFields(
        context: FieldAccessContext,
        candidateFields: Collection<String>,
    ): Set<String>
}
