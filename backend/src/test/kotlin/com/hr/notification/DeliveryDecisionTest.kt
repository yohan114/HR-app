package com.hr.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** The per-channel send/defer/suppress decision. */
class DeliveryDecisionTest {
    private val colombo = ZoneId.of("Asia/Colombo")

    private fun at(
        date: String,
        time: String,
    ) = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(colombo).toInstant()

    private val night = at("2026-03-10", "23:30")
    private val midday = at("2026-03-10", "12:00")

    private fun context(
        quiet: Boolean = true,
        channels: Set<NotificationChannel> = NotificationChannel.entries.toSet(),
        hasPush: Boolean = true,
    ) = RecipientContext(
        quietHours =
            if (quiet) QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0), colombo) else null,
        enabledChannels = channels,
        hasPushDestination = hasPush,
    )

    @Test
    fun `a normal push during the day sends`() {
        val decision =
            decideDelivery(NotificationChannel.PUSH, NotificationPriority.NORMAL, context(), midday)

        assertEquals(DeliveryDecision.Send, decision)
    }

    /**
     * Deferred, not dropped. The user asked not to be interrupted overnight, not to be kept
     * ignorant — a leave approval that never arrives is a worse failure than one that arrives late.
     */
    @Test
    fun `a normal push at night is held until the window ends`() {
        val decision =
            decideDelivery(NotificationChannel.PUSH, NotificationPriority.NORMAL, context(), night)

        val deferred = assertInstanceOf(DeliveryDecision.Defer::class.java, decision)
        assertEquals(at("2026-03-11", "07:00"), deferred.until)
    }

    @Test
    fun `an urgent push overrides quiet hours`() {
        val decision =
            decideDelivery(NotificationChannel.PUSH, NotificationPriority.URGENT, context(), night)

        assertEquals(DeliveryDecision.Send, decision)
    }

    /**
     * In-app is a list the user opens, not an interruption. Holding it back would leave the app
     * showing nothing at 23:00 for something that already happened, and make the unread badge
     * disagree with reality until morning.
     */
    @Test
    fun `in-app is written immediately even during quiet hours`() {
        val decision =
            decideDelivery(NotificationChannel.IN_APP, NotificationPriority.LOW, context(), night)

        assertEquals(DeliveryDecision.Send, decision)
    }

    /**
     * A disabled channel must lose to quiet hours, not the other way round. Deferring it would
     * deliver at 07:00 something the user switched off — the exact opposite of the request.
     */
    @Test
    fun `a disabled channel is suppressed rather than deferred`() {
        val decision =
            decideDelivery(
                NotificationChannel.PUSH,
                NotificationPriority.NORMAL,
                context(channels = setOf(NotificationChannel.IN_APP)),
                night,
            )

        assertEquals(DeliveryDecision.Suppress(SuppressionReason.CHANNEL_DISABLED), decision)
    }

    /** Holding a push with no token until morning only produces the same failure later. */
    @Test
    fun `a push with no live token is suppressed rather than deferred`() {
        val decision =
            decideDelivery(
                NotificationChannel.PUSH,
                NotificationPriority.NORMAL,
                context(hasPush = false),
                night,
            )

        assertEquals(DeliveryDecision.Suppress(SuppressionReason.NO_DESTINATION), decision)
    }

    @Test
    fun `a user with no quiet hours is never deferred`() {
        val decision =
            decideDelivery(
                NotificationChannel.PUSH,
                NotificationPriority.LOW,
                context(quiet = false),
                night,
            )

        assertEquals(DeliveryDecision.Send, decision)
    }

    /** A missing token stops email too — the check is scoped to the channel that needs it. */
    @Test
    fun `email is unaffected by push destination`() {
        val decision =
            decideDelivery(
                NotificationChannel.EMAIL,
                NotificationPriority.NORMAL,
                context(hasPush = false),
                midday,
            )

        assertEquals(DeliveryDecision.Send, decision)
    }

    @Test
    fun `a deferred release is always in the future`() {
        listOf(at("2026-03-10", "22:00"), night, at("2026-03-11", "06:59")).forEach { instant ->
            val decision =
                decideDelivery(
                    NotificationChannel.PUSH,
                    NotificationPriority.NORMAL,
                    context(),
                    instant,
                )
            val deferred = assertInstanceOf(DeliveryDecision.Defer::class.java, decision)
            assertTrue(deferred.until > instant, "release at $instant was not in the future")
        }
    }
}
