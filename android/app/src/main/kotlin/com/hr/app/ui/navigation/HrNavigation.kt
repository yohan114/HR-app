package com.hr.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.hr.app.R
import java.util.UUID

/**
 * The bottom navigation destinations.
 *
 * Five tabs, per docs/05-screens-ux.md §2. Note [requiresApprovalAuthority] on Approvals: the app
 * is **role-adaptive, not role-switched** — there is no "switch to manager mode". An employee with
 * no approval authority sees Requests in that slot instead; the tab bar composes itself from who
 * you are.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val requiresApprovalAuthority: Boolean = false,
) {
    HOME("home", R.string.nav_home, Icons.Default.Home),
    TIME("time", R.string.nav_time, Icons.Default.AccessTime),
    APPROVALS("approvals", R.string.nav_approvals, Icons.Default.CheckCircle, requiresApprovalAuthority = true),
    PEOPLE("people", R.string.nav_people, Icons.Default.People),
    ME("me", R.string.nav_me, Icons.Default.Person),
    ;

    companion object {
        /**
         * The tabs to show for a given user.
         */
        fun forUser(canApprove: Boolean): List<TopLevelDestination> =
            entries.filter { !it.requiresApprovalAuthority || canApprove }
    }
}

/** Deep-link URI scheme and constants. See docs/05-screens-ux.md §2. */
object DeepLinks {
    const val SCHEME = "hrapp"

    const val APPROVALS = "$SCHEME://approvals"
    const val TIME = "$SCHEME://time"
    const val PEOPLE = "$SCHEME://people"
    const val DIRECTORY = "$SCHEME://directory"
    const val ME = "$SCHEME://me"
    const val PROFILE = "$SCHEME://profile"
    const val LEAVE = "$SCHEME://leave/{id}"
    const val PAYSLIP = "$SCHEME://payslip/{periodId}"
    const val EMPLOYEE = "$SCHEME://employee/{id}"
}

/**
 * Route names, patterns, and helper builders for navigation across the entire app.
 */
object HrDestinations {
    // Top-level destinations
    const val HOME = "home"
    const val TIME = "time"
    const val APPROVALS = "approvals"
    const val PEOPLE = "people"
    const val ME = "me"
    const val ME_PATTERN = "me?documentId={documentId}"

    // Secondary / Feature destinations
    const val SETTINGS = "settings"

    const val LEAVE = "leave"
    const val LEAVE_PATTERN = "leave?id={id}"

    const val PAYSLIP = "payslip"
    const val PAYSLIP_PATTERN = "payslip?periodId={periodId}"

    const val PROFILE = "profile"
    const val PROFILE_PATTERN = "profile/{employeeId}"

    const val LOANS = "loans"
    const val LOANS_PATTERN = "loans?id={id}"

    const val CLAIMS = "claims"
    const val CLAIMS_PATTERN = "claims?id={id}"

    const val BENEFITS = "benefits"
    const val BENEFITS_PATTERN = "benefits?id={id}"

    const val CAREER = "career"
    const val CAREER_PATTERN = "career?id={id}"

    const val DISCIPLINARY = "disciplinary"
    const val DISCIPLINARY_PATTERN = "disciplinary?id={id}"

    const val PERFORMANCE = "performance"
    const val PERFORMANCE_PATTERN = "performance?id={id}"

    const val RECRUITMENT = "recruitment"
    const val RECRUITMENT_PATTERN = "recruitment?id={id}"

    const val ONBOARDING = "onboarding"
    const val ONBOARDING_PATTERN = "onboarding?tab={tab}"

    const val DOCUMENTS = "documents"
    const val DOCUMENTS_PATTERN = "documents?tab={tab}&id={id}"

    const val TRAINING = "training"
    const val TRAINING_PATTERN = "training?tab={tab}&id={id}"

    const val TIMESHEETS = "timesheets"
    const val TIMESHEETS_PATTERN = "timesheets?tab={tab}&id={id}"

    // Route builders
    fun profile(employeeId: UUID): String = "profile/$employeeId"
    fun profile(employeeId: String): String = "profile/$employeeId"

    fun me(targetDocumentId: String? = null): String =
        if (targetDocumentId.isNullOrBlank()) ME else "me?documentId=$targetDocumentId"

