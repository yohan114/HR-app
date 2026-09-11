package com.hr.app.ui.recruitment

import com.hr.client.model.*

enum class RecruitmentTab(val title: String) {
    VACANCIES("Open Vacancies"),
    PIPELINE("Candidate Pipeline"),
    INTERVIEWS("My Interviews"),
    OFFERS("Offers & Scorecards"),
}

data class RecruitmentState(
    val selectedTab: RecruitmentTab = RecruitmentTab.VACANCIES,
    val vacancies: List<VacancyItem> = emptyList(),
    val candidates: List<CandidateItem> = emptyList(),
    val applications: List<ApplicationItem> = emptyList(),
    val interviews: List<InterviewItem> = emptyList(),
    val selectedOffer: OfferDetail? = null,
    val selectedVacancy: VacancyItem? = null,
    val selectedApplication: ApplicationItem? = null,
    val selectedInterview: InterviewItem? = null,
    val searchQuery: String = "",
    val selectedStageFilter: ApplicationItem.Stage? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showCreateVacancyDialog: Boolean = false,
    val showScheduleInterviewDialog: Boolean = false,
    val showScorecardDialog: Boolean = false,
    val showCreateOfferDialog: Boolean = false,
)
