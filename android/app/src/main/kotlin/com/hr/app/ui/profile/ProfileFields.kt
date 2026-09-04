package com.hr.app.ui.profile

import com.hr.client.model.EmployeeProfile

/**
 * Reads a profile field by the key the form schema names it.
 *
 * ## Why this exists rather than reflection
 *
 * The web console renders a profile from an untyped map, so "key absent" and "value null" stay
 * distinguishable — which is the distinction ADR 0006 turns on: a field the caller may not see is
 * **omitted**, not nulled.
 *
 * The generated Kotlin model erases that. Every property is nullable, so `dateOfBirth == null`
 * could mean "you are not allowed this" or "nobody has filled it in", and the UI cannot tell.
 * Rendering straight from the model would therefore show an empty row for a field the server
 * deliberately withheld — disclosing that it exists, and telling the user nothing useful.
 *
 * The fix is to take the *field list* from the form schema, which already omits what the caller
 * cannot see, and use this only to fetch the value of a field we have already been told to draw.
 * An unknown key returns null and the row renders as "not set", which is correct: the schema said
 * to show it, so the caller is permitted, so null genuinely means unset.
 *
 * Explicit rather than reflective on purpose. A reflective lookup would silently start exposing any
 * property added to the generated model, whereas a key missing from here fails visibly as a blank
 * value on a field somebody deliberately put in the schema.
 */
internal fun EmployeeProfile.valueFor(key: String): String? =
    when (key) {
        "employeeCode" -> employeeCode
        "status" -> status?.value
        "displayName" -> displayName
        "firstName" -> firstName
        "middleName" -> middleName
        "lastName" -> lastName
        "preferredName" -> preferredName
        "dateOfBirth" -> dateOfBirth?.toString()
        "workEmail" -> workEmail
        "personalEmail" -> personalEmail
        "mobile" -> mobile
        "workPhone" -> workPhone
        "joinDate" -> joinDate?.toString()
        "confirmationDate" -> confirmationDate?.toString()
        "probationEndDate" -> probationEndDate?.toString()
        "resignDate" -> resignDate?.toString()
        "lastWorkingDate" -> lastWorkingDate?.toString()
        "yearsOfService" -> yearsOfService?.toString()

        // Reference and employee fields hold a uuid, and a uuid is not something to show a person.
        // They render as "not set" until the pickers that resolve them to names land — which is
        // less wrong than printing `a3f1…` under a heading that says "Department".
        "titleId", "genderTypeId", "maritalStatusId", "bloodGroupId", "nationalityId",
        "religionId", "raceId", "companyId", "employmentTypeId", "employeeCategoryId",
        "employeeGroupId", "statutoryClassificationId", "departmentId", "designationId",
        "salaryGradeId", "corporateTitleId", "locationId", "costCentreId", "functionId",
        "supervisorId", "dottedLineSupervisorId",
        -> null

        // Addresses are free-form objects; a custom field lives in the JSONB bag. Both need a
        // renderer of their own rather than being flattened into a line of text.
        "permanentAddress", "currentAddress" -> null

        else -> customFields?.get(key)?.toString()
    }
