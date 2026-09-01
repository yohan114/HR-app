package com.hr.shared.crypto

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Base64

@DisplayName("Column encryption")
class FieldCipherTest {
    private val cipher = FieldCipher(FieldCipher.generateKey())

    @Test
    fun `round-trips a value`() {
        val secret = "JBSWY3DPEHPK3PXP"

        assertThat(cipher.decrypt(cipher.encrypt(secret))).isEqualTo(secret)
    }

    @Test
    fun `round-trips non-ascii and empty values`() {
        listOf("නිමාලි", "தமிழ்", "", "  ", "🔐").forEach { value ->
            assertThat(cipher.decrypt(cipher.encrypt(value))).describedAs(value).isEqualTo(value)
        }
    }

    @Test
    fun `null passes through unchanged`() {
        assertThat(cipher.encrypt(null)).isNull()
        assertThat(cipher.decrypt(null)).isNull()
    }

    /**
     * The property that makes this worth doing. Deterministic encryption would let anyone with
     * read access see which employees share a bank account without decrypting anything — the
     * column would leak equality even while protecting content.
     */
    @Test
    fun `the same plaintext encrypts differently every time`() {
        val ciphertexts = (1..20).map { cipher.encrypt("1234567890")!! }.toSet()

        assertThat(ciphertexts).hasSize(20)
        ciphertexts.forEach { assertThat(cipher.decrypt(it)).isEqualTo("1234567890") }
    }

    @Test
    fun `the plaintext never appears in the stored value`() {
        val stored = cipher.encrypt("SUPERSECRETVALUE")!!

        assertThat(stored).doesNotContain("SUPERSECRET")
        // Also check the decoded bytes: base64 of a plaintext would not contain the plaintext as a
        // substring, so the check above alone would pass even if nothing were encrypted.
        val raw = String(Base64.getDecoder().decode(stored.removePrefix("v1:")), Charsets.ISO_8859_1)
        assertThat(raw).doesNotContain("SUPERSECRET")
    }

    /**
     * GCM authenticates as well as encrypts, so a modified value is detected rather than decrypted
     * into something else. Without that, an attacker with write access to the column could flip
     * bits in a bank account number and the application would use the result.
     */
    @Test
    fun `a tampered value is rejected rather than silently decrypted`() {
        val stored = cipher.encrypt("1234567890")!!
        val bytes = Base64.getDecoder().decode(stored.removePrefix("v1:"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        val tampered = "v1:" + Base64.getEncoder().encodeToString(bytes)

        assertThatThrownBy { cipher.decrypt(tampered) }
            .isInstanceOf(FieldDecryptionException::class.java)
    }

    /**
     * Not a null return. Treating an undecryptable bank account as absent would let a payroll run
     * continue with a missing beneficiary and no error.
     */
    @Test
    fun `a value written with another key raises rather than returning null`() {
        val other = FieldCipher(FieldCipher.generateKey())

        assertThatThrownBy { cipher.decrypt(other.encrypt("1234567890")) }
            .isInstanceOf(FieldDecryptionException::class.java)
    }

    @Test
    fun `an unversioned value is rejected`() {
        assertThatThrownBy { cipher.decrypt("bm90LWVuY3J5cHRlZA==") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    /**
     * Starting without a key would mean the failure surfaces on the first row that needs one —
     * during a payroll run, or when a user tries to sign in with MFA. Refusing to start is louder
     * and cheaper.
     */
    @Test
    fun `a missing or malformed key prevents startup`() {
        assertThatThrownBy { FieldCipher("") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("hr.crypto.field-key")

        assertThatThrownBy { FieldCipher("not base64!") }
            .isInstanceOf(IllegalArgumentException::class.java)

        // A 128-bit key where 256 is required — plausible, and silently weaker.
        val short = Base64.getEncoder().encodeToString(ByteArray(16))
        assertThatThrownBy { FieldCipher(short) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("32 bytes")
    }

    @Test
    fun `a generated key is accepted by the constructor`() {
        val key = FieldCipher.generateKey()

        assertThat(Base64.getDecoder().decode(key)).hasSize(32)
        assertThat(FieldCipher(key).decrypt(FieldCipher(key).encrypt("x"))).isEqualTo("x")
    }
}
