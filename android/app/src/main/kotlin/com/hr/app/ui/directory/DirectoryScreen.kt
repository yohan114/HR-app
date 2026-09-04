package com.hr.app.ui.directory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.client.model.DirectoryEntry
import com.hr.app.ui.theme.Spacing

/**
 * Search-first: the field is the screen, and results update as you type.
 *
 * No initial browse-all state beyond the first page, because in a ten-thousand-person company a
 * list you scroll is not a way to find anybody — the search box is.
 */
@Composable
fun DirectoryScreen(
    onOpenProfile: (java.util.UUID) -> Unit,
    viewModel: DirectoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.s4)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChanged,
            label = { Text("Search people") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val error = state.error
        when {
            error != null ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    TextButton(onClick = viewModel::retry) { Text("Try again") }
                }

            state.loading && state.entries.isEmpty() ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }

            state.entries.isEmpty() ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text =
                            if (state.query.isBlank()) {
                                "No colleagues to show yet."
                            } else {
                                "Nothing matched “${state.query}”."
                            },
                        textAlign = TextAlign.Center,
                    )
                }

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    // Keyed on id so recomposition tracks people rather than positions — without
                    // it, every result shifting by one row as you type rebuilds the whole list.
                    items(state.entries, key = { it.id }) { entry ->
                        DirectoryRow(entry, onClick = { onOpenProfile(entry.id) })
                        HorizontalDivider()
                    }
                }
        }
    }
}

@Composable
private fun DirectoryRow(
    entry: DirectoryEntry,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                // The whole row, not just the name: a 48dp target is the accessibility minimum and
                // a tappable name alone is a smaller one.
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.s2),
    ) {
        Text(entry.displayName, style = MaterialTheme.typography.bodyLarge)

        // Every one of these may legitimately be absent: the projection omits what the caller has
        // no business seeing, and not everybody has a designation or a desk phone. Joined so the
        // row does not show a column of dashes.
        val detail = listOfNotNull(entry.designation, entry.department).joinToString(" · ")
        if (detail.isNotEmpty()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
