package com.hr.employee.internal

import com.hr.shared.api.BadRequestException
import com.hr.shared.api.FieldViolation
import com.hr.shared.api.ForbiddenException
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Applies a partial update to an [Employee], one field at a time.
 *
 * The mirror image of [EmployeeProjection]: an explicit setter per writable
 * field, with no reflective path that could apply a key nobody intended.
 *
 * ## Rejected, not ignored
 *
 * A field the caller may not write is a 403, not a silent skip. Silently
 * dropping it means the user watches the save succeed, comes back later, and
 * finds their change gone — the same failure the custom-field validator refuses
 * for unknown keys, for the same reason.
 *
 * ## All or nothing
 *
 * Every value is coerced before any is assigned, so a malformed date in the
 * fifth field does not leave the first four applied. The surrounding
 * transaction would roll back anyway, but relying on that would make this class
 * unsafe to call from anywhere else, and "safe only in the context I happened
 * to write it for" is how a helper becomes a bug later.
 *
 * ## Not every column is writable through this
 *
 * [SETTERS] deliberately omits `status`, `resignDate` and `lastWorkingDate`.
 * Those are outcomes of the joiner/leaver processes, which have their own
 * approval flows and side effects — payroll cut-off, access revocation, final
 * settlement. Letting a profile PATCH set `status = EXITED` would skip all of
 * it and leave someone paid but locked out, or the reverse.
 */
@Component
class EmployeeWriter {
    /**
     * Turns `clearFields: ["middleName"]` into `middleName: null`.
     *
     * Exists because a generated typed client cannot send a deliberate null.
     * kotlinx and Swift both encode "never set" and "explicitly null"
     * identically, so their encoders must omit nulls — otherwise a caller
     * changing one field sends a null for every other field, and the server
     * cannot tell that from an instruction to blank the record. Before this,
     * the generated Kotlin client's PATCH body carried forty explicit nulls and
     * no partial update could succeed at all.
     *
     * `clearFields` is the spelling that survives code generation. Downstream of
     * here everything is a plain null again, so [apply] and the custom-field
     * path need no knowledge of it.
     */
    fun expandClearFields(updates: Map<String, Any?>): Map<String, Any?> {
        val toClear = updates["clearFields"] ?: return updates - "clearFields"

        val collection =
            toClear as? Collection<*>
                ?: throw BadRequestException(
                    code = "MALFORMED_REQUEST",
                    message = "clearFields must be a list of field names",
                    field = "clearFields",
                )

        val keys = collection.filterIsInstance<String>()
        if (keys.size != collection.size) {
            throw BadRequestException(
                code = "MALFORMED_REQUEST",
                message = "clearFields must contain only field names",
                field = "clearFields",
            )
        }

        // A field both set and cleared in one request is two contradictory
        // instructions. Resolving it by precedence would make a save silently do
        // the opposite of what the caller intended, and which one won would
        // depend on map ordering.
        val contradictory = keys.filter { updates.containsKey(it) }
        if (contradictory.isNotEmpty()) {
            throw BadRequestException(
                code = "CONTRADICTORY_UPDATE",
                message =
                    "These fields are both set and cleared in the same request: " +
                        contradictory.sorted().joinToString(", "),
                details = mapOf("fields" to contradictory.sorted()),
            )
        }

        return (updates - "clearFields") + keys.associateWith { null }
    }

