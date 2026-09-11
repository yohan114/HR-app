package com.hr.app.ui.onboarding

import com.hr.client.model.*

enum class OnboardingTab(val label: String) {
    MY_ONBOARDING("My Onboarding"),
    TEAM_ONBOARDING("Team New Hires"),
    EXIT_NOTICES("Exit Notices"),
    CLEARANCE_SIGNOFFS("Clearance Matrix")
}

data class OnboardingState(
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val activeTab: OnboardingTab = OnboardingTab.MY_ONBOARDING,
    val myOnboarding: OnboardingInstanceDetail? = null,
    val teamInstances: List<OnboardingInstanceSummary> = emptyList(),
    val exitTypes: List<ExitTypeItem> = emptyList(),
    val exitNotices: List<ExitNoticeItem> = emptyList(),
    val selectedClearance: ClearanceDetailResponse? = null,

    // Dialogs & Modals
    val isTaskCompleteDialogOpen: Boolean = false,
    val selectedTaskForCompletion: OnboardingTaskItem? = null,
    val isExitNoticeDialogOpen: Boolean = false,
    val isClearanceSignOffDialogOpen: Boolean = false,
    val selectedClearanceTask: ClearanceTaskItem? = null,
)
