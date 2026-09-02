package com.hr.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.app.ui.theme.Spacing

/**
 * Sign-in: organisation, then credentials, then a second factor if the account has one.
 *
 * The organisation step is first because it lets someone type a work email instead of hunting for
 * a "service URL" — the single most complained-about step in the product this replaces. Once a
 * device has signed in, it is skipped entirely.
 */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                // Without imePadding the keyboard covers the button the user is trying to reach,
                // on exactly the screen where that is most infuriating.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (state.organisationName.isNotEmpty()) state.organisationName else "Sign in",
            style = MaterialTheme.typography.headlineMedium,
        )

        when (state.step) {
            SignInStep.ORGANISATION -> OrganisationStep(state, viewModel)
            SignInStep.CREDENTIALS -> CredentialsStep(state, viewModel)
            SignInStep.MFA -> MfaStep(state, viewModel)
        }

        // Bound to a local so the compiler can smart-cast it: `state` is a delegated property, and
        // a null check on one of its fields does not narrow the type at the use site.
        val error = state.error
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                // Announced when it appears. A sighted user sees the message under the field; a
                // screen-reader user would otherwise get no signal that the button did anything.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }

        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.padding(top = Spacing.s2))
        }
    }
}

@Composable
private fun OrganisationStep(
    state: SignInState,
    viewModel: SignInViewModel,
) {
    OutlinedTextField(
        value = state.orgInput,
        onValueChange = viewModel::onOrgInputChanged,
        label = { Text("Work email or organisation code") },
        singleLine = true,
        enabled = !state.busy,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.resolveOrganisation() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = viewModel::resolveOrganisation,
        enabled = !state.busy && state.orgInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Continue")
    }
}

@Composable
private fun CredentialsStep(
    state: SignInState,
    viewModel: SignInViewModel,
) {
    OutlinedTextField(
        value = state.username,
        onValueChange = viewModel::onUsernameChanged,
        label = { Text("Username or email") },
        singleLine = true,
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChanged,
        label = { Text("Password") },
        singleLine = true,
        enabled = !state.busy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.signIn() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = viewModel::signIn,
        enabled = !state.busy && state.username.isNotBlank() && state.password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Sign in")
    }
    TextButton(onClick = viewModel::changeOrganisation, enabled = !state.busy) {
        Text("Not ${state.organisationName.ifEmpty { "this organisation" }}?")
    }
}

@Composable
private fun MfaStep(
    state: SignInState,
    viewModel: SignInViewModel,
) {
    Text(
        text = "Enter the code from your authenticator app, or one of your recovery codes.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = state.code,
        onValueChange = viewModel::onCodeChanged,
        label = { Text("Verification code") },
        singleLine = true,
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.verifyMfa() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = viewModel::verifyMfa,
        enabled = !state.busy && state.code.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Verify")
    }
}
