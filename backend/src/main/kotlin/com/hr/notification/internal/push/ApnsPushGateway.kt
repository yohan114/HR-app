package com.hr.notification.internal.push

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Production Apple Push Notification service (APNs) HTTP/2 push gateway.
 */
class ApnsPushGateway(
    private val topic: String = "io.hrapp",
    private val isProduction: Boolean = false,
    private val jwtTokenProvider: () -> String?,
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
        val jwtToken = jwtTokenProvider()
        if (jwtToken == null) {
            log.warn("APNs token provider returned null; skipping live iOS push delivery")
            return PushResult.Failure("APNs authentication token unavailable", isInvalidToken = false)
        }

        val host = if (isProduction) "api.push.apple.com" else "api.sandbox.push.apple.com"
        val url = "https://$host/3/device/$token"

        val apnsPayload = mutableMapOf<String, Any>(
            "aps" to mapOf(
                "alert" to mapOf(
                    "title" to title,
                    "body" to body,
                ),
                "sound" to "default",
                "badge" to 1,
            ),
        )
        if (deepLink != null) {
            apnsPayload["deep_link"] = deepLink
        }
        apnsPayload.putAll(data)

        val jsonBody = objectMapper.writeValueAsString(apnsPayload)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("authorization", "bearer $jwtToken")
            .header("apns-topic", topic)
            .header("apns-push-type", "alert")
            .header("apns-priority", "10")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(Duration.ofSeconds(10))
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val apnsId = response.headers().firstValue("apns-id").orElse("apns-ok")
            if (response.statusCode() in 200..299) {
                PushResult.Success(apnsId)
            } else {
                val isInvalid = response.statusCode() in listOf(400, 410) &&
                    (response.body().contains("BadDeviceToken") || response.body().contains("Unregistered"))
                log.error("APNs push failed [status={}, apns-id={}]: {}", response.statusCode(), apnsId, response.body())
                PushResult.Failure("APNs error ${response.statusCode()}: ${response.body()}", isInvalidToken = isInvalid)
            }
        } catch (ex: Exception) {
            log.error("Network failure during APNs push dispatch: {}", ex.message)
            PushResult.Failure("Network exception: ${ex.message}", isInvalidToken = false)
        }
    }
}
