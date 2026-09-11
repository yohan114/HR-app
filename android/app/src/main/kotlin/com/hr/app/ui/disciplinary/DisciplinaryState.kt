package com.hr.app.ui.disciplinary

import java.util.UUID

enum class DisciplinaryGrievanceTab {
    MY_GRIEVANCES,
    FILE_GRIEVANCE,
    DISCIPLINARY_NOTICES,
    HOTLINE,
}

data class DisciplinaryGrievanceUiState(
    val activeTab: DisciplinaryGrievanceTab = DisciplinaryGrievanceTab.MY_GRIEVANCES,
    val isLoading: Boolean = false,
    val errorBanner: String? = null,
    val successBanner: String? = null,

    // Grievance Form State
    val selectedGroundId: UUID? = null,
    val selectedChannelId: UUID? = null,
    val grievanceTitle: String = "",
    val grievanceDescription: String = "",
    val isAnonymous: Boolean = false,
    val isSubmittingGrievance: Boolean = false,

    // Grievance Details & Appeal
    val showGrievanceDetailDialog: Boolean = false,
    val showGrievanceAppealDialog: Boolean = false,
    val appealGrievanceId: UUID? = null,
    val appealReasonText: String = "",
    val isSubmittingAppeal: Boolean = false,

    // Disciplinary & Corrective Action Response State
    val showActionResponseDialog: Boolean = false,
    val respondingActionId: UUID? = null,
    val responseExplanationText: String = "",
    val isSubmittingResponse: Boolean = false,

    // Action Appeal State
    val showActionAppealDialog: Boolean = false,
    val appealingActionId: UUID? = null,
    val actionAppealReasonText: String = "",
    val isSubmittingActionAppeal: Boolean = false,

    // Incident Details Sheet
    val showIncidentDetailSheet: Boolean = false,
    val selectedIncidentId: UUID? = null,
)
