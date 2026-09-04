package com.hr.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.app.data.secure.BiometricAuthenticator
import com.hr.app.ui.theme.Spacing

/**
 * Unlock on a cold start, when a sealed token exists.
 *
 * The prompt is raised immediately rather than behind a button. Making someone tap "Unlock" before
 * the system dialog appears adds a step to the most frequent interaction in the app — and the
 * dialog itself already has a cancel affordance, so the extra tap buys nothing.
 */
@Composable
fun BiometricUnlockScreen(
    onUnlocked: () -> Unit,
    onUsePassword: () -> Unit,
    viewModel: BiometricViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authenticator = rememberBiometricAuthenticator()

    LaunchedEffect(Unit) {
        if (authenticator != null && state.canUnlock) {
            viewModel.unlock(authenticator, onUnlocked)
        } else {
            onUsePassword()
        }
    }

    // Once the saved sign-in is gone there is nothing here to retry, so the screen hands over to
    // the password form rather than showing a dead end with a message on it.
    LaunchedEffect(state.canUnlock) {
        if (!state.canUnlock && state.message == null) onUsePassword()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium)

        val message = state.message
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        if (state.busy) CircularProgressIndicator()

        if (authenticator != null && state.canUnlock && !state.busy) {
            Button(
                onClick = { viewModel.unlock(authenticator, onUnlocked) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Try again")
            }
        }

        TextButton(onClick = onUsePassword, enabled = !state.busy) {
            Text("Use password instead")
        }
    }
}

/**
 * Offered right after a password sign-in.
 *
 * Deliberately skippable, and skipping is remembered only for this session — someone who declines
 * on a borrowed phone should not be nagged, and someone who declined by accident should be asked
 * again next time.
 */
@Composable
fun BiometricEnrolmentPrompt(
    onFinished: () -> Unit,
    viewModel: BiometricViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authenticator = rememberBiometricAuthenticator()

    LaunchedEffect(authenticator) {
        // Nothing to offer on a device with no biometric hardware, or one where the token is
        // already sealed and current.
        if (authenticator == null || !viewModel.shouldOfferEnrolment(authenticator)) onFinished()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Skip the password next time", style = MaterialTheme.typography.headlineMedium)
        Text(
            text =
                "Use your fingerprint or face to sign in. Your sign-in is stored in this phone's " +
                    "secure hardware and never leaves it.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        val message = state.message
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        if (state.busy) CircularProgressIndicator()

        Button(
            onClick = { authenticator?.let { viewModel.enrol(it, onFinished) } },
            enabled = !state.busy && authenticator != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Turn on")
        }
        TextButton(onClick = onFinished, enabled = !state.busy) {
            Text("Not now")
        }
    }
}

/**
 * The authenticator, or null when the host is not a `FragmentActivity`.
 *
 * `BiometricPrompt` needs one — it hosts an invisible fragment to survive configuration changes.
 * Returning null rather than crashing means a preview or a test host degrades to the password path
 * instead of taking the app down.
 */
@Composable
private fun rememberBiometricAuthenticator(): BiometricAuthenticator? {
    val context = LocalContext.current
    return remember(context) {
        (context as? FragmentActivity)?.let(::BiometricAuthenticator)
    }
}
