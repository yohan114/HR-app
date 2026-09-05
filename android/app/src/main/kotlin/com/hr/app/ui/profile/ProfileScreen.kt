package com.hr.app.ui.profile

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.FormField
import java.util.UUID

/**
 * An employee profile, with the field list taken from the server.
 *
 * **The schema decides what is drawn, not this file.** Fields the caller may not see are absent
 * from it, so they are absent here — and because they were never in the list, there is no row to
 * render and nothing to disclose. That is the whole reason the field list comes from the server:
 * the typed model makes every property nullable, so it cannot tell "withheld" from "empty", and a
 * screen built from the model would draw a blank row for a value it was refused.
 */
@Composable
fun ProfileScreen(
    employeeId: UUID?,
    onBack: () -> Unit,
    /**
     * Null for somebody else's profile.
     *
     * Settings belong to the person using the app, so the entry point only appears on their own
     * record — offering it from a colleague's profile would suggest it configures theirs.
     */
    onOpenSettings: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(employeeId) { viewModel.load(employeeId) }

    val error = state.error
    val profile = state.profile

    when {
        error != null ->
            Centred {
                Text(
                    text = error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                TextButton(onClick = viewModel::retry) { Text("Try again") }
                TextButton(onClick = onBack) { Text("Back") }
            }

        state.loading || profile == null -> Centred { CircularProgressIndicator() }

        else -> {
            // Own profile has no edit form, and everything on it belongs to the caller — so there
            // is nothing to withhold and the fallback list is safe.
            val fields: List<FormField> =
                state.schema?.sections?.flatMap { it.fields } ?: OWN_PROFILE_FIELDS

            LazyColumn(modifier = Modifier.fillMaxSize().padding(Spacing.s4)) {
                item {
                    Text(
                        text = profile.displayName ?: "Employee",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = Spacing.s3),
                    )
                }

                items(fields, key = { it.key }) { field ->
                    ProfileRow(label = field.label, value = profile.valueFor(field.key))
                    HorizontalDivider()
                }

                if (onOpenSettings != null) {
                    item {
                        TextButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.padding(top = Spacing.s3),
                        ) {
                            Text("Notification settings")
                        }
                    }
                }

                item {
                    TextButton(onClick = onBack, modifier = Modifier.padding(top = Spacing.s3)) {
                        Text("Back")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    label: String,
    value: String?,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s2)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // An em dash rather than an empty line, so the row still reads as a row. Safe here because
        // the field is in the schema, which means the caller is permitted it — so blank genuinely
        // means nobody filled it in.
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

/**
 * The fallback list for the caller's own profile.
 *
 * `/v1/employees/me` has no companion form endpoint, and it does not need one: every field on it is
 * the caller's own, so nothing is being withheld. Kept short and boring — the fields somebody
 * actually checks about themselves.
 */
private val OWN_PROFILE_FIELDS =
    listOf(
        FormField(key = "employeeCode", label = "Employee code", type = FormField.Type.TEXT),
        FormField(key = "workEmail", label = "Work email", type = FormField.Type.EMAIL),
        FormField(key = "mobile", label = "Mobile", type = FormField.Type.PHONE),
        FormField(key = "joinDate", label = "Join date", type = FormField.Type.DATE),
        FormField(key = "yearsOfService", label = "Years of service", type = FormField.Type.NUMBER),
        FormField(key = "status", label = "Status", type = FormField.Type.TEXT),
    )
