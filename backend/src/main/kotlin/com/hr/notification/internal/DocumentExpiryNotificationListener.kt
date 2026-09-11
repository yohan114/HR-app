package com.hr.notification.internal

import com.hr.employee.DocumentExpiringEvent
import com.hr.notification.DeliveryDecision
import com.hr.notification.NotificationChannel
import com.hr.notification.NotificationPriority
import com.hr.notification.decideDelivery
import com.hr.shared.persistence.Uuid7
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Listens for [DocumentExpiringEvent] from the employee module and dispatches alerts
 * to the employee's user account, applying user channel preferences and quiet hours.
 *
 * Implements the notification consumer half of P1-BE-40.
 */
@Component
class DocumentExpiryNotificationListener(
    private val notificationSettingsService: NotificationSettingsService,
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val EVENT_KEY = "document.expiring"
    }

    @EventListener
    @Transactional
    fun onDocumentExpiring(event: DocumentExpiringEvent) {
        log.info(
            "Processing DocumentExpiringEvent for employee {} document {} (type={}, daysRemaining={})",
            event.employeeId,
            event.documentId,
            event.docType,
            event.daysRemaining,
        )

        val userIds =
            jdbc.query(
                "SELECT id FROM app_user WHERE employee_id = ? AND status = 'ACTIVE'",
                { rs, _ -> rs.getObject("id", UUID::class.java) },
                event.employeeId,
            )

        if (userIds.isEmpty()) {
            log.info("No active app_user found for employee {}; skipping direct user notification", event.employeeId)
            return
        }

        val priority =
            if (event.isExpired) {
                NotificationPriority.HIGH
            } else {
                NotificationPriority.NORMAL
            }

        val title =
            if (event.isExpired) {
                "Document Expired: ${formatDocType(event.docType)}"
            } else {
                "Document Expiring: ${formatDocType(event.docType)}"
            }

        val body =
            if (event.isExpired) {
                "Your ${formatDocType(event.docType)} expired on ${event.expiryDate}. Please submit a renewed copy."
            } else {
                "Your ${formatDocType(event.docType)} will expire in ${event.daysRemaining} days (on ${event.expiryDate})."
            }

        val deepLink = "hrapp://documents/${event.documentId}"
        val now = Instant.now()

        for (userId in userIds) {
            val hasPush =
                jdbc.queryForObject(
                    "SELECT count(*) FROM user_device WHERE user_id = ? AND push_token IS NOT NULL AND revoked_at IS NULL",
                    Int::class.java,
                    userId,
                )?.let { it > 0 } ?: false

            val context = notificationSettingsService.contextFor(userId, EVENT_KEY, hasPush)
            val channelsToSend = mutableListOf<String>()

            for (channel in NotificationChannel.entries) {
                val decision = decideDelivery(channel, priority, context, now)
                if (decision is DeliveryDecision.Send || decision is DeliveryDecision.Defer) {
                    channelsToSend.add(channel.name)
                }
            }

            if (channelsToSend.isEmpty()) {
                log.debug("All channels suppressed for user {} on event {}", userId, EVENT_KEY)
                continue
            }

            val notificationId = Uuid7.generate()
            jdbc.update(
                """
                INSERT INTO notification (id, tenant_id, user_id, event_key, title, body, deep_link,
                                          priority, channels, data, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::text[], '{}'::jsonb, ?)
                """.trimIndent(),
                notificationId,
                event.tenantId,
                userId,
                EVENT_KEY,
                title,
                body,
                deepLink,
                priority.name,
                channelsToSend.toTypedArray(),
                now,
            )

            for (channelName in channelsToSend) {
                val channel = NotificationChannel.valueOf(channelName)
                val decision = decideDelivery(channel, priority, context, now)
                val status =
                    when (decision) {
                        is DeliveryDecision.Send -> "PENDING"
                        is DeliveryDecision.Defer -> "PENDING"
                        is DeliveryDecision.Suppress -> "SUPPRESSED"
                    }
                val nextAttempt =
                    if (decision is DeliveryDecision.Defer) {
                        decision.until
                    } else {
                        now
                    }

                jdbc.update(
                    """
                    INSERT INTO notification_delivery (id, tenant_id, notification_id, channel, status,
                                                       attempt, attempted_at, next_attempt_at)
                    VALUES (?, ?, ?, ?, ?, 1, ?, ?)
                    """.trimIndent(),
                    Uuid7.generate(),
                    event.tenantId,
                    notificationId,
                    channelName,
                    status,
                    now,
                    nextAttempt,
                )
            }
        }
    }

    private fun formatDocType(type: String): String =
        type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
