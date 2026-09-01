package com.hr.identity.internal.mfa

import com.hr.identity.internal.AppUser
import com.hr.identity.internal.AppUserRepository
import com.hr.shared.api.ApiException
import com.hr.shared.crypto.FieldCipher
import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.Optional
import java.util.UUID

/**
 * The second factor, tested where it can be: everything except the database round trip.
 *
 * The rules under test are the ones whose absence turns MFA into decoration — that a code is
 * required before it is switched on, that recovery codes are single-use, that disabling requires
 * possession, and that verification is rate-limited.
 */
@DisplayName("MFA")
class MfaServiceTest {
    private val users = mockk<AppUserRepository>()
    private val cipher = FieldCipher(FieldCipher.generateKey())
    private val totp = TotpGenerator()
    private val recoveryCodes = RecoveryCodes()

    private lateinit var service: MfaService
    private lateinit var user: AppUser
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        TenantContext.set(
            TenantHandle(
                id = UUID.randomUUID(), code = "test", name = "Test", dataRegion = "default",
                defaultCurrency = "LKR", timezone = "Asia/Colombo", locale = "en",
                isolationTier = IsolationTier.SHARED, status = TenantStatus.ACTIVE,
            ),
        )

        user = AppUser(username = "nimali", email = "nimali@demo.local")
        service = MfaService(users, totp, recoveryCodes, cipher, "Demo Company")

