package com.hr.app.demo

import com.google.common.truth.Truth.assertThat
import com.hr.app.ui.settings.NotifiableEvent
import com.hr.app.ui.settings.collapse
import com.hr.app.ui.settings.expand
import com.hr.client.api.AuthenticationApi
import com.hr.client.api.DirectoryApi
import com.hr.client.api.EmployeesApi
import com.hr.client.api.MeApi
import com.hr.client.infrastructure.ApiClient
import com.hr.client.model.BiometricGrantRequest
import com.hr.client.model.DeviceInfo
import com.hr.client.model.NotificationChannel
import com.hr.client.model.PasswordGrantRequest
import com.hr.client.model.RefreshTokenRequest
import com.hr.client.model.RegisterDeviceRequest
import com.hr.client.model.ResolveTenantRequest
import com.hr.client.model.TokenResponse
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test

/**
 * The demo transport, driven exactly as the app drives it.
 *
 * ## Why this goes through Retrofit rather than calling the interceptor directly
 *
 * The claim under test is not "the interceptor returns a string". It is "the app can decode what
 * the interceptor returns **through the generated models**". Those models come from
 * `spec/openapi.yaml` and are unforgiving: a required property missing from the JSON throws
 * `MissingFieldException` at decode time, on a background thread, surfacing to the user as a
 * generic error message. Nothing about a fixture compiling says anything about that.
 *
 * So every test here builds the real `ApiClient`, creates the real generated Retrofit service, and
 * asserts on a decoded model. A payload that would fail on a device fails here.
 *
 * The bearer token is attached by a stand-in for `AuthInterceptor` rather than by the real one,
 * which needs `SessionStore` and a live session. What matters is only that the header is present:
 * the transport reads the caller's identity out of it and answers 401 without it, so an app that
 * stopped sending it would fail these tests rather than quietly demo fine.
 */
class DemoTransportTest {
    private var bearerToken: String? = null

