package com.hr.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hr.app.MainActivity
import com.hr.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Manages system notification channels, foreground notifications, and deep-link pending intents.
 */
@Singleton
open class HrNotificationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {

    companion object {
        const val CHANNEL_APPROVALS = "hr_approvals_channel"
        const val CHANNEL_ATTENDANCE = "hr_attendance_channel"
        const val CHANNEL_ANNOUNCEMENTS = "hr_announcements_channel"
        const val CHANNEL_COMPLIANCE = "hr_compliance_channel"
    }

    init {
        initNotificationChannels()
    }

    /**
     * Initializes Android system notification channels on API 26+.
     */
    fun initNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val channels = listOf(
                NotificationChannel(
                    CHANNEL_APPROVALS,
                    "Approvals & Requests",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Urgent manager approvals for leave and attendance regularisations"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_ATTENDANCE,
                    "Attendance & Shifts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Shift reminders, clock-in prompts, and geofence notices"
                },
                NotificationChannel(
                    CHANNEL_ANNOUNCEMENTS,
                    "Company Announcements",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Important company retreat and policy updates"
                },
                NotificationChannel(
                    CHANNEL_COMPLIANCE,
                    "Compliance & Expiry",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Passport, visa, and critical statutory document expiry alerts"
                    enableVibration(true)
                },
            )

            channels.forEach { channel ->
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Posts a notification to the system shade with a deep link PendingIntent.
     */
    open fun postNotification(payload: PushPayload) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payload.deepLink), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            payload.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, payload.channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setPriority(
                if (payload.priority.equals("HIGH", ignoreCase = true)) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        runCatching {
            NotificationManagerCompat.from(context).notify(payload.notificationId, builder.build())
        }
    }

    /**
     * Helper to send an approvals push alert.
     */
    fun sendApprovalsAlert(
        title: String = "Pending Approval Request",
        body: String = "Kasun Perera submitted an annual leave request requiring your approval.",
        deepLink: String = "hrapp://approvals",
    ) {
        postNotification(
            PushPayload(
                notificationId = Random.nextInt(1000, 9999),
                channelId = CHANNEL_APPROVALS,
                title = title,
                body = body,
                deepLink = deepLink,
                priority = "HIGH",
            ),
        )
    }

    /**
     * Helper to send an attendance clocking alert.
     */
    fun sendAttendanceAlert(
        title: String = "Shift Clock-In Reminder",
        body: String = "You have entered Colombo Head Office boundary. Tap to clock in for your shift.",
        deepLink: String = "hrapp://time",
    ) {
        postNotification(
            PushPayload(
                notificationId = Random.nextInt(1000, 9999),
                channelId = CHANNEL_ATTENDANCE,
                title = title,
                body = body,
                deepLink = deepLink,
                priority = "DEFAULT",
            ),
        )
    }

    /**
     * Helper to send an employee profile deep link alert.
     */
    fun sendEmployeeAlert(
        employeeId: String,
        title: String = "Workforce Milestone",
        body: String = "Congratulate Ruwan Fernando on his 5-year work anniversary today!",
        deepLink: String = "hrapp://employee/$employeeId",
    ) {
        postNotification(
            PushPayload(
                notificationId = Random.nextInt(1000, 9999),
                channelId = CHANNEL_ANNOUNCEMENTS,
                title = title,
                body = body,
                deepLink = deepLink,
                priority = "DEFAULT",
            ),
        )
    }
}
