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
