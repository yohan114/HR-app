package com.hr.employee.internal

import com.hr.identity.FieldAccess
import com.hr.identity.FieldAccessContext
import com.hr.identity.FieldMasker
import com.hr.identity.FieldPermissions
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Turns an [Employee] into a response payload, applying field permissions.
 *
 * ## Why a projection rather than a Jackson filter
 *
 * A `@JsonFilter` is the conventional answer and it has a failure mode this
 * does not: it applies to a serialisation path, and a new endpoint that returns
 * the entity by some other route — a nested object, a list wrapper, an event
 * payload — silently skips it. The leak is invisible in review because the
 * absence of an annotation looks like nothing at all.
 *
 * Here there is no entity-to-JSON path at all. The only way to get an employee
 * into a response is [project], which takes the caller's id and cannot produce
 * output without consulting permissions. Forgetting to apply the filter is not
 * a mistake you can make, because there is no unfiltered variant to reach for.
 *
 * The cost is [FIELDS] — an explicit accessor per field, which must be extended
 * when a column is added. That is a real maintenance burden, and it is the point:
 * adding a column forces a decision about who may see it, at the moment the
 * person adding it has the context to decide. A reflective projection would
 * expose the new column to everyone by default and nobody would notice.
 */
@Component
class EmployeeProjection(
    private val fieldPermissions: FieldPermissions,
) {
    /**
     * The employee as this caller is allowed to see it.
     *
     * `HIDDEN` fields are absent from the map — not null. A null would tell the
     * caller the field exists and they are not allowed it, and would make a
     * client render a disabled input for something they should not know about.
     */
    fun project(
        employee: Employee,
        context: FieldAccessContext,
    ): Map<String, Any?> {
        val payload = LinkedHashMap<String, Any?>(FIELDS.size + 8)

        // Identity of the record itself is never permission-controlled: without
        // it the payload cannot be addressed, cached or updated. It is also not
        // sensitive — you already had to be allowed to fetch this employee.
        payload["id"] = employee.id
        payload["version"] = employee.version

        for ((key, accessor) in FIELDS) {
            when (fieldPermissions.accessFor(context, key)) {
                FieldAccess.HIDDEN -> Unit
                FieldAccess.MASKED -> {
                    // A built-in field has a declared wire type. Masking a date or a uuid would
                    // put `••••` where the client expects a `LocalDate` or `UUID`, and kotlinx
                    // aborts the whole response on that — one masked date would blank the entire
                    // profile on Android and iOS. Where the mask cannot fit the type, the field is
                    // hidden instead: strictly more restrictive, and a fully-masked date conveyed
                    // nothing beyond its own existence anyway.
                    val value = accessor(employee)
                    if (FieldMasker.canMask(value)) payload[key] = FieldMasker.mask(value)
                }
                FieldAccess.READ, FieldAccess.WRITE -> payload[key] = accessor(employee)
            }
        }

        // Custom fields are permission-checked individually under their own
        // keys, so a tenant's "Disciplinary notes" field is restrictable in
        // exactly the same way as a built-in one.
        val custom = LinkedHashMap<String, Any?>()
        for ((key, value) in employee.customFields) {
            when (fieldPermissions.accessFor(context, key)) {
                FieldAccess.HIDDEN -> Unit
                FieldAccess.MASKED -> custom[key] = FieldMasker.mask(value)
                FieldAccess.READ, FieldAccess.WRITE -> custom[key] = value
            }
        }
        if (custom.isNotEmpty()) payload["customFields"] = custom

        return payload
    }

    /** Which fields this caller may write. Used to reject a forbidden update. */
    fun writableFieldsFor(
        context: FieldAccessContext,
        customFieldKeys: Collection<String> = emptyList(),
    ): Set<String> = fieldPermissions.writableFields(context, FIELDS.keys + customFieldKeys)

    fun readableFieldsFor(
        context: FieldAccessContext,
        customFieldKeys: Collection<String> = emptyList(),
    ): Set<String> = fieldPermissions.readableFields(context, FIELDS.keys + customFieldKeys)

    /** The context for a caller looking at [employee]. */
    fun contextFor(
        viewerUserId: UUID,
        viewerEmployeeId: UUID?,
        employee: Employee,
        canManage: Boolean,
    ): FieldAccessContext =
        FieldAccessContext(
            userId = viewerUserId,
            entityType = ENTITY_TYPE,
            // A null viewerEmployeeId means a user account with no employee
            // record — a platform operator or an integration. Never self.
            subjectIsSelf = viewerEmployeeId != null && viewerEmployeeId == employee.id,
            canManage = canManage,
        )

    companion object {
        const val ENTITY_TYPE = "employee"

        /**
         * Every readable field, keyed as the API names it.
         *
         * Ordered as a profile screen reads top to bottom, because the response
         * is a `LinkedHashMap` and clients that render fields in payload order
         * get a sensible layout for free.
         *
         * `nationalIdEnc` is absent deliberately — the ciphertext is never a
         * response value. Decryption is a separate, audited operation.
         */
        val FIELDS: Map<String, (Employee) -> Any?> =
            linkedMapOf(
                "employeeCode" to { e -> e.employeeCode },
                "status" to { e -> e.status.name },
                "displayName" to { e -> e.displayName },
                "firstName" to { e -> e.firstName },
                "middleName" to { e -> e.middleName },
                "lastName" to { e -> e.lastName },
                "preferredName" to { e -> e.preferredName },
                "photoKey" to { e -> e.photoKey },
                "titleId" to { e -> e.titleId },
                "dateOfBirth" to { e -> e.dateOfBirth },
                "genderTypeId" to { e -> e.genderTypeId },
                "maritalStatusId" to { e -> e.maritalStatusId },
                "bloodGroupId" to { e -> e.bloodGroupId },
                "nationalityId" to { e -> e.nationalityId },
                "religionId" to { e -> e.religionId },
                "raceId" to { e -> e.raceId },
                "workEmail" to { e -> e.workEmail },
                "personalEmail" to { e -> e.personalEmail },
                "mobile" to { e -> e.mobile },
                "workPhone" to { e -> e.workPhone },
                "permanentAddress" to { e -> e.permanentAddress },
                "currentAddress" to { e -> e.currentAddress },
                "companyId" to { e -> e.companyId },
                "joinDate" to { e -> e.joinDate },
                "confirmationDate" to { e -> e.confirmationDate },
                "probationEndDate" to { e -> e.probationEndDate },
                "resignDate" to { e -> e.resignDate },
                "lastWorkingDate" to { e -> e.lastWorkingDate },
                "employmentTypeId" to { e -> e.employmentTypeId },
                "employeeCategoryId" to { e -> e.employeeCategoryId },
                "employeeGroupId" to { e -> e.employeeGroupId },
                "statutoryClassificationId" to { e -> e.statutoryClassificationId },
                "departmentId" to { e -> e.departmentId },
                "designationId" to { e -> e.designationId },
                "salaryGradeId" to { e -> e.salaryGradeId },
                "corporateTitleId" to { e -> e.corporateTitleId },
                "locationId" to { e -> e.locationId },
                "costCentreId" to { e -> e.costCentreId },
                "functionId" to { e -> e.functionId },
                "supervisorId" to { e -> e.supervisorId },
                "dottedLineSupervisorId" to { e -> e.dottedLineSupervisorId },
                "yearsOfService" to { e -> e.yearsOfService() },
            )
    }
}
