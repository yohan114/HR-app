package com.hr.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.hr.app.R
import com.hr.app.ui.approvals.ApprovalsScreen
import com.hr.app.ui.attendance.AttendanceScreen
import com.hr.app.ui.benefit.BenefitScreen
import com.hr.app.ui.directory.DirectoryScreen
import com.hr.app.ui.disciplinary.DisciplinaryGrievanceScreen
import com.hr.app.ui.document.DocumentScreen
import com.hr.app.ui.expense.ExpenseScreen
import com.hr.app.ui.home.HomeScreen
import com.hr.app.ui.leave.LeaveScreen
import com.hr.app.ui.lifecycle.CareerTimelineScreen
import com.hr.app.ui.loan.LoanScreen
import com.hr.app.ui.onboarding.OnboardingScreen
import com.hr.app.ui.payroll.PayslipScreen
import com.hr.app.ui.performance.PerformanceScreen
import com.hr.app.ui.profile.ProfileScreen
import com.hr.app.ui.recruitment.RecruitmentScreen
import com.hr.app.ui.settings.NotificationSettingsScreen
import com.hr.app.ui.theme.Spacing
import com.hr.app.ui.timesheet.TimesheetScreen
import com.hr.app.ui.training.TrainingScreen
import java.util.UUID

/**
 * Top-level Jetpack Compose NavHost managing routing, arguments, backstack, and deep links
 * across all top-level destinations and feature sub-screens.
 */
