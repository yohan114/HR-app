package com.hr.disciplinary.internal

import com.hr.disciplinary.AppealStatus
import com.hr.disciplinary.CorrectiveActionStatus
import com.hr.disciplinary.CorrectiveActionType
import com.hr.disciplinary.DisciplinaryAppealStatus
import com.hr.disciplinary.GrievanceSeverity
import com.hr.disciplinary.GrievanceStatus
import com.hr.disciplinary.IncidentSeverity
import com.hr.disciplinary.IncidentStatus
import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "grievance_ground_group")
class GrievanceGroundGroup(
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "description")
    var description: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "grievance_ground")
class GrievanceGround(
    @Column(name = "group_id", nullable = false)
    var groupId: UUID,
    @Column(name = "code", nullable = false, length = 64)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "description")
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    var severity: GrievanceSeverity = GrievanceSeverity.MEDIUM,
    @Column(name = "default_handler_role", nullable = false, length = 64)
    var defaultHandlerRole: String = "HR_MANAGER",
    @Column(name = "sla_days", nullable = false)
    var slaDays: Int = 5,
) : TenantScopedEntity()

@Entity
@Table(name = "grievance_channel")
class GrievanceChannel(
    @Column(name = "code", nullable = false, length = 32)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "is_confidential", nullable = false)
    var isConfidential: Boolean = true,
) : TenantScopedEntity()

@Entity
@Table(name = "grievance")
class Grievance(
    @Column(name = "grievance_number", nullable = false, length = 64)
    var grievanceNumber: String,
    @Column(name = "raised_by_employee_id")
    var raisedByEmployeeId: UUID? = null,
    @Column(name = "on_behalf_of_employee_id")
    var onBehalfOfEmployeeId: UUID? = null,
    @Column(name = "anonymous", nullable = false)
    var anonymous: Boolean = false,
    @Column(name = "ground_id", nullable = false)
    var groundId: UUID,
    @Column(name = "channel_id", nullable = false)
    var channelId: UUID,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Column(name = "description", nullable = false)
    var description: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: GrievanceStatus = GrievanceStatus.SUBMITTED,
    @Column(name = "handler_user_id")
    var handlerUserId: UUID? = null,
    @Column(name = "raised_at", nullable = false)
    var raisedAt: Instant = Instant.now(),
    @Column(name = "target_resolution_date", nullable = false)
    var targetResolutionDate: Instant,
    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,
    @Column(name = "resolution")
    var resolution: String? = null,
    @Column(name = "satisfaction_rating")
    var satisfactionRating: Int? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "grievance_appeal")
class GrievanceAppeal(
    @Column(name = "grievance_id", nullable = false)
    var grievanceId: UUID,
    @Column(name = "reason", nullable = false)
    var reason: String,
    @Column(name = "appealed_at", nullable = false)
    var appealedAt: Instant = Instant.now(),
    @Column(name = "reviewer_user_id")
    var reviewerUserId: UUID? = null,
    @Column(name = "outcome")
    var outcome: String? = null,
    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: AppealStatus = AppealStatus.PENDING,
) : TenantScopedEntity()

@Entity
@Table(name = "incident_type")
class IncidentType(
    @Column(name = "code", nullable = false, length = 64)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "description")
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    var severity: IncidentSeverity = IncidentSeverity.MEDIUM,
) : TenantScopedEntity()

@Entity
@Table(name = "incident_subtype")
class IncidentSubtype(
    @Column(name = "incident_type_id", nullable = false)
    var incidentTypeId: UUID,
    @Column(name = "code", nullable = false, length = 64)
    var code: String,
    @Column(name = "name", nullable = false, length = 128)
    var name: String,
    @Column(name = "description")
    var description: String? = null,
) : TenantScopedEntity()

@Entity
@Table(name = "disciplinary_incident")
class DisciplinaryIncident(
    @Column(name = "incident_number", nullable = false, length = 64)
    var incidentNumber: String,
    @Column(name = "employee_id", nullable = false)
    var employeeId: UUID,
    @Column(name = "reported_by_employee_id", nullable = false)
    var reportedByEmployeeId: UUID,
    @Column(name = "incident_type_id", nullable = false)
    var incidentTypeId: UUID,
    @Column(name = "subtype_id")
    var subtypeId: UUID? = null,
    @Column(name = "incident_date", nullable = false)
    var incidentDate: LocalDate,
    @Column(name = "location", length = 128)
    var location: String? = null,
    @Column(name = "description", nullable = false)
    var description: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "witnesses", columnDefinition = "jsonb")
    var witnesses: MutableList<String> = mutableListOf(),
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: IncidentStatus = IncidentStatus.REPORTED,
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    var severity: IncidentSeverity = IncidentSeverity.MEDIUM,
) : TenantScopedEntity()

@Entity
@Table(name = "corrective_action")
class CorrectiveAction(
    @Column(name = "incident_id", nullable = false)
    var incidentId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    var actionType: CorrectiveActionType,
    @Column(name = "issued_at", nullable = false)
    var issuedAt: Instant = Instant.now(),
    @Column(name = "issued_by", nullable = false)
    var issuedBy: UUID,
    @Column(name = "document_key", length = 255)
    var documentKey: String? = null,
    @Column(name = "title", nullable = false, length = 255)
    var title: String,
    @Column(name = "details", nullable = false)
    var details: String,
    @Column(name = "response_due_date")
    var responseDueDate: LocalDate? = null,
    @Column(name = "employee_response")
    var employeeResponse: String? = null,
    @Column(name = "responded_at")
    var respondedAt: Instant? = null,
    @Column(name = "outcome")
    var outcome: String? = null,
    @Column(name = "effective_from")
    var effectiveFrom: LocalDate? = null,
    @Column(name = "effective_to")
    var effectiveTo: LocalDate? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: CorrectiveActionStatus = CorrectiveActionStatus.ISSUED,
) : TenantScopedEntity()

@Entity
@Table(name = "incident_journal")
class IncidentJournal(
    @Column(name = "incident_id", nullable = false)
    var incidentId: UUID,
    @Column(name = "entry", nullable = false)
    var entry: String,
    @Column(name = "entered_by", nullable = false)
    var enteredBy: UUID,
    @Column(name = "entered_at", nullable = false)
    var enteredAt: Instant = Instant.now(),
) : TenantScopedEntity()

@Entity
@Table(name = "disciplinary_appeal")
class DisciplinaryAppeal(
    @Column(name = "incident_id", nullable = false)
    var incidentId: UUID,
    @Column(name = "corrective_action_id", nullable = false)
    var correctiveActionId: UUID,
    @Column(name = "reason", nullable = false)
    var reason: String,
    @Column(name = "appealed_at", nullable = false)
    var appealedAt: Instant = Instant.now(),
    @Column(name = "reviewer_user_id")
    var reviewerUserId: UUID? = null,
    @Column(name = "outcome")
    var outcome: String? = null,
    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: DisciplinaryAppealStatus = DisciplinaryAppealStatus.SUBMITTED,
) : TenantScopedEntity()
