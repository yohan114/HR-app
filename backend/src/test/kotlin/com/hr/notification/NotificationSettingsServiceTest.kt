package com.hr.notification

import com.hr.notification.internal.ChannelPreference
import com.hr.notification.internal.NotificationPreference
import com.hr.notification.internal.NotificationPreferenceRepository
import com.hr.notification.internal.NotificationQuietHoursEntity
import com.hr.notification.internal.NotificationQuietHoursRepository
import com.hr.notification.internal.NotificationSettings
import com.hr.notification.internal.NotificationSettingsService
import com.hr.notification.internal.QuietHoursSettings
import com.hr.shared.api.ApiException
import com.hr.tenancy.TenantContext
import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * The settings service: what is stored, what is defaulted, and what is refused.
 *
 * The repositories are mocked because nothing here needs a database to be wrong — every decision
 * worth testing is about which rows get written and which requests get rejected.
 */
class NotificationSettingsServiceTest {
    private val preferences = mockk<NotificationPreferenceRepository>(relaxed = true)
    private val quietHours = mockk<NotificationQuietHoursRepository>(relaxed = true)
    private val service = NotificationSettingsService(preferences, quietHours)

    private val tenantId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun enterTenant() {
        TenantContext.set(
            TenantHandle(
                id = tenantId,
                code = "demo",
                name = "Demo",
                dataRegion = "default",
                defaultCurrency = "LKR",
                timezone = "Asia/Colombo",
                locale = "en",
                isolationTier = IsolationTier.SHARED,
                status = TenantStatus.ACTIVE,
            ),
        )
        every { preferences.findAllByTenantIdAndUserId(any(), any()) } returns emptyList()
        every { quietHours.findByTenantIdAndUserId(any(), any()) } returns null
        // `relaxed` answers a generic `save` with a bare Object, which cannot cast to the entity.
        // Echoing the argument back is what a real repository does anyway.
        every { quietHours.save(any()) } answers { firstArg() }
    }

    @AfterEach
    fun leaveTenant() {
        TenantContext.clear()
    }

    /* ---------------------------------------------------------------------- */
    /* Defaults                                                                */
    /* ---------------------------------------------------------------------- */

    /**
     * SMS is billed per message. A default that fans every event out to SMS would hand a tenant an
     * invoice they never agreed to.
     */
    @Test
    fun `sms is off by default and the free channels are on`() {
        assertThat(NotificationSettingsService.defaultEnabled(NotificationChannel.SMS)).isFalse()
        assertThat(NotificationSettingsService.defaultEnabled(NotificationChannel.PUSH)).isTrue()
        assertThat(NotificationSettingsService.defaultEnabled(NotificationChannel.EMAIL)).isTrue()
        assertThat(NotificationSettingsService.defaultEnabled(NotificationChannel.IN_APP)).isTrue()
    }

    @Test
    fun `a user who has said nothing gets the defaults`() {
        val context = service.contextFor(userId, "leave.approved", hasPushDestination = true)

        assertThat(context.enabledChannels)
            .containsExactlyInAnyOrder(
                NotificationChannel.IN_APP,
                NotificationChannel.PUSH,
                NotificationChannel.EMAIL,
            )
        assertThat(context.quietHours).isNull()
    }

    /**
     * Storing a row per user per event per channel would put 1.6 M rows in a 10,000-person tenant,
     * all of them restating the default.
     */
    @Test
    fun `preferences matching the default are not stored`() {
        val saved = slot<List<NotificationPreference>>()
        every { preferences.saveAll(capture(saved)) } returns emptyList()

        service.replace(
            userId,
            NotificationSettings(
                preferences =
                    listOf(
                        // Both at their defaults — nothing to record.
                        ChannelPreference("leave.approved", NotificationChannel.PUSH, enabled = true),
                        ChannelPreference("leave.approved", NotificationChannel.SMS, enabled = false),
                        // Differs from the default, so it must be kept.
                        ChannelPreference("leave.approved", NotificationChannel.EMAIL, enabled = false),
                    ),
                quietHours = null,
            ),
        )

        assertThat(saved.captured).hasSize(1)
        assertThat(saved.captured.single().channel).isEqualTo(NotificationChannel.EMAIL)
        assertThat(saved.captured.single().enabled).isFalse()
    }

