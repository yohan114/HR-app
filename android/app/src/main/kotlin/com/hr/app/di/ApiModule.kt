package com.hr.app.di

import com.hr.app.BuildConfig
import com.hr.client.api.ApprovalsApi
import com.hr.client.api.AttendanceApi
import com.hr.client.api.DirectoryApi
import com.hr.client.api.EmployeesApi
import com.hr.client.api.LeaveApi
import com.hr.client.api.MeApi
import com.hr.client.api.MobileApi
import com.hr.client.api.PayrollApi
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

    @Provides
    @Singleton
    fun provideMobileApi(apiClient: ApiClient): MobileApi =
        apiClient.createService(MobileApi::class.java)

    @Provides
    @Singleton
    fun provideAttendanceApi(apiClient: ApiClient): AttendanceApi =
        apiClient.createService(AttendanceApi::class.java)

    @Provides
    @Singleton
    fun provideApprovalsApi(apiClient: ApiClient): ApprovalsApi =
        apiClient.createService(ApprovalsApi::class.java)

    @Provides
    @Singleton
    fun provideLeaveApi(apiClient: ApiClient): LeaveApi =
        apiClient.createService(LeaveApi::class.java)

    @Provides
    @Singleton
    fun providePayrollApi(apiClient: ApiClient): PayrollApi =
        apiClient.createService(PayrollApi::class.java)

    @Provides
    @Singleton
    fun provideLoansApi(apiClient: ApiClient): com.hr.client.api.LoansApi =
        apiClient.createService(com.hr.client.api.LoansApi::class.java)

    @Provides
    @Singleton
    fun provideExpensesApi(apiClient: ApiClient): com.hr.client.api.ExpensesApi =
        apiClient.createService(com.hr.client.api.ExpensesApi::class.java)

    @Provides
    @Singleton
    fun provideBenefitsApi(apiClient: ApiClient): com.hr.client.api.BenefitsApi =
        apiClient.createService(com.hr.client.api.BenefitsApi::class.java)

    @Provides
    @Singleton
    fun provideLifecycleApi(apiClient: ApiClient): com.hr.client.api.LifecycleApi =
        apiClient.createService(com.hr.client.api.LifecycleApi::class.java)

    @Provides
    @Singleton
    fun provideGrievanceApi(apiClient: ApiClient): com.hr.client.api.GrievanceApi =
        apiClient.createService(com.hr.client.api.GrievanceApi::class.java)

    @Provides
    @Singleton
    fun provideDisciplinaryApi(apiClient: ApiClient): com.hr.client.api.DisciplinaryApi =
        apiClient.createService(com.hr.client.api.DisciplinaryApi::class.java)

    @Provides
    @Singleton
    fun providePerformanceApi(apiClient: ApiClient): com.hr.client.api.PerformanceApi =
        apiClient.createService(com.hr.client.api.PerformanceApi::class.java)

    @Provides
    @Singleton
    fun provideRecruitmentApi(apiClient: ApiClient): com.hr.client.api.RecruitmentApi =
        apiClient.createService(com.hr.client.api.RecruitmentApi::class.java)

    @Provides
    @Singleton
    fun provideOnboardingApi(apiClient: ApiClient): com.hr.client.api.OnboardingApi =
        apiClient.createService(com.hr.client.api.OnboardingApi::class.java)

    @Provides
    @Singleton
    fun provideOffboardingApi(apiClient: ApiClient): com.hr.client.api.OffboardingApi =
        apiClient.createService(com.hr.client.api.OffboardingApi::class.java)

    @Provides
    @Singleton
    fun provideDocumentsApi(apiClient: ApiClient): com.hr.client.api.DocumentsApi =
        apiClient.createService(com.hr.client.api.DocumentsApi::class.java)

    @Provides
    @Singleton
    fun provideSignaturesApi(apiClient: ApiClient): com.hr.client.api.SignaturesApi =
        apiClient.createService(com.hr.client.api.SignaturesApi::class.java)

    @Provides
    @Singleton
    fun provideTrainingApi(apiClient: ApiClient): com.hr.client.api.TrainingApi =
        apiClient.createService(com.hr.client.api.TrainingApi::class.java)

    @Provides
    @Singleton
    fun provideTimesheetsApi(apiClient: ApiClient): com.hr.client.api.TimesheetsApi =
        apiClient.createService(com.hr.client.api.TimesheetsApi::class.java)
}



