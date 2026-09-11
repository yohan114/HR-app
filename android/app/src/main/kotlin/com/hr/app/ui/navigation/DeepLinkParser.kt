package com.hr.app.ui.navigation

import android.net.Uri

/**
 * Parsed representation of deep link URIs across the application.
 */
sealed interface ParsedDeepLink {
    data object Approvals : ParsedDeepLink
    data object Time : ParsedDeepLink
    data object People : ParsedDeepLink
    data object Me : ParsedDeepLink
    data class Leave(val id: String) : ParsedDeepLink
    data class Employee(val id: String) : ParsedDeepLink
    data class Payslip(val periodId: String) : ParsedDeepLink
    data class Compliance(val documentId: String? = null) : ParsedDeepLink
    data class Loans(val id: String? = null) : ParsedDeepLink
    data class Claims(val id: String? = null) : ParsedDeepLink
    data class Benefits(val id: String? = null) : ParsedDeepLink
    data class Career(val id: String? = null) : ParsedDeepLink
    data class Disciplinary(val id: String? = null) : ParsedDeepLink
    data class Grievance(val id: String? = null) : ParsedDeepLink
    data class Performance(val id: String? = null) : ParsedDeepLink
    data class Recruitment(val id: String? = null) : ParsedDeepLink
    data class Onboarding(val tab: String? = null) : ParsedDeepLink
    data class Documents(val tab: String? = null, val id: String? = null) : ParsedDeepLink
    data class Training(val tab: String? = null, val id: String? = null) : ParsedDeepLink
    data class Timesheets(val tab: String? = null, val id: String? = null) : ParsedDeepLink
}


/**
 * Parser for `hrapp://` URI schemes into strongly-typed [ParsedDeepLink] instances.
 *
 * Implements client-side contract matching backend `DeepLink.ROUTES`:
 * - `hrapp://approvals` -> [ParsedDeepLink.Approvals]
 * - `hrapp://time` -> [ParsedDeepLink.Time]
 * - `hrapp://leave/{id}` -> [ParsedDeepLink.Leave]
 * - `hrapp://employee/{id}` -> [ParsedDeepLink.Employee]
 * - `hrapp://payslip/{id}` -> [ParsedDeepLink.Payslip]
 * - `hrapp://directory` or `hrapp://people` -> [ParsedDeepLink.People]
 * - `hrapp://profile` or `hrapp://me` -> [ParsedDeepLink.Me]
 * - `hrapp://compliance` or `hrapp://documents` or `hrapp://documents/{id}` -> [ParsedDeepLink.Compliance]
 */
object DeepLinkParser {
    const val SCHEME = "hrapp"

    fun parse(uriString: String?): ParsedDeepLink? {
        if (uriString.isNullOrBlank()) return null
        val trimmed = uriString.trim()
        val prefix = "$SCHEME://"
        if (!trimmed.startsWith(prefix, ignoreCase = true)) return null

        val pathAndQuery = trimmed.substring(prefix.length)
        if (pathAndQuery.isBlank()) return null

        val rawPath = pathAndQuery.substringBefore('?').substringBefore('#')
        val segments = rawPath.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null

        val host = segments[0].lowercase()
        val remaining = segments.drop(1)

        return when {
            host == "approvals" -> ParsedDeepLink.Approvals
            host == "time" -> ParsedDeepLink.Time
            host == "directory" || host == "people" -> ParsedDeepLink.People
            host == "me" || host == "profile" -> ParsedDeepLink.Me
            host == "compliance" || host == "documents" || host == "document" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                ParsedDeepLink.Compliance(id)
            }
            host == "leave" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
                ParsedDeepLink.Leave(id)
            }
            host == "employee" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
                ParsedDeepLink.Employee(id)
            }
            host == "payslip" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
                ParsedDeepLink.Payslip(id)
            }
            host == "loans" || host == "loan" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                ParsedDeepLink.Loans(id)
            }
            host == "claims" || host == "claim" || host == "expenses" || host == "expense" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                ParsedDeepLink.Claims(id)
            }
            host == "benefits" || host == "benefit" || host == "insurance" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                ParsedDeepLink.Benefits(id)
            }
            host == "career" || host == "lifecycle" || host == "timeline" || host == "movements" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                ParsedDeepLink.Career(id)
            }
            host == "grievance" || host == "grievances" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                ParsedDeepLink.Grievance(id)
            }
            host == "disciplinary" || host == "incident" || host == "incidents" || host == "hotline" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                ParsedDeepLink.Disciplinary(id)
            }
            host == "performance" || host == "goals" || host == "appraisal" || host == "okr" || host == "okrs" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                ParsedDeepLink.Performance(id)
            }
            host == "recruitment" || host == "vacancies" || host == "vacancy" || host == "ats" || host == "interviews" || host == "candidates" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                ParsedDeepLink.Recruitment(id)
            }
            host == "onboarding" -> {
                val tab = remaining.firstOrNull()?.takeIf { it.isNotBlank() } ?: "my_onboarding"
                ParsedDeepLink.Onboarding(tab)
            }
            host == "offboarding" || host == "exit" || host == "resignation" -> {
                ParsedDeepLink.Onboarding("exit_notices")
            }
            host == "clearance" -> {
                ParsedDeepLink.Onboarding("clearance")
            }
            host == "letters" || host == "letter" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                ParsedDeepLink.Documents(tab = "letters", id = id)
            }
            host == "signatures" || host == "signature" || host == "esign" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                ParsedDeepLink.Documents(tab = "signatures", id = id)
            }
            host == "vault" -> {
                val id = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                ParsedDeepLink.Documents(tab = "vault", id = id)
            }
            host == "training" || host == "courses" || host == "learning" || host == "certificates" || host == "upskill" -> {
                val defaultTab = when (host) {
                    "certificates" -> "certificates"
                    "learning" -> "my_learning"
                    else -> null
                }
                val first = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                val isKnownTab = first in setOf("catalog", "courses", "my_learning", "learning", "certificates", "skill_gaps", "needs", "gaps")
                val tab = if (isKnownTab) first else defaultTab
                val id = if (isKnownTab) remaining.getOrNull(1)?.takeIf { it.isNotBlank() } else first
                ParsedDeepLink.Training(tab = tab, id = id)
            }
            host == "timesheets" || host == "timesheet" || host == "billing" || host == "matrix" || host == "reconciliation" -> {
                val defaultTab = when (host) {
                    "matrix" -> "matrix"
                    "reconciliation" -> "reconciliation"
                    else -> null
                }
                val first = remaining.firstOrNull()?.takeIf { it.isNotBlank() }
                val isKnownTab = first in setOf("current", "matrix", "history", "reconciliation", "projects")
                val tab = if (isKnownTab) first else defaultTab
                val id = if (isKnownTab) remaining.getOrNull(1)?.takeIf { it.isNotBlank() } else first
                ParsedDeepLink.Timesheets(tab = tab, id = id)
            }
            else -> null
        }
    }

    fun parse(uri: Uri?): ParsedDeepLink? {
        if (uri == null) return null
        return parse(uri.toString())
    }
}
