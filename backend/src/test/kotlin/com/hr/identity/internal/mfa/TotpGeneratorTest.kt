package com.hr.identity.internal.mfa

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant

/**
 * TOTP, verified against the RFC's own test vectors.
 *
 * This is the rare case where correctness can be *proven* rather than argued: RFC 6238 Appendix B
 * publishes known secrets, known times and the codes they must produce. An implementation that
 * matches them is right, and one that does not is wrong, and no amount of code review substitutes
 * for the check.
 *
 * It matters more than usual here because a subtly wrong TOTP fails in the worst possible way — it
 * works for the developer testing it, and locks out a fraction of users whose clock happens to sit
 * on the other side of a step boundary.
 */
@DisplayName("TOTP")
class TotpGeneratorTest {
    private val totp = TotpGenerator()

    /** RFC 6238 Appendix B, the SHA-1 rows. The seed is the ASCII string `12345678901234567890`. */
    private val rfcSecret = Base32.encode("12345678901234567890".toByteArray())

    @Nested
    @DisplayName("RFC 6238 test vectors")
    inner class RfcVectors {
        @ParameterizedTest(name = "T={0} produces {1}")
        @CsvSource(
            "59,           94287082",
            "1111111109,   07081804",
            "1111111111,   14050471",
            "1234567890,   89005924",
            "2000000000,   69279037",
            "20000000000,  65353130",
        )
        fun `match the published values`(
            epochSecond: Long,
            expected: String,
        ) {
            val code = totp.generate(rfcSecret, at = Instant.ofEpochSecond(epochSecond), digits = 8)

            assertThat(code).isEqualTo(expected)
        }

        /**
         * The product uses 6 digits, which is the trailing 6 of the 8-digit vector — the
         * truncation is a modulus, so the leading digits simply fall away.
         */
        @Test
        fun `six-digit codes are the tail of the eight-digit vector`() {
            val code = totp.generate(rfcSecret, at = Instant.ofEpochSecond(59))

            assertThat(code).isEqualTo("287082")
        }
    }

    @Nested
    @DisplayName("Validation window")
    inner class Window {
        private val now = Instant.ofEpochSecond(1_700_000_000)

        @Test
        fun `the current code is accepted`() {
            assertThat(totp.isValid(rfcSecret, totp.generate(rfcSecret, now), now)).isTrue()
        }

        /**
         * A code typed at the 29th second of its step arrives during the next one. Without this
         * tolerance a measurable fraction of correct codes would be rejected, and the user would
         * have no way to tell that from a wrong one.
         */
        @Test
        fun `the previous and next steps are accepted`() {
            val previous = totp.generate(rfcSecret, now.minusSeconds(30))
            val next = totp.generate(rfcSecret, now.plusSeconds(30))

            assertThat(totp.isValid(rfcSecret, previous, now)).isTrue()
            assertThat(totp.isValid(rfcSecret, next, now)).isTrue()
        }

        /** Two steps out is a code from over a minute ago. That is not clock skew. */
        @Test
        fun `two steps away is rejected`() {
            val stale = totp.generate(rfcSecret, now.minusSeconds(90))
            val early = totp.generate(rfcSecret, now.plusSeconds(90))

            assertThat(totp.isValid(rfcSecret, stale, now)).isFalse()
            assertThat(totp.isValid(rfcSecret, early, now)).isFalse()
        }

        @Test
        fun `a code for a different secret is rejected`() {
            val other = totp.generateSecret()

            assertThat(totp.isValid(rfcSecret, totp.generate(other, now), now)).isFalse()
        }
    }

    @Nested
    @DisplayName("Malformed input")
    inner class Malformed {
        private val now = Instant.ofEpochSecond(1_700_000_000)

        /**
         * All of these reach the endpoint from a real client eventually. None may throw: an
         * exception here becomes a 500 on the login path, which is both a worse user experience
         * than "wrong code" and a signal an attacker can measure.
         */
        @Test
        fun `is rejected rather than thrown`() {
            listOf("", "   ", "abc", "12345", "1234567", "12 34 56", "٣٤٥٦٧٨").forEach { code ->
                assertThat(totp.isValid(rfcSecret, code, now))
                    .describedAs("code %s", code)
                    .isFalse()
            }
        }

        /** A corrupt stored secret must fail closed, not crash the login endpoint. */
        @Test
        fun `a malformed secret rejects rather than throwing`() {
            assertThat(totp.isValid("not!valid!base32", "123456", now)).isFalse()
            assertThat(totp.isValid("", "123456", now)).isFalse()
        }

        /**
         * People retype secrets when a camera will not focus on the QR code. Rejecting a
         * correctly-typed secret because of case or spacing would be technically defensible and
         * would generate support tickets.
         */
        @Test
        fun `a secret is accepted in lower case and with spaces`() {
            val spaced = rfcSecret.chunked(4).joinToString(" ").lowercase()

            assertThat(totp.generate(spaced, Instant.ofEpochSecond(59), digits = 8))
                .isEqualTo("94287082")
        }
    }

    @Nested
    @DisplayName("Secrets and provisioning")
    inner class Secrets {
        @Test
        fun `a generated secret is 160 bits of base32`() {
            val secret = totp.generateSecret()

            // 20 bytes = 160 bits = 32 base32 characters.
            assertThat(secret).hasSize(32)
            assertThat(secret).matches("[A-Z2-7]+")
            assertThat(Base32.decode(secret)).hasSize(20)
        }

        @Test
        fun `generated secrets differ`() {
            val secrets = (1..50).map { totp.generateSecret() }.toSet()

            assertThat(secrets).hasSize(50)
        }

        @Test
        fun `the provisioning uri carries what an authenticator app needs`() {
            val uri = totp.provisioningUri("JBSWY3DPEHPK3PXP", "nimali@demo.local", "Demo Company")

            assertThat(uri).startsWith("otpauth://totp/")
            assertThat(uri).contains("secret=JBSWY3DPEHPK3PXP")
            // Issuer twice — as a label prefix and as a parameter — because apps disagree about
            // which they read, and an unlabelled account in a list of thirty is unidentifiable.
            assertThat(uri).contains("Demo%20Company:nimali%40demo.local")
            assertThat(uri).contains("issuer=Demo%20Company")
            assertThat(uri).contains("algorithm=SHA1").contains("digits=6").contains("period=30")
        }
    }

    @Nested
    @DisplayName("Base32")
    inner class Base32Codec {
        /** RFC 4648 §10 test vectors. */
        @ParameterizedTest(name = "{0} encodes to {1}")
        @CsvSource(
            "f,      MY",
            "fo,     MZXQ",
            "foo,    MZXW6",
            "foob,   MZXW6YQ",
            "fooba,  MZXW6YTB",
            "foobar, MZXW6YTBOI",
        )
        fun `matches RFC 4648`(
            input: String,
            expected: String,
        ) {
            assertThat(Base32.encode(input.toByteArray())).isEqualTo(expected)
        }

        @Test
        fun `round-trips arbitrary bytes`() {
            val bytes = ByteArray(20) { (it * 7 - 128).toByte() }

            assertThat(Base32.decode(Base32.encode(bytes))).isEqualTo(bytes)
        }
    }
}
