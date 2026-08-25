package com.hr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hr.app.ui.navigation.TopLevelDestination
import com.hr.app.ui.theme.HrTheme
import com.hr.app.ui.theme.Spacing
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity hosting the Compose UI.
 *
 * Currently a navigation shell only. Real screens arrive in Phase 1 (auth, directory, profile,
 * org chart) — the shell exists now so those screens land into a structure that already handles
 * edge-to-edge insets, theming and role-adaptive tabs.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before setContent so the splash theme hands over cleanly.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_HR)

        setContent {
            HrTheme {
                HrAppShell()
            }
        }
    }
}

@Composable
private fun HrAppShell() {
    // Wired to `GET /v1/me` permissions in Phase 1. Hardcoded here so the shell is demonstrable.
    val canApprove = true
    val destinations = remember(canApprove) { TopLevelDestination.forUser(canApprove) }
    var selected by remember { mutableStateOf(TopLevelDestination.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = { selected = destination },
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.s4),
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
        }
    }
}