    @Test
    fun `an sms opt-in is stored because it differs from the default`() {
        val saved = slot<List<NotificationPreference>>()
        every { preferences.saveAll(capture(saved)) } returns emptyList()

        service.replace(
            userId,
            NotificationSettings(
                preferences =
                    listOf(ChannelPreference("payroll.published", NotificationChannel.SMS, enabled = true)),
                quietHours = null,
            ),
        )

        assertThat(saved.captured).hasSize(1)
        assertThat(saved.captured.single().enabled).isTrue()
    }

    /* ---------------------------------------------------------------------- */
    /* Reading a stored preference back                                        */
    /* ---------------------------------------------------------------------- */

    @Test
    fun `a stored opt-out removes the channel from the context`() {
        every { preferences.findAllByTenantIdAndUserId(tenantId, userId) } returns
            listOf(
                NotificationPreference(
                    tenantId = tenantId,
                    userId = userId,
                    eventKey = "leave.approved",
                    channel = NotificationChannel.PUSH,
                    enabled = false,
                ),
            )

        val context = service.contextFor(userId, "leave.approved", hasPushDestination = true)

        assertThat(context.enabledChannels).doesNotContain(NotificationChannel.PUSH)
        assertThat(context.enabledChannels).contains(NotificationChannel.EMAIL)
    }

    /** A preference for one event must not silence a different one. */
    @Test
    fun `a preference applies only to its own event`() {
        every { preferences.findAllByTenantIdAndUserId(tenantId, userId) } returns
            listOf(
                NotificationPreference(
                    tenantId = tenantId,
                    userId = userId,
                    eventKey = "leave.approved",
                    channel = NotificationChannel.PUSH,
                    enabled = false,
                ),
            )

        val other = service.contextFor(userId, "payroll.published", hasPushDestination = true)

        assertThat(other.enabledChannels).contains(NotificationChannel.PUSH)
    }

