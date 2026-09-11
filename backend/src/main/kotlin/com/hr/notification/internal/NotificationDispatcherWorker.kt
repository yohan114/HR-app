package com.hr.notification.internal

import com.hr.notification.DeliveryFailure
import com.hr.notification.NotificationChannel
import com.hr.notification.RetryPolicy
import com.hr.notification.internal.push.PushGateway
import com.hr.notification.internal.push.PushResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/**
 * Scheduled background worker that sweeps due [notification_delivery] records and
 * dispatches them via the appropriate provider gateway.
 */
@Component
class NotificationDispatcherWorker(
    private val jdbc: JdbcTemplate,
    private val pushGateway: PushGateway,
    @Value("\${hr.notification.dispatcher.enabled:true}") private val enabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class PendingDelivery(
        val deliveryId: UUID,
        val tenantId: UUID,
        val notificationId: UUID,
        val channel: String,
        val attempt: Int,
        val userId: UUID,
        val title: String,
        val body: String,
        val deepLink: String?,
    )

    @Scheduled(fixedDelayString = "\${hr.notification.dispatcher.interval-ms:5000}")
    fun sweepAndDispatch() {
        if (!enabled) return

        val dueDeliveries = fetchDueDeliveries(batchSize = 50)
        if (dueDeliveries.isEmpty()) return

        log.debug("Found {} due notification deliveries to dispatch", dueDeliveries.size)

        for (delivery in dueDeliveries) {
            try {
                processDelivery(delivery)
            } catch (ex: Exception) {
                log.error("Unexpected error processing notification delivery {}: {}", delivery.deliveryId, ex.message, ex)
            }
        }
    }

    fun dispatchBatch(): Int {
        val dueDeliveries = fetchDueDeliveries(batchSize = 50)
        for (delivery in dueDeliveries) {
            processDelivery(delivery)
        }
        return dueDeliveries.size
    }

    private fun fetchDueDeliveries(batchSize: Int): List<PendingDelivery> {
        val sql = """
            SELECT d.id AS delivery_id, d.tenant_id, d.notification_id, d.channel, d.attempt,
                   n.user_id, n.title, n.body, n.deep_link
            FROM notification_delivery d
            JOIN notification n ON n.id = d.notification_id AND n.tenant_id = d.tenant_id
            WHERE d.status = 'PENDING'
              AND (d.next_attempt_at IS NULL OR d.next_attempt_at <= now())
            ORDER BY d.next_attempt_at ASC NULLS FIRST
            LIMIT ?
        """.trimIndent()

        return jdbc.query(sql, { rs: ResultSet, _: Int ->
            PendingDelivery(
                deliveryId = rs.getObject("delivery_id", UUID::class.java),
                tenantId = rs.getObject("tenant_id", UUID::class.java),
                notificationId = rs.getObject("notification_id", UUID::class.java),
                channel = rs.getString("channel"),
                attempt = rs.getInt("attempt"),
                userId = rs.getObject("user_id", UUID::class.java),
                title = rs.getString("title"),
                body = rs.getString("body"),
                deepLink = rs.getString("deep_link"),
            )
        }, batchSize)
    }

    @Transactional
    fun processDelivery(delivery: PendingDelivery) {
        val now = Instant.now()
        when (delivery.channel) {
            NotificationChannel.PUSH.name -> dispatchPush(delivery, now)
            NotificationChannel.IN_APP.name -> markDelivered(delivery.deliveryId, delivery.tenantId, "IN_APP", now)
            NotificationChannel.EMAIL.name -> markDelivered(delivery.deliveryId, delivery.tenantId, "EMAIL", now)
            else -> markDelivered(delivery.deliveryId, delivery.tenantId, "SYSTEM", now)
        }
    }

    private fun dispatchPush(delivery: PendingDelivery, now: Instant) {
        // Query active device push tokens for recipient user
        val tokens = jdbc.queryForList(
            "SELECT push_token FROM user_device WHERE user_id = ? AND push_token IS NOT NULL AND revoked_at IS NULL",
            String::class.java,
            delivery.userId,
        ).filter { it.isNotBlank() }

        if (tokens.isEmpty()) {
            log.info("No active device tokens found for user {}; marking delivery as DEAD_LETTERED", delivery.userId)
            markDeadLettered(delivery.deliveryId, delivery.tenantId, "No registered push tokens for user")
            return
        }

        var anyDelivered = false
        var lastError: String? = null

        for (token in tokens) {
            val result = pushGateway.send(
                token = token,
                title = delivery.title,
                body = delivery.body,
                deepLink = delivery.deepLink,
            )

            when (result) {
                is PushResult.Success -> {
                    anyDelivered = true
                }
                is PushResult.Failure -> {
                    lastError = result.errorMessage
                    if (result.isInvalidToken) {
                        log.warn("Device token rejected as invalid; revoking token: {}", token.take(12) + "...")
                        jdbc.update(
                            "UPDATE user_device SET revoked_at = ? WHERE push_token = ?",
                            now,
                            token,
                        )
                    }
                }
            }
        }

        if (anyDelivered) {
            markDelivered(delivery.deliveryId, delivery.tenantId, "PUSH", now)
        } else {
            handlePushFailure(delivery, lastError ?: "Push delivery failed", now)
        }
    }

    private fun handlePushFailure(delivery: PendingDelivery, errorMsg: String, now: Instant) {
        val nextAttempt = RetryPolicy.nextAttemptAt(delivery.attempt, now)
        if (nextAttempt == null) {
            log.warn("Exhausted all {} retry attempts for delivery {}; dead-lettering", RetryPolicy.MAX_ATTEMPTS, delivery.deliveryId)
            markDeadLettered(delivery.deliveryId, delivery.tenantId, errorMsg)
        } else {
            log.info("Scheduling retry #{} for delivery {} at {}", delivery.attempt + 1, delivery.deliveryId, nextAttempt)
            jdbc.update(
                """
                UPDATE notification_delivery
                SET attempt = attempt + 1,
                    next_attempt_at = ?,
                    error = ?,
                    status = 'PENDING'
                WHERE id = ? AND tenant_id = ?
                """.trimIndent(),
                nextAttempt,
                errorMsg,
                delivery.deliveryId,
                delivery.tenantId,
            )
        }
    }

    private fun markDelivered(deliveryId: UUID, tenantId: UUID, provider: String, now: Instant) {
        jdbc.update(
            """
            UPDATE notification_delivery
            SET status = 'DELIVERED',
                provider = ?,
                delivered_at = ?,
                error = NULL
            WHERE id = ? AND tenant_id = ?
            """.trimIndent(),
            provider,
            now,
            deliveryId,
            tenantId,
        )
    }

    private fun markDeadLettered(deliveryId: UUID, tenantId: UUID, reason: String) {
        jdbc.update(
            """
            UPDATE notification_delivery
            SET status = 'DEAD_LETTERED',
                error = ?
            WHERE id = ? AND tenant_id = ?
            """.trimIndent(),
            reason,
            deliveryId,
            tenantId,
        )
    }
}
