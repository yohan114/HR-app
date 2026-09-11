package com.hr.employee.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface EmployeeDocumentRepository : JpaRepository<EmployeeDocument, UUID> {
    fun findAllByTenantIdAndEmployeeId(
        tenantId: UUID,
        employeeId: UUID,
    ): List<EmployeeDocument>

    /**
     * Active documents with an expiry date, filtered by tenant.
     * Serves the per-tenant sweeper sweep and employee document review.
     */
    @Query(
        """
        SELECT d FROM EmployeeDocument d
        WHERE d.tenantId = :tenantId
          AND d.expiryDate IS NOT NULL
          AND d.status <> com.hr.employee.internal.DocumentStatus.REPLACED
        ORDER BY d.expiryDate ASC
        """,
    )
    fun findTrackableDocuments(
        @Param("tenantId") tenantId: UUID,
    ): List<EmployeeDocument>

    /**
     * Documents approaching expiry or expired for a specific employee within [windowDays].
     */
    @Query(
        """
        SELECT d FROM EmployeeDocument d
        WHERE d.tenantId = :tenantId
          AND d.employeeId = :employeeId
          AND d.expiryDate IS NOT NULL
          AND d.expiryDate <= :cutoffDate
          AND d.status <> com.hr.employee.internal.DocumentStatus.REPLACED
        ORDER BY d.expiryDate ASC
        """,
    )
    fun findExpiringForEmployee(
        @Param("tenantId") tenantId: UUID,
        @Param("employeeId") employeeId: UUID,
        @Param("cutoffDate") cutoffDate: LocalDate,
    ): List<EmployeeDocument>
}
