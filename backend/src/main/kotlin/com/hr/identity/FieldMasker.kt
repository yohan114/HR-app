package com.hr.identity

import java.time.LocalDate

/**
 * Redacts a value for [FieldAccess.MASKED].
 *
 * ## Masking is not a display concern
 *
 * The tempting implementation is to send the real value and let the client show
 * dots. That is not masking, it is a hint — the value is in the response body,
 * in the HTTP cache, in the client's local database, and in any log that
 * captures payloads. Masking happens here, server-side, and the true value
 * never leaves the process.
 *
 * ## What a mask may reveal
 *
 * A mask that preserves too much is a slow leak: `1985-••-••` narrows a date of
 * birth to a year, and combined with a directory listing that is often enough
 * to identify someone. So the rule is that a mask preserves only what makes the
 * masked value *useful for confirmation* — the last four digits of an account
 * you are about to pay into, the domain of an email — and nothing that helps
 * reconstruct the rest.
 *
 * Dates get no partial mask at all, because every component of a date of birth
 * is identifying and none of it is needed for confirmation.
 */
object FieldMasker {
    private const val DOTS = "••••"

    /**
     * Digits a value must contain before its last four may be shown.
     *
     * Eight leaves at least half hidden. Below that, "the last four" is most of
     * the value.
     */
    private const val MIN_DIGITS_FOR_PARTIAL = 8

    /** Digits, and the separators that appear inside account and phone numbers. */
    private val NUMERIC_SEQUENCE = Regex("""[0-9][0-9 \-+()/]*""")

    /**
     * Date-shaped text, in the separators the API and JSONB actually carry.
     *
     * Covers ISO (`1990-05-02`), the same with a time suffix, and the slash and dot forms a bulk
     * import might land in a custom field.
     */
    private val DATE_LIKE = Regex("""\d{4}[-/.]\d{1,2}[-/.]\d{1,2}([T ].*)?|\d{1,2}[-/.]\d{1,2}[-/.]\d{4}""")

    /** No whitespace, one `@`, a dotted domain. Deliberately stricter than "contains an @". */
    private val EMAIL_LIKE = Regex("""[^\s@]+@[^\s@.]+(\.[^\s@.]+)+""")

    /**
     * Whether masking [value] produces something its declared wire type still accepts.
     *
     * The mask is the string `••••`. That is fine for a field the API declares as a string, and a
     * contract violation for one declared as a date, a uuid or a number — and not a harmless one:
     * the generated Kotlin model types `dateOfBirth` as `LocalDate` and the Swift model as `Date`,
     * and kotlinx aborts decoding of the **entire response** on a type mismatch. A single masked
     * date would blank the whole profile on both mobile clients.
     *
     * So a field that cannot be masked without lying about its type is hidden instead. That is
     * strictly more restrictive, and it costs almost nothing: a fully-masked date already conveys
     * only that a value exists.
     *
     * Free-form maps and lists are maskable because the schema declares them
     * `additionalProperties: true` — there is no element type to violate.
     */
    fun canMask(value: Any?): Boolean =
        when (value) {
            null -> true
            is String -> true
            is Map<*, *> -> value.values.all(::canMask)
            is Collection<*> -> value.all(::canMask)
            // LocalDate, UUID, Number, Boolean and anything else with a declared non-string type.
            else -> false
        }

    fun mask(value: Any?): Any? =
        when (value) {
            null -> null
            is LocalDate -> DOTS
            is Number -> DOTS
            is Boolean -> DOTS
            is Collection<*> -> value.map { mask(it) }
            is Map<*, *> -> value.mapValues { (_, v) -> mask(v) }
            else -> maskString(value.toString())
        }

    /**
     * Partial masking is for account and phone numbers, and nothing else.
     *
     * "The last four" is an idiom with one purpose: confirming you are about to
     * pay into the right account, or that a number on file is the one in your
     * hand. It carries no such meaning for text, where the trailing characters
     * are simply part of the value — a masked city of `••••ombo` is Colombo to
     * anyone who has seen a map, and a masked surname is worse.
     *
     * So the test is what the value *is*, not how long it is. Free text is
     * masked completely.
     */
    private fun maskString(value: String): String =
        when {
            value.isEmpty() -> value
            // Order matters: the date test must come before the number test, because an ISO date
            // passes the number test. See [looksLikeDate].
            looksLikeDate(value) -> DOTS
            looksLikeEmail(value) -> maskEmail(value)
            isMaskableNumber(value) -> DOTS + value.takeLast(4)
            else -> DOTS
        }

    /**
     * A date written as text.
     *
     * Necessary because only a *built-in* date arrives as a `LocalDate`. A tenant-defined `DATE`
     * custom field lives in a JSONB column and arrives as the string `"1990-05-02"` — which then
     * fell through to the number branch, since stripping the dashes leaves eight digits, and was
     * masked as `••••5-02`. That published the month and day of a date of birth from the code
     * whose entire purpose is to withhold them.
     */
    private fun looksLikeDate(value: String): Boolean = DATE_LIKE.matches(value.trim())

    /**
     * A conservative test for an actual email address.
     *
     * `contains('@')` is not that test, and using it leaked badly: an address line such as
     * `Flat 3 @ 42 Galle Road, Colombo` was routed to [maskEmail], which preserves everything from
     * the last `@` onwards — so the mask emitted the entire street address.
     *
     * This requires the shape of an address rather than the presence of a character: no
     * whitespace, exactly one `@`, and a dotted domain after it.
     */
    private fun looksLikeEmail(value: String): Boolean = EMAIL_LIKE.matches(value.trim())

    private fun isMaskableNumber(value: String): Boolean =
        NUMERIC_SEQUENCE.matches(value.trim().removePrefix("+")) &&
            value.count(Char::isDigit) >= MIN_DIGITS_FOR_PARTIAL

    /**
     * `alice.smith@example.com` becomes `••••@example.com`.
     *
     * The domain is kept because it answers the question a masked email is
     * usually shown for — is this their work address or a personal one — while
     * the local part, which is the identifying half, is removed entirely. No
     * first initial: `a••••@example.com` plus a directory listing is often
     * enough to guess the whole address.
     */
    private fun maskEmail(value: String): String {
        val at = value.lastIndexOf('@')
        // A trailing '@' has no domain to preserve, so there is nothing safe to
        // show. Fall through to a complete mask rather than emitting "••••@".
        if (at <= 0 || at == value.length - 1) return DOTS
        return DOTS + value.substring(at)
    }
}
