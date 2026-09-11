package com.hr.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.app.R
import com.hr.app.ui.navigation.TopLevelDestination
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import java.util.UUID

/**
 * The mobile home dashboard screen.
 *
 * Implements P1-AND-07…10:
 * Displays role-adaptive server-driven cards (Pending Approvals, Milestones, Expiring Documents,
 * Quick Actions, Announcements).
 */
@Composable
fun HomeScreen(
    onNavigateToTab: (TopLevelDestination) -> Unit,
    onOpenProfile: (UUID) -> Unit,
    onNavigateToCompliance: ((String?) -> Unit)? = null,
    onNavigateToLeave: () -> Unit = {},
    onNavigateToPayslips: () -> Unit = {},
    onNavigateToLoans: () -> Unit = {},
    onNavigateToClaims: () -> Unit = {},
    onNavigateToBenefits: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {

    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4),
    ) {
        Spacer(modifier = Modifier.height(Spacing.s3))

        // Top App Header: Greeting & Sync indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (state.tenantName.isNotBlank()) {
                    Text(
                        text = state.tenantName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = viewModel::refresh) {
                if (state.refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh dashboard",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Offline Banner
        if (state.isOffline) {
            Spacer(modifier = Modifier.height(Spacing.s2))
            Surface(
                shape = RoundedCornerShape(Radius.control),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.s3, vertical = Spacing.s2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Text(
                        text = stringResource(R.string.sync_offline),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.s3))

        // Content States
        when {
            state.loading && state.cards.isEmpty() ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Text(
                        text = "Loading your dashboard…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            state.error != null && state.cards.isEmpty() ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.error ?: "Could not load dashboard.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    TextButton(onClick = viewModel::retry) {
                        Text("Try again")
                    }
                }

            state.cards.isEmpty() ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "You're all caught up!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(Spacing.s1))
                    Text(
                        text = "No pending approvals or updates at the moment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

            else ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    contentPadding = PaddingValues(bottom = Spacing.s6),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                ) {
                    items(state.cards, key = { it.id }) { card ->
                        HomeCardRenderer(
                            card = card,
                            pendingOutboxEntities = state.pendingOutboxEntities,
                            onNavigateToTab = onNavigateToTab,
                            onOpenProfile = onOpenProfile,
                            onNavigateToCompliance = onNavigateToCompliance,
                            onNavigateToLeave = onNavigateToLeave,
                            onNavigateToPayslips = onNavigateToPayslips,
                            onNavigateToLoans = onNavigateToLoans,
                            onNavigateToClaims = onNavigateToClaims,
                            onNavigateToBenefits = onNavigateToBenefits,
                        )

                    }
                }
        }
    }
}
