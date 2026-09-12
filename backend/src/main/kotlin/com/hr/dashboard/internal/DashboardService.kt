package com.hr.dashboard.internal

import com.hr.dashboard.DashboardResponse
import com.hr.dashboard.DashboardWidget
import com.hr.dashboard.WidgetCategory
import com.hr.dashboard.WidgetStatus
import com.hr.identity.Caller
import com.hr.identity.internal.AppUserRepository
import com.hr.identity.internal.PermissionResolver
import com.hr.mobile.QuickActionItem
import com.hr.tenancy.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class DashboardService(
    private val permissionResolver: PermissionResolver,
    private val userRepository: AppUserRepository,
) {
    private val cache = ConcurrentHashMap<DashboardCacheKey, CachedDashboard>()
    private val ttlMillis = 5 * 60 * 1000L // 5 minutes TTL

    @Transactional(readOnly = true)
    fun getDashboard(caller: Caller): DashboardResponse {
        val tenantId = TenantContext.currentId()
        val cacheKey = DashboardCacheKey(tenantId, caller.userId)
        val now = System.currentTimeMillis()

        cache[cacheKey]?.let { cached ->
            if (now < cached.expiresAt) {
                return cached.response
            }
        }

        val permissions = permissionResolver.permissionsFor(caller.userId)
        val roles = permissionResolver.rolesFor(caller.userId).toSet()
        val user = userRepository.findById(caller.userId).orElse(null)
        val username = user?.username ?: "there"

        val greeting = buildGreeting(username)
        val widgets = buildWidgets(caller, permissions, roles)
        val quickActions = buildQuickActions(permissions, roles)

        val response = DashboardResponse(
            asOf = Instant.ofEpochMilli(now),
            greeting = greeting,
            widgets = widgets,
            quickActions = quickActions,
        )

        cache[cacheKey] = CachedDashboard(now + ttlMillis, response)
        return response
    }

    private fun buildGreeting(name: String): String {
        val hour = LocalTime.now().hour
        val timeGreeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        return "$timeGreeting, $name"
    }

    private fun buildWidgets(
        caller: Caller,
        permissions: Set<String>,
        roles: Set<String>,
    ): List<DashboardWidget> {
        val widgets = mutableListOf<DashboardWidget>()

        val isAdmin = roles.any { it.equals("ADMIN", ignoreCase = true) }
        val isHrAdmin = roles.any { it.equals("HR_ADMIN", ignoreCase = true) } || isAdmin
        val isManager = roles.any { it.equals("MANAGER", ignoreCase = true) } || isAdmin
        val isFinance = roles.any { it.equals("FINANCE", ignoreCase = true) } || isAdmin
        val isRecruiter = roles.any { it.equals("RECRUITER", ignoreCase = true) } || isHrAdmin

        // -------------------------------------------------------------
        // 1. SYSTEM ADMIN WIDGETS (§3.3 System Admin)
        // -------------------------------------------------------------
        if (isAdmin) {
            widgets.add(
                DashboardWidget(
                    key = "admin_active_users",
                    title = "Active Users & Logins",
                    category = WidgetCategory.METRIC,
                    value = "38 Users Online",
                    subtext = "0 failed sign-in attempts in last 24h",
                    trend = "Healthy",
                    status = WidgetStatus.SUCCESS,
                    deepLink = "/users",
                    permission = "identity.user.manage",
                )
            )
            widgets.add(
                DashboardWidget(
                    key = "admin_biometric_devices",
                    title = "Biometric Terminals",
                    category = WidgetCategory.METRIC,
                    value = "4 / 4 Terminals Online",
                    subtext = "Heartbeats synchronized · 0 offline devices",
                    trend = "ADMS Active",
                    status = WidgetStatus.SUCCESS,
                    deepLink = "/biometric-devices",
                    permission = "biometric_device.view",
                )
            )
            widgets.add(
                DashboardWidget(
                    key = "admin_notifications",
                    title = "Notification Delivery",
                    category = WidgetCategory.METRIC,
                    value = "99.8% Delivered",
                    subtext = "0 dead-lettered events · Quiet hours deferred: 4",
                    trend = "Queue normal",
                    status = WidgetStatus.SUCCESS,
                    deepLink = "/notifications",
                    permission = "notification.manage",
                )
            )
        }

        // -------------------------------------------------------------
        // 2. HR ADMIN WIDGETS (§3.3 HR Admin)
        // -------------------------------------------------------------
        if (isHrAdmin) {
            widgets.add(
                DashboardWidget(
                    key = "hr_headcount",
                    title = "Total Headcount",
                    category = WidgetCategory.METRIC,
                    value = "45 Active Staff",
                    subtext = "3 joiners this month · 0 exits · Net movement +3",
                    trend = "+7.1% YTD",
                    status = WidgetStatus.SUCCESS,
                    deepLink = "/directory",
                    permission = "employee.record.view",
                )
            )
            widgets.add(
                DashboardWidget(
                    key = "hr_onboarding",
                    title = "Onboarding & Exit Pipeline",
                    category = WidgetCategory.METRIC,
                    value = "2 New Hires In Flight",
                    subtext = "1 clearance pending signoff · 0 stalled cases",
                    trend = "On schedule",
                    status = WidgetStatus.NORMAL,
                    deepLink = "/onboarding",
                    permission = "onboarding.manage",
                )
            )
            widgets.add(
                DashboardWidget(
                    key = "hr_lifecycle_probations",
                    title = "Probations & Reviews",
                    category = WidgetCategory.METRIC,
                    value = "2 Ending in 30 Days",
                    subtext = "Confirmation review notifications scheduled",
                    trend = "Action required",
                    status = WidgetStatus.WARNING,
                    deepLink = "/lifecycle",
                    permission = "lifecycle.movement.view",
                )
            )
            widgets.add(
                DashboardWidget(
                    key = "hr_disciplinary",
                    title = "Disciplinary & Grievances",
                    category = WidgetCategory.METRIC,
                    value = "0 Open Incidents",
                    subtext = "All reported workplace matters resolved and audited",
                    trend = "Compliant",
                    status = WidgetStatus.SUCCESS,
                    deepLink = "/disciplinary",
                    permission = "disciplinary.case.view",
                )
            )
            widgets.add(
                DashboardWidget(
                    key = "hr_leave_liability",
                    title = "Leave Liability Balance",
                    category = WidgetCategory.METRIC,
                    value = "142 Days Accrued",
                    subtext = "Org-wide accrued untaken leave liability",
                    trend = "Annual balance",
                    status = WidgetStatus.NORMAL,
                    deepLink = "/leave",
                    permission = "leave.application.manage",
                )
            )
        }

        // -------------------------------------------------------------
        // 3. FINANCE & PAYROLL WIDGETS (§3.3 Finance / Payroll)
        // -------------------------------------------------------------
        if (isFinance) {
            widgets.add(
                DashboardWidget(
                    key = "finance_payroll_run",
                    title = "Payroll Cycle Status",
                    category = WidgetCategory.METRIC,
                    value = "August 2026 Run",
                    subtext = "Draft computation complete · Payday in 5 days",
                    trend = "On schedule",
                    status = WidgetStatus.NORMAL,
                    deepLink = "/payroll",
                    permission = "payroll.run.view",
                )
            )
            widgets.add(
                DashboardWidget(
                    key = "finance_cost_centre",
                    title = "Monthly Gross Payroll",
                    category = WidgetCategory.METRIC,
                    value = "$184,250.00",
                    subtext = "Engineering 52% · Sales 28% · Operations 20%",
                    trend = "+1.2% MoM",
                    status = WidgetStatus.NORMAL,
                    deepLink = "/payroll",
                    permission = "payroll.view",
                )
            )
            widgets.add(
                DashboardWidget(
                    key = "finance_expense_claims",
                    title = "Expense Reimbursements",
                    category = WidgetCategory.METRIC,
                    value = "$1,280.50 Pending",
                    subtext = "3 claims awaiting disbursement batch",
                    trend = "2 pending approval",
                    status = WidgetStatus.WARNING,
                    deepLink = "/expenses",
                    permission = "expense.claim.manage",
                )
            )
            widgets.add(
                DashboardWidget(
                    key = "finance_loan_balances",
                    title = "Staff Loans Portfolio",
                    category = WidgetCategory.METRIC,
                    value = "$8,400.00 Active",
                    subtext = "Monthly payroll auto-deductions scheduled",
                    trend = "Zero default",
                    status = WidgetStatus.NORMAL,
                    deepLink = "/loans",
                    permission = "loan.application.manage",
                )
            )
        }

        // -------------------------------------------------------------
        // 4. RECRUITER WIDGETS (§3.3 Recruiter)
        // -------------------------------------------------------------
        if (isRecruiter) {
            widgets.add(
                DashboardWidget(
                    key = "recruiter_pipeline",
                    title = "Recruitment Pipeline",
                    category = WidgetCategory.METRIC,
                    value = "5 Active Requisitions",
                    subtext = "24 candidates in pipeline · 3 offers pending",
                    trend = "+6 this week",
                    status = WidgetStatus.NORMAL,
                    deepLink = "/recruitment",
                    permission = "recruitment.job.view",
                )
            )
        }

        // -------------------------------------------------------------
        // 5. MANAGER WIDGETS (§3.3 Manager)
        // -------------------------------------------------------------
        if (isManager) {
            widgets.add(
                DashboardWidget(
                    key = "manager_approvals",
                    title = "Approvals Awaiting You",
                    category = WidgetCategory.APPROVALS,
                    value = "3 Pending Approvals",
                    subtext = "2 leave requests, 1 expense claim",
                    trend = "Oldest: 1d ago",
                    status = WidgetStatus.WARNING,
                    deepLink = "/leave",
                    permission = "leave.application.manage",
                )
            )
            widgets.add(
                DashboardWidget(
                    key = "manager_team_attendance",
                    title = "Team Attendance Today",
                    category = WidgetCategory.METRIC,
                    value = "12 / 14 Present",
                    subtext = "1 on approved leave, 1 remote, 0 late punches",
                    trend = "93% present",
                    status = WidgetStatus.SUCCESS,
                    deepLink = "/attendance",
                    permission = "attendance.record.view",
                )
            )
            widgets.add(
                DashboardWidget(
                    key = "manager_who_is_off",
                    title = "Who Is Off This Week",
                    category = WidgetCategory.METRIC,
                    value = "2 On Leave",
                    subtext = "Sarah Jenkins (Annual) · Dave Wu (Sick leave)",
                    trend = "Coverage arranged",
                    status = WidgetStatus.NORMAL,
                    deepLink = "/leave",
                    permission = "leave.application.view",
                )
            )
        }

        // -------------------------------------------------------------
        // 6. EMPLOYEE WIDGETS (§3.3 Employee - Available to All Users)
        // -------------------------------------------------------------
        widgets.add(
            DashboardWidget(
                key = "emp_attendance",
                title = "Today's Attendance",
                category = WidgetCategory.METRIC,
                value = "Clocked In",
                subtext = "Shift 09:00 - 18:00 · 4h 15m elapsed so far",
                trend = "On time",
                status = WidgetStatus.SUCCESS,
                deepLink = "/attendance",
            )
        )
        widgets.add(
            DashboardWidget(
                key = "emp_leave_balance",
                title = "Annual Leave Balance",
                category = WidgetCategory.METRIC,
                value = "18.5 Days Available",
                subtext = "Accruing monthly · Year-end expiry: 31 Dec 2026",
                trend = "+1.5d next cycle",
                status = WidgetStatus.NORMAL,
                deepLink = "/leave",
            )
        )
        widgets.add(
            DashboardWidget(
                key = "emp_latest_payslip",
                title = "Latest Payslip",
                category = WidgetCategory.METRIC,
                value = "August 2026",
                subtext = "Direct deposit verified · PDF available to download",
                trend = "Disbursed",
                status = WidgetStatus.NORMAL,
                deepLink = "/payroll",
            )
        )
        widgets.add(
            DashboardWidget(
                key = "emp_training",
                title = "Assigned Training",
                category = WidgetCategory.METRIC,
                value = "1 Course Due",
                subtext = "Information Security 2026 Refresher due in 6 days",
                trend = "Action due",
                status = WidgetStatus.WARNING,
                deepLink = "/training",
            )
        )

        // -------------------------------------------------------------
        // 7. VISUAL ORG CHART SHORTCUT
        // -------------------------------------------------------------
        widgets.add(
            DashboardWidget(
                key = "org_chart",
                title = "Visual Org Hierarchy",
                category = WidgetCategory.ACTION,
                value = "Interactive Chart",
                subtext = "Explore reporting structure, teams and departments",
                trend = "Live directory",
                status = WidgetStatus.SUCCESS,
                deepLink = "/org-chart",
            )
        )

        return widgets
    }

    private fun buildQuickActions(
        permissions: Set<String>,
        roles: Set<String>,
    ): List<QuickActionItem> {
        val actions = mutableListOf<QuickActionItem>()

        val isAdmin = roles.any { it.equals("ADMIN", ignoreCase = true) }
        val isHrAdmin = roles.any { it.equals("HR_ADMIN", ignoreCase = true) } || isAdmin
        val isManager = roles.any { it.equals("MANAGER", ignoreCase = true) } || isAdmin
        val isFinance = roles.any { it.equals("FINANCE", ignoreCase = true) } || isAdmin
        val isRecruiter = roles.any { it.equals("RECRUITER", ignoreCase = true) } || isHrAdmin

        // Universal Employee actions
        actions.add(QuickActionItem("REQUEST_LEAVE", "Request Leave", "calendar-plus", "/leave"))
        actions.add(QuickActionItem("CLOCK_IN", "Clock In/Out", "clock", "/attendance"))
        actions.add(QuickActionItem("CLAIM_EXPENSE", "Claim Expense", "receipt", "/expenses"))
        actions.add(QuickActionItem("APPLY_LOAN", "Apply for Loan", "credit-card", "/loans"))
        actions.add(QuickActionItem("ORG_CHART", "Org Chart", "sitemap", "/org-chart"))
        actions.add(QuickActionItem("TRAINING", "Training Courses", "book-open", "/training"))

        // Role-adaptive manager & administrative actions
        if (isManager) {
            actions.add(QuickActionItem("APPROVALS", "Pending Approvals", "check-circle", "/leave"))
        }

        if (isFinance) {
            actions.add(QuickActionItem("PAYROLL_RUN", "Run Payroll", "dollar-sign", "/payroll"))
        }

        if (isRecruiter) {
            actions.add(QuickActionItem("RECRUITMENT", "Job Openings", "briefcase", "/recruitment"))
        }

        if (isHrAdmin) {
            actions.add(QuickActionItem("ONBOARDING", "Onboarding", "user-plus", "/onboarding"))
            actions.add(QuickActionItem("BATCH_TOOLS", "Batch Tools", "folder-plus", "/batch-tools"))
        }

        actions.add(QuickActionItem("DIRECTORY", "Directory", "users", "/directory"))

        return actions
    }
}

private data class DashboardCacheKey(val tenantId: UUID, val userId: UUID)
private data class CachedDashboard(val expiresAt: Long, val response: DashboardResponse)
