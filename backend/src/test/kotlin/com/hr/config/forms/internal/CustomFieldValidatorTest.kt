package com.hr.config.forms.internal

import com.hr.shared.api.ApiException
import com.hr.shared.api.FieldViolation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Validator tests.
 *
 * Runs without a database, which matters here: custom fields are the mechanism
 * by which tenant configuration reaches production without a code review, so
 * the rules governing them need to be the best-tested part of the module rather
 * than the least.
 */
@DisplayName("Custom field validation")
class CustomFieldValidatorTest {
    private val validator = CustomFieldValidator()

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Required fields")
    inner class Required {
        @Test
        fun `a missing required field is rejected`() {
            val definitions = listOf(field("tshirtSize", CustomFieldType.TEXT, required = true))

            assertThat(violationsFrom { validator.validate(emptyMap(), definitions) })
                .extracting<String> { it.field }
                .containsExactly("tshirtSize")
        }

        /**
         * PATCH sends only what changed. Treating every absent field as missing
         * would make it impossible to update one field on a record that has any
         * other required field.
         */
        @Test
        fun `a missing required field is tolerated in a partial update`() {
            val definitions = listOf(field("tshirtSize", CustomFieldType.TEXT, required = true))

            assertThatCode { validator.validate(emptyMap(), definitions, partial = true) }
                .doesNotThrowAnyException()
        }

        /**
         * Not mentioning a field and explicitly clearing it are different
         * intents, and only one of them is allowed on a required field.
         */
        @Test
        fun `explicitly nulling a required field is rejected even in a partial update`() {
            val definitions = listOf(field("tshirtSize", CustomFieldType.TEXT, required = true))

            assertThat(violationsFrom { validator.validate(mapOf("tshirtSize" to null), definitions, partial = true) })
                .extracting<String> { it.code }
                .containsExactly("REQUIRED")
        }

        @Test
        fun `a blank string does not satisfy a required field`() {
            val definitions = listOf(field("tshirtSize", CustomFieldType.TEXT, required = true))

            assertThat(violationsFrom { validator.validate(mapOf("tshirtSize" to "   "), definitions) })
                .extracting<String> { it.code }
                .containsExactly("REQUIRED")
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Unknown fields")
    inner class Unknown {
        /**
         * Silently dropping a value someone typed is worse than refusing it:
         * they watch the field save, return later, and find it empty with no
         * explanation.
         */
        @Test
        fun `an unknown key is rejected rather than ignored`() {
            assertThat(violationsFrom { validator.validate(mapOf("notAField" to "x"), emptyList()) })
                .extracting<String> { it.code }
                .containsExactly("UNKNOWN_FIELD")
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Text")
    inner class Text {
        @Test
        fun `enforces minimum and maximum length`() {
            val definitions = listOf(field("code", CustomFieldType.TEXT, rules = mapOf("minLength" to 3, "maxLength" to 5)))

            assertThat(violationsFrom { validator.validate(mapOf("code" to "ab"), definitions) })
                .extracting<String> { it.code }.containsExactly("MIN_LENGTH")
            assertThat(violationsFrom { validator.validate(mapOf("code" to "abcdef"), definitions) })
                .extracting<String> { it.code }.containsExactly("MAX_LENGTH")
            assertThatCode { validator.validate(mapOf("code" to "abcd"), definitions) }
                .doesNotThrowAnyException()
        }

        @Test
        fun `enforces a pattern and uses the configured message`() {
            val definitions =
                listOf(
                    field(
                        "staffId",
                        CustomFieldType.TEXT,
                        rules = mapOf("pattern" to "^[A-Z]{2}[0-9]{4}$", "patternMessage" to "Two letters then four digits"),
                    ),
                )

            val violations = violationsFrom { validator.validate(mapOf("staffId" to "abc"), definitions) }
            assertThat(violations).singleElement()
                .satisfies({
                    assertThat(it.code).isEqualTo("PATTERN")
                    assertThat(it.message).isEqualTo("Two letters then four digits")
                })
        }

        /**
         * A malformed regex is a configuration error. Rejecting the user's
         * input for it would be unfixable from their side — they cannot correct
         * a pattern they cannot see — so the value passes and the problem
         * surfaces to whoever configured the field.
         */
        @Test
        fun `a malformed pattern does not reject the user's input`() {
            val definitions = listOf(field("x", CustomFieldType.TEXT, rules = mapOf("pattern" to "([unclosed")))

            assertThatCode { validator.validate(mapOf("x" to "anything"), definitions) }
                .doesNotThrowAnyException()
        }

        @Test
        fun `rejects a non-string`() {
            val definitions = listOf(field("name", CustomFieldType.TEXT))

            assertThat(violationsFrom { validator.validate(mapOf("name" to 42), definitions) })
                .extracting<String> { it.code }.containsExactly("WRONG_TYPE")
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Number")
    inner class Number {
        @Test
        fun `accepts a numeric string as well as a number`() {
            val definitions = listOf(field("count", CustomFieldType.NUMBER))

            assertThatCode { validator.validate(mapOf("count" to 5), definitions) }.doesNotThrowAnyException()
            assertThatCode { validator.validate(mapOf("count" to "5.5"), definitions) }.doesNotThrowAnyException()
        }

        @Test
        fun `enforces min and max`() {
            val definitions = listOf(field("age", CustomFieldType.NUMBER, rules = mapOf("min" to 18, "max" to 65)))

            assertThat(violationsFrom { validator.validate(mapOf("age" to 17), definitions) })
                .extracting<String> { it.code }.containsExactly("MIN")
            assertThat(violationsFrom { validator.validate(mapOf("age" to 70), definitions) })
                .extracting<String> { it.code }.containsExactly("MAX")
        }

        @Test
        fun `rejects a non-numeric string`() {
            val definitions = listOf(field("count", CustomFieldType.NUMBER))

            assertThat(violationsFrom { validator.validate(mapOf("count" to "banana"), definitions) })
                .extracting<String> { it.code }.containsExactly("WRONG_TYPE")
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Date")
    inner class Date {
        @Test
        fun `accepts an ISO date and rejects anything else`() {
            val definitions = listOf(field("startedOn", CustomFieldType.DATE))

            assertThatCode { validator.validate(mapOf("startedOn" to "2026-03-01"), definitions) }
                .doesNotThrowAnyException()
            assertThat(violationsFrom { validator.validate(mapOf("startedOn" to "01/03/2026"), definitions) })
                .extracting<String> { it.code }.containsExactly("INVALID_DATE")
        }

        @Test
        fun `enforces a date range`() {
            val definitions =
                listOf(field("startedOn", CustomFieldType.DATE, rules = mapOf("minDate" to "2026-01-01", "maxDate" to "2026-12-31")))

            assertThat(violationsFrom { validator.validate(mapOf("startedOn" to "2025-12-31"), definitions) })
                .extracting<String> { it.code }.containsExactly("MIN_DATE")
            assertThat(violationsFrom { validator.validate(mapOf("startedOn" to "2027-01-01"), definitions) })
                .extracting<String> { it.code }.containsExactly("MAX_DATE")
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Choices")
    inner class Choices {
        @Test
        fun `a dropdown value must be one of the options`() {
            val definitions = listOf(choiceField("size", CustomFieldType.DROPDOWN, "S", "M", "L"))

            assertThatCode { validator.validate(mapOf("size" to "M"), definitions) }.doesNotThrowAnyException()
            assertThat(violationsFrom { validator.validate(mapOf("size" to "XXL"), definitions) })
                .extracting<String> { it.code }.containsExactly("INVALID_OPTION")
        }

        @Test
        fun `a multi-select rejects any value outside the options`() {
            val definitions = listOf(choiceField("skills", CustomFieldType.MULTI_SELECT, "kotlin", "swift"))

            assertThatCode { validator.validate(mapOf("skills" to listOf("kotlin", "swift")), definitions) }
                .doesNotThrowAnyException()
            assertThat(violationsFrom { validator.validate(mapOf("skills" to listOf("kotlin", "cobol")), definitions) })
                .extracting<String> { it.code }.containsExactly("INVALID_OPTION")
        }

        @Test
        fun `a multi-select rejects a bare string`() {
            val definitions = listOf(choiceField("skills", CustomFieldType.MULTI_SELECT, "kotlin"))

            assertThat(violationsFrom { validator.validate(mapOf("skills" to "kotlin"), definitions) })
                .extracting<String> { it.code }.containsExactly("WRONG_TYPE")
        }
    }

    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Employee reference")
    inner class EmployeeReference {
        @Test
        fun `accepts a uuid and rejects anything else`() {
            val definitions = listOf(field("buddy", CustomFieldType.EMPLOYEE))

            assertThatCode { validator.validate(mapOf("buddy" to UUID.randomUUID().toString()), definitions) }
                .doesNotThrowAnyException()
            assertThat(violationsFrom { validator.validate(mapOf("buddy" to "nimal"), definitions) })
                .extracting<String> { it.code }.containsExactly("INVALID_REFERENCE")
        }
    }

    // -----------------------------------------------------------------------
    /**
     * Reporting one problem at a time turns filling a form into a guessing
     * game: fix the date, resubmit, get told about the phone number.
     */
    @Test
    fun `reports every violation at once rather than stopping at the first`() {
        val definitions =
            listOf(
                field("a", CustomFieldType.TEXT, required = true),
                field("b", CustomFieldType.NUMBER, rules = mapOf("min" to 10)),
                choiceField("c", CustomFieldType.DROPDOWN, "x"),
            )

        val violations = violationsFrom { validator.validate(mapOf("b" to 1, "c" to "z"), definitions) }

        assertThat(violations).extracting<String> { it.field }
            .containsExactlyInAnyOrder("a", "b", "c")
    }

    // ------------------------------------------------------------------------
    // Helpers

    private fun field(
        key: String,
        type: CustomFieldType,
        required: Boolean = false,
        rules: Map<String, Any?> = emptyMap(),
    ) = FieldDefinition(entityType = "employee", fieldKey = key, dataType = type).apply {
        validation = (rules + if (required) mapOf("required" to true) else emptyMap()).toMutableMap()
    }

    private fun choiceField(
        key: String,
        type: CustomFieldType,
        vararg options: String,
    ) = FieldDefinition(entityType = "employee", fieldKey = key, dataType = type).apply {
        this.options = options.map { mutableMapOf<String, Any?>("value" to it, "label" to it) }.toMutableList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun violationsFrom(block: () -> Unit): List<FieldViolation> {
        var caught: ApiException? = null
        assertThatThrownBy { block() }.isInstanceOfSatisfying(ApiException::class.java) { caught = it }
        return caught?.details?.get("violations") as? List<FieldViolation> ?: emptyList()
    }
}
