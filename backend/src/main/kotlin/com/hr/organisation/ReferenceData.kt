package com.hr.organisation

import java.util.UUID

/**
 * A row in a reference taxonomy — employee category, blood group, relationship,
 * and the twenty-odd others created by `create_reference_table()` in V5.
 *
 * ## Why one type rather than twenty-seven entities
 *
 * These tables are structurally identical by construction: the migration
 * generates them all from one function, so they cannot diverge. Mapping each to
 * its own JPA entity would be roughly a thousand lines of boilerplate whose only
 * distinguishing feature is the table name, and every one of them a place to
 * forget something.
 *
 * They are also read-mostly lookup data with no behaviour and no relationships
 * worth navigating. A single type plus a table-name parameter models that
 * honestly. See [ReferenceTable] for how the table name is kept safe.
 */
data class ReferenceItem(
    val id: UUID,
    val code: String,
    val name: String,
    val description: String?,
    val sequence: Int,
    val active: Boolean,
)

/**
 * The reference tables that may be queried.
 *
 * An allow-list, not a convention. The table name is interpolated into SQL —
 * it cannot be a bind parameter, because SQL does not permit binding an
 * identifier. Accepting an arbitrary string here would be a straightforward
 * injection point, so callers name a member of this enum and the API layer
 * parses their input into one.
 *
 * Adding a taxonomy means adding it here *and* to the migration; a mismatch
 * fails the round trip in `ReferenceDataTest` rather than at runtime.
 */
enum class ReferenceTable(val tableName: String) {
    EMPLOYEE_CATEGORY("employee_category"),
    EMPLOYEE_GROUP("employee_group"),
    EMPLOYMENT_TYPE("employment_type"),
    EMPLOYEE_TITLE("employee_title"),
    STATUTORY_CLASSIFICATION("statutory_classification"),
    FUNCTION("function"),
    FUNCTIONAL_ROLE("functional_role"),
    CLASSIFICATION("classification"),
    GENDER_TYPE("gender_type"),
    MARITAL_STATUS("marital_status"),
    BLOOD_GROUP("blood_group"),
    ATTACHMENT_TYPE("attachment_type"),
    CURRENCY_TYPE("currency_type"),
    NATIONALITY("nationality"),
    RELIGION("religion"),
    RACE("race"),
    RELATIONSHIP("relationship"),
    DWELLING_TYPE("dwelling_type"),
    ROUTE("route"),
    STATION("station"),
    QUALIFICATION_TYPE("qualification_type"),
    QUALIFICATION("qualification"),
    SUBJECT("subject"),
    LANGUAGE("language"),
    MEMBERSHIP_TYPE("membership_type"),
    BARGAINING_UNIT("bargaining_unit"),
    EXTRACURRICULAR_TYPE("extracurricular_type"),
    CORPORATE_TITLE("corporate_title"),
    ;

    companion object {
        private val byApiName = entries.associateBy { it.apiName }

        /**
         * Resolves the kebab-case name used in URLs, e.g. `blood-group`.
         *
         * Returns null rather than throwing so the caller decides the status
         * code — an unknown taxonomy is a 404, not a 500.
         */
        fun fromApiName(value: String): ReferenceTable? = byApiName[value.lowercase()]
    }

    /** URL form: `employee_category` becomes `employee-category`. */
    val apiName: String get() = tableName.replace('_', '-')
}
