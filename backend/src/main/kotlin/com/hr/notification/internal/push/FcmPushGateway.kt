package com.hr.notification.internal.push

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Production Firebase Cloud Messaging (FCM) HTTP v1 push gateway.
 */
class FcmPushGateway(
    private val projectId: String,
    private val accessTokenProvider: () -> String?,
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) : PushGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(
        token: String,
        title: String,
        body: String,
        deepLink: String?,
        data: Map<String, String>,
    ): PushResult {
        val accessToken = accessTokenProvider()
        if (accessToken == null) {
            log.warn("FCM credentials missing; skipping live push delivery")
            return PushResult.Failure("FCM OAuth2 access token unavailable", isInvalidToken = false)
        }

        val url = "https://fcm.googleapis.com/v1/projects/$projectId/messages:send"
        val payloadData = data.toMutableMap()
        if (deepLink != null) {
            payloadData["deep_link"] = deepLink
        }

        val fcmMessage = mapOf(
            "message" to mapOf(
                "token" to token,
                "notification" to mapOf(
                    "title" to title,
                    "body" to body,
                ),
                "data" to payloadData,
                "android" to mapOf(
                    "priority" to "HIGH",
                    "notification" to mapOf(
                        "channel_id" to "hr_default",
                    ),
                ),
            ),
        )

        val jsonBody = objectMapper.writeValueAsString(fcmMessage)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json; UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(Duration.ofSeconds(10))
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                val node = objectMapper.readTree(response.body())
                val messageId = node.path("name").asText("fcm-ok")
                PushResult.Success(messageId)
            } else {
                val isInvalid = response.statusCode() == 404 || response.body().contains("UNREGISTERED")
                log.error("FCM push failed [status={}]: {}", response.statusCode(), response.body())
                PushResult.Failure("FCM error ${response.statusCode()}: ${response.body()}", isInvalidToken = isInvalid)
            }
        } catch (ex: Exception) {
            log.error("Network failure during FCM push dispatch: {}", ex.message)
            PushResult.Failure("Network exception: ${ex.message}", isInvalidToken = false)
        }
    }
}
