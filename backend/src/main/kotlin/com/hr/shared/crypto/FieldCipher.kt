package com.hr.shared.crypto

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Application-layer encryption for individual columns.
 *
 * ## Why columns are encrypted at all when the disk already is
 *
 * Storage encryption protects a stolen disk. It does nothing about the far likelier exposures: a
 * database backup copied to a laptop, a read replica handed to an analytics team, a support
 * engineer with a `psql` prompt, or an application bug that returns a row it should not. Those all
 * see plaintext through a fully-encrypted disk. The columns this protects — TOTP secrets, bank
 * account numbers, identity documents — are the ones where that difference matters.
 *
 * ## AES-256-GCM
 *
 * Authenticated encryption, so tampering is detected rather than silently decrypted into something
 * else. A random 96-bit IV per value, which is the size GCM is specified for; anything else forces
 * an extra hashing step internally and buys nothing.
 *
 * Deliberately **not** deterministic. Equal plaintexts must not produce equal ciphertexts, or the
 * column leaks equality — an attacker with read access could tell which employees share a bank
 * account without decrypting anything. The cost is that these columns cannot be indexed or joined
 * on, which is correct: a column you can search is a column you have not really protected.
 *
 * ## The version prefix
 *
 * Every value is stored as `v1:base64(iv‖ciphertext‖tag)`. The prefix exists so a future key
 * rotation can decrypt old values with the old key while writing new ones with the new. Without it,
 * rotation means a migration that must decrypt and re-encrypt every row in one transaction, and
 * that is the kind of operation that gets deferred until it is impossible.
 */
@Component
class FieldCipher(
    @Value("\${hr.crypto.field-key:}") configuredKey: String,
) {
    private val key: SecretKeySpec

    init {
        require(configuredKey.isNotBlank()) {
            "hr.crypto.field-key is not set. Encrypted columns cannot be read or written without " +
                "it, so the application refuses to start rather than failing later on the first " +
                "row that needs it."
        }

        val decoded =
            runCatching { Base64.getDecoder().decode(configuredKey) }
                .getOrElse { throw IllegalArgumentException("hr.crypto.field-key is not valid base64") }

        require(decoded.size == KEY_BYTES) {
            "hr.crypto.field-key must decode to $KEY_BYTES bytes for AES-256, got ${decoded.size}"
        }

        key = SecretKeySpec(decoded, "AES")
    }

    fun encrypt(plaintext: String?): String? {
        if (plaintext == null) return null

        val iv = ByteArray(IV_BYTES).also(RANDOM::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return PREFIX_V1 + Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    /**
     * @throws FieldDecryptionException when the value was written with a key we do not hold, or
     *   has been tampered with. Deliberately not a null return: silently treating an undecryptable
     *   bank account as absent would let a payroll run continue with a missing beneficiary.
     */
    fun decrypt(stored: String?): String? {
        if (stored == null) return null

        require(stored.startsWith(PREFIX_V1)) {
            "Encrypted value has no recognised version prefix"
        }

        return try {
            val bytes = Base64.getDecoder().decode(stored.removePrefix(PREFIX_V1))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES),
            )
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            // The message deliberately carries nothing about the value or the key.
            throw FieldDecryptionException("Could not decrypt a stored value", e)
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX_V1 = "v1:"
        private const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private val RANDOM = SecureRandom()

        /** Generates a key in the form the configuration expects. For provisioning, not runtime. */
        fun generateKey(): String {
            val bytes = ByteArray(KEY_BYTES).also(RANDOM::nextBytes)
            return Base64.getEncoder().encodeToString(bytes)
        }
    }
}

class FieldDecryptionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
