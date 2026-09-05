package com.hr.app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.app.ui.SessionViewModel
import com.hr.app.ui.auth.BiometricEnrolmentPrompt
import com.hr.app.ui.auth.BiometricUnlockScreen
import com.hr.app.ui.auth.SignInScreen
import com.hr.app.ui.directory.DirectoryScreen
import com.hr.app.ui.profile.ProfileScreen
import com.hr.app.ui.settings.NotificationSettingsScreen
import com.hr.app.ui.navigation.TopLevelDestination
import com.hr.app.ui.theme.HrTheme
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.MeResponse
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity hosting the Compose UI.
 *
 * Currently a navigation shell only. Real screens arrive in Phase 1 (auth, directory, profile,
 * org chart) — the shell exists now so those screens land into a structure that already handles
 * edge-to-edge insets, theming and role-adaptive tabs.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before setContent so the splash theme hands over cleanly.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_HR)

        setContent {
            HrTheme {
                HrApp()
            }
        }
    }
}

/**
 * Sign-in or the app, depending on whether there is a session.
 *
 * State is held here rather than in a navigation graph because there is exactly one decision and it
 * has no back stack: signing out must not leave the shell reachable with the system back button,
 * and a `NavHost` would need an explicit `popUpTo` to guarantee that. One boolean cannot get
 * that wrong.
 */
@Composable
private fun HrApp(viewModel: SessionViewModel = hiltViewModel()) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val startWithBiometric by viewModel.startWithBiometric.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val profileFailed by viewModel.profileFailed.collectAsStateWithLifecycle()
    val canApprove by viewModel.canApprove.collectAsStateWithLifecycle()
    var offerEnrolment by rememberSaveable { mutableStateOf(false) }

    when {
        // Offered once per sign-in, and skippable. Placed before the shell rather than inside
        // Settings because this is the only moment we hold a live refresh token *and* the user has
        // just proved who they are — the two things sealing one requires.
        signedIn && offerEnrolment ->
            BiometricEnrolmentPrompt(onFinished = { offerEnrolment = false })

        signedIn ->
            HrAppShell(
                user = currentUser,
                canApprove = canApprove,
                profileFailed = profileFailed,
                onRetryProfile = viewModel::loadProfile,
                onSignOut = viewModel::signOut,
            )

        // A device that has enrolled starts here, not on the password form. This is the feature.
        startWithBiometric ->
            BiometricUnlockScreen(
                onUnlocked = {
                    viewModel.onSignedIn()
                    offerEnrolment = true
                },
                onUsePassword = viewModel::usePasswordInstead,
            )

        else ->
            SignInScreen(
                onSignedIn = {
                    viewModel.onSignedIn()
                    offerEnrolment = true
                },
            )
    }
}

/**
 * The signed-in app.
 *
 * Waits for `GET /v1/me` before drawing the tab bar. Rendering a default set and correcting it a
 * moment later would move every tab sideways under the user's thumb — and the tab set is not
 * cosmetic here, it is the role-adaptive navigation the product is designed around.
 */
@Composable
private fun HrAppShell(
    user: MeResponse?,
    canApprove: Boolean,
    profileFailed: Boolean,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    if (user == null) {
        LoadingOrRetry(failed = profileFailed, onRetry = onRetryProfile)
        return
    }

    val destinations = remember(canApprove) { TopLevelDestination.forUser(canApprove) }
    var selected by remember { mutableStateOf(TopLevelDestination.HOME) }
    var openProfileId by remember { mutableStateOf<java.util.UUID?>(null) }
    var openSettings by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = {
                            selected = destination
                            openProfileId = null
                            openSettings = false
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                // Null: the label below already announces the destination, and a
                                // duplicate content description makes screen readers read it twice.
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // A profile opened from the directory sits *over* the tab it came from, and Back
            // returns to the search with its query intact. Losing what you typed because you
            // looked at a result is the fastest way to make a directory unusable.
            val openProfile = openProfileId
            when {
                openSettings ->
                    NotificationSettingsScreen(onBack = { openSettings = false })

                openProfile != null ->
                    ProfileScreen(employeeId = openProfile, onBack = { openProfileId = null })

                selected == TopLevelDestination.PEOPLE ->
                    DirectoryScreen(onOpenProfile = { openProfileId = it })

                selected == TopLevelDestination.ME ->
                    ProfileScreen(
                        employeeId = null,
                        onBack = onSignOut,
                        onOpenSettings = { openSettings = true },
                    )

                // The remaining tabs land with the modules that fill them. The placeholder names
                // the destination so the shell is navigable rather than blank.
                else ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(selected.labelRes),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = stringResource(R.string.placeholder_phase_one),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onSignOut) { Text("Sign out") }
                    }
            }
        }
    }
}

/**
 * Shown while `/v1/me` loads, and offering a retry if it fails.
 *
 * A failure here is not a sign-out: the session is valid, the request was not. Dropping the user
 * back to the password form would make a flaky network look like an expired login.
 */
@Composable
private fun LoadingOrRetry(
    failed: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (failed) {
            Text("Could not load your profile.")
            TextButton(onClick = onRetry) { Text("Try again") }
        } else {
            CircularProgressIndicator()
        }
    }
}

