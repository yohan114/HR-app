package com.hr.config.forms.internal

import com.hr.shared.api.BusinessRuleException
import com.hr.shared.api.FieldViolation
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Validates custom field values against their definitions.
 *
 * ## Why the server validates at all
 *
 * The clients validate too, for immediate feedback — but client validation is
 * a courtesy, not a control. Anything reaching this method may have come from
 * a modified app, a replayed request, or a bulk import. If the server does not
 * check, a `NUMBER` field can end up holding `"banana"` and every consumer of
 * that JSONB column has to defend against it forever.
 *
 * ## Why it collects violations rather than throwing on the first
 *
 * Reporting one problem at a time turns filling a form into a guessing game:
 * the user fixes the date, resubmits, and is told about the phone number. Same
 * reasoning as the password policy validator.
 */
@Component
class CustomFieldValidator {
    /**
     * Validates a set of values.
     *
     * @param values submitted values keyed by field key
     * @param definitions the active definitions for the entity
     * @param partial when true, absent fields are not treated as missing —
     *   used for PATCH, where the client sends only what changed. A required
     *   field can then be omitted, but not explicitly set to null.
     */
    fun validate(
        values: Map<String, Any?>,
        definitions: List<FieldDefinition>,
        partial: Boolean = false,
    ) {
        val byKey = definitions.associateBy { it.fieldKey }
        val violations = mutableListOf<FieldViolation>()

        // Unknown keys are rejected rather than ignored. Silently dropping a
        // value the user typed is worse than refusing it: they see the field
        // save, come back, and find it empty with no explanation.
        values.keys.filterNot { byKey.containsKey(it) }.forEach { unknown ->
            violations +=
                FieldViolation(
                    field = unknown,
                    code = "UNKNOWN_FIELD",
                    message = "No such field on this record",
                    rejectedValue = null,
                )
        }

        definitions.forEach { definition ->
            val present = values.containsKey(definition.fieldKey)
            val value = values[definition.fieldKey]

            if (!present) {
                if (!partial && definition.isRequired()) {
                    violations += required(definition)
                }
                return@forEach
            }

            if (value == null || (value is String && value.isBlank())) {
                // An explicit null always violates a required field, even in a
                // partial update — clearing a mandatory value is a different
                // intent from not mentioning it.
                if (definition.isRequired()) violations += required(definition)
                return@forEach
            }

            violations += validateValue(definition, value)
        }

        if (violations.isNotEmpty()) {
            throw BusinessRuleException(
                code = "CUSTOM_FIELD_VALIDATION_FAILED",
                message = "One or more fields are not valid",
                details = mapOf("violations" to violations),
            )
        }
    }

    private fun validateValue(
        definition: FieldDefinition,
        value: Any,
    ): List<FieldViolation> =
        when (definition.dataType) {
            CustomFieldType.TEXT -> validateText(definition, value)
            CustomFieldType.NUMBER -> validateNumber(definition, value)
            CustomFieldType.DATE -> validateDate(definition, value)
            CustomFieldType.CHECKBOX -> validateBoolean(definition, value)
            CustomFieldType.DROPDOWN, CustomFieldType.RADIO -> validateSingleChoice(definition, value)
            CustomFieldType.MULTI_SELECT -> validateMultiChoice(definition, value)
            CustomFieldType.EMPLOYEE -> validateUuid(definition, value)
            // The value is an object-storage key produced by the upload
            // endpoint, which has already checked type and size. Re-checking
            // the file here would mean fetching it back from S3 on every save.
            CustomFieldType.ATTACHMENT -> validateText(definition, value)
        }

    private fun validateText(
        definition: FieldDefinition,
        value: Any,
    ): List<FieldViolation> {
        val text = value as? String ?: return listOf(wrongType(definition, "text", value))
        val violations = mutableListOf<FieldViolation>()

        definition.intRule("minLength")?.let {
            if (text.length < it) {
                violations += violation(definition, "MIN_LENGTH", "Must be at least $it characters", text)
            }
        }
        definition.intRule("maxLength")?.let {
            if (text.length > it) {
                violations += violation(definition, "MAX_LENGTH", "Must be at most $it characters", text)
            }
        }
        definition.stringRule("pattern")?.let { pattern ->
            val matches =
                runCatching { Regex(pattern).matches(text) }
                    // A malformed pattern is a configuration error, not a user
                    // error. Rejecting the user's input for it would be
                    // unfixable from their side, so the value passes and the
                    // problem surfaces to whoever configured the field.
                    .getOrElse { return@let }
            if (!matches) {
                violations +=
                    violation(
                        definition,
                        "PATTERN",
                        definition.stringRule("patternMessage") ?: "Not in the expected format",
                        text,
                    )
            }
        }
        return violations
    }

