package com.hr.disciplinary

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ---------------------------------------------------------------------------
// Grievance Enums
// ---------------------------------------------------------------------------

enum class GrievanceSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class GrievanceStatus {
    SUBMITTED,
    ASSIGNED,
    UNDER_INVESTIGATION,
    RESOLVED,
    APPEALED,
    CLOSED,
}

enum class AppealStatus {
    PENDING,
    UPHELD,
    MODIFIED,
    DISMISSED,
}

// ---------------------------------------------------------------------------
// Disciplinary Enums
// ---------------------------------------------------------------------------

enum class IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class IncidentStatus {
    REPORTED,
    UNDER_INVESTIGATION,
    ACTION_PROPOSED,
    ACTION_ISSUED,
    APPEALED,
    CONCLUDED,
}

enum class CorrectiveActionType {
    ORAL_WARNING,
    WRITTEN_WARNING,
    SHOW_CAUSE,
    CHARGE_SHEET,
    DOMESTIC_INQUIRY,
    SUSPENSION,
    DEMOTION,
    TERMINATION,
}

enum class CorrectiveActionStatus {
    ISSUED,
    RESPONDED,
    UNDER_REVIEW,
    CONFIRMED,
    REVOKED,
    APPEALED,
}

enum class DisciplinaryAppealStatus {
    SUBMITTED,
    UPHELD,
    REDUCED,
    OVERTURNED,
}

// ---------------------------------------------------------------------------
// Grievance DTOs
// ---------------------------------------------------------------------------

data class GrievanceGroundGroupItem(
    val id: UUID,
    val code: String,
    val name: String,
    val description: String?,
)

data class GrievanceGroundItem(
    val id: UUID,
    val groupId: UUID,
    val groupName: String?,
    val code: String,
    val name: String,
    val description: String?,
    val severity: GrievanceSeverity,
    val defaultHandlerRole: String,
    val slaDays: Int,
)

data class GrievanceGroundsResponse(
    val groups: List<GrievanceGroundGroupItem>,
    val grounds: List<GrievanceGroundItem>,
)

data class GrievanceChannelItem(
    val id: UUID,
    val code: String,
    val name: String,
    val isConfidential: Boolean,
)

data class GrievanceChannelsResponse(
    val channels: List<GrievanceChannelItem>,
)

data class GrievanceItem(
    val id: UUID,
    val grievanceNumber: String,
    val title: String,
    val groundCode: String,
    val groundName: String,
    val channelName: String,
    val severity: GrievanceSeverity,
    val anonymous: Boolean,
    val status: GrievanceStatus,
    val raisedAt: Instant,
    val targetResolutionDate: Instant,
    val resolvedAt: Instant?,
    val resolution: String?,
    val satisfactionRating: Int?,
)

data class GrievanceListResponse(
    val totalCount: Int,
    val pendingCount: Int,
    val grievances: List<GrievanceItem>,
)

data class GrievanceAppealItem(
    val id: UUID,
    val grievanceId: UUID,
    val reason: String,
    val appealedAt: Instant,
    val status: AppealStatus,
    val outcome: String?,
    val reviewedAt: Instant?,
)

data class GrievanceDetailResponse(
    val id: UUID,
    val grievanceNumber: String,
    val title: String,
    val description: String,
    val ground: GrievanceGroundItem,
    val channel: GrievanceChannelItem,
    val anonymous: Boolean,
    val raisedByEmployeeName: String?,
    val status: GrievanceStatus,
    val raisedAt: Instant,
    val targetResolutionDate: Instant,
    val resolvedAt: Instant?,
    val resolution: String?,
    val satisfactionRating: Int?,
    val appeal: GrievanceAppealItem?,
)

data class GrievanceSubmitRequest(
    val groundId: UUID,
    val channelId: UUID,
    val title: String,
    val description: String,
    val anonymous: Boolean = false,
    val onBehalfOfEmployeeId: UUID? = null,
    val attachmentKeys: List<String>? = null,
)

data class GrievanceAppealRequest(
    val reason: String,
)

// ---------------------------------------------------------------------------
// Disciplinary DTOs
// ---------------------------------------------------------------------------

data class IncidentSubtypeItem(
    val id: UUID,
    val typeId: UUID,
    val code: String,
    val name: String,
    val description: String?,
)

data class IncidentTypeItem(
    val id: UUID,
    val code: String,
    val name: String,
    val description: String?,
    val severity: IncidentSeverity,
    val subtypes: List<IncidentSubtypeItem>,
)

data class IncidentTypesResponse(
    val types: List<IncidentTypeItem>,
)

data class DisciplinaryIncidentItem(
    val id: UUID,
    val incidentNumber: String,
    val employeeId: UUID,
    val employeeName: String,
    val reportedByEmployeeId: UUID,
    val reportedByEmployeeName: String,
    val typeCode: String,
    val typeName: String,
    val subtypeName: String?,
    val incidentDate: LocalDate,
    val location: String?,
    val description: String,
    val severity: IncidentSeverity,
    val status: IncidentStatus,
    val createdAt: Instant,
)

data class DisciplinaryIncidentListResponse(
    val totalCount: Int,
    val openCount: Int,
    val incidents: List<DisciplinaryIncidentItem>,
)

data class CorrectiveActionItem(
    val id: UUID,
    val incidentId: UUID,
    val actionType: CorrectiveActionType,
    val issuedAt: Instant,
    val issuedBy: UUID,
    val issuedByName: String,
    val title: String,
    val details: String,
    val responseDueDate: LocalDate?,
    val employeeResponse: String?,
    val respondedAt: Instant?,
    val outcome: String?,
    val effectiveFrom: LocalDate?,
    val effectiveTo: LocalDate?,
    val status: CorrectiveActionStatus,
)

data class IncidentJournalEntryItem(
    val id: UUID,
    val incidentId: UUID,
    val entry: String,
    val enteredBy: UUID,
    val enteredByName: String,
    val enteredAt: Instant,
)

data class DisciplinaryAppealItem(
    val id: UUID,
    val incidentId: UUID,
    val correctiveActionId: UUID,
    val reason: String,
    val appealedAt: Instant,
    val outcome: String?,
    val reviewedAt: Instant?,
    val status: DisciplinaryAppealStatus,
)

data class DisciplinaryIncidentDetailResponse(
    val incident: DisciplinaryIncidentItem,
    val correctiveActions: List<CorrectiveActionItem>,
    val journalEntries: List<IncidentJournalEntryItem>,
    val appeals: List<DisciplinaryAppealItem>,
)

data class DisciplinaryIncidentReportRequest(
    val employeeId: UUID,
    val incidentTypeId: UUID,
    val subtypeId: UUID? = null,
    val incidentDate: LocalDate,
    val location: String? = null,
    val description: String,
    val severity: IncidentSeverity? = null,
    val witnesses: List<String>? = null,
)

data class IssueCorrectiveActionRequest(
    val actionType: CorrectiveActionType,
    val title: String,
    val details: String,
    val responseDueDate: LocalDate? = null,
    val effectiveFrom: LocalDate? = null,
    val effectiveTo: LocalDate? = null,
)

data class CorrectiveActionResponseRequest(
    val employeeResponse: String,
)

data class AddJournalEntryRequest(
    val entry: String,
)

data class DisciplinaryAppealRequest(
    val reason: String,
)
