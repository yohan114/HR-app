package com.hr.app.notification

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HrNotificationManagerTest {

    private lateinit var context: Context
    private lateinit var notificationManager: HrNotificationManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        notificationManager = spyk(HrNotificationManager(context))
        every { notificationManager.postNotification(any()) } returns Unit
    }

    @Test
    fun `channel identifiers are unique and correctly named`() {
        val channels = listOf(
            HrNotificationManager.CHANNEL_APPROVALS,
            HrNotificationManager.CHANNEL_ATTENDANCE,
            HrNotificationManager.CHANNEL_ANNOUNCEMENTS,
            HrNotificationManager.CHANNEL_COMPLIANCE,
        )

        assertEquals(4, channels.toSet().size)
        assertTrue(channels.all { it.startsWith("hr_") && it.endsWith("_channel") })
        assertEquals("hr_approvals_channel", HrNotificationManager.CHANNEL_APPROVALS)
        assertEquals("hr_attendance_channel", HrNotificationManager.CHANNEL_ATTENDANCE)
        assertEquals("hr_announcements_channel", HrNotificationManager.CHANNEL_ANNOUNCEMENTS)
        assertEquals("hr_compliance_channel", HrNotificationManager.CHANNEL_COMPLIANCE)
    }

    @Test
    fun `sendApprovalsAlert formats high-priority approval notification with deep link`() {
        notificationManager.sendApprovalsAlert(
            title = "Pending Leave Request",
            body = "Nimali requested 2 days annual leave",
            deepLink = "hrapp://approvals",
        )

        verify {
            notificationManager.postNotification(
                match {
                    it.channelId == HrNotificationManager.CHANNEL_APPROVALS &&
                        it.title == "Pending Leave Request" &&
                        it.body == "Nimali requested 2 days annual leave" &&
                        it.deepLink == "hrapp://approvals" &&
                        it.priority == "HIGH"
                },
            )
        }
    }

    @Test
    fun `sendAttendanceAlert formats shift clock-in notification`() {
        notificationManager.sendAttendanceAlert(
            title = "Geofence Entered",
            body = "Tap to clock in at Colombo HQ",
            deepLink = "hrapp://time",
        )

        verify {
            notificationManager.postNotification(
                match {
                    it.channelId == HrNotificationManager.CHANNEL_ATTENDANCE &&
                        it.title == "Geofence Entered" &&
                        it.body == "Tap to clock in at Colombo HQ" &&
                        it.deepLink == "hrapp://time" &&
                        it.priority == "DEFAULT"
                },
            )
        }
    }

    @Test
    fun `sendEmployeeAlert formats workforce milestone notification`() {
        val employeeId = "00000000-0000-0000-0000-000000000004"
        notificationManager.sendEmployeeAlert(
            employeeId = employeeId,
            title = "Work Anniversary",
            body = "Celebrate Ruwan's 5th year!",
        )

        verify {
            notificationManager.postNotification(
                match {
                    it.channelId == HrNotificationManager.CHANNEL_ANNOUNCEMENTS &&
                        it.title == "Work Anniversary" &&
                        it.body == "Celebrate Ruwan's 5th year!" &&
                        it.deepLink == "hrapp://employee/$employeeId"
                },
            )
        }
    }

    @Test
    fun `mock push receiver extracts intent extras and routes to notification manager`() {
        val receiver = MockPushReceiver()
        val mockManager = mockk<HrNotificationManager>(relaxed = true)
        receiver.notificationManager = mockManager

        val intent = mockk<Intent>()
        every { intent.action } returns MockPushReceiver.ACTION_TEST_PUSH
        every { intent.getStringExtra("title") } returns "Test Action Push"
        every { intent.getStringExtra("body") } returns "Broadcast receiver payload"
        every { intent.getStringExtra("deepLink") } returns "hrapp://time"
        every { intent.getStringExtra("channelId") } returns HrNotificationManager.CHANNEL_ATTENDANCE
        every { intent.getIntExtra("notificationId", any()) } returns 7788

        receiver.onReceive(context, intent)

        verify(exactly = 1) {
            mockManager.postNotification(
                PushPayload(
                    notificationId = 7788,
                    channelId = HrNotificationManager.CHANNEL_ATTENDANCE,
                    title = "Test Action Push",
                    body = "Broadcast receiver payload",
                    deepLink = "hrapp://time",
                ),
            )
        }
    }

    @Test
    fun `mock push receiver ignores unrecognized action or null intent`() {
        val receiver = MockPushReceiver()
        val mockManager = mockk<HrNotificationManager>(relaxed = true)
        receiver.notificationManager = mockManager

        receiver.onReceive(context, null)

        val wrongIntent = mockk<Intent>()
        every { wrongIntent.action } returns "android.intent.action.BOOT_COMPLETED"
        receiver.onReceive(context, wrongIntent)

        verify(exactly = 0) {
            mockManager.postNotification(any())
        }
    }
}
