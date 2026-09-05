package com.hr.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.NotificationChannel
import java.time.LocalTime

/**
 * Notification preferences and quiet hours.
 *
 * Saved explicitly rather than on every toggle. A switch that writes immediately means a mis-tap
 * silently changes what reaches the user, and there is no moment at which they can decide not to
 * — which matters more here than usual, because the thing being switched off is how they find out
 * about anything.
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Text("Notifications", style = MaterialTheme.typography.headlineSmall)

        val error = state.error
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        QuietHoursSection(
            enabled = state.quietHoursEnabled,
            start = state.quietStart,
            end = state.quietEnd,
            timezone = state.timezone,
            onEnabledChange = viewModel::setQuietHoursEnabled,
        )

        HorizontalDivider()

        NotifiableEvent.entries.forEach { event ->
            EventSection(
                event = event,
                switches = state.switches,
                onToggle = viewModel::toggle,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Button(
                onClick = viewModel::save,
                // Disabled until something has changed: a Save that does nothing still sends a
                // request, and a user who taps it and sees nothing happen learns to distrust it.
                enabled = state.dirty && !state.saving,
            ) {
                Text(if (state.saving) "Saving…" else "Save")
            }
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun QuietHoursSection(
    enabled: Boolean,
    start: LocalTime,
    end: LocalTime,
    timezone: String,
    onEnabledChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Quiet hours", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "$start to $end, $timezone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Text(
            // Said plainly because "quiet hours" reads as "you will not be told", and a user who
            // believes that will turn it off rather than risk missing an approval.
            text = "Notifications that arrive during quiet hours are held and delivered afterwards, not dropped. Urgent security alerts still come through.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EventSection(
    event: NotifiableEvent,
    switches: Map<Pair<String, NotificationChannel>, Boolean>,
    onToggle: (String, NotificationChannel, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(event.label, style = MaterialTheme.typography.titleSmall)
        Text(
            text = event.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CONFIGURABLE_CHANNELS.forEach { channel ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = channelLabel(channel),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = switches[event.key to channel] ?: defaultEnabled(channel),
                    onCheckedChange = { onToggle(event.key, channel, it) },
                )
            }
        }
    }
}

private fun channelLabel(channel: NotificationChannel): String =
    when (channel) {
        NotificationChannel.PUSH -> "On this device"
        NotificationChannel.EMAIL -> "Email"
        NotificationChannel.IN_APP -> "In the app"
        NotificationChannel.SMS -> "Text message"
    }
