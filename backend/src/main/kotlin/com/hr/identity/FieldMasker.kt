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
            value.contains('@') -> maskEmail(value)
            isMaskableNumber(value) -> DOTS + value.takeLast(4)
            else -> DOTS
        }

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
