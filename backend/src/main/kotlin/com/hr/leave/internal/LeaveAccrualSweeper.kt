package com.hr.leave.internal

import com.hr.leave.LeaveAccrualService
import com.hr.leave.LeaveYearStatus
import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

data class AccrualSweepSummary(
    val tenantsProcessed: Int,
    val totalProcessed: Int,
    val totalCredited: Int,
    val totalSkipped: Int,
)

/**
 * Scheduled background job orchestrating periodic absence and leave accruals.
 *
 * Runs across active tenants, binds tenant context, and executes accrual calculations idempotently.
 * Implements P2-BE-25.
 */
@Component
class LeaveAccrualSweeper(
    private val leaveAccrualService: LeaveAccrualService,
    private val leaveYearRepository: LeaveYearRepository,
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Scheduled daily accrual job at 02:00 AM server time.
     */
    @Scheduled(cron = "\${hr.jobs.leave-accrual-sweeper.cron:0 0 2 * * ?}")
    fun runDailySweep() {
        val today = LocalDate.now()
        log.info("Starting scheduled leave accrual sweep for date: {}", today)
        val summary = sweepAll(today)
        log.info(
            "Leave accrual sweep completed. Tenants: {}, Total: {}, Credited: {}, Skipped: {}",
            summary.tenantsProcessed,
            summary.totalProcessed,
            summary.totalCredited,
            summary.totalSkipped,
        )
    }

    fun sweepAll(asOfDate: LocalDate = LocalDate.now()): AccrualSweepSummary {
        val activeTenants =
            TenantContext.runWithoutTenant {
                jdbc.query(
                    """
                    SELECT id, code, name, default_currency, timezone, locale
                    FROM tenant
                    WHERE status = 'ACTIVE'
                    """.trimIndent(),
                ) { rs, _ ->
                    TenantSweepTarget(
                        id = rs.getObject("id", UUID::class.java),
                        code = rs.getString("code"),
                        name = rs.getString("name"),
                        currency = rs.getString("default_currency") ?: "USD",
                        timezone = rs.getString("timezone") ?: "UTC",
                        locale = rs.getString("locale") ?: "en",
                    )
                }
            }

        var tenantsProcessed = 0
        var totalProcessed = 0
        var totalCredited = 0
        var totalSkipped = 0

        for (target in activeTenants) {
            val handle = target.toHandle()
            val (processed, credited, skipped) =
                TenantContext.runAs(handle) {
                    sweepTenant(target.id, asOfDate)
                }
            tenantsProcessed++
            totalProcessed += processed
            totalCredited += credited
            totalSkipped += skipped
        }

        return AccrualSweepSummary(
            tenantsProcessed = tenantsProcessed,
            totalProcessed = totalProcessed,
            totalCredited = totalCredited,
            totalSkipped = totalSkipped,
        )
    }

    private fun sweepTenant(tenantId: UUID, asOfDate: LocalDate): Triple<Int, Int, Int> {
        val activeYear = leaveYearRepository.findActiveYearAsOf(asOfDate)
            ?: leaveYearRepository.findByStatus(LeaveYearStatus.ACTIVE).firstOrNull()
            ?: return Triple(0, 0, 0)

        val results = leaveAccrualService.runAccrualBatch(tenantId, activeYear.id, asOfDate)
        val credited = results.count { !it.skipped && it.daysAccrued > java.math.BigDecimal.ZERO }
        val skipped = results.count { it.skipped }

        return Triple(results.size, credited, skipped)
    }

    private data class TenantSweepTarget(
        val id: UUID,
        val code: String,
        val name: String,
        val currency: String,
        val timezone: String,
        val locale: String,
    ) {
        fun toHandle() =
            TenantHandle(
                id = id,
                code = code,
                name = name,
                dataRegion = "ap-southeast-2",
                defaultCurrency = currency,
                timezone = timezone,
                locale = locale,
                isolationTier = IsolationTier.SHARED,
                status = TenantStatus.ACTIVE,
            )
    }
}
