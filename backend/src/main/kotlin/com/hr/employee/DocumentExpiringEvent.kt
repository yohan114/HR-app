package com.hr.employee

import java.time.LocalDate
import java.util.UUID

/**
 * Domain event published when an employee document (visa, contract, passport, licence)
 * enters its alert window or reaches expiration.
 *
 * Consumed by the notification pipeline to deliver proactive alerts to employees and managers.
 */
data class DocumentExpiringEvent(
    val tenantId: UUID,
    val employeeId: UUID,
    val documentId: UUID,
    val docType: String,
    val expiryDate: LocalDate,
    val daysRemaining: Long,
    val isExpired: Boolean,
)
