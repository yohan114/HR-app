package com.hr.app.ui.approvals

import com.hr.client.model.ApprovalItem

enum class ApprovalFilterTab {
    ALL,
    LEAVE,
    ATTENDANCE,
}

/**
 * UI State for the mobile manager approvals inbox.
 */
data class ApprovalsState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val selectedTab: ApprovalFilterTab = ApprovalFilterTab.ALL,
    val items: List<ApprovalItem> = emptyList(),
    val pendingOutboxCount: Int = 0,
    val selectedItemForDetail: ApprovalItem? = null,
    val rejectionTargetItem: ApprovalItem? = null,
    val rejectionRemarks: String = "",
    val actionInProgressId: String? = null,
    val successSnackbarMessage: String? = null,
) {
    val totalCount: Int get() = items.size
    val leaveCount: Int get() = items.count { it.type.equals("LEAVE", ignoreCase = true) }
    val attendanceCount: Int get() = items.count { it.type.equals("ATTENDANCE", ignoreCase = true) }

    val filteredItems: List<ApprovalItem>
        get() = when (selectedTab) {
            ApprovalFilterTab.ALL -> items
            ApprovalFilterTab.LEAVE -> items.filter { it.type.equals("LEAVE", ignoreCase = true) }
            ApprovalFilterTab.ATTENDANCE -> items.filter { it.type.equals("ATTENDANCE", ignoreCase = true) }
        }
}
