package com.hr.disciplinary.internal

import com.hr.disciplinary.GrievanceStatus
import com.hr.disciplinary.IncidentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface GrievanceGroundGroupRepository : JpaRepository<GrievanceGroundGroup, UUID> {
    fun findByTenantIdOrderByCodeAsc(tenantId: UUID): List<GrievanceGroundGroup>
    fun findByTenantIdAndCode(tenantId: UUID, code: String): GrievanceGroundGroup?
}

@Repository
interface GrievanceGroundRepository : JpaRepository<GrievanceGround, UUID> {
    fun findByTenantIdOrderByCodeAsc(tenantId: UUID): List<GrievanceGround>
    fun findByTenantIdAndGroupId(tenantId: UUID, groupId: UUID): List<GrievanceGround>
    fun findByTenantIdAndCode(tenantId: UUID, code: String): GrievanceGround?
}

@Repository
interface GrievanceChannelRepository : JpaRepository<GrievanceChannel, UUID> {
    fun findByTenantIdOrderByCodeAsc(tenantId: UUID): List<GrievanceChannel>
    fun findByTenantIdAndCode(tenantId: UUID, code: String): GrievanceChannel?
}

@Repository
interface GrievanceRepository : JpaRepository<Grievance, UUID> {
    fun findByTenantIdAndRaisedByEmployeeIdOrderByRaisedAtDesc(tenantId: UUID, raisedByEmployeeId: UUID): List<Grievance>
    fun findByTenantIdOrderByRaisedAtDesc(tenantId: UUID): List<Grievance>
    fun findByTenantIdAndId(tenantId: UUID, id: UUID): Grievance?
    fun countByTenantIdAndStatusIn(tenantId: UUID, statuses: Collection<GrievanceStatus>): Long
}

@Repository
interface GrievanceAppealRepository : JpaRepository<GrievanceAppeal, UUID> {
    fun findByTenantIdAndGrievanceId(tenantId: UUID, grievanceId: UUID): GrievanceAppeal?
}

@Repository
interface IncidentTypeRepository : JpaRepository<IncidentType, UUID> {
    fun findByTenantIdOrderByCodeAsc(tenantId: UUID): List<IncidentType>
    fun findByTenantIdAndCode(tenantId: UUID, code: String): IncidentType?
}

@Repository
interface IncidentSubtypeRepository : JpaRepository<IncidentSubtype, UUID> {
    fun findByTenantIdAndIncidentTypeIdOrderByCodeAsc(tenantId: UUID, incidentTypeId: UUID): List<IncidentSubtype>
    fun findByTenantIdOrderByCodeAsc(tenantId: UUID): List<IncidentSubtype>
}

@Repository
interface DisciplinaryIncidentRepository : JpaRepository<DisciplinaryIncident, UUID> {
    fun findByTenantIdAndEmployeeIdOrderByIncidentDateDesc(tenantId: UUID, employeeId: UUID): List<DisciplinaryIncident>
    fun findByTenantIdOrderByIncidentDateDesc(tenantId: UUID): List<DisciplinaryIncident>
    fun findByTenantIdAndId(tenantId: UUID, id: UUID): DisciplinaryIncident?
    fun countByTenantIdAndStatusNot(tenantId: UUID, status: IncidentStatus): Long
}

@Repository
interface CorrectiveActionRepository : JpaRepository<CorrectiveAction, UUID> {
    fun findByTenantIdAndIncidentIdOrderByIssuedAtAsc(tenantId: UUID, incidentId: UUID): List<CorrectiveAction>
    fun findByTenantIdAndId(tenantId: UUID, id: UUID): CorrectiveAction?
}

@Repository
interface IncidentJournalRepository : JpaRepository<IncidentJournal, UUID> {
    fun findByTenantIdAndIncidentIdOrderByEnteredAtAsc(tenantId: UUID, incidentId: UUID): List<IncidentJournal>
}

@Repository
interface DisciplinaryAppealRepository : JpaRepository<DisciplinaryAppeal, UUID> {
    fun findByTenantIdAndIncidentId(tenantId: UUID, incidentId: UUID): List<DisciplinaryAppeal>
    fun findByTenantIdAndCorrectiveActionId(tenantId: UUID, correctiveActionId: UUID): DisciplinaryAppeal?
}
