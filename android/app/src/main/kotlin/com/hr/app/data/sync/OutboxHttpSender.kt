package com.hr.app.data.sync

import android.util.Log
import com.hr.app.BuildConfig
import com.hr.app.data.local.OutboxEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends outbox entries over HTTP.
 *
 * Deliberately generic rather than typed: an outbox entry already carries a method, a path and a
 * serialised body. Routing each entry through its typed generated-client method would mean a
 * `when` over every mutation in the product, which is a maintenance burden that grows with every
 * feature and buys nothing — the payload was serialised from a typed object when it was enqueued.
 *
 * The status-code mapping is the important part, and follows docs/sync-protocol.md §4.4.
 */
@Singleton
class OutboxHttpSender
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val json: Json,
    ) : OutboxSender {
        override suspend fun send(entry: OutboxEntry): SendOutcome =
            withContext(Dispatchers.IO) {
                try {
                    val request =
                        Request.Builder()
                            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/" + entry.path.trimStart('/'))
                            .method(entry.httpMethod, entry.payload.toRequestBody(JSON_MEDIA_TYPE))
                            // The contract that makes unlimited retry safe. Generated once when
                            // the entry was created, never regenerated here.
                            .header("Idempotency-Key", entry.idempotencyKey)
                            .build()

                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        when {
                            response.isSuccessful -> SendOutcome.Confirmed

                            // Someone else acted first. That is a settled outcome, not a failure —
                            // retrying would never change it, and surfacing it as an error would
                            // be misleading.
                            response.code == 409 && errorCode(body) == "ALREADY_DECIDED" ->
                                SendOutcome.Confirmed

                            response.code == 401 -> SendOutcome.AuthenticationRequired

                            response.code == 429 || response.code >= 500 -> SendOutcome.Retryable

                            response.code in 400..499 ->
                                SendOutcome.Rejected(errorCode(body), errorMessage(body))

                            else -> SendOutcome.Retryable
                        }
                    }
                } catch (e: IOException) {
                    // Network unavailable, DNS failure, timeout. Always retryable.
                    Log.d(TAG, "Network failure sending outbox entry ${entry.id}: ${e.message}")
                    SendOutcome.Retryable
                } catch (e: Exception) {
                    // A malformed entry would fail identically forever, so retrying is pointless.
                    Log.w(TAG, "Unrecoverable failure sending outbox entry ${entry.id}", e)
                    SendOutcome.Rejected("CLIENT_ERROR", e.message)
                }
            }

        /** Extracts `error.code` from the standard envelope (docs/03-architecture.md §9). */
        private fun errorCode(body: String): String? = errorField(body, "code")

        private fun errorMessage(body: String): String? = errorField(body, "message")

        private fun errorField(
            body: String,
            field: String,
        ): String? =
            runCatching {
                json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get(field)?.jsonPrimitive?.content
            }.getOrNull()

        private companion object {
            const val TAG = "OutboxHttpSender"
            val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        }
    }
