package com.hr.notification.internal

import com.hr.attendance.AttendanceAnomalyEvent
import com.hr.notification.DeliveryDecision
import com.hr.notification.NotificationChannel
import com.hr.notification.NotificationPriority
import com.hr.notification.decideDelivery
import com.hr.shared.persistence.Uuid7
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
class AttendanceNotificationListener(
    private val notificationSettingsService: NotificationSettingsService,
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val EVENT_ATTENDANCE_ANOMALY = "attendance.anomaly"
    }

    @EventListener
    @Transactional
    fun onAttendanceAnomaly(event: AttendanceAnomalyEvent) {
        log.info(
            "Processing AttendanceAnomalyEvent for employee {} (type={}, date={})",
            event.employeeId,
            event.anomalyType,
            event.workDate,
        )

        val userIds = jdbc.query(
            "SELECT id FROM app_user WHERE employee_id = ? AND status = 'ACTIVE'",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            event.employeeId,
        )

        if (userIds.isEmpty()) {
            log.info("No active app_user found for employee {}; skipping attendance anomaly notification", event.employeeId)
            return
        }

        val title = "Attendance Alert: ${event.anomalyType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}"
        val body = event.message
        val deepLink = "hrapp://time"
        val now = Instant.now()

        for (userId in userIds) {
            val hasPush = jdbc.queryForObject(
                "SELECT count(*) FROM user_device WHERE user_id = ? AND push_token IS NOT NULL AND revoked_at IS NULL",
                Int::class.java,
                userId,
            )?.let { it > 0 } ?: false

            val context = notificationSettingsService.contextFor(userId, EVENT_ATTENDANCE_ANOMALY, hasPush)
            val channelsToSend = mutableListOf<String>()

            for (channel in NotificationChannel.entries) {
                val decision = decideDelivery(channel, NotificationPriority.NORMAL, context, now)
                if (decision is DeliveryDecision.Send || decision is DeliveryDecision.Defer) {
                    channelsToSend.add(channel.name)
                }
            }

            if (channelsToSend.isEmpty()) continue

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
                EVENT_ATTENDANCE_ANOMALY,
                title,
                body,
                deepLink,
                NotificationPriority.NORMAL.name,
                channelsToSend.toTypedArray(),
                now,
            )

            for (channelName in channelsToSend) {
                val channel = NotificationChannel.valueOf(channelName)
                val decision = decideDelivery(channel, NotificationPriority.NORMAL, context, now)
                val status = when (decision) {
                    is DeliveryDecision.Send -> "PENDING"
                    is DeliveryDecision.Defer -> "PENDING"
                    is DeliveryDecision.Suppress -> "SUPPRESSED"
                }
                val nextAttempt = if (decision is DeliveryDecision.Defer) decision.until else now

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
}
