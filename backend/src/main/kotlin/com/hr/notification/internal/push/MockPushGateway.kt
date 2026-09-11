package com.hr.notification.internal.push

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Mock implementation of [PushGateway] for local development and unit/integration testing.
 * Records all dispatched messages in memory for inspection.
 */
@Component
class MockPushGateway : PushGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    data class DispatchedPush(
        val messageId: String,
        val token: String,
        val title: String,
        val body: String,
        val deepLink: String?,
        val data: Map<String, String>,
    )

    private val sentPushes = CopyOnWriteArrayList<DispatchedPush>()

    override fun send(
        token: String,
        title: String,
        body: String,
        deepLink: String?,
        data: Map<String, String>,
    ): PushResult {
        if (token.isBlank() || token.startsWith("invalid_")) {
            log.warn("Mock push rejected for invalid token: {}", token)
            return PushResult.Failure("Invalid device token", isInvalidToken = true)
        }

        val messageId = "mock-msg-" + UUID.randomUUID().toString().take(8)
        val push = DispatchedPush(
            messageId = messageId,
            token = token,
            title = title,
            body = body,
            deepLink = deepLink,
            data = data,
        )
        sentPushes.add(push)
        log.info("Mock push sent successfully [msgId={}, token={}, title={}]", messageId, token.take(12) + "...", title)
        return PushResult.Success(messageId)
    }

    fun getSentPushes(): List<DispatchedPush> = sentPushes.toList()

    fun clear() {
        sentPushes.clear()
    }
}
