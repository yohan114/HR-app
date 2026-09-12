package com.hr.notification

import com.hr.leave.LeaveAppliedEvent
import com.hr.leave.LeaveApprovedEvent
import com.hr.leave.LeaveRejectedEvent
import com.hr.notification.internal.LeaveNotificationListener
import com.hr.notification.internal.NotificationDispatcherWorker
import com.hr.notification.internal.NotificationSettingsService
import com.hr.notification.internal.push.MockPushGateway
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@DisplayName("Leave Notification Listener & End-to-End Push Delivery")
class LeaveNotificationListenerTest {

    private val jdbc = mockk<JdbcTemplate>(relaxed = true)
    private val settingsService = mockk<NotificationSettingsService>()
    private val mockPushGateway = MockPushGateway()

    private lateinit var listener: LeaveNotificationListener
    private lateinit var dispatcherWorker: NotificationDispatcherWorker

    private val tenantId = UUID.randomUUID()
    private val employeeId = UUID.randomUUID()
    private val approverUserId = UUID.randomUUID()
    private val applicantUserId = UUID.randomUUID()
    private val applicationId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        listener = LeaveNotificationListener(
            notificationSettingsService = settingsService,
            jdbc = jdbc,
        )
        dispatcherWorker = NotificationDispatcherWorker(
            jdbc = jdbc,
            pushGateway = mockPushGateway,
            enabled = true,
        )
        mockPushGateway.clear()
    }

    @Test
    fun `onLeaveApplied publishes notification with approvals deep link for approver`() {
        val event = LeaveAppliedEvent(
            tenantId = tenantId,
            applicationId = applicationId,
            employeeId = employeeId,
            leaveTypeName = "Annual Leave",
            startDate = LocalDate.now().plusDays(5),
            endDate = LocalDate.now().plusDays(7),
            totalDays = BigDecimal("3.0"),
        )

        // Mock finding manager user ID
        every {
            jdbc.query(
                match<String> { it.contains("SELECT u.id") },
                any<RowMapper<UUID>>(),
                employeeId,
            )
        } returns listOf(approverUserId)

        // Mock device check: approver has push token
        every {
            jdbc.queryForObject(
                match<String> { it.contains("SELECT count(*)") },
                Int::class.java,
                approverUserId,
            )
        } returns 1

        // Mock delivery context (normal preferences, not quiet hours)
        every {
            settingsService.contextFor(approverUserId, "leave.applied", true)
        } returns RecipientContext(
            enabledChannels = setOf(NotificationChannel.PUSH, NotificationChannel.IN_APP),
            quietHours = null,
            hasPushDestination = true,
        )

        listener.onLeaveApplied(event)

        // Verify notification row was inserted with deep link hrapp://approvals
        verify {
            jdbc.update(
                match<String> { it.contains("INSERT INTO notification") },
                any<UUID>(),
                tenantId,
                approverUserId,
                "leave.applied",
                match<String> { it.contains("Annual Leave") },
                match<String> { it.contains("3.0 day(s)") },
                "hrapp://approvals",
                "NORMAL",
                any<Array<String>>(),
                any<Instant>(),
            )
        }

        // Verify delivery rows were created with PENDING status
        verify(atLeast = 1) {
            jdbc.update(
                match<String> { it.contains("INSERT INTO notification_delivery") },
                any<UUID>(),
                tenantId,
                any<UUID>(),
                "PUSH",
                "PENDING",
                any<Instant>(),
                any<Instant>(),
            )
        }
    }

    @Test
    fun `onLeaveApproved publishes notification with specific leave deep link for applicant`() {
        val event = LeaveApprovedEvent(
            tenantId = tenantId,
            applicationId = applicationId,
            employeeId = employeeId,
            approverId = UUID.randomUUID(),
            leaveTypeName = "Casual Leave",
            startDate = LocalDate.now().plusDays(1),
            endDate = LocalDate.now().plusDays(1),
            totalDays = BigDecimal("1.0"),
            remarks = "Enjoy your day off",
        )

        every {
            jdbc.query(
                match<String> { it.contains("SELECT id FROM app_user WHERE employee_id = ?") },
                any<RowMapper<UUID>>(),
                employeeId,
            )
        } returns listOf(applicantUserId)

        every {
            jdbc.queryForObject(
                match<String> { it.contains("SELECT count(*)") },
                Int::class.java,
                applicantUserId,
            )
        } returns 1

        every {
            settingsService.contextFor(applicantUserId, "leave.approved", true)
        } returns RecipientContext(
            enabledChannels = setOf(NotificationChannel.PUSH),
            quietHours = null,
            hasPushDestination = true,
        )

        listener.onLeaveApproved(event)

        // Verify notification row has deep link pointing to specific leave request
        verify {
            jdbc.update(
                match<String> { it.contains("INSERT INTO notification") },
                any<UUID>(),
                tenantId,
                applicantUserId,
                "leave.approved",
                match<String> { it.contains("Casual Leave") },
                match<String> { it.contains("approved") },
                "hrapp://leave/$applicationId",
                "NORMAL",
                any<Array<String>>(),
                any<Instant>(),
            )
        }
    }

    @Test
    fun `onLeaveRejected creates high priority notification for applicant`() {
        val event = LeaveRejectedEvent(
            tenantId = tenantId,
            applicationId = applicationId,
            employeeId = employeeId,
            approverId = UUID.randomUUID(),
            leaveTypeName = "Medical Leave",
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusDays(2),
            totalDays = BigDecimal("3.0"),
            reason = "Medical certificate missing",
        )

        every {
            jdbc.query(
                match<String> { it.contains("SELECT id FROM app_user WHERE employee_id = ?") },
                any<RowMapper<UUID>>(),
                employeeId,
            )
        } returns listOf(applicantUserId)

        every {
            jdbc.queryForObject(
                match<String> { it.contains("SELECT count(*)") },
                Int::class.java,
                applicantUserId,
            )
        } returns 1

        every {
            settingsService.contextFor(applicantUserId, "leave.rejected", true)
        } returns RecipientContext(
            enabledChannels = setOf(NotificationChannel.PUSH),
            quietHours = null,
            hasPushDestination = true,
        )

        listener.onLeaveRejected(event)

        verify {
            jdbc.update(
                match<String> { it.contains("INSERT INTO notification") },
                any<UUID>(),
                tenantId,
                applicantUserId,
                "leave.rejected",
                match<String> { it.contains("Medical Leave") },
                match<String> { it.contains("Medical certificate missing") },
                "hrapp://leave/$applicationId",
                "HIGH",
                any<Array<String>>(),
                any<Instant>(),
            )
        }
    }

    @Test
    fun `quiet hours defers push delivery until quiet hours end`() {
        val event = LeaveApprovedEvent(
            tenantId = tenantId,
            applicationId = applicationId,
            employeeId = employeeId,
            approverId = UUID.randomUUID(),
            leaveTypeName = "Annual Leave",
            startDate = LocalDate.now().plusDays(2),
            endDate = LocalDate.now().plusDays(3),
            totalDays = BigDecimal("2.0"),
        )

        every {
            jdbc.query(
                match<String> { it.contains("SELECT id FROM app_user WHERE employee_id = ?") },
                any<RowMapper<UUID>>(),
                employeeId,
            )
        } returns listOf(applicantUserId)

        every {
            jdbc.queryForObject(
                match<String> { it.contains("SELECT count(*)") },
                Int::class.java,
                applicantUserId,
            )
        } returns 1

        // Active quiet hours covering current time: 00:00 to 23:59
        val quietConfig = QuietHours(
            start = LocalTime.of(0, 0),
            end = LocalTime.of(23, 59),
            zone = ZoneId.of("UTC"),
        )

        every {
            settingsService.contextFor(applicantUserId, "leave.approved", true)
        } returns RecipientContext(
            enabledChannels = setOf(NotificationChannel.PUSH),
            quietHours = quietConfig,
            hasPushDestination = true,
        )

        val nextAttemptSlot = slot<Instant>()
        every {
            jdbc.update(
                match<String> { it.contains("INSERT INTO notification_delivery") },
                any<UUID>(),
                tenantId,
                any<UUID>(),
                "PUSH",
                "PENDING",
                any<Instant>(),
                capture(nextAttemptSlot),
            )
        } returns 1

        listener.onLeaveApproved(event)

        // The next attempt timestamp should be in the future (deferred until quiet hours end)
        assertThat(nextAttemptSlot.isCaptured).isTrue()
        assertThat(nextAttemptSlot.captured).isAfter(Instant.now())
    }

    @Test
    fun `end-to-end leave notification is dispatched via push gateway and marked DELIVERED`() {
        val deliveryId = UUID.randomUUID()
        val pendingDelivery = NotificationDispatcherWorker.PendingDelivery(
            deliveryId = deliveryId,
            tenantId = tenantId,
            notificationId = UUID.randomUUID(),
            channel = "PUSH",
            attempt = 1,
            userId = applicantUserId,
            title = "Leave Request Approved: Annual Leave",
            body = "Your leave request for 2.0 day(s) has been approved.",
            deepLink = "hrapp://leave/$applicationId",
        )

        every {
            jdbc.queryForList(
                match<String> { it.contains("SELECT push_token FROM user_device") },
                String::class.java,
                applicantUserId,
            )
        } returns listOf("mock_device_token_abc_123")

        dispatcherWorker.processDelivery(pendingDelivery)

        // Push gateway should receive the notification with deep link
        val pushes = mockPushGateway.getSentPushes()
        assertThat(pushes).hasSize(1)
        assertThat(pushes[0].token).isEqualTo("mock_device_token_abc_123")
        assertThat(pushes[0].title).isEqualTo("Leave Request Approved: Annual Leave")
        assertThat(pushes[0].deepLink).isEqualTo("hrapp://leave/$applicationId")

        // Delivery marked DELIVERED
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
}
