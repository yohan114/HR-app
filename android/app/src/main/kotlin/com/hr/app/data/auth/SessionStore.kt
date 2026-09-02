package com.hr.app.data.auth

import android.util.Log
import com.hr.app.data.sync.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the current session and refreshes it exactly once at a time.
 *
 * ## Single-flight is a correctness requirement, not an optimisation
 *
 * The server rotates refresh tokens on every exchange and treats a **reused** token as theft: it
 * revokes the entire token family, and every device sharing that login has to sign in again
 * (`AuthenticationService`, RFC 9700 §4.14.2).
 *
 * So the obvious implementation destroys itself. Six requests are in flight when the access token
 * expires. All six get a 401. All six call refresh with the same token. The first rotates it; the
 * other five present a token the server has just marked used — and the server, correctly, concludes
 * the token was stolen and revokes everything. The user is signed out of every device they own
 * because a screen loaded six widgets.
 *
 * OkHttp makes this the default outcome rather than an unlucky one: it calls
 * [okhttp3.Authenticator.authenticate] from whichever thread got the 401, concurrently, with no
 * coordination of its own. The mutex here is what prevents it.
 *
 * ## Why this does not touch the keystore
 *
 * `SecureTokenStore.seal` takes a `Cipher` that a biometric prompt has already unlocked, so
 * sealing needs an Activity and a user gesture. That is a real difference from iOS, where writing
 * does not prompt and only reading does — so the iOS counterpart reseals on every rotation and
 * this one cannot.
 *
 * The split is therefore: this class owns the *live* session, and biometric enrolment and unlock
 * are explicit screens that call [currentRefreshToken] and [adopt] around their own prompt.
 * Pretending otherwise would mean a fingerprint dialog appearing every fifteen minutes in the
 * middle of whatever the user was doing.
 *
 * One consequence worth stating: after a rotation the sealed copy is stale. It is single-use and
 * already spent, so a cold start that presents it gets `TOKEN_REUSE_DETECTED` and the family is
 * revoked — the exact failure this file is about. [ResealRequired] exists so the enrolment flow
 * can be re-run at a moment the user is already looking at the app.
 *
 * Mirrors `TokenProvider` on iOS. Kept deliberately parallel: the two platforms implement one
 * written protocol, and a divergence in *this* file is a divergence in who gets signed out.
 */