        every { users.findById(any()) } returns Optional.of(user)
        every { users.save(any()) } answers { firstArg() }
    }

    private fun currentCode(): String = totp.generate(cipher.decrypt(user.mfaSecretEnc)!!)

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Enrolment")
    inner class Enrolment {
        /**
         * The reason enrolment is two steps. Switching MFA on before the user proves their app
         * works would let a mis-scanned QR code lock someone out of their own account, with no way
         * back except an administrator — which is a support call and a social-engineering path
         * straight past the factor.
         */
        @Test
        fun `beginning enrolment does not enable MFA`() {
            service.beginEnrolment(userId)

            assertThat(user.mfaEnabled).isFalse()
            assertThat(user.mfaSecretEnc).isNotNull()
        }

        @Test
        fun `the secret is stored encrypted, never in plaintext`() {
            val enrolment = service.beginEnrolment(userId)

            assertThat(user.mfaSecretEnc).doesNotContain(enrolment.secret)
            assertThat(cipher.decrypt(user.mfaSecretEnc)).isEqualTo(enrolment.secret)
        }

        @Test
        fun `confirming with a valid code enables MFA and returns recovery codes`() {
            service.beginEnrolment(userId)

            val codes = service.confirmEnrolment(userId, currentCode())

            assertThat(user.mfaEnabled).isTrue()
            assertThat(codes).hasSize(10)
            assertThat(codes).allSatisfy { assertThat(it).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{2}") }
        }

        @Test
        fun `confirming with a wrong code leaves MFA off`() {
            service.beginEnrolment(userId)

            assertThatThrownBy { service.confirmEnrolment(userId, "000000") }
                .isInstanceOf(ApiException::class.java)

            assertThat(user.mfaEnabled).isFalse()
        }

        @Test
        fun `confirming without starting is refused`() {
            assertThatThrownBy { service.confirmEnrolment(userId, "123456") }
                .isInstanceOfSatisfying(ApiException::class.java) {
                    assertThat(it.code).isEqualTo("MFA_NOT_STARTED")
                }
        }

        /** Only the hashes are kept, so a database compromise yields nothing usable. */
        @Test
        fun `recovery codes are never stored in plaintext`() {
            service.beginEnrolment(userId)
            val codes = service.confirmEnrolment(userId, currentCode())

            val stored = cipher.decrypt(user.mfaRecoveryCodesEnc)!!
            codes.forEach { assertThat(stored).doesNotContain(it.replace("-", "")) }
        }

        @Test
        fun `enrolling again while enabled is refused`() {
            enable()

            assertThatThrownBy { service.beginEnrolment(userId) }
                .isInstanceOfSatisfying(ApiException::class.java) {
                    assertThat(it.code).isEqualTo("MFA_ALREADY_ENABLED")
                }
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Verification")
    inner class Verification {
        @Test
        fun `a current TOTP code is accepted`() {
            enable()

            assertThat(service.verify(userId, currentCode())).isEqualTo(MfaMethod.TOTP)
        }

        @Test
        fun `a wrong code is rejected`() {
            enable()

            assertThatThrownBy { service.verify(userId, "000000") }
                .isInstanceOfSatisfying(ApiException::class.java) {
                    assertThat(it.code).isEqualTo("MFA_INVALID_CODE")
                }
        }

        @Test
        fun `a recovery code is accepted without saying which it was`() {
            val codes = enable()

            assertThat(service.verify(userId, codes.first())).isEqualTo(MfaMethod.RECOVERY_CODE)
        }

        /**
         * The property that separates a recovery code from a second password. A reusable code is
         * a credential written on paper in a drawer that never expires.
         */
        @Test
        fun `a recovery code cannot be used twice`() {
            val codes = enable()

            service.verify(userId, codes.first())

            assertThatThrownBy { service.verify(userId, codes.first()) }
                .isInstanceOf(ApiException::class.java)
        }

        @Test
        fun `using one recovery code leaves the others usable`() {
            val codes = enable()

            service.verify(userId, codes[0])

            assertThat(service.verify(userId, codes[1])).isEqualTo(MfaMethod.RECOVERY_CODE)
            assertThat(service.status(userId).recoveryCodesRemaining).isEqualTo(8)
        }

        /** Retyped from paper by someone who has just lost their phone. */
        @Test
        fun `a recovery code is accepted in lower case and without separators`() {
            val codes = enable()

            val typed = codes.first().replace("-", "").lowercase()
            assertThat(service.verify(userId, typed)).isEqualTo(MfaMethod.RECOVERY_CODE)
        }

        /**
         * Saying "MFA is not enabled" would confirm the account's state to whoever holds the
         * challenge token. One code covers every failure.
         */
        @Test
        fun `verifying against an account without MFA gives the same error as a wrong code`() {
            assertThatThrownBy { service.verify(userId, "123456") }
                .isInstanceOfSatisfying(ApiException::class.java) {
                    assertThat(it.code).isEqualTo("MFA_INVALID_CODE")
                }
        }

        /**
         * Six digits over a ±1 step window is one in ~333,000 per guess — fine against a person,
         * thin against a script.
         */
        @Test
        fun `repeated failures are rate-limited`() {
            enable()

            repeat(5) {
                assertThatThrownBy { service.verify(userId, "000000") }
                    .isInstanceOf(ApiException::class.java)
            }

            assertThatThrownBy { service.verify(userId, "000000") }
                .isInstanceOfSatisfying(ApiException::class.java) {
                    assertThat(it.status).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                }
        }

        /** Otherwise a user who fumbles twice is locked out for the rest of the window. */
        @Test
        fun `a success clears the attempt counter`() {
            enable()

            repeat(4) { runCatching { service.verify(userId, "000000") } }
            service.verify(userId, currentCode())

            repeat(4) {
                assertThatThrownBy { service.verify(userId, "000000") }
                    .describedAs("should still be a plain rejection, not a rate limit")
                    .isInstanceOfSatisfying(ApiException::class.java) {
                        assertThat(it.status).isEqualTo(HttpStatus.UNAUTHORIZED)
                    }
            }
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Management")
    inner class Management {
        /**
         * Possession, not just a live session. Without this an attacker who borrowed an unlocked
         * laptop could switch the factor off from a settings screen, which makes it decorative.
         */
        @Test
        fun `disabling requires a current code`() {
            enable()

            assertThatThrownBy { service.disable(userId, "000000") }
                .isInstanceOf(ApiException::class.java)
            assertThat(user.mfaEnabled).isTrue()

            service.disable(userId, currentCode())
            assertThat(user.mfaEnabled).isFalse()
        }

        @Test
        fun `disabling clears the secret and the recovery codes`() {
            enable()

            service.disable(userId, currentCode())

            assertThat(user.mfaSecretEnc).isNull()
            assertThat(user.mfaRecoveryCodesEnc).isNull()
        }

        @Test
        fun `regenerating invalidates the previous codes`() {
            val original = enable()

            val fresh = service.regenerateRecoveryCodes(userId, currentCode())

            assertThat(fresh).hasSize(10).doesNotContainAnyElementsOf(original)
            assertThatThrownBy { service.verify(userId, original.first()) }
                .isInstanceOf(ApiException::class.java)
            assertThat(service.verify(userId, fresh.first())).isEqualTo(MfaMethod.RECOVERY_CODE)
        }

        @Test
        fun `status reports enrolment progress`() {
            assertThat(service.status(userId))
                .isEqualTo(MfaStatus(enabled = false, enrolmentPending = false, recoveryCodesRemaining = 0))

            service.beginEnrolment(userId)
            assertThat(service.status(userId).enrolmentPending).isTrue()

            service.confirmEnrolment(userId, currentCode())
            assertThat(service.status(userId))
                .isEqualTo(MfaStatus(enabled = true, enrolmentPending = false, recoveryCodesRemaining = 10))
        }
    }

    // ------------------------------------------------------------------------

    /** Enrols and enables, returning the recovery codes. */
    private fun enable(): List<String> {
        service.beginEnrolment(userId)
        return service.confirmEnrolment(userId, currentCode())
    }
}