    private val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = bearerToken
                val request =
                    if (token == null) {
                        chain.request()
                    } else {
                        chain.request().newBuilder().header("Authorization", "Bearer $token").build()
                    }
                chain.proceed(request)
            }
            .addInterceptor(DemoTransportInterceptor())
            .build()

    // The host is never resolved: every request in this file is answered before a socket is opened.
    private val apiClient = ApiClient(baseUrl = "http://demo.invalid/", callFactory = client)

    private val authApi = apiClient.createService(AuthenticationApi::class.java)
    private val meApi = apiClient.createService(MeApi::class.java)
    private val directoryApi = apiClient.createService(DirectoryApi::class.java)
    private val employeesApi = apiClient.createService(EmployeesApi::class.java)

    /** Signs in and adopts the session, as `AuthRepository` does. */
    private suspend fun signIn(username: String): TokenResponse {
        val response =
            authApi.issueToken(
                DemoCompany.TENANT_CODE,
                PasswordGrantRequest(
                    username = username,
                    password = "any password at all",
                    device =
                        DeviceInfo(
                            deviceId = "test-device",
                            platform = DeviceInfo.Platform.ANDROID,
                        ),
                ),
            )
        assertThat(response.isSuccessful).isTrue()
        val tokens = requireNotNull(response.body())
        bearerToken = tokens.accessToken
        return tokens
    }

    private suspend fun search(query: String?) =
        requireNotNull(
            directoryApi.searchDirectory(
                query = query,
                departmentId = null,
                locationId = null,
                cursor = null,
                limit = 50,
            ).body(),
        )

    // ------------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------------

    @Test
    fun `resolve tenant decodes and names the demo organisation`() =
        runBlocking<Unit> {
            val body =
                requireNotNull(authApi.resolveTenant("demo", ResolveTenantRequest(orgCode = "demo")).body())

            assertThat(body.code).isEqualTo("demo")
            assertThat(body.name).isEqualTo("Demo Company")
            assertThat(body.authMethods).isNotEmpty()
        }

    /** The whole reason the demo build exists: the sign-in form must never be a dead end. */
    @Test
    fun `any credentials are accepted`() =
        runBlocking<Unit> {
            val tokens = signIn("somebody nobody has ever heard of")

            assertThat(tokens.accessToken).isNotEmpty()
            assertThat(tokens.refreshToken).isNotEmpty()
            assertThat(tokens.expiresIn).isGreaterThan(0)
            assertThat(tokens.tokenType).isEqualTo(TokenResponse.TokenType.BEARER)
        }

    @Test
    fun `refresh rotates the token and keeps the identity`() =
        runBlocking<Unit> {
            val original = signIn("manager")

            val rotated =
                requireNotNull(
                    authApi.refreshToken("demo", RefreshTokenRequest(original.refreshToken)).body(),
                )
            assertThat(rotated.refreshToken).isNotEqualTo(original.refreshToken)

            bearerToken = rotated.accessToken
            assertThat(requireNotNull(meApi.getMe().body()).username).isEqualTo("manager")
        }

    /** Presenting the *access* token to the refresh endpoint is a client bug, and must not pass. */
    @Test
    fun `refresh refuses anything that is not a refresh token`() =
        runBlocking<Unit> {
            val tokens = signIn("admin")

            assertThat(authApi.refreshToken("demo", RefreshTokenRequest(tokens.accessToken)).code())
                .isEqualTo(401)
        }

    @Test
    fun `the biometric grant exchanges a sealed refresh token`() =
        runBlocking<Unit> {
            val tokens = signIn("employee")

            val response =
                authApi.biometricToken(
                    "demo",
                    BiometricGrantRequest(
                        sealedRefreshToken = tokens.refreshToken,
                        deviceId = "test-device",
                    ),
                )

            assertThat(response.isSuccessful).isTrue()
            assertThat(requireNotNull(response.body()).accessToken).isNotEmpty()
        }

    @Test
    fun `device registration decodes`() =
        runBlocking<Unit> {
            signIn("admin")

            val response =
                authApi.registerDevice(
                    RegisterDeviceRequest(
                        deviceId = "test-device",
                        platform = RegisterDeviceRequest.Platform.ANDROID,
                        model = "Pixel 8",
                        osVersion = "15",
                        appVersion = "0.1.0",
                    ),
                )

            assertThat(response.code()).isEqualTo(201)
            assertThat(requireNotNull(response.body()).deviceId).isEqualTo("test-device")
        }

    // ------------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------------

    @Test
    fun `me decodes and carries the demo organisation`() =
        runBlocking<Unit> {
            signIn("employee")

            val body = requireNotNull(meApi.getMe().body())

            assertThat(body.username).isEqualTo("employee")
            assertThat(body.tenant.code).isEqualTo("demo")
            assertThat(body.tenant.defaultCurrency).isEqualTo("LKR")
            assertThat(body.tenant.timezone).isEqualTo("Asia/Colombo")
            assertThat(body.employeeId).isEqualTo(DemoWorkforce.byCode("E004")!!.id)
            assertThat(body.permissions).contains("employee.directory")
        }

    /** Drives the Approvals tab: `admin` holds `employee.manage` and `employee` does not. */
    @Test
    fun `only the admin account confers approval authority`() =
        runBlocking<Unit> {
            signIn("admin")
            assertThat(requireNotNull(meApi.getMe().body()).permissions).contains("employee.manage")

            signIn("employee")
            assertThat(requireNotNull(meApi.getMe().body()).permissions).doesNotContain("employee.manage")
        }

    @Test
    fun `an unauthenticated request is refused rather than answered`() =
        runBlocking<Unit> {
            bearerToken = null

            assertThat(meApi.getMe().code()).isEqualTo(401)
        }

    /** Typing a colleague's name signs you in as them; anything unrecognised lands on `admin`. */
    @Test
    fun `the username selects who you sign in as`() =
        runBlocking<Unit> {
            signIn("anusha")
            val anusha = requireNotNull(meApi.getMe().body())
            assertThat(anusha.employeeId).isEqualTo(DemoWorkforce.byCode("E007")!!.id)
            assertThat(anusha.permissions).doesNotContain("employee.manage")

            signIn("qwerty")
            assertThat(requireNotNull(meApi.getMe().body()).username).isEqualTo("admin")
        }

    // ------------------------------------------------------------------------
    // Directory
    // ------------------------------------------------------------------------

    @Test
    fun `the directory decodes and returns the whole workforce`() =
        runBlocking<Unit> {
            signIn("employee")

            val page = search(null)

            assertThat(page.items).hasSize(DemoWorkforce.people.size)
            assertThat(page.hasMore).isFalse()
            assertThat(page.items.map { it.displayName }).contains("Nimali Wickramasinghe")
            assertThat(page.items.first { it.employeeCode == "E004" }.department).isEqualTo("Engineering")
            assertThat(page.items.first { it.employeeCode == "E004" }.location)
                .isEqualTo("Colombo Head Office")
        }

    @Test
    fun `the directory filters by name, department and employee code`() =
        runBlocking<Unit> {
            signIn("employee")

            assertThat(search("perera").items.map { it.employeeCode }).containsExactly("E005")
            assertThat(search("Finance").items.map { it.employeeCode }).containsExactly("E007", "E009")
            assertThat(search("E001").items.map { it.displayName }).containsExactly("Nimali Wickramasinghe")
        }

    // ------------------------------------------------------------------------
    // Employee profiles
    // ------------------------------------------------------------------------

    @Test
    fun `own profile decodes with the caller's own sensitive fields present`() =
        runBlocking<Unit> {
            signIn("employee")

            val profile = requireNotNull(employeesApi.getOwnEmployeeProfile().body())

            assertThat(profile.employeeCode).isEqualTo("E004")
            assertThat(profile.displayName).isEqualTo("Kasun Fernando")
            assertThat(profile.workEmail).isEqualTo("kasun.fernando@demo.local")
            assertThat(profile.mobile).isEqualTo("0771000004")
            assertThat(profile.joinDate).isNotNull()
            assertThat(profile.yearsOfService).isAtLeast(0)
            // Sensitive, and this is its owner: it is theirs to see.
            assertThat(profile.dateOfBirth).isNotNull()
        }

    /**
     * `employee.manage` does not confer sight of sensitive fields.
     *
     * Maintaining records and handling identity documents are separable duties, so even the
     * administrator gets a colleague's profile with no date of birth — and the form schema omits
     * the field entirely, so the screen never draws an empty row that discloses it exists.
     */
    @Test
    fun `a colleague's profile omits sensitive fields even for an administrator`() =
        runBlocking<Unit> {
            signIn("admin")
            val dilani = DemoWorkforce.byCode("E005")!!

            val profile = requireNotNull(employeesApi.getEmployeeProfile(dilani.id).body())
            assertThat(profile.displayName).isEqualTo("Dilani Perera")
            assertThat(profile.dateOfBirth).isNull()

            val schema = requireNotNull(employeesApi.getEmployeeEditForm(dilani.id).body())
            val keys = schema.sections.flatMap { section -> section.fields.map { it.key } }
            assertThat(keys).doesNotContain("dateOfBirth")
            assertThat(keys).doesNotContain("personalEmail")
            assertThat(keys).contains("workEmail")
        }

    @Test
    fun `the form schema decodes with labelled sections`() =
        runBlocking<Unit> {
            signIn("admin")

            val schema =
                requireNotNull(employeesApi.getEmployeeEditForm(DemoWorkforce.byCode("E002")!!.id).body())

            assertThat(schema.entityType).isEqualTo("employee")
            assertThat(schema.version).isEqualTo("base")
            assertThat(schema.sections.map { it.key })
                .containsExactly("personal", "contact", "employment", "workstation")
                .inOrder()
            assertThat(schema.sections.all { it.label.isNotBlank() }).isTrue()
        }

    /**
     * A record the caller may not open answers 404, not 403.
     *
     * A 403 confirms the record exists, which turns the endpoint into an oracle for enumerating
     * employee ids — so a forbidden record and a non-existent one must be indistinguishable.
     */
    @Test
    fun `an employee cannot open a colleague and cannot tell that from a missing record`() =
        runBlocking<Unit> {
            signIn("employee")

            val colleague = employeesApi.getEmployeeProfile(DemoWorkforce.byCode("E009")!!.id)
            val absent = employeesApi.getEmployeeProfile(demoId("employee", "NOBODY"))

            assertThat(colleague.code()).isEqualTo(404)
            assertThat(absent.code()).isEqualTo(404)
        }

    /** `manager` holds `employee.view` without `employee.view.all`, so the reporting line decides. */
    @Test
    fun `a manager sees their reporting line but not their own supervisor`() =
        runBlocking<Unit> {
            signIn("manager")

            assertThat(employeesApi.getEmployeeProfile(DemoWorkforce.byCode("E004")!!.id).code())
                .isEqualTo(200)
            assertThat(employeesApi.getEmployeeProfile(DemoWorkforce.byCode("E001")!!.id).code())
                .isEqualTo(404)
            assertThat(employeesApi.getEmployeeProfile(DemoWorkforce.byCode("E007")!!.id).code())
                .isEqualTo(404)
        }

    // ------------------------------------------------------------------------
    // Notification settings
    // ------------------------------------------------------------------------

    @Test
    fun `notification settings decode and round-trip through a save`() =
        runBlocking<Unit> {
            signIn("employee")

            val loaded = requireNotNull(meApi.getNotificationSettings().body())
            assertThat(loaded.quietHours?.timezone).isEqualTo("Asia/Colombo")
            // Sparse: only what differs from the default is stored, which is exactly what the
            // screen's expand/collapse pair exists to cope with.
            assertThat(loaded.preferences.map { it.eventKey }).contains("attendance.missing")

            // Expand to the dense matrix the screen draws, flip one switch, collapse it back — the
            // path the settings screen takes, so the fixture is exercised by the code that will
            // consume it rather than by an assertion written to match it.
            val switch = NotifiableEvent.LEAVE_DECIDED.key to NotificationChannel.EMAIL
            val matrix = expand(loaded).toMutableMap().apply { put(switch, false) }

            val saved =
                requireNotNull(meApi.replaceNotificationSettings(collapse(matrix, loaded.quietHours)).body())
            assertThat(expand(saved)[switch]).isFalse()

            // And it is still there on the next load, which is what makes the save button honest.
            assertThat(expand(requireNotNull(meApi.getNotificationSettings().body()))[switch]).isFalse()
        }
}
