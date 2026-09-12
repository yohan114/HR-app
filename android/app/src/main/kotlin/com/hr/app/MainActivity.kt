package com.hr.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hr.app.ui.SessionViewModel
import com.hr.app.ui.auth.BiometricEnrolmentPrompt
import com.hr.app.ui.auth.BiometricUnlockScreen
import com.hr.app.ui.auth.SignInScreen
import com.hr.app.ui.navigation.DeepLinkParser
import com.hr.app.ui.navigation.HrNavHost
import com.hr.app.ui.navigation.TopLevelDestination
import com.hr.app.ui.navigation.navigateToDeepLink
import com.hr.app.ui.theme.HrTheme
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.MeResponse
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The single activity hosting the Compose UI.
 *
 * Handles deep links from push notifications, supporting both cold launch and [onNewIntent] routing.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val incomingDeepLink = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before setContent so the splash theme hands over cleanly.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_HR)

        intent?.data?.let { incomingDeepLink.value = it }

        setContent {
            HrTheme {
                val deepLinkUri by incomingDeepLink.collectAsStateWithLifecycle()
                HrApp(
                    deepLinkUri = deepLinkUri,
                    onDeepLinkConsumed = { incomingDeepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { incomingDeepLink.value = it }
    }
}

/**
 * Sign-in or the app, depending on whether there is a session.
 */
@Composable
private fun HrApp(
    viewModel: SessionViewModel = hiltViewModel(),
    deepLinkUri: Uri? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val startWithBiometric by viewModel.startWithBiometric.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val profileFailed by viewModel.profileFailed.collectAsStateWithLifecycle()
    val canApprove by viewModel.canApprove.collectAsStateWithLifecycle()
    var offerEnrolment by rememberSaveable { mutableStateOf(false) }

    when {
        // Offered once per sign-in, and skippable.
        signedIn && offerEnrolment ->
            BiometricEnrolmentPrompt(onFinished = { offerEnrolment = false })

        signedIn ->
            HrAppShell(
                user = currentUser,
                canApprove = canApprove,
                profileFailed = profileFailed,
                deepLinkUri = deepLinkUri,
                onDeepLinkConsumed = onDeepLinkConsumed,
                onRetryProfile = viewModel::loadProfile,
                onSignOut = viewModel::signOut,
            )

        // A device that has enrolled starts here, not on the password form.
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
 * The signed-in app shell using Jetpack Compose Navigation.
 */
@Composable
private fun HrAppShell(
    user: MeResponse?,
    canApprove: Boolean,
    profileFailed: Boolean,
    deepLinkUri: Uri? = null,
    onDeepLinkConsumed: () -> Unit = {},
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    if (user == null) {
        LoadingOrRetry(failed = profileFailed, onRetry = onRetryProfile)
        return
    }

    val navController = rememberNavController()
    val destinations = remember(canApprove) { TopLevelDestination.forUser(canApprove) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Only display the bottom navigation bar on top-level destinations
    val isTopLevelDestination = destinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    LaunchedEffect(deepLinkUri) {
        val uri = deepLinkUri ?: return@LaunchedEffect
        val parsed = DeepLinkParser.parse(uri) ?: return@LaunchedEffect
        navController.navigateToDeepLink(parsed, canApprove)
        onDeepLinkConsumed()
    }

    Scaffold(
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar {
                    destinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
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
            }
        },
    ) { padding ->
        HrNavHost(
            navController = navController,
            canApprove = canApprove,
            onSignOut = onSignOut,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
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
