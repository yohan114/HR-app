package com.hr.app.di

import com.hr.app.BuildConfig
import com.hr.app.data.auth.UnauthenticatedApi
import com.hr.client.api.AuthenticationApi
import com.hr.client.infrastructure.ApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The HTTP client the authentication endpoints use — deliberately *not* the app's.
 *
 * Two reasons, either sufficient:
 *
 * **Recursion.** A refresh sent through the authenticated client reaches `AuthInterceptor`, which
 * asks `SessionStore` for a token, which — finding it expired — begins a refresh, which sends the
 * same request again. Nothing breaks the cycle except the socket timeout.
 *
 * **The server refuses it.** `/v1/auth/token`, `/refresh`, `/resolve-tenant` and `/mfa/verify` take
 * `X-Tenant-Code`, and a bearer token alongside it is answered with `TENANT_MISMATCH` — once a
 * token exists the tenant comes from its claim, so sending both is a contradiction rather than
 * redundancy.
 *
 * Sharing a connection pool with the authenticated client would be a small optimisation and a large
 * footgun, because the sharing is invisible at the call site. Two clients, one obvious rule: if it
 * establishes a session, it uses this one.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    @UnauthenticatedApi
    fun provideUnauthenticatedClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    @UnauthenticatedApi
    fun provideAuthenticationApi(
        @UnauthenticatedApi client: OkHttpClient,
    ): AuthenticationApi =
        ApiClient(baseUrl = BuildConfig.API_BASE_URL, callFactory = client)
            .createService(AuthenticationApi::class.java)

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L
}
