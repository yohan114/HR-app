package com.hr.employee.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Employee persistence.
 *
 * No method filters by `tenant_id`. That is not an oversight — row-level
 * security appends the predicate to every query and `TenantAwareDataSource`
 * binds the tenant on connection checkout, so a query that "forgets" it returns
 * zero rows rather than another customer's data. See ADR 0002.
 */
@Repository
interface EmployeeRepository : JpaRepository<Employee, UUID> {
    fun findByEmployeeCode(employeeCode: String): Employee?

    @Query("SELECT e FROM Employee e WHERE lower(e.workEmail) = lower(:email)")
    fun findByWorkEmail(
        @Param("email") email: String,
    ): Employee?

    /** Direct reports only. For the whole subtree use [EmployeeHierarchyRepository]. */
    fun findBySupervisorIdOrderByDisplayName(supervisorId: UUID): List<Employee>

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.status NOT IN ('EXITED', 'PENDING_JOIN')")
    fun countEmployed(): Long
}

/**
 * Hierarchy queries against the materialised `employee_hierarchy` ltree.
 *
 * "Everyone under this manager" is asked on nearly every screen a manager opens
 * — team attendance, team leave, approvals, the org chart. Walking
 * `supervisor_id` recursively per request against a 10,000-employee tenant is
 * the kind of query that looks fine in development and falls over in
 * production. An ltree prefix match is a single indexed scan.
 */
@Repository
interface EmployeeHierarchyRepository : JpaRepository<Employee, UUID> {
    /**
     * Everyone beneath [managerId], at any depth.
     *
     * `<@` is ltree containment: rows whose path is a descendant of the
     * manager's. The manager themselves is excluded, which is nearly always
     * what a caller means by "my team".
     */
    @Query(
        value = """
        SELECT e.* FROM employee e
        JOIN employee_hierarchy h ON h.employee_id = e.id
        WHERE h.path <@ (SELECT path FROM employee_hierarchy WHERE employee_id = :managerId)
          AND e.id <> :managerId
          AND (:includeExited OR e.status NOT IN ('EXITED', 'PENDING_JOIN'))
        ORDER BY h.depth, e.display_name
        """,
        nativeQuery = true,
    )
    fun findSubtree(
        @Param("managerId") managerId: UUID,
        @Param("includeExited") includeExited: Boolean = false,
    ): List<Employee>

    /**
     * The chain of managers above [employeeId], nearest first.
     *
     * Used by approval routing (`SUPERVISOR_LEVEL_N` resolves against this) and
     * by the "my path to the CEO" view in the org chart.
     */
    @Query(
        value = """
        SELECT e.* FROM employee e
        JOIN employee_hierarchy h ON h.employee_id = e.id
        WHERE h.path @> (SELECT path FROM employee_hierarchy WHERE employee_id = :employeeId)
          AND e.id <> :employeeId
        ORDER BY h.depth DESC
        """,
        nativeQuery = true,
    )
    fun findAncestors(
        @Param("employeeId") employeeId: UUID,
    ): List<Employee>

    /**
     * Whether [managerId] is anywhere above [employeeId].
     *
     * The data-scope check behind "my team" permissions. Answered by an index
     * lookup rather than by materialising the subtree and searching it.
     */
    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1
            FROM employee_hierarchy child, employee_hierarchy manager
            WHERE child.employee_id = :employeeId
              AND manager.employee_id = :managerId
              AND child.path <@ manager.path
              AND child.employee_id <> manager.employee_id
        )
        """,
        nativeQuery = true,
    )
    fun isManagerOf(
        @Param("managerId") managerId: UUID,
        @Param("employeeId") employeeId: UUID,
    ): Boolean

    /** Employees with no supervisor. Normally one; more than one is worth surfacing to HR. */
    @Query(
        value = """
        SELECT e.* FROM employee e
        JOIN employee_hierarchy h ON h.employee_id = e.id
        WHERE h.depth = 0
          AND e.status NOT IN ('EXITED', 'PENDING_JOIN')
        ORDER BY e.display_name
        """,
        nativeQuery = true,
    )
    fun findRoots(): List<Employee>

    /**
     * Rebuilds a subtree's paths.
     *
     * Normally maintained by trigger. Exposed for the bulk-import path, where
     * employees arrive in arbitrary order and a supervisor may be created after
     * their report.
     */
    @Query(value = "SELECT rebuild_employee_hierarchy(:employeeId)", nativeQuery = true)
    fun rebuildHierarchy(
        @Param("employeeId") employeeId: UUID,
    )
}
