package com.hr.app.data.auth

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A stable identity for this installation, and the organisation it last signed in to.
 *
 * ## Why not an Android device id
 *
 * `ANDROID_ID` is scoped per app-signing-key and survives uninstall, and the advertising id is
 * resettable and policy-encumbered. Neither is what this needs. The server treats a device as
 * something a refresh token is bound to and that a user can revoke from a list — so it wants an
 * identifier that is stable for the life of an *installation* and gone when the app is removed. A
 * random UUID in the app's own storage is exactly that, and it carries no cross-app tracking
 * meaning that would need declaring in a privacy policy.
 *
 * ## Why the tenant code is remembered
 *
 * `POST /v1/auth/token/refresh` requires `X-Tenant-Code`: the refresh token lookup is itself
 * tenant-scoped by row-level security, so the tenant has to be known before the token can be found.
 * A refresh happens long after the sign-in screen is gone, so the code has to outlive it.
 *
 * The organisation someone works for is not a secret and this is not a credential — it goes in
 * ordinary preferences, while the refresh token it accompanies is sealed in hardware.
 */
@Singleton
class DeviceIdProvider
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun deviceId(): String =
            prefs.getString(KEY_DEVICE_ID, null)
                ?: UUID.randomUUID().toString().also { prefs.edit { putString(KEY_DEVICE_ID, it) } }

        fun rememberTenantCode(code: String) = prefs.edit { putString(KEY_TENANT_CODE, code) }

        fun lastTenantCode(): String? = prefs.getString(KEY_TENANT_CODE, null)

        /** Called on sign-out. The device id survives — revoking a device should not rename it. */
        fun forgetTenant() = prefs.edit { remove(KEY_TENANT_CODE) }

        private companion object {
            const val PREFS = "hr_device"
            const val KEY_DEVICE_ID = "device_id"
            const val KEY_TENANT_CODE = "tenant_code"
        }
    }
