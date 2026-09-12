package com.hr.dashboard

import com.hr.dashboard.internal.DashboardService
import com.hr.identity.Caller
import com.hr.identity.internal.AppUser
import com.hr.identity.internal.AppUserRepository
import com.hr.identity.internal.PermissionResolver
import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional
import java.util.UUID

@DisplayName("Role-Based Composite Dashboard (Phase C — GET /v1/dashboard)")
class DashboardServiceTest {

    private val permissionResolver = mock(PermissionResolver::class.java)
    private val userRepository = mock(AppUserRepository::class.java)
    private val service = DashboardService(permissionResolver, userRepository)

    private val tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val userId = UUID.randomUUID()
    private val caller = Caller(userId = userId, employeeId = UUID.randomUUID(), deviceId = UUID.randomUUID())

    @BeforeEach
    fun setUp() {
        TenantContext.set(
            TenantHandle(
                id = tenantId,
                code = "test",
                name = "Test Tenant",
                dataRegion = "default",
                defaultCurrency = "USD",
                timezone = "UTC",
                locale = "en-US",
                isolationTier = IsolationTier.SHARED,
                status = TenantStatus.ACTIVE,
            ),
        )
        val mockUser = AppUser(
            username = "test.user",
            email = "test@example.com",
            passwordHash = null,
            employeeId = null,
            status = com.hr.identity.internal.UserStatus.ACTIVE,
            locale = "en-US",
            timezone = "UTC",
        )
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(mockUser))
    }

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `employee role receives personalized employee widgets and actions`() {
        `when`(permissionResolver.rolesFor(userId)).thenReturn(listOf("EMPLOYEE"))
        `when`(permissionResolver.permissionsFor(userId)).thenReturn(emptySet())

        val response = service.getDashboard(caller)

        assertThat(response.greeting).contains("test.user")
        val widgetKeys = response.widgets.map { it.key }
        assertThat(widgetKeys).contains(
            "emp_attendance",
            "emp_leave_balance",
            "emp_latest_payslip",
            "emp_training",
            "org_chart",
        )
        assertThat(widgetKeys).doesNotContain("hr_headcount", "finance_payroll_run", "manager_approvals")

        val actionKeys = response.quickActions.map { it.key }
        assertThat(actionKeys).contains("REQUEST_LEAVE", "CLOCK_IN", "CLAIM_EXPENSE", "APPLY_LOAN", "ORG_CHART")
    }

    @Test
    fun `manager role receives approvals, team attendance and who is off widgets`() {
        `when`(permissionResolver.rolesFor(userId)).thenReturn(listOf("MANAGER", "EMPLOYEE"))
        `when`(permissionResolver.permissionsFor(userId)).thenReturn(setOf("leave.application.manage"))

        val response = service.getDashboard(caller)

        val widgetKeys = response.widgets.map { it.key }
        assertThat(widgetKeys).contains(
            "manager_approvals",
            "manager_team_attendance",
            "manager_who_is_off",
            "emp_attendance",
        )

        val actionKeys = response.quickActions.map { it.key }
        assertThat(actionKeys).contains("APPROVALS")
    }

    @Test
    fun `hr admin role receives headcount, onboarding, probations and leave liability widgets`() {
        `when`(permissionResolver.rolesFor(userId)).thenReturn(listOf("HR_ADMIN", "EMPLOYEE"))
        `when`(permissionResolver.permissionsFor(userId)).thenReturn(setOf("employee.record.view"))

        val response = service.getDashboard(caller)

        val widgetKeys = response.widgets.map { it.key }
        assertThat(widgetKeys).contains(
            "hr_headcount",
            "hr_onboarding",
            "hr_lifecycle_probations",
            "hr_disciplinary",
            "hr_leave_liability",
        )

        val actionKeys = response.quickActions.map { it.key }
        assertThat(actionKeys).contains("ONBOARDING", "BATCH_TOOLS")
    }

    @Test
    fun `finance role receives payroll run, gross cost, expense and loan widgets`() {
        `when`(permissionResolver.rolesFor(userId)).thenReturn(listOf("FINANCE", "EMPLOYEE"))
        `when`(permissionResolver.permissionsFor(userId)).thenReturn(setOf("payroll.run.view"))

        val response = service.getDashboard(caller)

        val widgetKeys = response.widgets.map { it.key }
        assertThat(widgetKeys).contains(
            "finance_payroll_run",
            "finance_cost_centre",
            "finance_expense_claims",
            "finance_loan_balances",
        )

        val actionKeys = response.quickActions.map { it.key }
        assertThat(actionKeys).contains("PAYROLL_RUN")
    }

    @Test
    fun `system admin role receives all administrative and infrastructure widgets`() {
        `when`(permissionResolver.rolesFor(userId)).thenReturn(listOf("ADMIN"))
        `when`(permissionResolver.permissionsFor(userId)).thenReturn(setOf("identity.user.manage"))

        val response = service.getDashboard(caller)

        val widgetKeys = response.widgets.map { it.key }
        assertThat(widgetKeys).contains(
            "admin_active_users",
            "admin_biometric_devices",
            "admin_notifications",
            "hr_headcount",
            "finance_payroll_run",
            "manager_approvals",
        )
    }

    @Test
    fun `dashboard caches response for 5 minutes per tenant and caller`() {
        `when`(permissionResolver.rolesFor(userId)).thenReturn(listOf("EMPLOYEE"))
        `when`(permissionResolver.permissionsFor(userId)).thenReturn(emptySet())

        val firstCall = service.getDashboard(caller)
        val secondCall = service.getDashboard(caller)

        assertThat(firstCall.asOf).isEqualTo(secondCall.asOf)
        assertThat(firstCall.widgets).isEqualTo(secondCall.widgets)
    }
}
