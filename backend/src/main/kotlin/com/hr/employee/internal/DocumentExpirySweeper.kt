package com.hr.employee.internal

import com.hr.employee.DocumentExpiringEvent
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Sweeper detecting expiring employee documents (visas, passports, contracts, certifications)
 * and transitioning document lifecycle statuses, feeding into the notification event bus.
 *
 * Implements P1-BE-40.
 */
@Component
class DocumentExpirySweeper(
    private val documentRepository: EmployeeDocumentRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Daily scheduled run at 01:00 AM server time by default.
     */
    @Scheduled(cron = "\${hr.jobs.document-expiry-sweeper.cron:0 0 1 * * ?}")
    fun runDailySweep() {
        val today = LocalDate.now()
        log.info("Starting scheduled document expiry sweep for date: {}", today)
        val result = sweepAll(today)
        log.info(
            "Document expiry sweep finished. Scanned: {}, Expiring: {}, Expired: {}, Events dispatched: {}",
            result.scannedCount,
            result.expiringCount,
            result.expiredCount,
            result.eventsPublished,
        )
    }

    /**
     * Sweeps all active tenants across the platform.
     */
    fun sweepAll(asOfDate: LocalDate = LocalDate.now()): DocumentSweepResult {
        // Query active tenants without tenant filter
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

        var totalScanned = 0
        var totalExpiring = 0
        var totalExpired = 0
        var totalEvents = 0

        for (target in activeTenants) {
            val handle = target.toHandle()
            val result =
                TenantContext.runAs(handle) {
                    sweepTenant(target.id, asOfDate)
                }
            totalScanned += result.scannedCount
            totalExpiring += result.expiringCount
            totalExpired += result.expiredCount
            totalEvents += result.eventsPublished
        }

        return DocumentSweepResult(
            scannedCount = totalScanned,
            expiringCount = totalExpiring,
            expiredCount = totalExpired,
            eventsPublished = totalEvents,
        )
    }

    /**
     * Sweeps documents within a single tenant.
     * Expects [tenantId] to match current tenant context.
     */
    @Transactional
    fun sweepTenant(
        tenantId: UUID,
        asOfDate: LocalDate = LocalDate.now(),
    ): DocumentSweepResult {
        val documents = documentRepository.findTrackableDocuments(tenantId)
        var expiringCount = 0
        var expiredCount = 0
        var eventsPublished = 0

        for (doc in documents) {
            val expiry = doc.expiryDate ?: continue
            val daysRemaining = ChronoUnit.DAYS.between(asOfDate, expiry)

            if (expiry.isBefore(asOfDate)) {
                // Has passed expiry date
                expiredCount++
                if (doc.status != DocumentStatus.EXPIRED) {
                    doc.status = DocumentStatus.EXPIRED
                    documentRepository.save(doc)
                }
                eventPublisher.publishEvent(
                    DocumentExpiringEvent(
                        tenantId = tenantId,
                        employeeId = doc.employeeId,
                        documentId = doc.id,
                        docType = doc.docType.name,
                        expiryDate = expiry,
                        daysRemaining = daysRemaining,
                        isExpired = true,
                    ),
                )
                eventsPublished++
            } else if (daysRemaining <= doc.alertDaysBefore.toLong()) {
                // Approaching expiry within alert window
                expiringCount++
                if (doc.status != DocumentStatus.EXPIRING) {
                    doc.status = DocumentStatus.EXPIRING
                    documentRepository.save(doc)
                }
                eventPublisher.publishEvent(
                    DocumentExpiringEvent(
                        tenantId = tenantId,
                        employeeId = doc.employeeId,
                        documentId = doc.id,
                        docType = doc.docType.name,
                        expiryDate = expiry,
                        daysRemaining = daysRemaining,
                        isExpired = false,
                    ),
                )
                eventsPublished++
            }
        }

        return DocumentSweepResult(
            scannedCount = documents.size,
            expiringCount = expiringCount,
            expiredCount = expiredCount,
            eventsPublished = eventsPublished,
        )
    }
}

data class DocumentSweepResult(
    val scannedCount: Int,
    val expiringCount: Int,
    val expiredCount: Int,
    val eventsPublished: Int,
)

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
            dataRegion = "default",
            defaultCurrency = currency,
            timezone = timezone,
            locale = locale,
            isolationTier = com.hr.tenancy.IsolationTier.SHARED,
            status = com.hr.tenancy.TenantStatus.ACTIVE,
        )
}