    fun payslip(periodId: String? = null): String =
        if (periodId.isNullOrBlank()) PAYSLIP else "payslip?periodId=$periodId"
    fun payslip(periodId: UUID?): String =
        if (periodId == null) PAYSLIP else "payslip?periodId=$periodId"

    fun leave(id: String? = null): String =
        if (id.isNullOrBlank()) LEAVE else "leave?id=$id"

    fun onboarding(tab: String? = null): String =
        if (tab.isNullOrBlank()) ONBOARDING else "onboarding?tab=$tab"

    fun documents(tab: String? = null, id: String? = null): String {
        val params = mutableListOf<String>()
        if (!tab.isNullOrBlank()) params.add("tab=$tab")
        if (!id.isNullOrBlank()) params.add("id=$id")
        return if (params.isEmpty()) DOCUMENTS else "$DOCUMENTS?${params.joinToString("&")}"
    }

    fun training(tab: String? = null, id: String? = null): String {
        val params = mutableListOf<String>()
        if (!tab.isNullOrBlank()) params.add("tab=$tab")
        if (!id.isNullOrBlank()) params.add("id=$id")
        return if (params.isEmpty()) TRAINING else "$TRAINING?${params.joinToString("&")}"
    }

    fun timesheets(tab: String? = null, id: String? = null): String {
        val params = mutableListOf<String>()
        if (!tab.isNullOrBlank()) params.add("tab=$tab")
        if (!id.isNullOrBlank()) params.add("id=$id")
        return if (params.isEmpty()) TIMESHEETS else "$TIMESHEETS?${params.joinToString("&")}"
    }
}

/**
 * Extension on [NavController] to navigate to deep link destinations consistently.
 */
fun NavController.navigateToDeepLink(parsed: ParsedDeepLink, canApprove: Boolean) {
    when (parsed) {
        is ParsedDeepLink.Approvals -> {
            if (canApprove) {
                navigate(TopLevelDestination.APPROVALS.route) {
                    popUpTo(graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        is ParsedDeepLink.Time -> {
            navigate(TopLevelDestination.TIME.route) {
                popUpTo(graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        is ParsedDeepLink.People -> {
            navigate(TopLevelDestination.PEOPLE.route) {
                popUpTo(graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        is ParsedDeepLink.Me -> {
            navigate(TopLevelDestination.ME.route) {
                popUpTo(graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        is ParsedDeepLink.Employee -> {
            navigate(HrDestinations.profile(parsed.id))
        }
        is ParsedDeepLink.Leave -> {
            navigate(HrDestinations.leave(parsed.id))
        }
        is ParsedDeepLink.Payslip -> {
            navigate(HrDestinations.payslip(parsed.periodId))
        }
        is ParsedDeepLink.Compliance -> {
            navigate(HrDestinations.documents(id = parsed.documentId))
        }
        is ParsedDeepLink.Documents -> {
            navigate(HrDestinations.documents(tab = parsed.tab, id = parsed.id))
        }
        is ParsedDeepLink.Training -> {
            navigate(HrDestinations.training(tab = parsed.tab, id = parsed.id))
        }
        is ParsedDeepLink.Timesheets -> {
            navigate(HrDestinations.timesheets(tab = parsed.tab, id = parsed.id))
        }
        is ParsedDeepLink.Loans -> {
            navigate(HrDestinations.LOANS)
        }
        is ParsedDeepLink.Claims -> {
            navigate(HrDestinations.CLAIMS)
        }
        is ParsedDeepLink.Benefits -> {
            navigate(HrDestinations.BENEFITS)
        }
        is ParsedDeepLink.Career -> {
            navigate(HrDestinations.CAREER)
        }
        is ParsedDeepLink.Disciplinary, is ParsedDeepLink.Grievance -> {
            navigate(HrDestinations.DISCIPLINARY)
        }
        is ParsedDeepLink.Performance -> {
            navigate(HrDestinations.PERFORMANCE)
        }
        is ParsedDeepLink.Recruitment -> {
            navigate(HrDestinations.RECRUITMENT)
        }
        is ParsedDeepLink.Onboarding -> {
            navigate(HrDestinations.onboarding(tab = parsed.tab))
        }
    }
}
