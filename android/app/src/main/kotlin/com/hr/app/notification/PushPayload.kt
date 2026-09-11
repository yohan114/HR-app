package com.hr.app.notification

/**
 * Strongly typed payload representing an incoming push notification.
 */
data class PushPayload(
    val notificationId: Int,
    val channelId: String,
    val title: String,
    val body: String,
    val deepLink: String,
    val priority: String = "HIGH",
)
