package com.hr.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkParserTest {

    @Test
    fun `parse approvals link resolves to Approvals destination`() {
        val result = DeepLinkParser.parse("hrapp://approvals")
        assertEquals(ParsedDeepLink.Approvals, result)
    }

    @Test
    fun `parse approvals link with trailing slash resolves correctly`() {
        val result = DeepLinkParser.parse("hrapp://approvals/")
        assertEquals(ParsedDeepLink.Approvals, result)
    }

    @Test
    fun `parse time link resolves to Time destination`() {
        val result = DeepLinkParser.parse("hrapp://time")
        assertEquals(ParsedDeepLink.Time, result)
    }

    @Test
    fun `parse directory and people links resolve to People destination`() {
        assertEquals(ParsedDeepLink.People, DeepLinkParser.parse("hrapp://people"))
        assertEquals(ParsedDeepLink.People, DeepLinkParser.parse("hrapp://directory"))
    }

    @Test
    fun `parse me and profile links resolve to Me destination`() {
        assertEquals(ParsedDeepLink.Me, DeepLinkParser.parse("hrapp://me"))
        assertEquals(ParsedDeepLink.Me, DeepLinkParser.parse("hrapp://profile"))
    }

    @Test
    fun `parse leave link extracts application id`() {
        val result = DeepLinkParser.parse("hrapp://leave/leave-app-999")
        assertNotNull(result)
        assertTrue(result is ParsedDeepLink.Leave)
        assertEquals("leave-app-999", (result as ParsedDeepLink.Leave).id)
    }

    @Test
    fun `parse employee link extracts employee identifier`() {
        val result = DeepLinkParser.parse("hrapp://employee/00000000-0000-0000-0000-000000000004")
        assertNotNull(result)
        assertTrue(result is ParsedDeepLink.Employee)
        assertEquals("00000000-0000-0000-0000-000000000004", (result as ParsedDeepLink.Employee).id)
    }

    @Test
    fun `parse payslip link extracts periodId`() {
        val result = DeepLinkParser.parse("hrapp://payslip/period-august-2026")
        assertNotNull(result)
        assertTrue(result is ParsedDeepLink.Payslip)
        assertEquals("period-august-2026", (result as ParsedDeepLink.Payslip).periodId)
    }

    @Test
    fun `parse compliance and documents links resolve to Compliance destination`() {
        assertEquals(ParsedDeepLink.Compliance(null), DeepLinkParser.parse("hrapp://compliance"))
        assertEquals(ParsedDeepLink.Compliance(null), DeepLinkParser.parse("hrapp://documents"))
        assertEquals(
            ParsedDeepLink.Compliance("00000000-0000-0000-0000-000000000001"),
            DeepLinkParser.parse("hrapp://documents/00000000-0000-0000-0000-000000000001"),
        )
        assertEquals(
            ParsedDeepLink.Compliance("doc-123"),
            DeepLinkParser.parse("hrapp://document/doc-123"),
        )
    }

    @Test
    fun `parse loans and loan links resolves correctly`() {
        assertEquals(ParsedDeepLink.Loans(null), DeepLinkParser.parse("hrapp://loans"))
        assertEquals(ParsedDeepLink.Loans(null), DeepLinkParser.parse("hrapp://loan"))
        assertEquals(ParsedDeepLink.Loans("LN-001"), DeepLinkParser.parse("hrapp://loans/LN-001"))
        assertEquals(ParsedDeepLink.Loans("LN-002"), DeepLinkParser.parse("hrapp://loan/LN-002"))
    }

    @Test
    fun `parse claims and expenses links resolves correctly`() {
        assertEquals(ParsedDeepLink.Claims(null), DeepLinkParser.parse("hrapp://claims"))
        assertEquals(ParsedDeepLink.Claims(null), DeepLinkParser.parse("hrapp://claim"))
        assertEquals(ParsedDeepLink.Claims(null), DeepLinkParser.parse("hrapp://expenses"))
        assertEquals(ParsedDeepLink.Claims(null), DeepLinkParser.parse("hrapp://expense"))
        assertEquals(ParsedDeepLink.Claims("EXP-001"), DeepLinkParser.parse("hrapp://claims/EXP-001"))
        assertEquals(ParsedDeepLink.Claims("EXP-002"), DeepLinkParser.parse("hrapp://expenses/EXP-002"))
    }

    @Test
    fun `parse benefits and insurance links resolves correctly`() {
        assertEquals(ParsedDeepLink.Benefits(null), DeepLinkParser.parse("hrapp://benefits"))
        assertEquals(ParsedDeepLink.Benefits(null), DeepLinkParser.parse("hrapp://benefit"))
        assertEquals(ParsedDeepLink.Benefits(null), DeepLinkParser.parse("hrapp://insurance"))
        assertEquals(ParsedDeepLink.Benefits("BEN-001"), DeepLinkParser.parse("hrapp://benefits/BEN-001"))
        assertEquals(ParsedDeepLink.Benefits("BEN-002"), DeepLinkParser.parse("hrapp://benefit/BEN-002"))
        assertEquals(ParsedDeepLink.Benefits("INS-999"), DeepLinkParser.parse("hrapp://insurance/INS-999"))
    }

    @Test
    fun `parse career and movements links resolves correctly`() {
        assertEquals(ParsedDeepLink.Career(null), DeepLinkParser.parse("hrapp://career"))
        assertEquals(ParsedDeepLink.Career(null), DeepLinkParser.parse("hrapp://lifecycle"))
        assertEquals(ParsedDeepLink.Career(null), DeepLinkParser.parse("hrapp://timeline"))
        assertEquals(ParsedDeepLink.Career(null), DeepLinkParser.parse("hrapp://movements"))
        assertEquals(ParsedDeepLink.Career("MOV-001"), DeepLinkParser.parse("hrapp://career/MOV-001"))
        assertEquals(ParsedDeepLink.Career("MOV-002"), DeepLinkParser.parse("hrapp://movements/MOV-002"))
    }

    @Test
    fun `parse grievance and disciplinary links resolves correctly`() {
        assertEquals(ParsedDeepLink.Grievance(null), DeepLinkParser.parse("hrapp://grievance"))
        assertEquals(ParsedDeepLink.Grievance(null), DeepLinkParser.parse("hrapp://grievances"))
        assertEquals(ParsedDeepLink.Grievance("GRV-001"), DeepLinkParser.parse("hrapp://grievance/GRV-001"))
        assertEquals(ParsedDeepLink.Disciplinary(null), DeepLinkParser.parse("hrapp://disciplinary"))
        assertEquals(ParsedDeepLink.Disciplinary(null), DeepLinkParser.parse("hrapp://incident"))
        assertEquals(ParsedDeepLink.Disciplinary(null), DeepLinkParser.parse("hrapp://incidents"))
        assertEquals(ParsedDeepLink.Disciplinary(null), DeepLinkParser.parse("hrapp://hotline"))
        assertEquals(ParsedDeepLink.Disciplinary("DISC-001"), DeepLinkParser.parse("hrapp://disciplinary/DISC-001"))
        assertEquals(ParsedDeepLink.Disciplinary("DISC-002"), DeepLinkParser.parse("hrapp://incident/DISC-002"))
    }

    @Test
    fun `parse performance and okr links resolves correctly`() {
        assertEquals(ParsedDeepLink.Performance(null), DeepLinkParser.parse("hrapp://performance"))
        assertEquals(ParsedDeepLink.Performance(null), DeepLinkParser.parse("hrapp://goals"))
        assertEquals(ParsedDeepLink.Performance(null), DeepLinkParser.parse("hrapp://appraisal"))
        assertEquals(ParsedDeepLink.Performance(null), DeepLinkParser.parse("hrapp://okr"))
        assertEquals(ParsedDeepLink.Performance(null), DeepLinkParser.parse("hrapp://okrs"))
        assertEquals(ParsedDeepLink.Performance("G-001"), DeepLinkParser.parse("hrapp://goals/G-001"))
        assertEquals(ParsedDeepLink.Performance("APP-001"), DeepLinkParser.parse("hrapp://appraisal/APP-001"))
    }

    @Test
    fun `parse recruitment and ats links resolves correctly`() {
        assertEquals(ParsedDeepLink.Recruitment(null), DeepLinkParser.parse("hrapp://recruitment"))
        assertEquals(ParsedDeepLink.Recruitment(null), DeepLinkParser.parse("hrapp://vacancies"))
        assertEquals(ParsedDeepLink.Recruitment(null), DeepLinkParser.parse("hrapp://vacancy"))
        assertEquals(ParsedDeepLink.Recruitment(null), DeepLinkParser.parse("hrapp://ats"))
        assertEquals(ParsedDeepLink.Recruitment(null), DeepLinkParser.parse("hrapp://interviews"))
        assertEquals(ParsedDeepLink.Recruitment(null), DeepLinkParser.parse("hrapp://candidates"))
        assertEquals(ParsedDeepLink.Recruitment("VAC-2026-001"), DeepLinkParser.parse("hrapp://vacancies/VAC-2026-001"))
        assertEquals(ParsedDeepLink.Recruitment("APP-2026-001"), DeepLinkParser.parse("hrapp://ats/APP-2026-001"))
    }

    @Test
    fun `parse onboarding, offboarding, and clearance links resolves correctly`() {
        assertEquals(ParsedDeepLink.Onboarding("my_onboarding"), DeepLinkParser.parse("hrapp://onboarding"))
        assertEquals(ParsedDeepLink.Onboarding("team"), DeepLinkParser.parse("hrapp://onboarding/team"))
        assertEquals(ParsedDeepLink.Onboarding("exit_notices"), DeepLinkParser.parse("hrapp://offboarding"))
        assertEquals(ParsedDeepLink.Onboarding("exit_notices"), DeepLinkParser.parse("hrapp://exit"))
        assertEquals(ParsedDeepLink.Onboarding("exit_notices"), DeepLinkParser.parse("hrapp://resignation"))
        assertEquals(ParsedDeepLink.Onboarding("clearance"), DeepLinkParser.parse("hrapp://clearance"))
    }

    @Test
    fun `parse letters, signatures, and vault links resolves to Documents destination`() {
        assertEquals(ParsedDeepLink.Documents("letters", null), DeepLinkParser.parse("hrapp://letters"))
        assertEquals(ParsedDeepLink.Documents("letters", "LTR-001"), DeepLinkParser.parse("hrapp://letter/LTR-001"))
        assertEquals(ParsedDeepLink.Documents("signatures", null), DeepLinkParser.parse("hrapp://signatures"))
        assertEquals(ParsedDeepLink.Documents("signatures", "SIG-001"), DeepLinkParser.parse("hrapp://signature/SIG-001"))
        assertEquals(ParsedDeepLink.Documents("signatures", null), DeepLinkParser.parse("hrapp://esign"))
        assertEquals(ParsedDeepLink.Documents("vault", null), DeepLinkParser.parse("hrapp://vault"))
        assertEquals(ParsedDeepLink.Documents("vault", "DOC-999"), DeepLinkParser.parse("hrapp://vault/DOC-999"))
    }

    @Test
    fun `parse training, courses, learning, certificates, and upskill links resolves to Training destination`() {
        assertEquals(ParsedDeepLink.Training(null, null), DeepLinkParser.parse("hrapp://training"))
        assertEquals(ParsedDeepLink.Training(null, null), DeepLinkParser.parse("hrapp://courses"))
        assertEquals(ParsedDeepLink.Training("my_learning", null), DeepLinkParser.parse("hrapp://learning"))
        assertEquals(ParsedDeepLink.Training("certificates", null), DeepLinkParser.parse("hrapp://certificates"))
        assertEquals(ParsedDeepLink.Training(null, null), DeepLinkParser.parse("hrapp://upskill"))
        assertEquals(ParsedDeepLink.Training(null, "CRS-001"), DeepLinkParser.parse("hrapp://training/CRS-001"))
    }

    @Test
    fun `parse timesheet, billing, matrix, and reconciliation links resolves to Timesheets destination`() {
        assertEquals(ParsedDeepLink.Timesheets(null, null), DeepLinkParser.parse("hrapp://timesheets"))
        assertEquals(ParsedDeepLink.Timesheets(null, null), DeepLinkParser.parse("hrapp://timesheet"))
        assertEquals(ParsedDeepLink.Timesheets(null, null), DeepLinkParser.parse("hrapp://billing"))
        assertEquals(ParsedDeepLink.Timesheets("matrix", null), DeepLinkParser.parse("hrapp://matrix"))
        assertEquals(ParsedDeepLink.Timesheets("reconciliation", null), DeepLinkParser.parse("hrapp://reconciliation"))
        assertEquals(ParsedDeepLink.Timesheets(null, "TS-001"), DeepLinkParser.parse("hrapp://timesheets/TS-001"))
    }

    @Test
    fun `rejects invalid schemes and unknown routes`() {
        assertNull(DeepLinkParser.parse("https://hr.example.com/approvals"))
        assertNull(DeepLinkParser.parse("ftp://approvals"))
        assertNull(DeepLinkParser.parse("hrapp://unknown/deep/link"))
        assertNull(DeepLinkParser.parse(null as String?))
        assertNull(DeepLinkParser.parse(""))
        assertNull(DeepLinkParser.parse("   "))
    }

    @Test
    fun `rejects parameterized routes with missing id`() {
        assertNull(DeepLinkParser.parse("hrapp://leave"))
        assertNull(DeepLinkParser.parse("hrapp://leave/"))
        assertNull(DeepLinkParser.parse("hrapp://employee"))
        assertNull(DeepLinkParser.parse("hrapp://employee/"))
        assertNull(DeepLinkParser.parse("hrapp://payslip"))
        assertNull(DeepLinkParser.parse("hrapp://payslip/"))
    }
}
