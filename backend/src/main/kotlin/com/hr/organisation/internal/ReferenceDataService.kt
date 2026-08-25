package com.hr.organisation.internal

import com.hr.organisation.ReferenceItem
import com.hr.organisation.ReferenceTable
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Reads and writes the reference taxonomies.
 *
 * Uses JDBC with an interpolated table name rather than JPA, because the table
 * is chosen at runtime. That is only safe because the name comes from
 * [ReferenceTable] — an enum — and never from user input. The API layer parses
 * a request string into the enum and 404s if it does not match; by the time a
 * name reaches this class it is one of twenty-eight compile-time constants.
 *
 * Row-level security still applies: these are tenant-scoped tables and the
 * connection carries the tenant binding, so a forgotten predicate returns
 * nothing rather than another customer's data.
 */
@Service
class ReferenceDataService(
    private val jdbc: JdbcTemplate,
) {
    private val rowMapper =
        RowMapper { rs, _ ->
            ReferenceItem(
                id = rs.getObject("id", UUID::class.java),
                code = rs.getString("code"),
                name = rs.getString("name"),
                description = rs.getString("description"),
                sequence = rs.getInt("sequence"),
                active = rs.getBoolean("active"),
            )
        }

    @Transactional(readOnly = true)
    fun list(
        table: ReferenceTable,
        includeInactive: Boolean = false,
    ): List<ReferenceItem> {
        val activeClause = if (includeInactive) "" else "WHERE active = true"
        return jdbc.query(
            """
            SELECT id, code, name, description, sequence, active
            FROM ${table.tableName}
            $activeClause
            ORDER BY sequence, name
            """.trimIndent(),
            rowMapper,
        )
    }

    @Transactional(readOnly = true)
    fun findByCode(
        table: ReferenceTable,
        code: String,
    ): ReferenceItem? =
        jdbc.query(
            "SELECT id, code, name, description, sequence, active FROM ${table.tableName} WHERE code = ?",
            rowMapper,
            code,
        ).firstOrNull()

    @Transactional(readOnly = true)
    fun findById(
        table: ReferenceTable,
        id: UUID,
    ): ReferenceItem? =
        jdbc.query(
            "SELECT id, code, name, description, sequence, active FROM ${table.tableName} WHERE id = ?",
            rowMapper,
            id,
        ).firstOrNull()

    /**
     * Loads several taxonomies in one call.
     *
     * The mobile clients sync reference data as a unit — a form needs gender,
     * marital status, nationality and blood group together, and issuing four
     * round trips to render one screen is exactly the chattiness that makes an
     * app feel slow.
     */
    @Transactional(readOnly = true)
    fun listAll(tables: Collection<ReferenceTable>): Map<String, List<ReferenceItem>> =
        tables.associate { it.apiName to list(it) }
}
