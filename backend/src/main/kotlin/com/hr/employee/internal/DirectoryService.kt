package com.hr.employee.internal

import com.hr.shared.api.CursorRequest
import com.hr.shared.api.CursorPage
import com.hr.shared.api.Cursors
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Employee directory search.
 *
 * The first genuinely user-facing feature: "find a colleague" is the most-used
 * screen in an HR app after clocking in, and it has to be instant. On the
 * client it reads from the local store; this is what populates that store and
 * serves the first sync.
 *
 * ## What the directory does NOT return
 *
 * Salary, bank details, national identity numbers, date of birth, home address.
 * A directory is a "how do I contact this person" tool, and the fields it
 * carries are the ones a colleague may see. Anything requiring
 * `employee.view` and a data-scope check goes through the profile endpoint,
 * which applies field-level permissions.
 *
 * Keeping that separation at the *query* level rather than filtering afterwards
 * matters: a serialisation filter that someone forgets to apply leaks; a column
 * that was never selected cannot.
 */
@Service
class DirectoryService(
    private val jdbc: JdbcTemplate,
) {
    private val rowMapper =
        RowMapper { rs, _ ->
            DirectoryEntry(
                id = rs.getObject("id", UUID::class.java),
                employeeCode = rs.getString("employee_code"),
                displayName = rs.getString("display_name"),
                preferredName = rs.getString("preferred_name"),
                designation = rs.getString("designation_name"),
                department = rs.getString("department_name"),
                location = rs.getString("location_name"),
                workEmail = rs.getString("work_email"),
                mobile = rs.getString("mobile"),
                workPhone = rs.getString("work_phone"),
                photoKey = rs.getString("photo_key"),
                supervisorId = rs.getObject("supervisor_id", UUID::class.java),
            )
        }

    /**
     * Full-text search over names, employee code and work email.
     *
     * Results are weighted so a name match outranks a code match, which
     * outranks an email match — see the `employee_search_vector` trigger. A
     * colleague searching "Silva" wants the person called Silva, not everyone
     * whose email happens to contain it.
     */
    @Transactional(readOnly = true)
    fun search(
        query: String?,
        departmentId: UUID? = null,
        locationId: UUID? = null,
        page: CursorRequest = CursorRequest(),
    ): CursorPage<DirectoryEntry> {
        val conditions = mutableListOf<String>()
        val parameters = mutableListOf<Any>()

        // Exited and not-yet-joined employees are absent from the directory.
        // A leaver's contact details lingering in a colleague-facing search is
        // both confusing and, in some markets, a data-retention problem.
        conditions += "e.status NOT IN ('EXITED', 'PENDING_JOIN')"

        val trimmedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedQuery != null) {
            // `websearch_to_tsquery` tolerates whatever a person types —
            // unbalanced quotes, stray operators — where `to_tsquery` throws a
            // syntax error. A search box must never return a 500 because
            // someone typed an apostrophe.
            conditions += "e.search_vector @@ websearch_to_tsquery('simple', ?)"
            parameters += trimmedQuery
        }

        departmentId?.let {
            conditions += "e.department_id = ?"
            parameters += it
        }
        locationId?.let {
            conditions += "e.location_id = ?"
            parameters += it
        }

        page.cursor?.let { cursor ->
            val decoded = Cursors.decode(cursor)
            // Keyset pagination on (display_name, id). The id breaks ties, so
            // two people with the same name cannot cause a row to be skipped or
            // repeated across pages.
            conditions += "(e.display_name, e.id) > (?, ?)"
            parameters += decoded["displayName"] as? String ?: ""
            parameters += UUID.fromString(decoded["id"] as? String ?: UUID(0, 0).toString())
        }

        // Fetch one extra row to determine whether another page exists, rather
        // than issuing a second COUNT query over the same predicate.
        val limit = page.limit + 1

        val sql =
            """
            SELECT e.id, e.employee_code, e.display_name, e.preferred_name,
                   e.work_email, e.mobile, e.work_phone, e.photo_key, e.supervisor_id,
                   d.name  AS designation_name,
                   dept.name AS department_name,
                   l.name  AS location_name
            FROM employee e
            LEFT JOIN designation d  ON d.id = e.designation_id
            LEFT JOIN department dept ON dept.id = e.department_id
            LEFT JOIN location l     ON l.id = e.location_id
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY e.display_name, e.id
            LIMIT $limit
            """.trimIndent()

        val rows = jdbc.query(sql, rowMapper, *parameters.toTypedArray())

        val hasMore = rows.size > page.limit
        val items = if (hasMore) rows.take(page.limit) else rows
        val nextCursor =
            items.lastOrNull()
                ?.takeIf { hasMore }
                ?.let { Cursors.encode(mapOf("displayName" to it.displayName, "id" to it.id.toString())) }

        return CursorPage(items = items, nextCursor = nextCursor)
    }

    /** Direct reports, for the team view and the org chart's expand action. */
    @Transactional(readOnly = true)
    fun directReports(managerId: UUID): List<DirectoryEntry> =
        jdbc.query(
            """
            SELECT e.id, e.employee_code, e.display_name, e.preferred_name,
                   e.work_email, e.mobile, e.work_phone, e.photo_key, e.supervisor_id,
                   d.name AS designation_name,
                   dept.name AS department_name,
                   l.name AS location_name
            FROM employee e
            LEFT JOIN designation d   ON d.id = e.designation_id
            LEFT JOIN department dept ON dept.id = e.department_id
            LEFT JOIN location l      ON l.id = e.location_id
            WHERE e.supervisor_id = ?
              AND e.status NOT IN ('EXITED', 'PENDING_JOIN')
            ORDER BY e.display_name
            """.trimIndent(),
            rowMapper,
            managerId,
        )
}

/**
 * A directory result.
 *
 * Deliberately narrow. Every field here is one a colleague may see; anything
 * more sensitive requires the profile endpoint and a permission check.
 */
data class DirectoryEntry(
    val id: UUID,
    val employeeCode: String,
    val displayName: String,
    val preferredName: String?,
    val designation: String?,
    val department: String?,
    val location: String?,
    val workEmail: String?,
    val mobile: String?,
    val workPhone: String?,
    val photoKey: String?,
    val supervisorId: UUID?,
)