    @Test
    fun `stored quiet hours reach the decision context`() {
        every { quietHours.findByTenantIdAndUserId(tenantId, userId) } returns
            NotificationQuietHoursEntity(
                tenantId = tenantId,
                userId = userId,
                startAt = LocalTime.of(22, 0),
                endAt = LocalTime.of(7, 0),
                timezone = "Asia/Colombo",
                days = arrayOf(1, 2, 3),
            )

        val context = service.contextFor(userId, "leave.approved", hasPushDestination = true)

        assertThat(context.quietHours).isNotNull
        assertThat(context.quietHours?.days)
            .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)
        assertThat(context.quietHours?.zone?.id).isEqualTo("Asia/Colombo")
    }

    /* ---------------------------------------------------------------------- */
    /* Validation                                                              */
    /* ---------------------------------------------------------------------- */

    /**
     * A zone the JVM cannot resolve would throw at dispatch time, long after the user could do
     * anything about it. It is refused at the point they can still fix it.
     */
    @Test
    fun `an unknown timezone is refused`() {
        assertThatThrownBy {
            service.replace(userId, settingsWithZone("Mars/Olympus_Mons"))
        }.isInstanceOfSatisfying(ApiException::class.java) {
            assertThat(it.code).isEqualTo("VALIDATION_FAILED")
            assertThat(it.field).isEqualTo("quietHours.timezone")
        }
    }

    /**
     * A fixed offset is syntactically a valid `ZoneId`, and is exactly the mistake the schema
     * comment warns about — but refusing it would also refuse `UTC`, which is legitimate for a
     * tenant that genuinely has no local time. Accepted, and documented rather than silently
     * converted.
     */
    @Test
    fun `a real zone is accepted`() {
        service.replace(userId, settingsWithZone("Europe/London"))

        verify { quietHours.save(any()) }
    }

    @Test
    fun `a window that starts and ends at the same time is refused`() {
        assertThatThrownBy {
            service.replace(
                userId,
                NotificationSettings(
                    preferences = emptyList(),
                    quietHours =
                        QuietHoursSettings(
                            startAt = LocalTime.of(9, 0),
                            endAt = LocalTime.of(9, 0),
                            timezone = "UTC",
                        ),
                ),
            )
        }.isInstanceOfSatisfying(ApiException::class.java) {
            assertThat(it.code).isEqualTo("VALIDATION_FAILED")
            assertThat(it.field).isEqualTo("quietHours.endAt")
        }
    }

    @Test
    fun `a day outside one to seven is refused`() {
        assertThatThrownBy {
            service.replace(
                userId,
                NotificationSettings(
                    preferences = emptyList(),
                    quietHours =
                        QuietHoursSettings(
                            startAt = LocalTime.of(22, 0),
                            endAt = LocalTime.of(7, 0),
                            timezone = "UTC",
                            days = listOf(0, 8),
                        ),
                ),
            )
        }.isInstanceOfSatisfying(ApiException::class.java) {
            assertThat(it.field).isEqualTo("quietHours.days")
        }
    }

    @Test
    fun `a blank event key is refused`() {
        assertThatThrownBy {
            service.replace(
                userId,
                NotificationSettings(
                    preferences = listOf(ChannelPreference("  ", NotificationChannel.PUSH, true)),
                    quietHours = null,
                ),
            )
        }.isInstanceOfSatisfying(ApiException::class.java) {
            assertThat(it.field).isEqualTo("preferences.eventKey")
        }
    }

    /**
     * The whole request is validated before anything is written. A bad entry halfway down a long
     * list must not leave the first half saved and the rest discarded — the user would have no way
     * to tell which of their changes survived.
     */
    @Test
    fun `nothing is written when any entry is invalid`() {
        assertThatThrownBy {
            service.replace(
                userId,
                NotificationSettings(
                    preferences =
                        listOf(
                            ChannelPreference("leave.approved", NotificationChannel.EMAIL, false),
                            ChannelPreference("", NotificationChannel.PUSH, false),
                        ),
                    quietHours = null,
                ),
            )
        }.isInstanceOf(ApiException::class.java)

        verify(exactly = 0) { preferences.deleteByTenantIdAndUserId(any(), any()) }
        verify(exactly = 0) { preferences.saveAll(any<List<NotificationPreference>>()) }
    }

    /** Quiet hours are validated before the preference writes, for the same reason. */
    @Test
    fun `invalid quiet hours prevent the preference writes`() {
        assertThatThrownBy {
            service.replace(
                userId,
                NotificationSettings(
                    preferences =
                        listOf(ChannelPreference("leave.approved", NotificationChannel.EMAIL, false)),
                    quietHours =
                        QuietHoursSettings(
                            startAt = LocalTime.of(22, 0),
                            endAt = LocalTime.of(7, 0),
                            timezone = "Nowhere/Nothing",
                        ),
                ),
            )
        }.isInstanceOf(ApiException::class.java)

        verify(exactly = 0) { preferences.saveAll(any<List<NotificationPreference>>()) }
    }

    /* ---------------------------------------------------------------------- */
    /* Digest                                                                  */
    /* ---------------------------------------------------------------------- */

    /**
     * A user asking for one channel immediately and another digested wants something a daily
     * summary cannot express. The stricter reading wins, so nothing arrives more often than asked.
     */
    @Test
    fun `any digest preference digests the event`() {
        every { preferences.findAllByTenantIdAndUserId(tenantId, userId) } returns
            listOf(
                NotificationPreference(
                    tenantId = tenantId,
                    userId = userId,
                    eventKey = "leave.approved",
                    channel = NotificationChannel.EMAIL,
                    enabled = true,
                    digestMode = DigestMode.DIGEST,
                ),
            )

        val context = service.contextFor(userId, "leave.approved", hasPushDestination = true)

        assertThat(context.digestMode).isEqualTo(DigestMode.DIGEST)
    }

    private fun settingsWithZone(zone: String) =
        NotificationSettings(
            preferences = emptyList(),
            quietHours =
                QuietHoursSettings(
                    startAt = LocalTime.of(22, 0),
                    endAt = LocalTime.of(7, 0),
                    timezone = zone,
                ),
        )
}
