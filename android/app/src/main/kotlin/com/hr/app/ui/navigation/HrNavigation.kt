package com.hr.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.ui.graphics.vector.ImageVector
import com.hr.app.R

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
         *
         * Approvals is replaced by Requests — the user's own submissions — when they approve
         * nothing. Showing an empty inbox to every employee would be worse than showing nothing.
         */
        fun forUser(canApprove: Boolean): List<TopLevelDestination> =
            entries.filter { !it.requiresApprovalAuthority || canApprove }
    }
}

/** Deep-link routes from push notifications. See docs/05-screens-ux.md §2. */
object DeepLinks {
    const val SCHEME = "hrapp"

    const val APPROVALS = "$SCHEME://approvals"
    const val LEAVE = "$SCHEME://leave/{id}"
    const val PAYSLIP = "$SCHEME://payslip/{periodId}"
    const val EMPLOYEE = "$SCHEME://employee/{id}"
}
