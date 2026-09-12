package com.hr.app.notification

import android.os.Build
import com.hr.app.BuildConfig
import com.hr.app.data.auth.DeviceIdProvider
import com.hr.app.data.auth.SessionStore
import com.hr.client.api.AuthenticationApi
import com.hr.client.model.RegisterDeviceRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages client push notification tokens:
 * - Persists local token rotation via [DeviceIdProvider].
 * - Synchronizes updated device tokens with the server using `POST /v1/auth/devices`.
 */
@Singleton
class PushTokenManager
    @Inject
    constructor(
        private val deviceIdProvider: DeviceIdProvider,
        private val authApi: AuthenticationApi,
        private val sessionStore: SessionStore,
    ) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun onNewToken(token: String) {
        deviceIdProvider.updatePushToken(token)
        if (sessionStore.hasSession) {
            scope.launch {
                runCatching {
                    syncDeviceRegistration(token)
                }
            }
        }
    }

    suspend fun syncDeviceRegistration(token: String? = deviceIdProvider.pushToken()) {
        val request = RegisterDeviceRequest(
            deviceId = deviceIdProvider.deviceId(),
            platform = RegisterDeviceRequest.Platform.ANDROID,
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            osVersion = Build.VERSION.RELEASE,
            appVersion = BuildConfig.VERSION_NAME,
            pushToken = token,
        )
        authApi.registerDevice(request)
    }
}