@Composable
fun HrNavHost(
    navController: NavHostController,
    canApprove: Boolean,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = TopLevelDestination.HOME.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // --- Top-Level Destinations ---
        composable(
            route = HrDestinations.HOME,
            deepLinks = listOf(navDeepLink { uriPattern = "hrapp://home" }),
        ) {
            HomeScreen(
                onNavigateToTab = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenProfile = { employeeId ->
                    navController.navigate(HrDestinations.profile(employeeId))
                },
                onNavigateToCompliance = { docId ->
                    navController.navigate(HrDestinations.documents(id = docId))
                },
                onNavigateToLeave = {
                    navController.navigate(HrDestinations.LEAVE)
                },
                onNavigateToPayslips = {
                    navController.navigate(HrDestinations.payslip(periodId = "00000000-0000-0000-0000-000000000201"))
                },
                onNavigateToLoans = {
                    navController.navigate(HrDestinations.LOANS)
                },
                onNavigateToClaims = {
                    navController.navigate(HrDestinations.CLAIMS)
                },
                onNavigateToBenefits = {
                    navController.navigate(HrDestinations.BENEFITS)
                },
            )
        }

        composable(
            route = HrDestinations.TIME,
            deepLinks = listOf(navDeepLink { uriPattern = "hrapp://time" }),
        ) {
            AttendanceScreen()
        }

        composable(
            route = HrDestinations.APPROVALS,
            deepLinks = listOf(navDeepLink { uriPattern = "hrapp://approvals" }),
        ) {
            if (canApprove) {
                ApprovalsScreen()
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(Spacing.s4),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.nav_approvals),
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

        composable(
            route = HrDestinations.PEOPLE,
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://people" },
                navDeepLink { uriPattern = "hrapp://directory" },
            ),
        ) {
            DirectoryScreen(
                onOpenProfile = { employeeId ->
                    navController.navigate(HrDestinations.profile(employeeId))
                },
            )
        }

        composable(
            route = HrDestinations.ME_PATTERN,
            arguments = listOf(
                navArgument("documentId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://me" },
                navDeepLink { uriPattern = "hrapp://profile" },
            ),
        ) { backStackEntry ->
            val targetDocId = backStackEntry.arguments?.getString("documentId")
            ProfileScreen(
                employeeId = null,
                targetDocumentId = targetDocId,
                onBack = onSignOut,
                onOpenSettings = { navController.navigate(HrDestinations.SETTINGS) },
                onOpenPayslips = {
                    navController.navigate(HrDestinations.payslip(periodId = "00000000-0000-0000-0000-000000000201"))
                },
                onOpenLoans = { navController.navigate(HrDestinations.LOANS) },
                onOpenClaims = { navController.navigate(HrDestinations.CLAIMS) },
                onOpenBenefits = { navController.navigate(HrDestinations.BENEFITS) },
                onOpenCareer = { navController.navigate(HrDestinations.CAREER) },
                onOpenDisciplinary = { navController.navigate(HrDestinations.DISCIPLINARY) },
                onOpenPerformance = { navController.navigate(HrDestinations.PERFORMANCE) },
                onOpenRecruitment = { navController.navigate(HrDestinations.RECRUITMENT) },
                onOpenOnboarding = { navController.navigate(HrDestinations.ONBOARDING) },
                onOpenDocuments = { navController.navigate(HrDestinations.DOCUMENTS) },
                onOpenTraining = { navController.navigate(HrDestinations.TRAINING) },
                onOpenTimesheets = { navController.navigate(HrDestinations.TIMESHEETS) },
            )
        }

        // --- Secondary / Detail Screens ---
        composable(
            route = HrDestinations.PROFILE_PATTERN,
            arguments = listOf(
                navArgument("employeeId") {
                    type = NavType.StringType
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://employee/{employeeId}" },
            ),
        ) { backStackEntry ->
            val employeeIdStr = backStackEntry.arguments?.getString("employeeId")
            val employeeUuid = employeeIdStr?.let { id -> runCatching { UUID.fromString(id) }.getOrNull() }
            ProfileScreen(
                employeeId = employeeUuid,
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = HrDestinations.SETTINGS) {
            NotificationSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = HrDestinations.LEAVE_PATTERN,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://leave/{id}" },
                navDeepLink { uriPattern = "hrapp://leave" },
            ),
        ) {
            LeaveScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = HrDestinations.PAYSLIP_PATTERN,
            arguments = listOf(
                navArgument("periodId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://payslip/{periodId}" },
                navDeepLink { uriPattern = "hrapp://payslip" },
            ),
        ) { backStackEntry ->
            val periodIdStr = backStackEntry.arguments?.getString("periodId")
            val payslipUuid = periodIdStr?.let { id -> runCatching { UUID.fromString(id) }.getOrNull() }
                ?: UUID.fromString("00000000-0000-0000-0000-000000000201")
            PayslipScreen(
                initialPayslipId = payslipUuid,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = HrDestinations.LOANS_PATTERN,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://loans" },
                navDeepLink { uriPattern = "hrapp://loan" },
            ),
        ) {
            LoanScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = HrDestinations.CLAIMS_PATTERN,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://claims" },
                navDeepLink { uriPattern = "hrapp://expenses" },
                navDeepLink { uriPattern = "hrapp://claim" },
                navDeepLink { uriPattern = "hrapp://expense" },
            ),
        ) {
            ExpenseScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = HrDestinations.BENEFITS_PATTERN,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://benefits" },
                navDeepLink { uriPattern = "hrapp://benefit" },
            ),
        ) {
            BenefitScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = HrDestinations.CAREER_PATTERN,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://career" },
                navDeepLink { uriPattern = "hrapp://timeline" },
            ),
        ) {
            CareerTimelineScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = HrDestinations.DISCIPLINARY_PATTERN,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://disciplinary" },
                navDeepLink { uriPattern = "hrapp://grievance" },
            ),
        ) {
            DisciplinaryGrievanceScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = HrDestinations.PERFORMANCE_PATTERN,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://performance" },
            ),
        ) {
            PerformanceScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = HrDestinations.RECRUITMENT_PATTERN,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://recruitment" },
            ),
        ) {
            RecruitmentScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = HrDestinations.ONBOARDING_PATTERN,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://onboarding" },
                navDeepLink { uriPattern = "hrapp://offboarding" },
            ),
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getString("tab")
            OnboardingScreen(
                viewModel = hiltViewModel(),
                initialTab = initialTab,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = HrDestinations.DOCUMENTS_PATTERN,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://compliance" },
                navDeepLink { uriPattern = "hrapp://documents" },
                navDeepLink { uriPattern = "hrapp://document" },
            ),
        ) {
            DocumentScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = HrDestinations.TRAINING_PATTERN,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://training" },
            ),
        ) {
            TrainingScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = HrDestinations.TIMESHEETS_PATTERN,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "hrapp://timesheets" },
                navDeepLink { uriPattern = "hrapp://timesheet" },
            ),
        ) {
            TimesheetScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
