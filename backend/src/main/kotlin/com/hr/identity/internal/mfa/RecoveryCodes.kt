package com.hr.identity.internal.mfa

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Single-use codes for when the authenticator is gone.
 *
 * The phone is lost, replaced, or wiped. Without these the only recovery is an administrator
 * turning MFA off for the account, which means a support call, an identity check nobody is trained
 * to do properly, and a social-engineering path straight past the second factor. Recovery codes
 * move that decision to enrolment time, when the user is already authenticated.
 *
 * ## Why these are hashed with SHA-256 and passwords are not
 *
 * Passwords need Argon2 because they are low-entropy and guessable — the work factor is what makes
 * a dictionary attack impractical. A recovery code is 100 bits from a CSPRNG, so guessing is
 * already impossible and a slow hash buys nothing. It would cost something, though: verification
 * has to try every unused code, so ten Argon2 verifications per attempt would turn recovery into a
 * denial-of-service lever.
 *
 * ## Why they are single-use
 *
 * A reusable code is a password that never expires, written on a piece of paper in a drawer. Each
 * is consumed on use and the remaining count is surfaced so the user knows when to regenerate.
 */
@Component
class RecoveryCodes {
    /**
     * A fresh set. The plaintext is returned once and never stored — only the hashes are kept, so
     * a database compromise yields nothing usable.
     */
    fun generate(count: Int = CODE_COUNT): GeneratedRecoveryCodes {
        val codes = (1..count).map { randomCode() }
        return GeneratedRecoveryCodes(plaintext = codes, hashes = codes.map(::hash))
    }

    /**
     * Consumes a code.
     *
     * @return the remaining hashes if [candidate] matched, or null if it did not. Returning the
     *   new set rather than mutating makes it impossible to check a code without also spending it,
     *   which is the mistake that turns single-use codes into reusable ones.
     */
    fun consume(
        candidate: String,
        remainingHashes: List<String>,
    ): List<String>? {
        val normalised = normalise(candidate)
        if (normalised.length != CODE_LENGTH) return null

        val target = hash(normalised)

        // Every entry is compared, and the comparison is constant-time, so neither the number of
        // codes remaining nor the position of the match is observable from timing.
        var matchedIndex = -1
        remainingHashes.forEachIndexed { index, stored ->
            if (constantTimeEquals(stored, target)) matchedIndex = index
        }

        if (matchedIndex < 0) return null
        return remainingHashes.filterIndexed { index, _ -> index != matchedIndex }
    }

    /**
     * Formatted for display: `A1B2-C3D4-E5`.
     *
     * Grouped because these get written down and read back. An unbroken run of ten characters is
     * measurably worse to transcribe than three short groups.
     */
    fun forDisplay(code: String): String = code.chunked(4).joinToString("-")

    private fun randomCode(): String =
        (1..CODE_LENGTH).map { ALPHABET[RANDOM.nextInt(ALPHABET.length)] }.joinToString("")

    /** Case-insensitive, and separators dropped, because the user is retyping from paper. */
    private fun normalise(code: String): String =
        code.trim().replace("-", "").replace(" ", "").uppercase()

    private fun hash(code: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(code.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean {
        if (a.length != b.length) return false
        var difference = 0
        for (i in a.indices) difference = difference or (a[i].code xor b[i].code)
        return difference == 0
    }

    private companion object {
        /**
         * Crockford-style: no `I`, `L`, `O`, `U`, or `0`/`1`. These are read off paper and typed
         * by someone who has just lost their phone and is not at their best.
         */
        const val ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789"
        const val CODE_LENGTH = 10
        const val CODE_COUNT = 10
        val RANDOM = SecureRandom()
    }
}

/** Plaintext is shown once, at enrolment. Only [hashes] is persisted. */
data class GeneratedRecoveryCodes(
    val plaintext: List<String>,
    val hashes: List<String>,
)
