package com.hr.payroll

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Public facade to look up payroll information (e.g. latest basic salary) across module boundaries
 * without violating Spring Modulith encapsulation.
 */
@Service
class PayrollLookupService(
    private val jdbc: JdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun findLatestBasicSalary(employeeId: UUID): BigDecimal? {
        val sql = "SELECT basic_salary FROM payroll_result WHERE employee_id = ? ORDER BY created_at DESC LIMIT 1"
        return jdbc.query(sql, { rs, _ -> rs.getBigDecimal("basic_salary") }, employeeId).firstOrNull()
    }
}
