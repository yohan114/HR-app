package com.hr.notification.internal

import com.hr.leave.LeaveAppliedEvent
import com.hr.leave.LeaveApprovedEvent
import com.hr.leave.LeaveRejectedEvent
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

/**
 * Listens for leave lifecycle domain events ([LeaveAppliedEvent], [LeaveApprovedEvent], [LeaveRejectedEvent])
 * and translates them into persisted notifications with channel decisions, quiet hours handling,
 * and deep links (`hrapp://approvals` or `hrapp://leave/{id}`).
 */
@Component
class LeaveNotificationListener(
    private val notificationSettingsService: NotificationSettingsService,
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val EVENT_LEAVE_APPLIED = "leave.applied"
        const val EVENT_LEAVE_APPROVED = "leave.approved"
        const val EVENT_LEAVE_REJECTED = "leave.rejected"
    }

    @EventListener
    @Transactional
    fun onLeaveApplied(event: LeaveAppliedEvent) {
        log.info(
            "Processing LeaveAppliedEvent for employee {} application {} ({} days)",
            event.employeeId,
            event.applicationId,
            event.totalDays,
        )

        // Find direct manager's active user account
        var approverUserIds = jdbc.query(
            """
            SELECT u.id 
            FROM app_user u 
            JOIN employee e ON e.id = u.employee_id 
            WHERE e.id = (SELECT reports_to FROM employee WHERE id = ?)
              AND u.status = 'ACTIVE'
            """.trimIndent(),
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            event.employeeId,
        )

        // Fallback: notify tenant managers/admins if no direct manager is linked
        if (approverUserIds.isEmpty()) {
            approverUserIds = jdbc.query(
                """
                SELECT id FROM app_user 
                WHERE tenant_id = ? AND status = 'ACTIVE' 
                  AND (role IN ('ADMIN', 'HR_ADMIN', 'MANAGER') OR 'leave.approve' = ANY(permissions))
                LIMIT 5
                """.trimIndent(),
                { rs, _ -> rs.getObject("id", UUID::class.java) },
                event.tenantId,
            )
        }

        if (approverUserIds.isEmpty()) {
            log.info("No approver user accounts found for tenant {}; skipping leave applied notification", event.tenantId)
            return
        }

        val title = "New Leave Request: ${event.leaveTypeName}"
        val body = "Leave application submitted for ${event.totalDays} day(s) from ${event.startDate} to ${event.endDate} requiring approval."
        val deepLink = "hrapp://approvals"

        for (userId in approverUserIds) {
            dispatchNotification(
                tenantId = event.tenantId,
                userId = userId,
                eventKey = EVENT_LEAVE_APPLIED,
                priority = NotificationPriority.NORMAL,
                title = title,
                body = body,
                deepLink = deepLink,
            )
        }
    }

    @EventListener
    @Transactional
    fun onLeaveApproved(event: LeaveApprovedEvent) {
        log.info(
            "Processing LeaveApprovedEvent for employee {} application {}",
            event.employeeId,
            event.applicationId,
        )

        val recipientUserIds = jdbc.query(
            "SELECT id FROM app_user WHERE employee_id = ? AND status = 'ACTIVE'",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            event.employeeId,
        )

        if (recipientUserIds.isEmpty()) {
            log.info("No active app_user found for employee {}; skipping leave approved notification", event.employeeId)
            return
        }

        val title = "Leave Request Approved: ${event.leaveTypeName}"
        val body = "Your leave request for ${event.totalDays} day(s) (${event.startDate} to ${event.endDate}) has been approved."
        val deepLink = "hrapp://leave/${event.applicationId}"

        for (userId in recipientUserIds) {
            dispatchNotification(
                tenantId = event.tenantId,
                userId = userId,
                eventKey = EVENT_LEAVE_APPROVED,
                priority = NotificationPriority.NORMAL,
                title = title,
                body = body,
                deepLink = deepLink,
            )
        }
    }

    @EventListener
    @Transactional
    fun onLeaveRejected(event: LeaveRejectedEvent) {
        log.info(
            "Processing LeaveRejectedEvent for employee {} application {}",
            event.employeeId,
            event.applicationId,
        )

        val recipientUserIds = jdbc.query(
            "SELECT id FROM app_user WHERE employee_id = ? AND status = 'ACTIVE'",
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            event.employeeId,
        )

        if (recipientUserIds.isEmpty()) {
            log.info("No active app_user found for employee {}; skipping leave rejected notification", event.employeeId)
            return
        }

        val title = "Leave Request Rejected: ${event.leaveTypeName}"
        val body = "Your leave request for ${event.totalDays} day(s) was rejected. Reason: ${event.reason}"
        val deepLink = "hrapp://leave/${event.applicationId}"

        for (userId in recipientUserIds) {
            dispatchNotification(
                tenantId = event.tenantId,
                userId = userId,
                eventKey = EVENT_LEAVE_REJECTED,
                priority = NotificationPriority.HIGH,
                title = title,
                body = body,
                deepLink = deepLink,
            )
        }
    }

    private fun dispatchNotification(
        tenantId: UUID,
        userId: UUID,
        eventKey: String,
        priority: NotificationPriority,
        title: String,
        body: String,
        deepLink: String,
    ) {
        val now = Instant.now()
        val hasPush = jdbc.queryForObject(
            "SELECT count(*) FROM user_device WHERE user_id = ? AND push_token IS NOT NULL AND revoked_at IS NULL",
            Int::class.java,
            userId,
        )?.let { it > 0 } ?: false

        val context = notificationSettingsService.contextFor(userId, eventKey, hasPush)
        val channelsToSend = mutableListOf<String>()

        for (channel in NotificationChannel.entries) {
            val decision = decideDelivery(channel, priority, context, now)
            if (decision is DeliveryDecision.Send || decision is DeliveryDecision.Defer) {
                channelsToSend.add(channel.name)
            }
        }

        if (channelsToSend.isEmpty()) {
            log.debug("All channels suppressed for user {} on event {}", userId, eventKey)
            return
        }

        val notificationId = Uuid7.generate()
        jdbc.update(
            """
            INSERT INTO notification (id, tenant_id, user_id, event_key, title, body, deep_link,
                                      priority, channels, data, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::text[], '{}'::jsonb, ?)
            """.trimIndent(),
            notificationId,
            tenantId,
            userId,
            eventKey,
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
                tenantId,
                notificationId,
                channelName,
                status,
                now,
                nextAttempt,
            )
        }
    }
}
