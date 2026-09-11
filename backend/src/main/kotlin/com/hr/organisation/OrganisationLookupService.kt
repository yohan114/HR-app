package com.hr.organisation

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Public facade to look up organization entities (departments, designations, salary grades, locations)
 * across module boundaries without violating Spring Modulith encapsulation.
 */
@Service
class OrganisationLookupService(
    private val jdbc: JdbcTemplate,
) {
    @Transactional(readOnly = true)
    fun findDepartmentName(id: UUID): String? {
        val sql = "SELECT name FROM department WHERE id = ?"
        return jdbc.query(sql, { rs, _ -> rs.getString("name") }, id).firstOrNull()
    }

    @Transactional(readOnly = true)
    fun findDesignationName(id: UUID): String? {
        val sql = "SELECT name FROM designation WHERE id = ?"
        return jdbc.query(sql, { rs, _ -> rs.getString("name") }, id).firstOrNull()
    }

    @Transactional(readOnly = true)
    fun findSalaryGradeCode(id: UUID): String? {
        val sql = "SELECT code FROM salary_grade WHERE id = ?"
        return jdbc.query(sql, { rs, _ -> rs.getString("code") }, id).firstOrNull()
    }

    @Transactional(readOnly = true)
    fun findLocationName(id: UUID): String? {
        val sql = "SELECT name FROM location WHERE id = ?"
        return jdbc.query(sql, { rs, _ -> rs.getString("name") }, id).firstOrNull()
    }

    @Transactional(readOnly = true)
    fun findAllDepartmentNames(): Map<UUID, String> {
        val sql = "SELECT id, name FROM department"
        return jdbc.query(sql) { rs, _ ->
            rs.getObject("id", UUID::class.java) to rs.getString("name")
        }.toMap()
    }
}
