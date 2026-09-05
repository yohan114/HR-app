package com.hr.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeepLinkTest {
    @Test
    fun `built links match the registered routes`() {
        assertTrue(DeepLink.isSupported(DeepLink.approvals()))
        assertTrue(DeepLink.isSupported(DeepLink.leave("7f3c")))
        assertTrue(DeepLink.isSupported(DeepLink.payslip("2026-03")))
        assertTrue(DeepLink.isSupported(DeepLink.employee("7f3c")))
    }

    /**
     * `deep_link_pattern` is tenant-editable. Without this guard, template-edit rights become the
     * ability to point an entire workforce's notifications at a site of the editor's choosing.
     */
    @Test
    fun `a link outside the scheme is rejected`() {
        assertFalse(DeepLink.isSupported("https://example.test/phish"))
        assertFalse(DeepLink.isSupported("javascript:alert(1)"))
        assertFalse(DeepLink.isSupported("hrapps://leave/1"))
        assertFalse(DeepLink.isSupported(""))
    }

    @Test
    fun `an unknown route is rejected`() {
        assertFalse(DeepLink.isSupported("hrapp://payroll/run"))
        assertFalse(DeepLink.isSupported("hrapp://leave"), "leave needs an id")
        assertFalse(DeepLink.isSupported("hrapp://leave/1/extra"))
    }

    @Test
    fun `a query string does not break matching`() {
        assertTrue(DeepLink.isSupported("hrapp://leave/7f3c?from=push"))
    }

    @Test
    fun `every route template validates against itself`() {
        DeepLink.ROUTES.forEach { route ->
            val concrete = route.replace(Regex("""\{[^}]+\}"""), "value")
            assertTrue(DeepLink.isSupported(concrete), "$route did not match its own shape")
        }
    }
}