@Singleton
class SessionStore
    @Inject
    constructor(
        private val clock: Clock,
    ) {
        /** Exchanges a refresh token for a new pair. The seam onto `POST /v1/auth/token/refresh`. */
        fun interface RefreshService {
            suspend fun refresh(refreshToken: String): Tokens
        }

        data class Tokens(
            val accessToken: String,
            val refreshToken: String,
            val expiresAtMillis: Long,
        )

        private val mutex = Mutex()

        @Volatile
        private var current: Tokens? = null

        /** The in-progress refresh. This single field is the whole mechanism. */
        private var inFlight: CompletableDeferred<Tokens?>? = null

        private var refreshService: RefreshService? = null

        /**
         * Wired after construction to break a dependency cycle: the refresh call goes through the
         * same OkHttp client this store authenticates.
         */
        fun attach(service: RefreshService) {
            refreshService = service
        }

        val hasSession: Boolean get() = current != null

        /**
         * True when the token has rotated since it was last sealed, so the sealed copy is spent.
         *
         * Presenting a spent refresh token on the next cold start is read as theft by the server.
         * The enrolment screen watches this and re-seals while the user is present.
         */
        @Volatile
        var resealRequired: Boolean = false
            private set

        /** Adopts a freshly issued pair, e.g. after a password sign-in. */
        suspend fun adopt(tokens: Tokens) {
            mutex.withLock {
                current = tokens
                resealRequired = true
            }
        }

        /**
         * The live refresh token, for a biometric enrolment flow to seal.
         *
         * Returns null when there is no session. The caller seals it behind a prompt and then calls
         * [markSealed]; nothing else in the app should read this.
         */
        fun currentRefreshToken(): String? = current?.refreshToken

        fun markSealed() {
            resealRequired = false
        }

        /**
         * A usable access token, refreshing first if it is expired or nearly so.
         *
         * Null means there is no session at all. Callers surface that as "sign in again" rather
         * than retrying — an unauthenticated request repeated forever achieves nothing and drains
         * the battery.
         */
        suspend fun accessToken(): String? {
            val existing = current ?: return null
            if (clock.now() + REFRESH_MARGIN_MILLIS < existing.expiresAtMillis) return existing.accessToken
            return refresh()?.accessToken
        }

        /**
         * Forces a refresh after a 401 and returns the new access token.
         *
         * @param staleToken the access token the failed request used. Concurrent callers that
         *   arrive after a refresh has already completed get the *new* token back without
         *   triggering another — which is what stops six 401s becoming six rotations.
         */
        suspend fun refreshAfterUnauthorized(staleToken: String?): String? {
            val latest = current?.accessToken
            if (staleToken != null && latest != null && staleToken != latest) {
                // Somebody already refreshed while this request was in flight. Retrying with the
                // token we now hold is both correct and free.
                return latest
            }
            return refresh()?.accessToken
        }

        /**
         * Drops the session. Called on sign-out and on `TOKEN_REUSE_DETECTED`.
         *
         * Clearing the *sealed* copy is the caller's job, because that needs no prompt and belongs
         * with the sign-out flow that also clears the outbox and the local cache.
         */
        suspend fun clear() {
            mutex.withLock {
                current = null
                inFlight = null
                resealRequired = false
            }
        }

        // --------------------------------------------------------------------

        /**
         * The single-flight gate.
         *
         * The first caller creates the deferred and does the work; everyone arriving while it runs
         * awaits the same one. Note the mutex is released before awaiting — holding it across the
         * network call would serialise every caller behind the refresh *and* deadlock the
         * bookkeeping at the end.
         */
        private suspend fun refresh(): Tokens? {
            val (deferred, isLeader) =
                mutex.withLock {
                    val existing = inFlight
                    if (existing != null) {
                        existing to false
                    } else {
                        val created = CompletableDeferred<Tokens?>()
                        inFlight = created
                        created to true
                    }
                }

            if (!isLeader) return deferred.await()

            val result =
                try {
                    performRefresh()
                } catch (e: Exception) {
                    Log.w(TAG, "Token refresh failed", e)
                    null
                }

            mutex.withLock { inFlight = null }
            deferred.complete(result)
            return result
        }

        private suspend fun performRefresh(): Tokens? {
            val service = refreshService ?: return null
            val refreshToken = current?.refreshToken ?: return null

            return try {
                val fresh = service.refresh(refreshToken)
                mutex.withLock {
                    current = fresh
                    // The sealed copy is now a spent token. Sealing the new one needs a biometric
                    // prompt, which cannot be raised from here — see the class comment.
                    resealRequired = true
                }
                fresh
            } catch (e: TokenReuseDetectedException) {
                // The family is already gone server-side. Keeping the local copy would make every
                // later request fail in a way the UI cannot explain.
                Log.e(TAG, "Refresh token reuse reported by the server; clearing the session")
                mutex.withLock {
                    current = null
                    resealRequired = false
                }
                null
            }
        }

        private companion object {
            const val TAG = "SessionStore"

            /**
             * Refresh this long before expiry rather than exactly at it. Covers request latency and
             * modest device clock skew — without a margin, a token that is valid when checked can be
             * expired by the time it reaches the server, producing a 401 that looks like a bug.
             */
            const val REFRESH_MARGIN_MILLIS = 60_000L
        }
    }

/** Signals `TOKEN_REUSE_DETECTED`: the server has revoked the whole family. */
class TokenReuseDetectedException : RuntimeException("Refresh token reuse detected")