    /**
     * @param writableFields what the caller may change, from [EmployeeProjection.writableFieldsFor]
     * @return the keys that were applied
     */
    fun apply(
        employee: Employee,
        updates: Map<String, Any?>,
        writableFields: Set<String>,
    ): Set<String> {
        val unknown = updates.keys.filterNot { SETTERS.containsKey(it) }
        if (unknown.isNotEmpty()) {
            throw BadRequestException(
                code = "UNKNOWN_FIELD",
                message = "No such editable field on an employee: ${unknown.sorted().joinToString(", ")}",
                details = mapOf("fields" to unknown.sorted()),
            )
        }

        val forbidden = updates.keys.filterNot { it in writableFields }
        if (forbidden.isNotEmpty()) {
            throw ForbiddenException(
                code = "FIELD_NOT_WRITABLE",
                message = "You may not change: ${forbidden.sorted().joinToString(", ")}",
                details = mapOf("fields" to forbidden.sorted()),
            )
        }

        // Pass one: coerce everything, collecting every failure rather than
        // stopping at the first. Reporting one problem at a time turns filling a
        // form into a guessing game.
        val violations = mutableListOf<FieldViolation>()
        val pending = mutableListOf<Pair<String, (Employee) -> Unit>>()

        for ((key, raw) in updates) {
            val field = SETTERS.getValue(key)
            try {
                pending += key to field.prepare(raw)
            } catch (e: CoercionFailure) {
                violations += FieldViolation(key, e.code, e.message ?: "Invalid value", raw)
            }
        }

        if (violations.isNotEmpty()) {
            throw BadRequestException(
                code = "FIELD_VALIDATION_FAILED",
                message = "One or more fields are not valid",
                details = mapOf("violations" to violations),
            )
        }

        // Pass two: assign. Nothing here can fail.
        pending.forEach { (_, assign) -> assign(employee) }
        return pending.mapTo(mutableSetOf()) { it.first }
    }

    // ------------------------------------------------------------------------

    /**
     * A writable field: how to turn a JSON value into a typed one, and where to
     * put it. Split so that coercion can happen for every field before any
     * assignment happens for one.
     */
    private class Field<T>(
        private val coerce: (Any?) -> T,
        private val assign: (Employee, T) -> Unit,
    ) {
        fun prepare(raw: Any?): (Employee) -> Unit {
            val value = coerce(raw)
            return { employee -> assign(employee, value) }
        }
    }

    /**
     * Coercion failed for a reason the caller can act on.
     *
     * A separate type from [BadRequestException] because it names a single
     * field, and the caller of [prepare][Field.prepare] is what knows which
     * field that was and whether others also failed.
     */
    private class CoercionFailure(val code: String, message: String) : RuntimeException(message)

