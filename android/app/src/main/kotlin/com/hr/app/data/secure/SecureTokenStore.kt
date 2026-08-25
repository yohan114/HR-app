package com.hr.app.data.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the refresh token sealed behind the device's biometric hardware.
 *
 * ## The problem this solves
 *
 * The single most-cited complaint about the product we are replacing is that biometric sign-in
 * does not remove the password: *"fingerprint login is enabled, but username and password are
 * still required… like no one tested this app."* That happens when an app treats biometrics as a
 * UI gesture — check the fingerprint, then go and do a normal password login anyway.
 *
 * Here the biometric check is load-bearing. The refresh token is encrypted with an AES key that
 * lives in the Android Keystore and is created with `setUserAuthenticationRequired(true)`. The
 * key material never leaves secure hardware, and the OS refuses to let us use it until the user
 * has authenticated. There is no code path that reads the token without a successful biometric
 * prompt, because the cipher simply will not initialise.
 *
 * ## Why the key is invalidated when biometrics change
 *
 * `setInvalidatedByBiometricEnrolment(true)` means that enrolling a new fingerprint or face
 * destroys the key, and the sealed token becomes permanently unreadable. That is the correct
 * property: otherwise someone who gains access to an unlocked device could add their own
 * fingerprint and inherit the previous owner's session. We surface this as a normal
 * re-authentication, which is why [TokenUnsealResult.KeyInvalidated] exists as a distinct
 * outcome rather than a generic failure.
 */
@Singleton
class SecureTokenStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs by lazy {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        private val keyStore: KeyStore by lazy {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        }

        val hasSealedToken: Boolean
            get() = prefs.contains(KEY_SEALED_TOKEN) && keyStore.containsAlias(KEY_ALIAS)

        /**
         * Prepares a cipher for sealing.
         *
         * The caller passes this to `BiometricPrompt` and receives it back unlocked. Splitting
         * "prepare" from "use" is what binds the cryptographic operation to the biometric prompt
         * rather than merely sequencing them.
         */
        fun prepareSealCipher(): Cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            }

        /**
         * Prepares a cipher for unsealing.
         *
         * Returns [TokenUnsealResult.KeyInvalidated] when the device's biometric enrolment has
         * changed since the token was sealed. That is not an error to log and swallow — it means
         * the user must sign in with a password again, and the UI needs to say so.
         */
        fun prepareUnsealCipher(): CipherPreparation {
            val sealed = prefs.getString(KEY_SEALED_TOKEN, null)
                ?: return CipherPreparation.NoSealedToken
            val iv = prefs.getString(KEY_IV, null)
                ?: return CipherPreparation.NoSealedToken

            return try {
                val cipher =
                    Cipher.getInstance(TRANSFORMATION).apply {
                        init(
                            Cipher.DECRYPT_MODE,
                            existingKey() ?: return CipherPreparation.NoSealedToken,
                            javax.crypto.spec.IvParameterSpec(Base64.decode(iv, Base64.NO_WRAP)),
                        )
                    }
                CipherPreparation.Ready(cipher, sealed)
            } catch (e: KeyPermanentlyInvalidatedException) {
                // Biometrics were re-enrolled. The sealed token can never be recovered, so clear
                // it rather than leaving a permanently unreadable blob behind.
                clear()
                CipherPreparation.KeyInvalidated
            }
        }

        /** Seals a refresh token using a cipher already unlocked by a biometric prompt. */
        fun seal(
            refreshToken: String,
            unlockedCipher: Cipher,
        ) {
            val encrypted = unlockedCipher.doFinal(refreshToken.toByteArray(Charsets.UTF_8))
            prefs.edit {
                putString(KEY_SEALED_TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                putString(KEY_IV, Base64.encodeToString(unlockedCipher.iv, Base64.NO_WRAP))
            }
        }

        /** Unseals using a cipher already unlocked by a biometric prompt. */
        fun unseal(
            sealedToken: String,
            unlockedCipher: Cipher,
        ): String = String(unlockedCipher.doFinal(Base64.decode(sealedToken, Base64.NO_WRAP)), Charsets.UTF_8)

        /**
         * Removes the sealed token and destroys the key.
         *
         * Called on sign-out, on device revocation, and when the key is found to be invalidated.
         * Deleting the key as well as the ciphertext means there is nothing left to attack.
         */
        fun clear() {
            prefs.edit { remove(KEY_SEALED_TOKEN).remove(KEY_IV) }
            runCatching { keyStore.deleteEntry(KEY_ALIAS) }
        }

        private fun existingKey(): SecretKey? = keyStore.getKey(KEY_ALIAS, null) as? SecretKey

        private fun getOrCreateKey(): SecretKey =
            existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                            // The whole point: the OS will not let us use this key until the
                            // user has authenticated biometrically.
                            .setUserAuthenticationRequired(true)
                            // Adding a fingerprint or face must not grant access to an existing
                            // session.
                            .setInvalidatedByBiometricEnrollment(true)
                            .build(),
                    )
                }
                .generateKey()

        private companion object {
            const val PREFS_NAME = "hr_secure_tokens"
            const val KEY_ALIAS = "hr_refresh_token_key"
            const val KEY_SEALED_TOKEN = "sealed_refresh_token"
            const val KEY_IV = "sealed_refresh_token_iv"
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val TRANSFORMATION =
                "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"
        }
    }

/** Outcome of preparing to unseal. */
sealed interface CipherPreparation {
    /** Ready to prompt. Pass [cipher] to BiometricPrompt, then [SecureTokenStore.unseal]. */
    data class Ready(val cipher: Cipher, val sealedToken: String) : CipherPreparation

    /** Nothing sealed on this device — the user has not enrolled, so show the password form. */
    data object NoSealedToken : CipherPreparation

    /** Device biometrics changed. The token is unrecoverable; require a password. */
    data object KeyInvalidated : CipherPreparation
}

/** Result of an end-to-end unseal attempt, for the UI to branch on. */
sealed interface TokenUnsealResult {
    data class Success(val refreshToken: String) : TokenUnsealResult

    data object NoSealedToken : TokenUnsealResult

    data object KeyInvalidated : TokenUnsealResult

    data class Failed(val reason: String) : TokenUnsealResult
}
