package com.hr.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.random.Random

/**
 * BroadcastReceiver enabling synthetic push notification testing without requiring
 * external Firebase Cloud Messaging credentials.
 *
 * Triggerable via:
 * `adb shell am broadcast -a com.hr.app.action.TEST_PUSH --es title "Leave Request" --es body "Pending approval" --es deepLink "hrapp://approvals"`
 */
class MockPushReceiver : BroadcastReceiver() {

    var notificationManager: HrNotificationManager? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.action != ACTION_TEST_PUSH) return

        val manager = notificationManager ?: context?.let { ctx ->
            HrNotificationManager(ctx.applicationContext ?: ctx)
        } ?: return

        val title = intent.getStringExtra("title") ?: "HR Notification"
        val body = intent.getStringExtra("body") ?: "You have a pending update."
        val deepLink = intent.getStringExtra("deepLink") ?: "hrapp://approvals"
        val channelId = intent.getStringExtra("channelId") ?: HrNotificationManager.CHANNEL_APPROVALS
        val notificationId = intent.getIntExtra("notificationId", Random.nextInt(1000, 9999))

        manager.postNotification(
            PushPayload(
                notificationId = notificationId,
                channelId = channelId,
                title = title,
                body = body,
                deepLink = deepLink,
            ),
        )
    }

    companion object {
        const val ACTION_TEST_PUSH = "com.hr.app.action.TEST_PUSH"
    }
}
