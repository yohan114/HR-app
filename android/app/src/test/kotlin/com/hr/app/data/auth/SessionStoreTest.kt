package com.hr.app.data.auth

import com.hr.app.data.sync.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The single-flight refresh guarantee.
 *
 * The most consequential property in the networking layer, and the one whose absence would be
 * hardest to diagnose in the field. The server rotates refresh tokens and treats reuse as theft by
 * revoking the whole family — so a client that refreshes concurrently signs the user out of every
 * device they own, and nothing crashes, so nothing is reported.
 *
 * OkHttp calls `Authenticator.authenticate` concurrently from whichever threads got a 401, which
 * makes the bad case the *default* rather than an unlucky race. It needs concurrency to reproduce,
 * so it will not appear in manual testing and will appear under real network conditions.
 *
 * Mirrors `TokenProviderTests` on iOS, deliberately: a divergence in this behaviour is a divergence
 * in who gets signed out.
 */
class SessionStoreTest {
    private val refreshCalls = AtomicInteger(0)
    private var issued = 0

    /** Held open so callers pile up behind an in-flight refresh, as they do on a real 401 storm. */
    private val gate = CompletableDeferred<Unit>()

    private var now = 1_000_000L

    private fun store(
        service: SessionStore.RefreshService = holdingService(),
    ): SessionStore = SessionStore(Clock { now }).apply { attach(service) }

    private fun holdingService() =
        SessionStore.RefreshService {
            refreshCalls.incrementAndGet()
            gate.await()
            issued++
            SessionStore.Tokens("access-$issued", "refresh-$issued", now + 900_000)
        }

    private fun immediateService() =
        SessionStore.RefreshService {
            refreshCalls.incrementAndGet()
            issued++
            SessionStore.Tokens("access-$issued", "refresh-$issued", now + 900_000)
        }

    private suspend fun SessionStore.withExpiredSession() =
        adopt(SessionStore.Tokens("stale", "refresh-0", expiresAtMillis = now - 1))

    // ------------------------------------------------------------------------

    /**
     * Ten simultaneous callers, one refresh. Without the gate this is ten rotations: one succeeds
     * and nine present a token the server has already spent, which it correctly reads as theft.
     */
    @Test
    fun `concurrent callers trigger exactly one refresh`() =
        runTest {
            val session = store()
            session.withExpiredSession()

            val callers = (1..10).map { async { session.accessToken() } }
            gate.complete(Unit)
            val tokens = callers.awaitAll()

            assertEquals("each concurrent caller started its own refresh", 1, refreshCalls.get())
            assertEquals("callers saw different tokens", 1, tokens.filterNotNull().toSet().size)
            assertEquals("some callers got no token", 10, tokens.filterNotNull().size)
        }

    @Test
    fun `a valid token is returned without refreshing`() =
        runTest {
            val session = store(immediateService())
            session.adopt(SessionStore.Tokens("good", "r0", expiresAtMillis = now + 600_000))

            assertEquals("good", session.accessToken())
            assertEquals(0, refreshCalls.get())
        }

    /**
     * Refreshing a minute early covers request latency and modest clock skew. A token that is valid
     * when checked but expired when it lands produces a 401 that looks like a bug.
     */
    @Test
    fun `a token inside the margin is refreshed`() =
        runTest {
            val session = store(immediateService())
            session.adopt(SessionStore.Tokens("nearly", "r0", expiresAtMillis = now + 30_000))

            assertEquals("access-1", session.accessToken())
        }

    @Test
    fun `no session returns null rather than refreshing`() =
        runTest {
            val session = store(immediateService())

            assertNull(session.accessToken())
            assertEquals(0, refreshCalls.get())
        }

    /**
     * The gate must reset. If the in-flight deferred were left in place after completing, every
     * later refresh would return the first one's result and the session would expire and never
     * recover.
     */
    @Test
    fun `a second refresh is possible after the first completes`() =
        runTest {
            val session = store(immediateService())
            session.withExpiredSession()

            assertEquals("access-1", session.accessToken())
            assertEquals("access-2", session.refreshAfterUnauthorized("access-1"))
            assertEquals("the single-flight gate did not reset", 2, refreshCalls.get())
        }

    /**
     * The 401 that arrives *after* somebody else already refreshed. Retrying with the token we now
     * hold is correct and free; refreshing again would spend a second token for nothing.
     */
    @Test
    fun `a stale 401 reuses the already-refreshed token`() =
        runTest {
            val session = store(immediateService())
            session.adopt(SessionStore.Tokens("current", "r0", expiresAtMillis = now + 600_000))

            assertEquals("current", session.refreshAfterUnauthorized("an-older-token"))
            assertEquals("refreshed despite already holding a newer token", 0, refreshCalls.get())
        }

    /**
     * The server has already revoked the family; holding the local copy would make every later
     * request fail in a way the UI cannot explain.
     */
    @Test
    fun `reuse detection clears the session`() =
        runTest {
            val session = store(SessionStore.RefreshService { throw TokenReuseDetectedException() })
            session.withExpiredSession()

            assertNull(session.accessToken())
            assertFalse("the session survived a reuse report", session.hasSession)
        }

    /** A network blip is not a sign-out. The session is still valid; the connection was not. */
    @Test
    fun `a transport failure leaves the session intact`() =
        runTest {
            val session = store(SessionStore.RefreshService { throw java.io.IOException("offline") })
            session.withExpiredSession()

            assertNull(session.accessToken())
            assertTrue("a network blip signed the user out", session.hasSession)
        }

    /**
     * After a rotation the sealed copy is a spent token, and presenting it on the next cold start
     * is read as theft. The enrolment flow watches this flag to re-seal while the user is present.
     */
    @Test
    fun `a rotation marks the sealed copy as stale`() =
        runTest {
            val session = store(immediateService())
            session.withExpiredSession()
            session.markSealed()
            assertFalse(session.resealRequired)

            session.accessToken()

            assertTrue("a rotation left the sealed token looking current", session.resealRequired)
        }

    @Test
    fun `clearing drops the session`() =
        runTest {
            val session = store(immediateService())
            session.adopt(SessionStore.Tokens("a", "r", now + 600_000))

            session.clear()

            assertFalse(session.hasSession)
            assertNull(session.currentRefreshToken())
        }
}
