package com.hr.notification

import com.hr.notification.internal.NotificationDispatcherWorker
import com.hr.notification.internal.push.MockPushGateway
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@DisplayName("Notification Dispatcher Worker (Push & In-App Dispatch Lifecycle)")
class NotificationDispatcherWorkerTest {

    private val jdbc = mockk<JdbcTemplate>(relaxed = true)
    private val mockPushGateway = MockPushGateway()
    private val worker = NotificationDispatcherWorker(
        jdbc = jdbc,
        pushGateway = mockPushGateway,
        enabled = true,
    )

    @Test
    fun `successfully dispatches PUSH notification and updates status to DELIVERED`() {
        val tenantId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val notificationId = UUID.randomUUID()
        val deliveryId = UUID.randomUUID()

        val delivery = NotificationDispatcherWorker.PendingDelivery(
            deliveryId = deliveryId,
            tenantId = tenantId,
            notificationId = notificationId,
            channel = "PUSH",
            attempt = 1,
            userId = userId,
            title = "Leave Request Approved",
            body = "Your annual leave request has been approved by your supervisor.",
            deepLink = "hrapp://leave/123",
        )

        // Mock finding active device token for user
        every {
            jdbc.queryForList(
                "SELECT push_token FROM user_device WHERE user_id = ? AND push_token IS NOT NULL AND revoked_at IS NULL",
                String::class.java,
                userId,
            )
        } returns listOf("fcm_device_token_xyz_881")

        worker.processDelivery(delivery)

        // Assert push was recorded in gateway
        val sent = mockPushGateway.getSentPushes()
        assertThat(sent).hasSize(1)
        assertThat(sent[0].token).isEqualTo("fcm_device_token_xyz_881")
        assertThat(sent[0].title).isEqualTo("Leave Request Approved")
        assertThat(sent[0].deepLink).isEqualTo("hrapp://leave/123")

        // Assert delivery row was marked DELIVERED
        verify {
            jdbc.update(
                match<String> { it.contains("SET status = 'DELIVERED'") },
                "PUSH",
                any<Instant>(),
                deliveryId,
                tenantId,
            )
        }
    }

    @Test
    fun `marks IN_APP delivery immediately as DELIVERED without external push call`() {
        mockPushGateway.clear()
        val tenantId = UUID.randomUUID()
        val deliveryId = UUID.randomUUID()

        val delivery = NotificationDispatcherWorker.PendingDelivery(
            deliveryId = deliveryId,
            tenantId = tenantId,
            notificationId = UUID.randomUUID(),
            channel = "IN_APP",
            attempt = 1,
            userId = UUID.randomUUID(),
            title = "Company Announcement",
            body = "Quarterly townhall scheduled for Friday.",
            deepLink = null,
        )

        worker.processDelivery(delivery)

        assertThat(mockPushGateway.getSentPushes()).isEmpty()
        verify {
            jdbc.update(
                match<String> { it.contains("SET status = 'DELIVERED'") },
                "IN_APP",
                any<Instant>(),
                deliveryId,
                tenantId,
            )
        }
    }

    @Test
    fun `revokes device token and dead-letters delivery when token is invalid`() {
        mockPushGateway.clear()
        val tenantId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val deliveryId = UUID.randomUUID()

        val delivery = NotificationDispatcherWorker.PendingDelivery(
            deliveryId = deliveryId,
            tenantId = tenantId,
            notificationId = UUID.randomUUID(),
            channel = "PUSH",
            attempt = 5, // Exhausted
            userId = userId,
            title = "Document Expiry Notice",
            body = "Passport expiring soon",
            deepLink = null,
        )

        // Invalid token prefix triggering mock push failure
        every {
            jdbc.queryForList(
                "SELECT push_token FROM user_device WHERE user_id = ? AND push_token IS NOT NULL AND revoked_at IS NULL",
                String::class.java,
                userId,
            )
        } returns listOf("invalid_dead_token_123")

        worker.processDelivery(delivery)

        // Verifies token was revoked
        verify {
            jdbc.update(
                "UPDATE user_device SET revoked_at = ? WHERE push_token = ?",
                any<Instant>(),
                "invalid_dead_token_123",
            )
        }

        // Verifies delivery was dead-lettered
        verify {
            jdbc.update(
                match<String> { it.contains("SET status = 'DEAD_LETTERED'") },
                any<String>(),
                deliveryId,
                tenantId,
            )
        }
    }
}
