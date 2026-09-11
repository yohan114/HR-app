package com.hr.employee.internal

import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

/**
 * An identity, travel or compliance document belonging to an employee (e.g. passport, visa, work permit).
 *
 * Mapped to `employee_document` table created in `V6__employee.sql`.
 * Expiry tracking drives the [DocumentExpirySweeper] job using [alertDaysBefore].
 */
@Entity
@Table(name = "employee_document")
class EmployeeDocument(
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 32)
    var docType: DocumentType,

    @Column(name = "doc_number_enc")
    var docNumberEnc: String? = null,

    @Column(name = "issue_date")
    var issueDate: LocalDate? = null,

    @Column(name = "expiry_date")
    var expiryDate: LocalDate? = null,

    @Column(name = "issuing_country", length = 2)
    var issuingCountry: String? = null,

    @Column(name = "attachment_key", length = 512)
    var attachmentKey: String? = null,

    @Column(name = "alert_days_before", nullable = false)
    var alertDaysBefore: Short = 30,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: DocumentStatus = DocumentStatus.VALID,
) : TenantScopedEntity()

enum class DocumentType {
    PASSPORT,
    VISA,
    WORK_PERMIT,
    LABOUR_CARD,
    DRIVING_LICENCE,
    NATIONAL_ID,
    OTHER,
}

enum class DocumentStatus {
    VALID,
    EXPIRING,
    EXPIRED,
    REPLACED,
}