    private fun validateNumber(
        definition: FieldDefinition,
        value: Any,
    ): List<FieldViolation> {
        val number =
            when (value) {
                is Number -> BigDecimal(value.toString())
                is String -> value.toBigDecimalOrNull()
                else -> null
            } ?: return listOf(wrongType(definition, "number", value))

        val violations = mutableListOf<FieldViolation>()
        definition.decimalRule("min")?.let {
            if (number < it) violations += violation(definition, "MIN", "Must be at least $it", value)
        }
        definition.decimalRule("max")?.let {
            if (number > it) violations += violation(definition, "MAX", "Must be at most $it", value)
        }
        return violations
    }

    private fun validateDate(
        definition: FieldDefinition,
        value: Any,
    ): List<FieldViolation> {
        val text = value as? String ?: return listOf(wrongType(definition, "date", value))
        val date =
            try {
                LocalDate.parse(text)
            } catch (e: DateTimeParseException) {
                return listOf(violation(definition, "INVALID_DATE", "Must be a date in YYYY-MM-DD form", value))
            }

        val violations = mutableListOf<FieldViolation>()
        definition.stringRule("minDate")?.let { min ->
            runCatching { LocalDate.parse(min) }.getOrNull()?.let {
                if (date.isBefore(it)) violations += violation(definition, "MIN_DATE", "Must be on or after $min", value)
            }
        }
        definition.stringRule("maxDate")?.let { max ->
            runCatching { LocalDate.parse(max) }.getOrNull()?.let {
                if (date.isAfter(it)) violations += violation(definition, "MAX_DATE", "Must be on or before $max", value)
            }
        }
        return violations
    }

    private fun validateBoolean(
        definition: FieldDefinition,
        value: Any,
    ): List<FieldViolation> =
        if (value is Boolean) emptyList() else listOf(wrongType(definition, "true or false", value))

    private fun validateSingleChoice(
        definition: FieldDefinition,
        value: Any,
    ): List<FieldViolation> {
        val text = value as? String ?: return listOf(wrongType(definition, "one of the allowed options", value))
        val allowed = definition.allowedOptionValues()
        return if (text in allowed) {
            emptyList()
        } else {
            listOf(violation(definition, "INVALID_OPTION", "Must be one of: ${allowed.joinToString(", ")}", value))
        }
    }

    private fun validateMultiChoice(
        definition: FieldDefinition,
        value: Any,
    ): List<FieldViolation> {
        val list = value as? List<*> ?: return listOf(wrongType(definition, "a list of options", value))
        val allowed = definition.allowedOptionValues()
        val invalid = list.filterNot { it is String && it in allowed }
        return if (invalid.isEmpty()) {
            emptyList()
        } else {
            listOf(violation(definition, "INVALID_OPTION", "Contains values that are not allowed: $invalid", value))
        }
    }

    private fun validateUuid(
        definition: FieldDefinition,
        value: Any,
    ): List<FieldViolation> {
        val text = value as? String ?: return listOf(wrongType(definition, "an employee reference", value))
        return if (runCatching { UUID.fromString(text) }.isSuccess) {
            emptyList()
        } else {
            listOf(violation(definition, "INVALID_REFERENCE", "Not a valid employee reference", value))
        }
    }

    // ------------------------------------------------------------------------

    private fun required(definition: FieldDefinition) =
        violation(definition, "REQUIRED", "This field is required", null)

    private fun wrongType(
        definition: FieldDefinition,
        expected: String,
        value: Any?,
    ) = violation(definition, "WRONG_TYPE", "Expected $expected", value)

    private fun violation(
        definition: FieldDefinition,
        code: String,
        message: String,
        value: Any?,
    ) = FieldViolation(
        field = definition.fieldKey,
        code = code,
        message = message,
        rejectedValue = value,
    )
}

// ---------------------------------------------------------------------------
// Rule accessors
//
// The validation map is untyped JSONB, so every read has to cope with a value
// arriving as an Int, a Long, a Double or a String depending on how it was
// written. Centralised here rather than repeated at each call site.
// ---------------------------------------------------------------------------

internal fun FieldDefinition.isRequired(): Boolean = validation["required"] == true

internal fun FieldDefinition.intRule(name: String): Int? =
    when (val raw = validation[name]) {
        is Number -> raw.toInt()
        is String -> raw.toIntOrNull()
        else -> null
    }

internal fun FieldDefinition.decimalRule(name: String): BigDecimal? =
    when (val raw = validation[name]) {
        is Number -> BigDecimal(raw.toString())
        is String -> raw.toBigDecimalOrNull()
        else -> null
    }

internal fun FieldDefinition.stringRule(name: String): String? = validation[name] as? String

internal fun FieldDefinition.allowedOptionValues(): Set<String> =
    options.orEmpty().mapNotNull { it["value"] as? String }.toSet()
