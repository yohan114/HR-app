package com.hr.config.forms

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * A form, described well enough for a client to render it without knowing what
 * the fields are.
 *
 * ## Why the server describes the form
 *
 * A tenant adds a field in the admin console and it appears on Android and iOS
 * on the next sync — no code change, no app release, no App Store review. That
 * turns "we need one extra field for this customer" from a two-week round trip
 * into a configuration change.
 *
 * The alternative — hardcoding the form and bolting a "custom fields" section
 * on the end — produces a visibly second-class experience where tenant fields
 * sit in a separate lump at the bottom, cannot be interleaved with related
 * built-in fields, and cannot be made required.
 *
 * So the schema covers the **whole** form: built-in fields and custom fields
 * together, in one ordered list of sections. A client renders what it is given.
 *
 * ## Permissions are already applied
 *
 * Fields the caller may not see are absent, not flagged. Fields they may see
 * but not change arrive with `editable: false`. The client does not filter —
 * it cannot leak what it never received.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class FormSchema(
    val entityType: String,
    /** Bumped when definitions change, so a client can skip re-rendering. */
    val version: String,
    val sections: List<FormSection>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FormSection(
    val key: String,
    val label: String,
    val fields: List<FormField>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FormField(
    val key: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    /**
     * False when the caller may see the value but not change it.
     *
     * Distinct from omitting the field: a read-only salary grade still belongs
     * on the profile, it simply is not the employee's to edit.
     */
    val editable: Boolean = true,
    val helpText: String? = null,
    val validation: FieldValidation? = null,
    val options: List<FieldOption>? = null,
    /**
     * For `REFERENCE` fields: which taxonomy supplies the options.
     *
     * The client already caches these (`GET /v1/reference`), so sending the
     * taxonomy name rather than inlining several hundred options keeps the
     * schema small.
     */
    val referenceTable: String? = null,
    /** True for tenant-defined fields. Clients use it for nothing; support does. */
    val custom: Boolean = false,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class FieldValidation(
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val min: java.math.BigDecimal? = null,
    val max: java.math.BigDecimal? = null,
    /**
     * A regular expression the value must match.
     *
     * Deliberately a plain string rather than a compiled pattern in the
     * response: clients apply it in their own regex engine, and the server
     * re-validates regardless. Client-side validation here is for immediate
     * feedback, never for enforcement.
     */
    val pattern: String? = null,
    val patternMessage: String? = null,
)

data class FieldOption(
    val value: String,
    val label: String,
)

/**
 * Field types a client must be able to render.
 *
 * Deliberately small. Every type added here is one that Android, iOS and web
 * must each implement before a tenant can use it, so the set grows only when a
 * real requirement cannot be expressed by what exists.
 */
enum class FieldType {
    TEXT,
    MULTILINE_TEXT,
    NUMBER,
    DATE,
    DROPDOWN,
    MULTI_SELECT,
    RADIO,
    CHECKBOX,
    ATTACHMENT,

    /** Picks another employee. Rendered as a directory search. */
    EMPLOYEE,

    /** Picks from a reference taxonomy named by [FormField.referenceTable]. */
    REFERENCE,

    EMAIL,
    PHONE,
}
