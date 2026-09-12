package com.hr.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HrNavigationTest {

    @Test
    fun `TopLevelDestination forUser includes Approvals only when user has approval authority`() {
        val approverTabs = TopLevelDestination.forUser(canApprove = true)
        assertTrue(approverTabs.contains(TopLevelDestination.APPROVALS))
        assertEquals(5, approverTabs.size)

        val employeeTabs = TopLevelDestination.forUser(canApprove = false)
        assertFalse(employeeTabs.contains(TopLevelDestination.APPROVALS))
        assertEquals(4, employeeTabs.size)
        assertTrue(employeeTabs.contains(TopLevelDestination.HOME))
        assertTrue(employeeTabs.contains(TopLevelDestination.TIME))
        assertTrue(employeeTabs.contains(TopLevelDestination.PEOPLE))
        assertTrue(employeeTabs.contains(TopLevelDestination.ME))
    }

    @Test
    fun `HrDestinations route builders generate valid parameterized routes`() {
        val testUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")

        assertEquals("profile/00000000-0000-0000-0000-000000000001", HrDestinations.profile(testUuid))
        assertEquals("profile/emp-42", HrDestinations.profile("emp-42"))

        assertEquals("me", HrDestinations.me(null))
        assertEquals("me", HrDestinations.me(""))
        assertEquals("me?documentId=doc-99", HrDestinations.me("doc-99"))

        assertEquals("payslip", HrDestinations.payslip(null as String?))
        assertEquals("payslip", HrDestinations.payslip(null as UUID?))
        assertEquals("payslip?periodId=period-1", HrDestinations.payslip("period-1"))
        assertEquals("payslip?periodId=00000000-0000-0000-0000-000000000001", HrDestinations.payslip(testUuid))

        assertEquals("leave", HrDestinations.leave(null))
        assertEquals("leave", HrDestinations.leave(""))
        assertEquals("leave?id=lv-123", HrDestinations.leave("lv-123"))

        assertEquals("onboarding", HrDestinations.onboarding(null))
        assertEquals("onboarding", HrDestinations.onboarding(""))
        assertEquals("onboarding?tab=team", HrDestinations.onboarding("team"))

        assertEquals("documents", HrDestinations.documents(null, null))
        assertEquals("documents?tab=vault", HrDestinations.documents("vault", null))
        assertEquals("documents?id=d-1", HrDestinations.documents(null, "d-1"))
        assertEquals("documents?tab=vault&id=d-1", HrDestinations.documents("vault", "d-1"))

        assertEquals("training", HrDestinations.training(null, null))
        assertEquals("training?tab=catalog", HrDestinations.training("catalog", null))
        assertEquals("training?id=c-1", HrDestinations.training(null, "c-1"))
        assertEquals("training?tab=catalog&id=c-1", HrDestinations.training("catalog", "c-1"))

        assertEquals("timesheets", HrDestinations.timesheets(null, null))
        assertEquals("timesheets?tab=matrix", HrDestinations.timesheets("matrix", null))
        assertEquals("timesheets?id=t-1", HrDestinations.timesheets(null, "t-1"))
        assertEquals("timesheets?tab=matrix&id=t-1", HrDestinations.timesheets("matrix", "t-1"))
    }

    @Test
    fun `DeepLinks constants use hrapp scheme`() {
        assertEquals("hrapp", DeepLinks.SCHEME)
        assertTrue(DeepLinks.APPROVALS.startsWith("hrapp://"))
        assertTrue(DeepLinks.TIME.startsWith("hrapp://"))
        assertTrue(DeepLinks.PEOPLE.startsWith("hrapp://"))
        assertTrue(DeepLinks.DIRECTORY.startsWith("hrapp://"))
        assertTrue(DeepLinks.ME.startsWith("hrapp://"))
        assertTrue(DeepLinks.PROFILE.startsWith("hrapp://"))
        assertTrue(DeepLinks.LEAVE.startsWith("hrapp://"))
        assertTrue(DeepLinks.PAYSLIP.startsWith("hrapp://"))
        assertTrue(DeepLinks.EMPLOYEE.startsWith("hrapp://"))
    }
}
