package com.hr.employee

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * Basic employee details needed by absence, payroll, statutory, and cross-domain modules.
 */
data class EmployeeLeaveProfile(
    val id: UUID,
    val tenantId: UUID,
    val employeeCode: String,
    val displayName: String,
    val firstName: String = "",
    val lastName: String = "",
    val joinDate: LocalDate,
    val confirmationDate: LocalDate? = null,
    val resignDate: LocalDate? = null,
    val status: String,
    val departmentId: UUID? = null,
    val designationId: UUID? = null,
    val salaryGradeId: UUID? = null,
    val locationId: UUID? = null,
    val supervisorId: UUID? = null,
)

/**
 * Public facade to look up employee tenure, identity, and lifecycle details across module boundaries
 * without violating Spring Modulith encapsulation.
 */
@Service
class EmployeeLookupService(
    private val jdbc: JdbcTemplate,
) {
    private val selectColumns =
        """
        id, tenant_id, employee_code, display_name, first_name, last_name,
        join_date, confirmation_date, resign_date, status,
        department_id, designation_id, salary_grade_id, location_id, supervisor_id
        """.trimIndent()

    @Transactional(readOnly = true)
    fun findById(employeeId: UUID): EmployeeLeaveProfile? {
        val sql = "SELECT $selectColumns FROM employee WHERE id = ?"
        return jdbc.query(sql, { rs, _ ->
            EmployeeLeaveProfile(
                id = rs.getObject("id", UUID::class.java),
                tenantId = rs.getObject("tenant_id", UUID::class.java),
                employeeCode = rs.getString("employee_code"),
                displayName = rs.getString("display_name"),
                firstName = rs.getString("first_name") ?: "",
                lastName = rs.getString("last_name") ?: "",
                joinDate = rs.getDate("join_date").toLocalDate(),
                confirmationDate = rs.getDate("confirmation_date")?.toLocalDate(),
                resignDate = rs.getDate("resign_date")?.toLocalDate(),
                status = rs.getString("status"),
                departmentId = rs.getObject("department_id", UUID::class.java),
                designationId = rs.getObject("designation_id", UUID::class.java),
                salaryGradeId = rs.getObject("salary_grade_id", UUID::class.java),
                locationId = rs.getObject("location_id", UUID::class.java),
                supervisorId = rs.getObject("supervisor_id", UUID::class.java),
            )
        }, employeeId).firstOrNull()
    }

    @Transactional(readOnly = true)
    fun findAll(): List<EmployeeLeaveProfile> {
        val sql = "SELECT $selectColumns FROM employee"
        return jdbc.query(sql) { rs, _ ->
            EmployeeLeaveProfile(
                id = rs.getObject("id", UUID::class.java),
                tenantId = rs.getObject("tenant_id", UUID::class.java),
                employeeCode = rs.getString("employee_code"),
                displayName = rs.getString("display_name"),
                firstName = rs.getString("first_name") ?: "",
                lastName = rs.getString("last_name") ?: "",
                joinDate = rs.getDate("join_date").toLocalDate(),
                confirmationDate = rs.getDate("confirmation_date")?.toLocalDate(),
                resignDate = rs.getDate("resign_date")?.toLocalDate(),
                status = rs.getString("status"),
                departmentId = rs.getObject("department_id", UUID::class.java),
                designationId = rs.getObject("designation_id", UUID::class.java),
                salaryGradeId = rs.getObject("salary_grade_id", UUID::class.java),
                locationId = rs.getObject("location_id", UUID::class.java),
                supervisorId = rs.getObject("supervisor_id", UUID::class.java),
            )
        }
    }

    @Transactional(readOnly = true)
    fun findActiveByTenant(tenantId: UUID): List<EmployeeLeaveProfile> {
        val sql =
            """
            SELECT $selectColumns
            FROM employee
            WHERE tenant_id = ? AND status NOT IN ('EXITED', 'PENDING_JOIN')
            """.trimIndent()
        return jdbc.query(sql, { rs, _ ->
            EmployeeLeaveProfile(
                id = rs.getObject("id", UUID::class.java),
                tenantId = rs.getObject("tenant_id", UUID::class.java),
                employeeCode = rs.getString("employee_code"),
                displayName = rs.getString("display_name"),
                firstName = rs.getString("first_name") ?: "",
                lastName = rs.getString("last_name") ?: "",
                joinDate = rs.getDate("join_date").toLocalDate(),
                confirmationDate = rs.getDate("confirmation_date")?.toLocalDate(),
                resignDate = rs.getDate("resign_date")?.toLocalDate(),
                status = rs.getString("status"),
                departmentId = rs.getObject("department_id", UUID::class.java),
                designationId = rs.getObject("designation_id", UUID::class.java),
                salaryGradeId = rs.getObject("salary_grade_id", UUID::class.java),
                locationId = rs.getObject("location_id", UUID::class.java),
                supervisorId = rs.getObject("supervisor_id", UUID::class.java),
            )
        }, tenantId)
    }

    @Transactional(readOnly = true)
    fun findDisplayName(employeeId: UUID): String? {
        val sql = "SELECT display_name FROM employee WHERE id = ?"
        return jdbc.query(sql, { rs, _ -> rs.getString("display_name") }, employeeId).firstOrNull()
    }

    @Transactional
    fun applyCareerCascade(
        employeeId: UUID,
        departmentId: UUID?,
        designationId: UUID?,
        salaryGradeId: UUID?,
        locationId: UUID?,
        supervisorId: UUID?,
        confirmationDate: LocalDate?,
    ) {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        if (departmentId != null) { updates.add("department_id = ?"); params.add(departmentId) }
        if (designationId != null) { updates.add("designation_id = ?"); params.add(designationId) }
        if (salaryGradeId != null) { updates.add("salary_grade_id = ?"); params.add(salaryGradeId) }
        if (locationId != null) { updates.add("location_id = ?"); params.add(locationId) }
        if (supervisorId != null) { updates.add("supervisor_id = ?"); params.add(supervisorId) }
        if (confirmationDate != null) { updates.add("confirmation_date = ?"); params.add(confirmationDate) }
        if (updates.isNotEmpty()) {
            params.add(employeeId)
            jdbc.update("UPDATE employee SET ${updates.joinToString(", ")} WHERE id = ?", *params.toTypedArray())
        }
    }

    @Transactional
    fun revertCareerCascade(
        employeeId: UUID,
        prevDepartmentId: UUID?,
        prevDesignationId: UUID?,
        prevSalaryGradeId: UUID?,
        prevLocationId: UUID?,
        prevSupervisorId: UUID?,
        clearConfirmationDate: Boolean,
    ) {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any?>()
        if (prevDepartmentId != null) { updates.add("department_id = ?"); params.add(prevDepartmentId) }
        if (prevDesignationId != null) { updates.add("designation_id = ?"); params.add(prevDesignationId) }
        if (prevSalaryGradeId != null) { updates.add("salary_grade_id = ?"); params.add(prevSalaryGradeId) }
        if (prevLocationId != null) { updates.add("location_id = ?"); params.add(prevLocationId) }
        if (prevSupervisorId != null) { updates.add("supervisor_id = ?"); params.add(prevSupervisorId) }
        if (clearConfirmationDate) { updates.add("confirmation_date = NULL") }
        if (updates.isNotEmpty()) {
            params.add(employeeId)
            jdbc.update("UPDATE employee SET ${updates.joinToString(", ")} WHERE id = ?", *params.toTypedArray())
        }
    }
}
