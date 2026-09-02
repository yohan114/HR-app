package com.hr.app.ui.auth

import com.hr.app.data.auth.AuthException
import com.hr.app.data.auth.AuthRepository
import com.hr.app.data.auth.DeviceIdProvider
import com.hr.app.data.auth.SignInOutcome
import com.hr.client.model.TokenResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * The sign-in state machine and its error messages.
 *
 * The messages matter as much as the transitions. Telling someone their password is wrong when the
 * train went into a tunnel sends them to reset a password that was fine, and "MFA required" is a
 * *success* that must not be rendered as a failure — the password was correct, and showing red text
 * at that moment teaches the user they did something wrong.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    private val auth = mockk<AuthRepository>()
    private val devices = mockk<DeviceIdProvider>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { devices.lastTenantCode() } returns null
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SignInViewModel(auth, devices)

    private fun tokens() =
        TokenResponse(
            accessToken = "access",
            refreshToken = "refresh",
            tokenType = TokenResponse.TokenType.BEARER,
            expiresIn = 900,
        )

    // ------------------------------------------------------------------------

    /**
     * A device that has signed in before should not be asked which company the user works for
     * again — it is the same answer every time, and the field is the most complained-about step in
     * the product this replaces.
     */
    @Test
    fun `a remembered organisation skips the first step`() {
        every { devices.lastTenantCode() } returns "demo"

        val state = viewModel().state.value

        assertEquals(SignInStep.CREDENTIALS, state.step)
        assertEquals("demo", state.tenantCode)
    }

    @Test
    fun `a fresh install starts at the organisation step`() {
        assertEquals(SignInStep.ORGANISATION, viewModel().state.value.step)
    }

    /**
     * The password was correct. Rendering this as an error would tell the user they did something
     * wrong at the exact moment they did not.
     */
    @Test
    fun `MFA required moves to the code step without an error`() =
        runTest(dispatcher) {
            coEvery { auth.signIn(any(), any(), any()) } returns
                Result.success(SignInOutcome.MfaRequired("challenge-token"))

            val model = viewModel()
            model.onOrgInputChanged("demo")
            every { devices.lastTenantCode() } returns "demo"
            model.onUsernameChanged("nimali")
            model.onPasswordChanged("secret")
            model.signIn()
            dispatcher.scheduler.advanceUntilIdle()

            val state = model.state.value
            assertEquals(SignInStep.MFA, state.step)
            assertEquals("challenge-token", state.mfaToken)
            assertNull("a successful password step showed an error", state.error)
        }

    /**
     * The second factor stands on its own, so holding the password in memory across another screen
     * is a window with no purpose.
     */
    @Test
    fun `the password is dropped once a code is required`() =
        runTest(dispatcher) {
            coEvery { auth.signIn(any(), any(), any()) } returns
                Result.success(SignInOutcome.MfaRequired("challenge"))

            val model = viewModel()
            model.onUsernameChanged("nimali")
            model.onPasswordChanged("secret")
            model.signIn()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals("", model.state.value.password)
        }

    @Test
    fun `a successful sign-in reports signed in`() =
        runTest(dispatcher) {
            coEvery { auth.signIn(any(), any(), any()) } returns
                Result.success(SignInOutcome.SignedIn(tokens()))

            val model = viewModel()
            model.onUsernameChanged("nimali")
            model.onPasswordChanged("secret")
            model.signIn()
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(model.state.value.signedIn)
            assertEquals("", model.state.value.password)
        }

    /**
     * The message a user acts on. Sending someone to reset a working password because their train
     * entered a tunnel is the specific failure this guards.
     */
    @Test
    fun `a network failure is not reported as bad credentials`() =
        runTest(dispatcher) {
            coEvery { auth.signIn(any(), any(), any()) } returns Result.failure(IOException("offline"))

            val model = viewModel()
            model.onUsernameChanged("nimali")
            model.onPasswordChanged("secret")
            model.signIn()
            dispatcher.scheduler.advanceUntilIdle()

            val error = model.state.value.error
            assertTrue("message was '$error'", error!!.contains("connection", ignoreCase = true))
        }

    @Test
    fun `server error codes map to their own messages`() =
        runTest(dispatcher) {
            val cases =
                mapOf(
                    "ACCOUNT_LOCKED" to "locked",
                    "ACCOUNT_DISABLED" to "not active",
                    "RATE_LIMITED" to "Too many",
                    "DEVICE_REVOKED" to "revoked",
                )

            cases.forEach { (code, expected) ->
                coEvery { auth.signIn(any(), any(), any()) } returns
                    Result.failure(AuthException(code, "developer-facing text"))

                val model = viewModel()
                model.onUsernameChanged("nimali")
                model.onPasswordChanged("secret")
                model.signIn()
                dispatcher.scheduler.advanceUntilIdle()

                val error = model.state.value.error
                assertTrue("$code produced '$error'", error!!.contains(expected, ignoreCase = true))
            }
        }

    /** The server's own message is developer-facing English and may carry internal detail. */
    @Test
    fun `an unknown code does not surface the server message`() =
        runTest(dispatcher) {
            coEvery { auth.signIn(any(), any(), any()) } returns
                Result.failure(AuthException("SOMETHING_NEW", "internal detail: connection pool exhausted"))

            val model = viewModel()
            model.onUsernameChanged("nimali")
            model.onPasswordChanged("secret")
            model.signIn()
            dispatcher.scheduler.advanceUntilIdle()

            val error = model.state.value.error
            assertTrue("leaked the server message: '$error'", !error!!.contains("connection pool"))
        }

    /** Authenticator codes are six digits. Anything else is a typo or a paste of something else. */
    @Test
    fun `the code field keeps only digits and caps at six`() {
        val model = viewModel()

        model.onCodeChanged("12 34 56")
        assertEquals("123456", model.state.value.code)

        model.onCodeChanged("1234567890")
        assertEquals("123456", model.state.value.code)

        model.onCodeChanged("abc123")
        assertEquals("123", model.state.value.code)
    }

    @Test
    fun `changing organisation resets the rest of the form`() {
        val model = viewModel()
        model.onUsernameChanged("nimali")
        model.onPasswordChanged("secret")

        model.changeOrganisation()

        val state = model.state.value
        assertEquals(SignInStep.ORGANISATION, state.step)
        assertEquals("", state.username)
        assertEquals("", state.password)
    }

    /** A submit with an empty field should do nothing rather than send a request that must fail. */
    @Test
    fun `signing in with empty fields does not call the server`() =
        runTest(dispatcher) {
            val model = viewModel()
            model.signIn()
            dispatcher.scheduler.advanceUntilIdle()

            assertNull(model.state.value.error)
            assertTrue(!model.state.value.busy)
        }
}
