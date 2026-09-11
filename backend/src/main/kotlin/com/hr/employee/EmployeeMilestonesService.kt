package com.hr.employee

import com.hr.tenancy.TenantContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Public facade providing milestone reminders and personal document expiry summaries
 * across the employee boundary.
 *
 * Implements milestone logic complying with docs/home-composite.md §5 and §6.
 */
@Service
class EmployeeMilestonesService(
    private val jdbc: JdbcTemplate,
) {
    /**
     * Finds upcoming birthdays and work anniversaries within [windowDays] from [asOfDate]
     * across active employees in the current tenant.
     */
    @Transactional(readOnly = true)
    fun findUpcomingMilestones(
        asOfDate: LocalDate = LocalDate.now(),
        windowDays: Long = 14,
    ): List<EmployeeMilestone> {
        val tenantId = TenantContext.currentId()
        val sql =
            """
            SELECT e.id, e.display_name, e.photo_key, e.date_of_birth, e.join_date, d.name AS designation_name
            FROM employee e
            LEFT JOIN designation d ON e.designation_id = d.id
            WHERE e.tenant_id = ?
              AND e.status NOT IN ('EXITED', 'PENDING_JOIN')
            """.trimIndent()

        val rows =
            jdbc.query(sql, { rs, _ ->
                EmployeeRawMilestoneData(
                    id = rs.getObject("id", UUID::class.java),
                    displayName = rs.getString("display_name"),
                    photoKey = rs.getString("photo_key"),
                    dateOfBirth = rs.getDate("date_of_birth")?.toLocalDate(),
                    joinDate = rs.getDate("join_date").toLocalDate(),
                    designation = rs.getString("designation_name"),
                )
            }, tenantId)

        val milestones = mutableListOf<EmployeeMilestone>()
        val endDate = asOfDate.plusDays(windowDays)

        for (emp in rows) {
            // Check Birthday
            emp.dateOfBirth?.let { dob ->
                val nextBday = nextOccurrence(dob, asOfDate)
                if (!nextBday.isBefore(asOfDate) && !nextBday.isAfter(endDate)) {
                    milestones.add(
                        EmployeeMilestone(
                            employeeId = emp.id,
                            displayName = emp.displayName,
                            designation = emp.designation,
                            photoKey = emp.photoKey,
                            type = MilestoneType.BIRTHDAY,
                            eventDate = nextBday,
                            yearsCount = null,
                            actionUri = "hrapp://employees/${emp.id}",
                        ),
                    )
                }
            }

            // Check Work Anniversary
            val nextAnniversary = nextOccurrence(emp.joinDate, asOfDate)
            if (!nextAnniversary.isBefore(asOfDate) && !nextAnniversary.isAfter(endDate)) {
                val years = nextAnniversary.year - emp.joinDate.year
                if (years > 0) {
                    milestones.add(
                        EmployeeMilestone(
                            employeeId = emp.id,
                            displayName = emp.displayName,
                            designation = emp.designation,
                            photoKey = emp.photoKey,
                            type = MilestoneType.WORK_ANNIVERSARY,
                            eventDate = nextAnniversary,
                            yearsCount = years,
                            actionUri = "hrapp://employees/${emp.id}",
                        ),
                    )
                }
            }
        }

        return milestones.sortedWith(compareBy({ it.eventDate }, { it.displayName }))
    }

    /**
     * Finds documents approaching expiry or expired for the given [employeeId].
     */
    @Transactional(readOnly = true)
    fun findExpiringDocumentsForEmployee(
        employeeId: UUID,
        asOfDate: LocalDate = LocalDate.now(),
        windowDays: Long = 30,
    ): List<EmployeeExpiringDocument> {
        val tenantId = TenantContext.currentId()
        val sql =
            """
            SELECT id, doc_type, expiry_date, status, alert_days_before
            FROM employee_document
            WHERE tenant_id = ?
              AND employee_id = ?
              AND expiry_date IS NOT NULL
              AND status <> 'REPLACED'
            ORDER BY expiry_date ASC
            """.trimIndent()

        val results = mutableListOf<EmployeeExpiringDocument>()
        jdbc.query(sql, { rs, _ ->
            val id = rs.getObject("id", UUID::class.java)
            val docType = rs.getString("doc_type")
            val expiryDate = rs.getDate("expiry_date").toLocalDate()
            val status = rs.getString("status")
            val alertDays = rs.getShort("alert_days_before").toLong()

            val daysRemaining = ChronoUnit.DAYS.between(asOfDate, expiryDate)
            val isDue = expiryDate.isBefore(asOfDate) || daysRemaining <= maxOf(windowDays, alertDays)

            if (isDue) {
                results.add(
                    EmployeeExpiringDocument(
                        id = id,
                        docType = docType,
                        expiryDate = expiryDate,
                        daysRemaining = daysRemaining,
                        status = status,
                        actionUri = "hrapp://documents/$id",
                    ),
                )
            }
        }, tenantId, employeeId)

        return results
    }

    private fun nextOccurrence(date: LocalDate, asOf: LocalDate): LocalDate {
        val candidate = safeDate(asOf.year, date.monthValue, date.dayOfMonth)
        return if (candidate.isBefore(asOf)) {
            safeDate(asOf.year + 1, date.monthValue, date.dayOfMonth)
        } else {
            candidate
        }
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
        val maxDay = java.time.YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, minOf(day, maxDay))
    }
}

data class EmployeeMilestone(
    val employeeId: UUID,
    val displayName: String,
    val designation: String?,
    val photoKey: String?,
    val type: MilestoneType,
    val eventDate: LocalDate,
    val yearsCount: Int?,
    val actionUri: String? = null,
)

enum class MilestoneType {
    BIRTHDAY,
    WORK_ANNIVERSARY,
}

data class EmployeeExpiringDocument(
    val id: UUID,
    val docType: String,
    val expiryDate: LocalDate,
    val daysRemaining: Long,
    val status: String,
    val actionUri: String? = null,
)

private data class EmployeeRawMilestoneData(
    val id: UUID,
    val displayName: String,
    val photoKey: String?,
    val dateOfBirth: LocalDate?,
    val joinDate: LocalDate,
    val designation: String?,
)
