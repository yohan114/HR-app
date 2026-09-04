package com.hr.app.di

import com.hr.app.BuildConfig
import com.hr.client.api.DirectoryApi
import com.hr.client.api.EmployeesApi
import com.hr.client.api.MeApi
import com.hr.client.infrastructure.ApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * The authenticated API surface.
 *
 * Everything here goes through the app's own `OkHttpClient`, which carries `AuthInterceptor` and
 * `TokenRefreshAuthenticator` — so a caller never thinks about tokens, and a 401 is refreshed and
 * retried beneath it.
 *
 * Deliberately separate from `AuthModule`, whose endpoints must *not* carry a session. Splitting
 * them across two files rather than two functions in one makes the rule visible from the import
 * list: a file that imports this one is making authenticated calls.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    @Provides
    @Singleton
    fun provideApiClient(client: OkHttpClient): ApiClient =
        ApiClient(baseUrl = BuildConfig.API_BASE_URL, callFactory = client)

    @Provides
    @Singleton
    fun provideMeApi(apiClient: ApiClient): MeApi = apiClient.createService(MeApi::class.java)

    @Provides
    @Singleton
    fun provideDirectoryApi(apiClient: ApiClient): DirectoryApi =
        apiClient.createService(DirectoryApi::class.java)

    @Provides
    @Singleton
    fun provideEmployeesApi(apiClient: ApiClient): EmployeesApi =
        apiClient.createService(EmployeesApi::class.java)
}
