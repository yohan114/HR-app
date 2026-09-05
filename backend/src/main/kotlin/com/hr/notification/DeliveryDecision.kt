package com.hr.notification

import java.time.Instant

/** The channels a notification can travel on. */
enum class NotificationChannel { PUSH, EMAIL, IN_APP, SMS }

/**
 * How much the notification is allowed to interrupt.
 *
 * [URGENT] is the only level that overrides quiet hours, so what goes in it is a product decision
 * with a real cost: a level that everything claims stops meaning anything, and users respond by
 * turning notifications off wholesale. Security events and emergency broadcasts qualify. A leave
 * approval does not, however pleased the requester will be to hear it.
 */
enum class NotificationPriority { LOW, NORMAL, HIGH, URGENT }

/** IMMEDIATE sends on arrival; DIGEST batches into one daily summary. */
enum class DigestMode { IMMEDIATE, DIGEST }

/** Why a notification was not sent on a channel. Recorded, so a quiet night is not read as an outage. */
enum class SuppressionReason {
    /** The user turned this channel off for this event. */
    CHANNEL_DISABLED,

    /** No template exists for the event on this channel, in any locale. */
    NO_TEMPLATE,

    /** PUSH with no live token: never registered, or the provider told us it was dead. */
    NO_DESTINATION,
}

/**
 * What to do with one notification on one channel.
 *
 * [Defer] rather than drop is the important case. A leave approval that arrives during quiet hours
 * still needs to arrive — silencing 22:00–07:00 is a request about *timing*, and a user who never
 * learns their request was approved has been failed more thoroughly than one woken at 3am.
 */
sealed interface DeliveryDecision {
    data object Send : DeliveryDecision

    data class Defer(val until: Instant) : DeliveryDecision

    data class Suppress(val reason: SuppressionReason) : DeliveryDecision
}

/**
 * Everything about a user that bears on whether to send.
 *
 * Assembled once per recipient by the dispatcher so the decision itself stays a pure function —
 * which is what makes the quiet-hours edge cases testable without a database.
 */
data class RecipientContext(
    val quietHours: QuietHours?,
    val enabledChannels: Set<NotificationChannel>,
    val digestMode: DigestMode = DigestMode.IMMEDIATE,
    val hasPushDestination: Boolean = true,
)

/**
 * Decides delivery for one channel.
 *
 * Ordering matters and is not arbitrary:
 *
 * 1. A disabled channel loses first. Deferring something the user has switched off would deliver it
 *    at 07:00 anyway, which is the opposite of what they asked for.
 * 2. A missing destination loses next — nothing downstream can rescue a push with no token, and
 *    holding it until morning only produces the same failure later.
 * 3. IN_APP never defers. It is a list the user opens, not an interruption; holding it back would
 *    mean the app shows nothing at 23:00 for an event that already happened, and the badge and the
 *    push would then disagree.
 * 4. URGENT overrides quiet hours, for the reasons in [NotificationPriority].
 */
fun decideDelivery(
    channel: NotificationChannel,
    priority: NotificationPriority,
    context: RecipientContext,
    now: Instant,
): DeliveryDecision {
    if (channel !in context.enabledChannels) {
        return DeliveryDecision.Suppress(SuppressionReason.CHANNEL_DISABLED)
    }
    if (channel == NotificationChannel.PUSH && !context.hasPushDestination) {
        return DeliveryDecision.Suppress(SuppressionReason.NO_DESTINATION)
    }
    if (channel == NotificationChannel.IN_APP) return DeliveryDecision.Send
    if (priority == NotificationPriority.URGENT) return DeliveryDecision.Send

    val quiet = context.quietHours ?: return DeliveryDecision.Send
    val until = quiet.endOf(now) ?: return DeliveryDecision.Send
    return DeliveryDecision.Defer(until)
}
