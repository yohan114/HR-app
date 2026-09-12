package com.hr.attendance

import java.time.LocalDate
import java.util.UUID

data class AttendanceAnomalyEvent(
    val tenantId: UUID,
    val employeeId: UUID,
    val anomalyType: String,
    val workDate: LocalDate,
    val message: String,
)