    companion object {
        /** Field keys this writer accepts. Exposed so tests can check it against the projection. */
        fun settableFields(): Set<String> = SETTERS.keys

        // Values arrive from JSON, so a UUID is a String and a date is a String.
        // These convert and report failure as a field violation rather than
        // letting a ClassCastException surface as a 500 — a malformed date is
        // the caller's mistake and they should be told which field.

        private fun requiredText(
            value: Any?,
            max: Int,
        ): String {
            val text = (value as? String)?.trim()
            if (text.isNullOrEmpty()) throw CoercionFailure("REQUIRED", "This field is required")
            if (text.length > max) throw CoercionFailure("MAX_LENGTH", "Must be at most $max characters")
            return text
        }

        private fun optionalText(
            value: Any?,
            max: Int,
        ): String? {
            if (value == null) return null
            val text =
                (value as? String)?.trim()
                    ?: throw CoercionFailure("WRONG_TYPE", "Expected text")
            // An empty string clears the field. Storing "" instead would make
            // "not set" and "set to nothing" two distinct states that every
            // query downstream then has to handle.
            if (text.isEmpty()) return null
            if (text.length > max) throw CoercionFailure("MAX_LENGTH", "Must be at most $max characters")
            return text
        }

        private fun optionalUuid(value: Any?): UUID? {
            if (value == null) return null
            val text = (value as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return runCatching { UUID.fromString(text) }
                .getOrElse { throw CoercionFailure("INVALID_REFERENCE", "Not a valid reference") }
        }

        private fun requiredUuid(value: Any?): UUID =
            optionalUuid(value) ?: throw CoercionFailure("REQUIRED", "This field is required")

        private fun optionalDate(value: Any?): LocalDate? {
            if (value == null) return null
            val text = (value as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return try {
                LocalDate.parse(text)
            } catch (e: DateTimeParseException) {
                throw CoercionFailure("INVALID_DATE", "Must be a date in YYYY-MM-DD form")
            }
        }

        private fun requiredDate(value: Any?): LocalDate =
            optionalDate(value) ?: throw CoercionFailure("REQUIRED", "This field is required")

        @Suppress("UNCHECKED_CAST")
        private fun address(value: Any?): MutableMap<String, Any?> {
            if (value == null) return mutableMapOf()
            val map =
                value as? Map<String, Any?>
                    ?: throw CoercionFailure("WRONG_TYPE", "Expected an address object")
            return LinkedHashMap(map)
        }

        private fun text(
            max: Int,
            assign: (Employee, String?) -> Unit,
        ) = Field({ optionalText(it, max) }, assign)

        private fun mandatoryText(
            max: Int,
            assign: (Employee, String) -> Unit,
        ) = Field({ requiredText(it, max) }, assign)

        private fun reference(assign: (Employee, UUID?) -> Unit) = Field(Companion::optionalUuid, assign)

        private fun date(assign: (Employee, LocalDate?) -> Unit) = Field(Companion::optionalDate, assign)

        /**
         * Every writable field.
         *
         * Keys match [EmployeeProjection.FIELDS] so that a single
         * `field_permission` row governs both reading and writing a field — two
         * key spaces would mean granting WRITE on `dateOfBirth` for reads and
         * `date_of_birth` for writes, and one of them would be forgotten.
         */
        private val SETTERS: Map<String, Field<*>> =
            mapOf(
                "employeeCode" to mandatoryText(64) { e, v -> e.employeeCode = v },
                "firstName" to mandatoryText(128) { e, v -> e.firstName = v },
                "middleName" to text(128) { e, v -> e.middleName = v },
                "lastName" to mandatoryText(128) { e, v -> e.lastName = v },
                "displayName" to mandatoryText(255) { e, v -> e.displayName = v },
                "preferredName" to text(128) { e, v -> e.preferredName = v },
                "photoKey" to text(512) { e, v -> e.photoKey = v },
                "titleId" to reference { e, v -> e.titleId = v },
                "dateOfBirth" to date { e, v -> e.dateOfBirth = v },
                "genderTypeId" to reference { e, v -> e.genderTypeId = v },
                "maritalStatusId" to reference { e, v -> e.maritalStatusId = v },
                "bloodGroupId" to reference { e, v -> e.bloodGroupId = v },
                "nationalityId" to reference { e, v -> e.nationalityId = v },
                "religionId" to reference { e, v -> e.religionId = v },
                "raceId" to reference { e, v -> e.raceId = v },
                "workEmail" to text(320) { e, v -> e.workEmail = v },
                "personalEmail" to text(320) { e, v -> e.personalEmail = v },
                "mobile" to text(32) { e, v -> e.mobile = v },
                "workPhone" to text(32) { e, v -> e.workPhone = v },
                "permanentAddress" to Field(Companion::address) { e, v -> e.permanentAddress = v },
                "currentAddress" to Field(Companion::address) { e, v -> e.currentAddress = v },
                "companyId" to Field(Companion::requiredUuid) { e, v -> e.companyId = v },
                "joinDate" to Field(Companion::requiredDate) { e, v -> e.joinDate = v },
                "confirmationDate" to date { e, v -> e.confirmationDate = v },
                "probationEndDate" to date { e, v -> e.probationEndDate = v },
                "employmentTypeId" to reference { e, v -> e.employmentTypeId = v },
                "employeeCategoryId" to reference { e, v -> e.employeeCategoryId = v },
                "employeeGroupId" to reference { e, v -> e.employeeGroupId = v },
                "statutoryClassificationId" to reference { e, v -> e.statutoryClassificationId = v },
                "departmentId" to reference { e, v -> e.departmentId = v },
                "designationId" to reference { e, v -> e.designationId = v },
                "salaryGradeId" to reference { e, v -> e.salaryGradeId = v },
                "corporateTitleId" to reference { e, v -> e.corporateTitleId = v },
                "locationId" to reference { e, v -> e.locationId = v },
                "costCentreId" to reference { e, v -> e.costCentreId = v },
                "functionId" to reference { e, v -> e.functionId = v },
                "supervisorId" to reference { e, v -> e.supervisorId = v },
                "dottedLineSupervisorId" to reference { e, v -> e.dottedLineSupervisorId = v },
            )
    }
}
