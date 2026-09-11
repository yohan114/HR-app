package com.hr.timesheet.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
interface TimesheetClientRepository : JpaRepository<TimesheetClientEntity, UUID> {
    fun findAllByOrderByClientNameAsc(): List<TimesheetClientEntity>
    fun findByClientCode(clientCode: String): TimesheetClientEntity?
}

@Repository
interface TimesheetProjectRepository : JpaRepository<TimesheetProjectEntity, UUID> {
    fun findAllByOrderByProjectNameAsc(): List<TimesheetProjectEntity>
    fun findAllByClientIdOrderByProjectNameAsc(clientId: UUID): List<TimesheetProjectEntity>
    fun findByProjectCode(projectCode: String): TimesheetProjectEntity?
}

@Repository
interface TimesheetActivityRepository : JpaRepository<TimesheetActivityEntity, UUID> {
    fun findAllByOrderByActivityNameAsc(): List<TimesheetActivityEntity>
    fun findAllByProjectIdOrderByActivityNameAsc(projectId: UUID): List<TimesheetActivityEntity>
    fun findByProjectIdAndActivityCode(projectId: UUID, activityCode: String): TimesheetActivityEntity?
}

@Repository
interface TimesheetRepository : JpaRepository<TimesheetEntity, UUID> {
    fun findAllByOrderByWeekStartDateDesc(): List<TimesheetEntity>
    fun findAllByEmployeeIdOrderByWeekStartDateDesc(employeeId: UUID): List<TimesheetEntity>
    fun findByEmployeeIdAndWeekStartDate(employeeId: UUID, weekStartDate: LocalDate): TimesheetEntity?

    @Query("""
        SELECT t FROM TimesheetEntity t
        WHERE (:employeeId IS NULL OR t.employeeId = :employeeId)
          AND (:status IS NULL OR t.status = :status)
        ORDER BY t.weekStartDate DESC
    """)
    fun filterTimesheets(
        @Param("employeeId") employeeId: UUID?,
        @Param("status") status: String?,
    ): List<TimesheetEntity>
}

@Repository
interface TimesheetEntryRepository : JpaRepository<TimesheetEntryEntity, UUID> {
    fun findAllByTimesheetIdOrderByEntryDateAsc(timesheetId: UUID): List<TimesheetEntryEntity>
    fun deleteByTimesheetId(timesheetId: UUID)
}
