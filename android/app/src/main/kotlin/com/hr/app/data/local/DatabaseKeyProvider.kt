package com.hr.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the SQLCipher passphrase for the local database.
 *
 * The passphrase is random, generated once per install, and itself encrypted with a Keystore key
 * before being stored. It is *not* biometric-gated: the database must be readable by background
 * sync and outbox workers, which run without a user present. Requiring biometrics here would mean
 * the app could not sync unless someone was looking at it.
 *
 * The biometric gate belongs on the refresh token ([com.hr.app.data.secure.SecureTokenStore]) and
 * on individual sensitive screens such as payslips — not on the storage layer as a whole.
 *
 * What this protects against is offline extraction: a device image, an ADB backup, or a stolen
 * phone whose filesystem is read directly. The Keystore key is non-exportable, so the passphrase
 * cannot be recovered off-device.
 */
@Singleton
class DatabaseKeyProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

        private val keyStore: KeyStore by lazy {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        }

        /** The database passphrase, generating and sealing one on first use. */
        fun passphrase(): ByteArray {
            val stored = prefs.getString(KEY_SEALED_PASSPHRASE, null)
            val iv = prefs.getString(KEY_IV, null)

            if (stored != null && iv != null) {
                return runCatching { unseal(stored, iv) }
                    // If the sealed passphrase cannot be recovered the database is unreadable and
                    // there is nothing to salvage. Start over rather than leaving the app wedged;
                    // everything here is a cache of server state, not a source of truth.
                    .getOrElse { generateAndSeal() }
            }
            return generateAndSeal()
        }

        /**
         * Destroys the key and the sealed passphrase.
         *
         * Called on sign-out and device revocation. Combined with deleting the database file, a
         * revoked device retains no readable copy of tenant data (docs/sync-protocol.md §8).
         */
        fun clear() {
            prefs.edit { remove(KEY_SEALED_PASSPHRASE).remove(KEY_IV) }
            runCatching { keyStore.deleteEntry(KEY_ALIAS) }
        }

        private fun generateAndSeal(): ByteArray {
            val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
            val sealed = cipher.doFinal(passphrase)
            prefs.edit {
                putString(KEY_SEALED_PASSPHRASE, Base64.encodeToString(sealed, Base64.NO_WRAP))
                putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            }
            return passphrase
        }

        private fun unseal(
            sealed: String,
            iv: String,
        ): ByteArray {
            val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: error("Database key missing")
            val cipher =
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
                }
            return cipher.doFinal(Base64.decode(sealed, Base64.NO_WRAP))
        }

        private fun getOrCreateKey(): SecretKey =
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)
                ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                    .apply {
                        init(
                            KeyGenParameterSpec.Builder(
                                KEY_ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                // Deliberately NOT user-authentication-required: background
                                // workers must be able to open the database with nobody present.
                                .setUserAuthenticationRequired(false)
                                .build(),
                        )
                    }
                    .generateKey()

        private companion object {
            const val PREFS_NAME = "hr_db_key"
            const val KEY_ALIAS = "hr_database_key"
            const val KEY_SEALED_PASSPHRASE = "sealed_passphrase"
            const val KEY_IV = "passphrase_iv"
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val PASSPHRASE_BYTES = 32
            const val GCM_TAG_BITS = 128
        }
    }
