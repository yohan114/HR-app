package com.hr.identity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * What a mask is allowed to reveal.
 *
 * A mask that preserves too much is a slow leak rather than a control, and it
 * looks correct in a screenshot either way — which is precisely why it needs
 * tests that assert on what is *absent*.
 */
@DisplayName("Field masking")
class FieldMaskerTest {
    @Test
    fun `an account number keeps only its last four digits`() {
        assertThat(FieldMasker.mask("8801234567890")).isEqualTo("••••7890")
    }

    @Test
    fun `a phone number with separators is still recognised as a number`() {
        assertThat(FieldMasker.mask("+94 77 123 4567")).isEqualTo("••••4567")
    }

    /**
     * Showing the last four of a six-digit value discloses two thirds of it.
     * Short numbers are all-or-nothing.
     */
    @Test
    fun `a short number is masked completely`() {
        assertThat(FieldMasker.mask("123456")).isEqualTo("••••")
    }

    /**
     * The finding that produced the current rule. "The last four" is an idiom
     * for account numbers; applied to text it just hands over the end of the
     * word. `••••ombo` is Colombo to anyone who has seen a map, and a masked
     * surname is worse.
     */
    @Test
    fun `free text is masked completely however long it is`() {
        assertThat(FieldMasker.mask("Colombo")).isEqualTo("••••")
        assertThat(FieldMasker.mask("42 Galle Road, Colombo 00300")).isEqualTo("••••")
        assertThat(FieldMasker.mask("Wickramasinghe")).isEqualTo("••••")
        assertThat(FieldMasker.mask("AB1")).isEqualTo("••••")
    }

    /**
     * A passport number is text with digits in it, not a number. Partial
     * masking it would reveal the discriminating part.
     */
    @Test
    fun `an alphanumeric identifier is masked completely`() {
        assertThat(FieldMasker.mask("N1234567")).isEqualTo("••••")
    }

    @Test
    fun `an email keeps its domain and loses the local part`() {
        assertThat(FieldMasker.mask("alice.smith@example.com")).isEqualTo("••••@example.com")
    }

    /**
     * No first initial. `a••••@example.com` plus a directory listing is often
     * enough to reconstruct the whole address.
     */
    @Test
    fun `an email reveals nothing of the local part`() {
        val masked = FieldMasker.mask("alice.smith@example.com") as String
        assertThat(masked).doesNotContain("alice").doesNotContain("smith").doesNotStartWith("a")
    }

    @Test
    fun `a malformed email is masked completely`() {
        assertThat(FieldMasker.mask("@example.com")).isEqualTo("••••")
        assertThat(FieldMasker.mask("alice@")).isEqualTo("••••")
    }

    /**
     * Every component of a date of birth is identifying and none of it is
     * needed for confirmation, so there is no partial form worth having.
     * `1985-••-••` would narrow a birth year, which is the damaging part.
     */
    @Test
    fun `a date reveals no component at all`() {
        val masked = FieldMasker.mask(LocalDate.of(1985, 3, 14)) as String
        assertThat(masked).isEqualTo("••••")
        assertThat(masked).doesNotContain("1985").doesNotContain("03").doesNotContain("14")
    }

    /**
     * A masked salary that still renders as a number tells you the order of
     * magnitude, which is most of what someone wanted to know.
     */
    @Test
    fun `a number is not partially masked`() {
        assertThat(FieldMasker.mask(1_250_000)).isEqualTo("••••")
        assertThat(FieldMasker.mask(42.5)).isEqualTo("••••")
    }

    @Test
    fun `a boolean is masked, because yes or no can be the sensitive part`() {
        assertThat(FieldMasker.mask(true)).isEqualTo("••••")
    }

    /**
     * An address is a JSON object. Masking the object as a whole would drop it
     * to a single string and lose the shape the client renders; masking each
     * value keeps the shape without the contents.
     */
    @Test
    fun `a nested structure is masked value by value`() {
        val address = mapOf("line1" to "42 Galle Road", "city" to "Colombo", "postcode" to "00300")

        @Suppress("UNCHECKED_CAST")
        val masked = FieldMasker.mask(address) as Map<String, Any?>

        assertThat(masked.keys).containsExactlyInAnyOrder("line1", "city", "postcode")
        assertThat(masked.values).allSatisfy { assertThat(it as String).startsWith("••••") }
        assertThat(masked["line1"] as String).doesNotContain("Galle")
        assertThat(masked["city"]).isEqualTo("••••")
        // Five digits — below the threshold, so no trailing digits survive.
        assertThat(masked["postcode"]).isEqualTo("••••")
    }

    @Test
    fun `a list is masked element by element`() {
        assertThat(FieldMasker.mask(listOf("1234567890", "0987654321")))
            .isEqualTo(listOf("••••7890", "••••4321"))
    }

    /**
     * Null stays null. Substituting dots would claim a value exists where none
     * does, and the client would render an empty field as populated.
     */
    @Test
    fun `null stays null`() {
        assertThat(FieldMasker.mask(null)).isNull()
    }

    @Test
    fun `an empty string stays empty`() {
        assertThat(FieldMasker.mask("")).isEqualTo("")
    }
}
