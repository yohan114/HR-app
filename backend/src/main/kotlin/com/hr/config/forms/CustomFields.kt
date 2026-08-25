package com.hr.config.forms

/**
 * The config module's published entry point for custom field values.
 *
 * Every module that stores tenant-defined values validates through here, so the
 * rules live in one place. Exposing the repository or the definitions instead
 * would let each caller interpret `validation` slightly differently, and two
 * modules disagreeing about what `required` means is the kind of divergence
 * nobody notices until a customer reports it.
 */
interface CustomFields {
    /**
     * Validates submitted values against the entity's active definitions.
     *
     * @param partial when true, absent fields are not treated as missing — for
     *   PATCH, where the client sends only what changed.
     * @throws com.hr.shared.api.BusinessRuleException with every violation, not
     *   just the first.
     */
    fun validate(
        entityType: String,
        values: Map<String, Any?>,
        partial: Boolean = false,
    )

    /** Active field keys for an entity, for permission checks and form building. */
    fun activeFieldKeys(entityType: String): Set<String>

    /**
     * The form a client renders, with permissions already applied.
     *
     * @param visibleFields fields the caller may see; others are absent from the
     *   schema entirely. Null means unrestricted.
     * @param editableFields fields the caller may change; others arrive with
     *   `editable = false`. Null means unrestricted.
     */
    fun schemaFor(
        entityType: String,
        locale: String,
        visibleFields: Set<String>?,
        editableFields: Set<String>?,
    ): FormSchema
}
