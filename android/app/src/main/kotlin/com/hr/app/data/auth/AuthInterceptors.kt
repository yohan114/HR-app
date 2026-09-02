package com.hr.app.data.auth

import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the bearer token to every authenticated request.
 *
 * Before this existed nothing on Android sent an `Authorization` header at all — including
 * `OutboxHttpSender`, which meant every queued mutation the user made offline would have come back
 * 401 on the next drain, been classified as `AuthenticationRequired`, and paused the outbox
 * forever. The write path was authenticated in design and anonymous in fact.
 */
@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val session: SessionStore,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            // Endpoints that establish a session cannot require one. Marked on the request rather
            // than matched by URL: a path list here would silently stop matching the day someone
            // renames an endpoint, and the failure would be an auth header on a login request —
            // which the server rejects with TENANT_MISMATCH rather than something diagnosable.
            if (request.header(HEADER_NO_AUTH) != null) {
                return chain.proceed(request.newBuilder().removeHeader(HEADER_NO_AUTH).build())
            }

            // `runBlocking` on OkHttp's own worker thread. The call is already off the main thread
            // by the time an interceptor runs, and the alternative — making the whole client
            // suspend — would mean reimplementing OkHttp's dispatcher.
            val token = runBlocking { session.accessToken() }

            val authenticated =
                if (token != null) {
                    request.newBuilder().header("Authorization", "Bearer $token").build()
                } else {
                    request
                }

            return chain.proceed(authenticated)
        }

        companion object {
            /** Set on requests that must go out unauthenticated. Stripped before sending. */
            const val HEADER_NO_AUTH = "X-HR-No-Auth"

            /** Marks a request as not requiring a session. */
            fun Request.Builder.withoutAuth(): Request.Builder = header(HEADER_NO_AUTH, "1")
        }
    }

/**
 * Refreshes once on a 401 and lets OkHttp retry.
 *
 * An [Authenticator] rather than an interceptor because OkHttp will replay the request for us with
 * whatever this returns, and will not loop indefinitely — [responseCount] is the guard that makes
 * that true.
 *
 * OkHttp calls this concurrently, from whichever threads got a 401. That is precisely the situation
 * [SessionStore] exists to survive: without its single-flight gate, six parallel 401s become six
 * refreshes, five of which present an already-rotated token and cause the server to revoke the
 * whole family.
 */
@Singleton
class TokenRefreshAuthenticator
    @Inject
    constructor(
        private val session: SessionStore,
    ) : Authenticator {
        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            // Give up after one retry. Without this a server that answers 401 to everything —
            // including the refresh endpoint — would spin here until the socket timed out.
            if (responseCount(response) >= 2) {
                Log.w(TAG, "Giving up after a retry still returned 401")
                return null
            }

            val stale = response.request.header("Authorization")?.removePrefix("Bearer ")
            val fresh = runBlocking { session.refreshAfterUnauthorized(stale) } ?: return null

            return response.request.newBuilder()
                .header("Authorization", "Bearer $fresh")
                .build()
        }

        private fun responseCount(response: Response): Int {
            var count = 1
            var prior = response.priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }

        private companion object {
            const val TAG = "TokenRefreshAuth"
        }
    }
