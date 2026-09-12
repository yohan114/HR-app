package com.hr.dashboard

import com.hr.mobile.QuickActionItem
import java.time.Instant

enum class WidgetCategory {
    METRIC,
    APPROVALS,
    ACTION,
    ALERT,
}

enum class WidgetStatus {
    NORMAL,
    WARNING,
    CRITICAL,
    SUCCESS,
}

data class DashboardWidget(
    val key: String,
    val title: String,
    val category: WidgetCategory,
    val value: String,
    val subtext: String,
    val trend: String? = null,
    val status: WidgetStatus? = WidgetStatus.NORMAL,
    val deepLink: String,
    val permission: String? = null,
)

data class DashboardResponse(
    val asOf: Instant,
    val greeting: String,
    val widgets: List<DashboardWidget>,
    val quickActions: List<QuickActionItem>,
)
