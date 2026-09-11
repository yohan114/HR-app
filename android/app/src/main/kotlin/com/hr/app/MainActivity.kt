package com.hr.app

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
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
import com.hr.app.ui.home.HomeScreen
import com.hr.app.ui.profile.ProfileScreen
import com.hr.app.ui.settings.NotificationSettingsScreen
import com.hr.app.ui.attendance.AttendanceScreen
import com.hr.app.ui.approvals.ApprovalsScreen
import com.hr.app.ui.leave.LeaveScreen
import com.hr.app.ui.payroll.PayslipScreen
import com.hr.app.ui.loan.LoanScreen
import com.hr.app.ui.expense.ExpenseScreen
import com.hr.app.ui.benefit.BenefitScreen
import com.hr.app.ui.lifecycle.CareerTimelineScreen
import com.hr.app.ui.disciplinary.DisciplinaryGrievanceScreen
import com.hr.app.ui.performance.PerformanceScreen
import com.hr.app.ui.recruitment.RecruitmentScreen
import com.hr.app.ui.onboarding.OnboardingScreen
import com.hr.app.ui.navigation.DeepLinkParser

import com.hr.app.ui.navigation.ParsedDeepLink
import com.hr.app.ui.navigation.TopLevelDestination
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
 * The signed-in app.
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

    val destinations = remember(canApprove) { TopLevelDestination.forUser(canApprove) }
    var selected by remember { mutableStateOf(TopLevelDestination.HOME) }
    var openProfileId by remember { mutableStateOf<java.util.UUID?>(null) }
    var openSettings by remember { mutableStateOf(false) }
    var openLeave by remember { mutableStateOf(false) }
    var openPayslipId by remember { mutableStateOf<java.util.UUID?>(null) }
    var openLoans by remember { mutableStateOf(false) }
    var openClaims by remember { mutableStateOf(false) }
    var openBenefits by remember { mutableStateOf(false) }
    var openCareer by remember { mutableStateOf(false) }
    var openDisciplinary by remember { mutableStateOf(false) }
    var openPerformance by remember { mutableStateOf(false) }
    var openRecruitment by remember { mutableStateOf(false) }
    var openOnboarding by remember { mutableStateOf(false) }
    var onboardingInitialTab by remember { mutableStateOf<String?>(null) }
    var openDocuments by remember { mutableStateOf(false) }
    var openTraining by remember { mutableStateOf(false) }
    var openTimesheets by remember { mutableStateOf(false) }
    var targetComplianceDocumentId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(deepLinkUri) {
        val uri = deepLinkUri ?: return@LaunchedEffect
        val parsed = DeepLinkParser.parse(uri) ?: return@LaunchedEffect
        when (parsed) {
            is ParsedDeepLink.Approvals -> {
                if (canApprove) {
                    selected = TopLevelDestination.APPROVALS
                    openProfileId = null
                    openSettings = false
                    openLeave = false
                    openPayslipId = null
                    openLoans = false
                    openClaims = false
                    openBenefits = false
                    openCareer = false
                    openDocuments = false
                }
            }
            is ParsedDeepLink.Time -> {
                selected = TopLevelDestination.TIME
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
                openCareer = false
                openDocuments = false
            }
            is ParsedDeepLink.People -> {
                selected = TopLevelDestination.PEOPLE
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
                openCareer = false
                openDocuments = false
            }
            is ParsedDeepLink.Me -> {
                selected = TopLevelDestination.ME
                targetComplianceDocumentId = null
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
                openCareer = false
                openDocuments = false
            }
            is ParsedDeepLink.Compliance -> {
                openDocuments = true
                targetComplianceDocumentId = parsed.documentId
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
                openCareer = false
            }
            is ParsedDeepLink.Documents -> {
                openDocuments = true
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
                openCareer = false
                openTraining = false
            }
            is ParsedDeepLink.Training -> {
                openTraining = true
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
                openCareer = false
                openDocuments = false
                openTimesheets = false
            }
            is ParsedDeepLink.Timesheets -> {
                openTimesheets = true
                openTraining = false
                openDocuments = false
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
                openCareer = false
                openDisciplinary = false
                openPerformance = false
                openRecruitment = false
                openOnboarding = false
            }
            is ParsedDeepLink.Employee -> {
                runCatching { java.util.UUID.fromString(parsed.id) }.getOrNull()?.let { uuid ->
                    openProfileId = uuid
                    openSettings = false
                    openLeave = false
                    openPayslipId = null
                    openLoans = false
                    openClaims = false
                    openBenefits = false
                    openCareer = false
                }
            }
            is ParsedDeepLink.Leave -> {
                openLeave = true
                openProfileId = null
                openSettings = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
                openCareer = false
            }
            is ParsedDeepLink.Payslip -> {
                openPayslipId = runCatching { java.util.UUID.fromString(parsed.periodId) }.getOrNull()
                    ?: java.util.UUID.fromString("00000000-0000-0000-0000-000000000201")
                openProfileId = null
                openSettings = false
                openLeave = false
                openLoans = false
                openClaims = false
                openBenefits = false
                openCareer = false
            }
            is ParsedDeepLink.Loans -> {
                openLoans = true
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openClaims = false
                openBenefits = false
                openCareer = false
            }
            is ParsedDeepLink.Claims -> {
                openClaims = true
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openBenefits = false
                openCareer = false
            }
            is ParsedDeepLink.Benefits -> {
                openBenefits = true
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openCareer = false
            }
            is ParsedDeepLink.Career -> {
                openCareer = true
                openDisciplinary = false
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
            }
            is ParsedDeepLink.Disciplinary, is ParsedDeepLink.Grievance -> {
                openDisciplinary = true
                openPerformance = false
                openCareer = false
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
            }
            is ParsedDeepLink.Performance -> {
                openPerformance = true
                openRecruitment = false
                openDisciplinary = false
                openCareer = false
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
            }
            is ParsedDeepLink.Recruitment -> {
                openRecruitment = true
                openOnboarding = false
                openPerformance = false
                openDisciplinary = false
                openCareer = false
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
            }
            is ParsedDeepLink.Onboarding -> {
                openOnboarding = true
                onboardingInitialTab = parsed.tab
                openRecruitment = false
                openPerformance = false
                openDisciplinary = false
                openCareer = false
                openProfileId = null
                openSettings = false
                openLeave = false
                openPayslipId = null
                openLoans = false
                openClaims = false
                openBenefits = false
            }
        }
        onDeepLinkConsumed()
    }


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
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                            openBenefits = false
                            openCareer = false
                            openDisciplinary = false
                            openPerformance = false
                            openRecruitment = false
                            openOnboarding = false
                            openDocuments = false
                            openTraining = false
                            openTimesheets = false
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

                openLeave ->
                    LeaveScreen(
                        viewModel = hiltViewModel(),
                        onNavigateBack = { openLeave = false },
                    )

                openPayslipId != null ->
                    PayslipScreen(
                        initialPayslipId = openPayslipId,
                        onNavigateBack = { openPayslipId = null },
                    )

                openLoans ->
                    LoanScreen(
                        viewModel = hiltViewModel(),
                        onNavigateBack = { openLoans = false },
                    )

                openClaims ->
                    ExpenseScreen(
                        viewModel = hiltViewModel(),
                        onNavigateBack = { openClaims = false },
                    )

                openBenefits ->
                    BenefitScreen(
                        viewModel = hiltViewModel(),
                        onNavigateBack = { openBenefits = false },
                    )

                openCareer ->
                    CareerTimelineScreen(
                        onNavigateBack = { openCareer = false },
                    )

                openDisciplinary ->
                    DisciplinaryGrievanceScreen(
                        onNavigateBack = { openDisciplinary = false },
                    )

                openPerformance ->
                    PerformanceScreen(
                        viewModel = hiltViewModel(),
                        onNavigateBack = { openPerformance = false },
                    )

                openRecruitment ->
                    RecruitmentScreen(
                        viewModel = hiltViewModel(),
                        onNavigateBack = { openRecruitment = false },
                    )

                openOnboarding ->
                    OnboardingScreen(
                        viewModel = hiltViewModel(),
                        initialTab = onboardingInitialTab,
                        onNavigateBack = { openOnboarding = false },
                    )

                openDocuments ->
                    com.hr.app.ui.document.DocumentScreen(
                        viewModel = hiltViewModel(),
                        onNavigateBack = { openDocuments = false },
                    )

                openTraining ->
                    com.hr.app.ui.training.TrainingScreen(
                        viewModel = hiltViewModel(),
                        onNavigateBack = { openTraining = false },
                    )

                openTimesheets ->
                    com.hr.app.ui.timesheet.TimesheetScreen(
                        viewModel = hiltViewModel(),
                        onNavigateBack = { openTimesheets = false },
                    )

                openProfile != null ->
                    ProfileScreen(employeeId = openProfile, onBack = { openProfileId = null })

                selected == TopLevelDestination.HOME ->
                    HomeScreen(
                        onNavigateToTab = { destination ->
                            selected = destination
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                        },
                        onOpenProfile = { employeeId -> openProfileId = employeeId },
                        onNavigateToCompliance = { docId ->
                            selected = TopLevelDestination.ME
                            targetComplianceDocumentId = docId
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                        },
                        onNavigateToLeave = {
                            openLeave = true
                            openProfileId = null
                            openSettings = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                        },
                        onNavigateToPayslips = {
                            openPayslipId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000201")
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openLoans = false
                            openClaims = false
                        },
                        onNavigateToLoans = {
                            openLoans = true
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openClaims = false
                        },
                        onNavigateToClaims = {
                            openClaims = true
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openBenefits = false
                        },
                        onNavigateToBenefits = {
                            openBenefits = true
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                        },
                    )

                selected == TopLevelDestination.PEOPLE ->
                    DirectoryScreen(onOpenProfile = { openProfileId = it })

                selected == TopLevelDestination.ME ->
                    ProfileScreen(
                        employeeId = null,
                        targetDocumentId = targetComplianceDocumentId,
                        onBack = onSignOut,
                        onOpenSettings = { openSettings = true },
                        onOpenPayslips = {
                            openPayslipId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000201")
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openLoans = false
                            openClaims = false
                        },
                        onOpenLoans = {
                            openLoans = true
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openClaims = false
                        },
                        onOpenClaims = {
                            openClaims = true
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openBenefits = false
                        },
                        onOpenBenefits = {
                            openBenefits = true
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                            openCareer = false
                        },
                        onOpenCareer = {
                            openCareer = true
                            openDisciplinary = false
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                            openBenefits = false
                        },
                        onOpenDisciplinary = {
                            openDisciplinary = true
                            openPerformance = false
                            openCareer = false
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                            openBenefits = false
                        },
                        onOpenPerformance = {
                            openPerformance = true
                            openRecruitment = false
                            openDisciplinary = false
                            openCareer = false
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                            openBenefits = false
                        },
                        onOpenRecruitment = {
                            openRecruitment = true
                            openPerformance = false
                            openDisciplinary = false
                            openCareer = false
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                            openBenefits = false
                            openOnboarding = false
                        },
                        onOpenOnboarding = {
                            openOnboarding = true
                            onboardingInitialTab = null
                            openRecruitment = false
                            openPerformance = false
                            openDisciplinary = false
                            openCareer = false
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                            openBenefits = false
                            openDocuments = false
                        },
                        onOpenDocuments = {
                            openDocuments = true
                            openOnboarding = false
                            openRecruitment = false
                            openPerformance = false
                            openDisciplinary = false
                            openCareer = false
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                            openBenefits = false
                            openTraining = false
                        },
                        onOpenTraining = {
                            openTraining = true
                            openDocuments = false
                            openOnboarding = false
                            openRecruitment = false
                            openPerformance = false
                            openDisciplinary = false
                            openCareer = false
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                            openBenefits = false
                            openTimesheets = false
                        },
                        onOpenTimesheets = {
                            openTimesheets = true
                            openTraining = false
                            openDocuments = false
                            openOnboarding = false
                            openRecruitment = false
                            openPerformance = false
                            openDisciplinary = false
                            openCareer = false
                            openProfileId = null
                            openSettings = false
                            openLeave = false
                            openPayslipId = null
                            openLoans = false
                            openClaims = false
                            openBenefits = false
                        },
                    )


                selected == TopLevelDestination.TIME ->
                    AttendanceScreen()

                selected == TopLevelDestination.APPROVALS ->
                    ApprovalsScreen()

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

