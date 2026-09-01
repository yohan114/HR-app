package com.hr.identity.internal.mfa

import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and
import kotlin.math.pow

/**
 * Time-based one-time passwords, RFC 6238.
 *
 * Written rather than pulled in because the algorithm is forty lines, the RFC publishes test
 * vectors that prove an implementation correct, and a dependency here would be a dependency in the
 * authentication path — the place where a supply-chain compromise costs the most.
 *
 * ## The parameters are not preferences
 *
 * SHA-1, 30-second steps and 6 digits are what Google Authenticator, Microsoft Authenticator, 1Password
 * and Authy actually implement. SHA-256 is permitted by the RFC and is cryptographically better,
 * and choosing it would mean codes that silently fail to match in most authenticator apps. The
 * threat TOTP defends against is not a weakness in HMAC-SHA1.
 */
@Component
class TotpGenerator(
    private val clock: () -> Instant = Instant::now,
) {
    /**
     * A new shared secret, base32-encoded.
     *
     * 160 bits, matching the HMAC-SHA1 block size — the RFC's recommended length, and what
     * authenticator apps expect from a QR code.
     */
    fun generateSecret(): String {
        val bytes = ByteArray(SECRET_BYTES)
        RANDOM.nextBytes(bytes)
        return Base32.encode(bytes)
    }

    /** The code for a given secret at a given moment. */
    fun generate(
        secret: String,
        at: Instant = clock(),
        digits: Int = DIGITS,
    ): String = generateForCounter(Base32.decode(secret), at.epochSecond / STEP_SECONDS, digits)

    /**
     * Whether [code] is currently valid for [secret].
     *
     * Accepts the immediately preceding and following steps as well as the current one. That
     * tolerance is not laziness: the user's phone clock and the server's differ, and a code typed
     * at the 29th second of a step will arrive during the next one. A window of ±1 step is the
     * standard trade — it accepts roughly 90 seconds of codes, which costs a factor of three in
     * brute-force resistance and is why [MfaService] rate-limits attempts rather than relying on
     * the window being narrow.
     *
     * The comparison is constant-time. A timing side channel here would let an attacker discover a
     * code digit by digit, which reduces the search from a million to sixty.
     */
    fun isValid(
        secret: String,
        code: String,
        at: Instant = clock(),
    ): Boolean {
        val normalised = code.trim().replace(" ", "")
        if (normalised.length != DIGITS || !normalised.all(Char::isDigit)) return false

        val key = runCatching { Base32.decode(secret) }.getOrElse { return false }
        val counter = at.epochSecond / STEP_SECONDS

        // Every candidate is computed and compared — no early return — so the work done does not
        // depend on which step matched.
        var matched = false
        for (offset in -WINDOW_STEPS..WINDOW_STEPS) {
            val candidate = generateForCounter(key, counter + offset, DIGITS)
            if (constantTimeEquals(candidate, normalised)) matched = true
        }
        return matched
    }

    /**
     * The `otpauth://` URI an authenticator app reads from a QR code.
     *
     * The issuer appears twice — once as a label prefix and once as a parameter — because apps
     * disagree about which they read, and an account that shows up as a bare username in a list of
     * thirty is one the user cannot identify.
     */
    fun provisioningUri(
        secret: String,
        account: String,
        issuer: String,
    ): String {
        val encodedIssuer = urlEncode(issuer)
        val label = "$encodedIssuer:${urlEncode(account)}"
        return "otpauth://totp/$label?secret=$secret&issuer=$encodedIssuer" +
            "&algorithm=SHA1&digits=$DIGITS&period=$STEP_SECONDS"
    }

    // ------------------------------------------------------------------------

    private fun generateForCounter(
        key: ByteArray,
        counter: Long,
        digits: Int,
    ): String {
        val message = ByteBuffer.allocate(8).putLong(counter).array()

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(message)

        // Dynamic truncation, RFC 4226 §5.3: the low nibble of the last byte selects which four
        // bytes of the digest to read, so the code depends on the whole digest rather than a fixed
        // slice of it.
        val offset = (hash[hash.size - 1] and 0x0f).toInt()
        val binary =
            ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)

        val modulus = 10.0.pow(digits).toInt()
        return (binary % modulus).toString().padStart(digits, '0')
    }

    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean {
        if (a.length != b.length) return false
        var difference = 0
        for (i in a.indices) difference = difference or (a[i].code xor b[i].code)
        return difference == 0
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private companion object {
        const val DIGITS = 6
        const val STEP_SECONDS = 30L
        const val WINDOW_STEPS = 1
        const val SECRET_BYTES = 20
        val RANDOM = SecureRandom()
    }
}

/**
 * Base32 as RFC 4648, without padding.
 *
 * `java.util.Base64` is not this, and TOTP secrets are base32 specifically because the alphabet
 * excludes the characters people confuse when reading a code off a screen — no `0`/`O`, no `1`/`I`.
 */
internal object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                out.append(ALPHABET[(buffer shr (bitsLeft - 5)) and 0x1f])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) out.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1f])
        return out.toString()
    }

    fun decode(encoded: String): ByteArray {
        // Case-insensitive, and spaces dropped, because users retype secrets from a screen when a
        // camera will not focus. Rejecting "jbsw y3dp" would be technically correct and unkind.
        val cleaned = encoded.trim().replace(" ", "").replace("=", "").uppercase()
        require(cleaned.isNotEmpty()) { "Empty base32 value" }

        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bitsLeft = 0
        for (character in cleaned) {
            val value = ALPHABET.indexOf(character)
            require(value >= 0) { "Not a base32 character: $character" }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.write((buffer shr (bitsLeft - 8)) and 0xff)
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }
}
