package com.hr.employee.internal

import com.hr.config.forms.CustomFields
import com.hr.config.forms.FormSchema
import com.hr.identity.Caller
import com.hr.identity.FieldAccessContext
import com.hr.shared.api.ForbiddenException
import com.hr.shared.api.NotFoundException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Reading and updating employee records.
 *
 * Two authorisation questions are answered here, and keeping them separate is
 * the point:
 *
 * 1. **May this caller see this record at all?** — [assertVisible]. Answered by
 *    ownership, then by permission plus reporting line.
 * 2. **Which of its fields?** — delegated to `FieldPermissions` via
 *    [EmployeeProjection].
 *
 * Collapsing them would mean either a manager sees every field of their team's
 * records, or an HR administrator with field grants can enumerate the whole
 * company. Both are wrong, and only the second is obvious.
 */
@Service
class EmployeeService(
    private val employees: EmployeeRepository,
    private val hierarchy: EmployeeHierarchyRepository,
    private val projection: EmployeeProjection,
    private val writer: EmployeeWriter,
    private val customFields: CustomFields,
) {
    @Transactional(readOnly = true)
    fun profile(
        caller: Caller,
        employeeId: UUID,
    ): Map<String, Any?> {
        val employee = load(employeeId)
        assertVisible(caller, employee)
        return projection.project(employee, contextFor(caller, employee))
    }

    /**
     * The form to render for editing this employee, with permissions applied.
     *
     * Built from the same context as the payload, so what the client is offered
     * and what the server will accept cannot drift apart. Deriving the form
     * separately is how you end up with an input that saves nothing.
     */
    @Transactional(readOnly = true)
    fun editForm(
        caller: Caller,
        employeeId: UUID,
        locale: String,
    ): FormSchema {
        val employee = load(employeeId)
        assertVisible(caller, employee)

        val context = contextFor(caller, employee)
        val customKeys = customFields.activeFieldKeys(EmployeeProjection.ENTITY_TYPE)

        return customFields.schemaFor(
            entityType = EmployeeProjection.ENTITY_TYPE,
            locale = locale,
            visibleFields = projection.readableFieldsFor(context, customKeys),
            editableFields = projection.writableFieldsFor(context, customKeys),
        )
    }

    /**
     * Applies a partial update.
     *
     * @param expectedVersion the `version` the client last read. Supplied, it
     *   makes the update fail rather than overwrite a concurrent change — two
     *   HR officers editing the same profile is common, and last-write-wins
     *   loses one of them silently.
     */
    @Transactional
    fun update(
        caller: Caller,
        employeeId: UUID,
        updates: Map<String, Any?>,
        expectedVersion: Long?,
    ): Map<String, Any?> {
        val employee = load(employeeId)
        assertVisible(caller, employee)

        if (expectedVersion != null && employee.version != expectedVersion) {
            throw com.hr.shared.api.ConflictException(
                code = "STALE_VERSION",
                message = "This record has changed since you loaded it",
                details = mapOf("expected" to expectedVersion, "actual" to employee.version),
            )
        }

        val context = contextFor(caller, employee)
        val customKeys = customFields.activeFieldKeys(EmployeeProjection.ENTITY_TYPE)
        val writable = projection.writableFieldsFor(context, customKeys)

        // A `customFields` that is not an object is a malformed request, not an absent one.
        // Reading it with `as?` and carrying on meant a string or an array there was dropped and
        // the save answered 200 — the caller was told their change succeeded and nothing had
        // changed.
        updates["customFields"]?.let {
            if (it !is Map<*, *>) {
                throw com.hr.shared.api.BadRequestException(
                    code = "MALFORMED_REQUEST",
                    message = "customFields must be an object",
                    field = "customFields",
                )
            }
        }

        val effectiveUpdates = writer.expandClearFields(updates, customKeys)

        @Suppress("UNCHECKED_CAST")
        val customUpdates =
            (effectiveUpdates["customFields"] as? Map<String, Any?>)
                // A blank string means "clear this", exactly as it does for a built-in text field.
                // Left as-is it bypassed every type check — the validator skips a blank value, so
                // `"  "` was stored verbatim in a slot declared NUMBER or DATE, and every consumer
                // of that JSONB column then had to defend against it.
                ?.mapValues { (_, value) -> if (value is String && value.isBlank()) null else value }

        if (customUpdates != null) {
            val forbidden = customUpdates.keys.filterNot { it in writable }
            if (forbidden.isNotEmpty()) {
                throw ForbiddenException(
                    code = "FIELD_NOT_WRITABLE",
                    message = "You may not change: ${forbidden.sorted().joinToString(", ")}",
                    details = mapOf("fields" to forbidden.sorted()),
                )
            }
            // Validated before anything is applied, so a rejected custom value
            // does not leave the built-in fields half-written.
            customFields.validate(EmployeeProjection.ENTITY_TYPE, customUpdates, partial = true)
        }

        writer.apply(employee, effectiveUpdates - "customFields", writable)

        customUpdates?.forEach { (key, value) ->
            if (value == null) employee.customFields.remove(key) else employee.customFields[key] = value
        }

        // `saveAndFlush`, not `save`. Hibernate increments `@Version` during flush, and flush
        // otherwise happens at commit — after the projection below has already read it. The
        // response would then carry the *pre*-increment version, the client would send it back as
        // `If-Match`, and the second save of every session would 409 against a record nobody else
        // had touched. Flushing here also surfaces a constraint violation as an exception from
        // this method rather than from the commit, where the request has no context left.
        val saved = employees.saveAndFlush(employee)
        return projection.project(saved, contextFor(caller, saved))
    }

    // ------------------------------------------------------------------------

    private fun load(employeeId: UUID): Employee =
        employees.findById(employeeId).orElseThrow {
            NotFoundException(message = "No such employee")
        }

    /**
     * Whether this caller may see this record at all.
     *
     * A caller who may not gets a 404, not a 403. A 403 confirms the record
     * exists, which turns this endpoint into an oracle: walk ids, and the ones
     * that answer 403 are real employees. The distinction costs nothing and the
     * caller cannot act on it either way.
     */
    private fun assertVisible(
        caller: Caller,
        employee: Employee,
    ) {
        if (caller.isSelf(employee.id)) return
        if (hasAuthority(PERMISSION_MANAGE) || hasAuthority(PERMISSION_VIEW_ALL)) return

        // "My team" means the whole subtree, not just direct reports — a
        // department head who cannot open the record of someone two levels down
        // has to ask a middle manager to do it for them.
        val viewerEmployeeId = caller.employeeId
        if (hasAuthority(PERMISSION_VIEW) &&
            viewerEmployeeId != null &&
            hierarchy.isManagerOf(viewerEmployeeId, employee.id)
        ) {
            return
        }

        throw NotFoundException(message = "No such employee")
    }

    private fun contextFor(
        caller: Caller,
        employee: Employee,
    ): FieldAccessContext =
        projection.contextFor(
            viewerUserId = caller.userId,
            viewerEmployeeId = caller.employeeId,
            employee = employee,
            canManage = hasAuthority(PERMISSION_MANAGE),
        )

    private fun hasAuthority(permission: String): Boolean =
        SecurityContextHolder.getContext().authentication
            ?.authorities
            ?.any { it.authority == permission } == true

    private companion object {
        const val PERMISSION_VIEW = "employee.view"
        const val PERMISSION_VIEW_ALL = "employee.view.all"
        const val PERMISSION_MANAGE = "employee.manage"
    }
}
