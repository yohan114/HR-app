package com.hr.app.ui.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hr.app.ui.theme.Spacing
import com.hr.app.ui.theme.Radius
import com.hr.client.model.*
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    viewModel: PerformanceViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val myAppraisals by viewModel.myAppraisals.collectAsState()
    val teamAppraisals by viewModel.teamAppraisals.collectAsState()
    val feedback by viewModel.feedback.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Performance & OKRs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "PeoplesHR Talent Management benchmark",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                    when (state.currentTab) {
                        PerformanceTab.GOALS_OKRS -> {
                            IconButton(onClick = { viewModel.openCreateGoalDialog() }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Goal",
                                )
                            }
                        }
                        PerformanceTab.FEEDBACK -> {
                            IconButton(onClick = { viewModel.openSendFeedbackDialog() }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Send Feedback",
                                )
                            }
                        }
                        else -> {}
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Success / Error notification banner
            state.successMessage?.let { success ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    shape = RoundedCornerShape(Radius.card),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text(
                            text = success,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.dismissMessages() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            state.errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(Radius.card),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.dismissMessages() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Primary Navigation Tabs
            PrimaryTabRow(
                selectedTabIndex = state.currentTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                PerformanceTab.entries.forEach { tab ->
                    Tab(
                        selected = state.currentTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (state.currentTab == tab) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            if (state.isLoading && goals.isEmpty() && myAppraisals.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (state.currentTab) {
                    PerformanceTab.GOALS_OKRS -> GoalsTabContent(
                        goals = goals,
                        selectedFilter = state.selectedCategoryFilter,
                        onFilterSelected = { viewModel.filterByCategory(it) },
                        onCheckIn = { viewModel.openCheckInDialog(it) },
                    )
                    PerformanceTab.MY_APPRAISAL -> MyAppraisalTabContent(
                        appraisals = myAppraisals,
                        onViewDetail = { viewModel.openAppraisalDetail(it) },
                        onStartSelfReview = { appraisal ->
                            viewModel.openAppraisalDetail(appraisal)
                            viewModel.openSelfReviewDialog()
                        },
                        onAcknowledge = { appraisal ->
                            viewModel.openAppraisalDetail(appraisal)
                            viewModel.acknowledgeAppraisal()
                        },
                    )
                    PerformanceTab.TEAM_APPRAISALS -> TeamAppraisalsTabContent(
                        teamAppraisals = teamAppraisals,
                        onEvaluate = { appraisal ->
                            viewModel.openAppraisalDetail(appraisal)
                            viewModel.openManagerReviewDialog()
                        },
                    )
                    PerformanceTab.FEEDBACK -> ContinuousFeedbackTabContent(
                        feedbackList = feedback,
                    )
                }
            }
        }
    }

    // ==================== Modals and Dialogs ====================

    if (state.isCheckInDialogOpen) {
        CheckInDialog(
            goal = state.selectedGoalForCheckIn,
            newValue = state.checkInNewValue,
            note = state.checkInNote,
            onNewValueChange = { viewModel.updateCheckInNewValue(it) },
            onNoteChange = { viewModel.updateCheckInNote(it) },
            onDismiss = { viewModel.closeCheckInDialog() },
            onSubmit = { viewModel.submitCheckIn() },
        )
    }

    if (state.isCreateGoalDialogOpen) {
        CreateGoalDialog(
            title = state.newGoalTitle,
            description = state.newGoalDescription,
            target = state.newGoalTargetValue,
            unit = state.newGoalUnit,
            weight = state.newGoalWeight,
            category = state.newGoalCategory,
            onTitleChange = { viewModel.updateNewGoalTitle(it) },
            onDescriptionChange = { viewModel.updateNewGoalDescription(it) },
            onTargetChange = { viewModel.updateNewGoalTarget(it) },
            onUnitChange = { viewModel.updateNewGoalUnit(it) },
            onWeightChange = { viewModel.updateNewGoalWeight(it) },
            onCategoryChange = { viewModel.updateNewGoalCategory(it) },
            onDismiss = { viewModel.closeCreateGoalDialog() },
            onSubmit = { viewModel.submitCreateGoal() },
        )
    }

    if (state.isDetailDialogOpen && state.selectedAppraisalDetail != null) {
        AppraisalDetailDialog(
            detail = state.selectedAppraisalDetail!!,
            onDismiss = { viewModel.closeAppraisalDetail() },
            onSelfReview = { viewModel.openSelfReviewDialog() },
            onManagerReview = { viewModel.openManagerReviewDialog() },
            onAcknowledge = { viewModel.acknowledgeAppraisal() },
        )
    }

    if (state.isSelfReviewDialogOpen) {
        SelfReviewDialog(
            comments = state.selfOverallComments,
            onCommentsChange = { viewModel.updateSelfOverallComments(it) },
            onDismiss = { viewModel.closeSelfReviewDialog() },
            onSubmit = { viewModel.submitSelfReview() },
        )
    }

    if (state.isManagerReviewDialogOpen) {
        ManagerReviewDialog(
            comments = state.managerOverallComments,
            calibrationNotes = state.managerCalibrationNotes,
            onCommentsChange = { viewModel.updateManagerOverallComments(it) },
            onCalibrationNotesChange = { viewModel.updateManagerCalibrationNotes(it) },
            onDismiss = { viewModel.closeManagerReviewDialog() },
            onSubmit = { viewModel.submitManagerReview() },
        )
    }

    if (state.isSendFeedbackDialogOpen) {
        SendFeedbackDialog(
            type = state.feedbackType,
            title = state.feedbackTitle,
            content = state.feedbackContent,
            isPrivate = state.isFeedbackPrivate,
            onTypeChange = { viewModel.updateFeedbackType(it) },
            onTitleChange = { viewModel.updateFeedbackTitle(it) },
            onContentChange = { viewModel.updateFeedbackContent(it) },
            onPrivateChange = { viewModel.updateFeedbackPrivate(it) },
            onDismiss = { viewModel.closeSendFeedbackDialog() },
            onSubmit = { viewModel.submitFeedback() },
        )
    }
}

// ============================================================================
// TAB 1: Goals & OKRs
// ============================================================================

@Composable
private fun GoalsTabContent(
    goals: List<GoalItem>,
    selectedFilter: GoalItem.Category?,
    onFilterSelected: (GoalItem.Category?) -> Unit,
    onCheckIn: (GoalItem) -> Unit,
) {
    val filteredGoals = if (selectedFilter == null) {
        goals
    } else {
        goals.filter { it.category == selectedFilter }
    }

    val avgProgress = if (goals.isNotEmpty()) {
        goals.map { it.progressPercentage.toDouble() }.average()
    } else 0.0

    val completedCount = goals.count { it.status == GoalItem.Status.COMPLETED }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        item {
            // Metrics Dashboard Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(Radius.card),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.s4),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricStatItem(
                        label = "Total Goals",
                        value = "${goals.size}",
                        icon = Icons.Default.Flag,
                    )
                    MetricStatItem(
                        label = "Avg Progress",
                        value = "%.0f%%".format(avgProgress),
                        icon = Icons.Default.TrendingUp,
                    )
                    MetricStatItem(
                        label = "Achieved",
                        value = "$completedCount",
                        icon = Icons.Default.CheckCircle,
                    )
                }
            }
        }

        item {
            // Filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { onFilterSelected(null) },
                    label = { Text("All (${goals.size})") },
                )
                FilterChip(
                    selected = selectedFilter == GoalItem.Category.INDIVIDUAL,
                    onClick = { onFilterSelected(GoalItem.Category.INDIVIDUAL) },
                    label = { Text("Individual") },
                )
                FilterChip(
                    selected = selectedFilter == GoalItem.Category.DEPARTMENTAL,
                    onClick = { onFilterSelected(GoalItem.Category.DEPARTMENTAL) },
                    label = { Text("Department") },
                )
                FilterChip(
                    selected = selectedFilter == GoalItem.Category.DEVELOPMENTAL,
                    onClick = { onFilterSelected(GoalItem.Category.DEVELOPMENTAL) },
                    label = { Text("Growth") },
                )
            }
        }

        if (filteredGoals.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.s8),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No performance goals found in this category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(filteredGoals) { goal ->
                GoalCard(goal = goal, onCheckIn = { onCheckIn(goal) })
            }
        }
    }
}

@Composable
private fun MetricStatItem(
    label: String,
    value: String,
    icon: ImageVector,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.s1))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GoalCard(
    goal: GoalItem,
    onCheckIn: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(Radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        CategoryBadge(category = goal.category)
                        StatusChip(status = goal.status)
                    }
                    Spacer(modifier = Modifier.height(Spacing.s1))
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "Weight: ${goal.weight}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            goal.description?.let { desc ->
                Spacer(modifier = Modifier.height(Spacing.s2))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s3))

            // Progress Bar and Metric
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Progress: ${goal.currentValue} / ${goal.targetValue} ${goal.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${goal.progressPercentage}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s1))
            LinearProgressIndicator(
                progress = { (goal.progressPercentage.toFloat() / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
            )

            Spacer(modifier = Modifier.height(Spacing.s3))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Due: ${goal.dueDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = onCheckIn,
                    contentPadding = PaddingValues(horizontal = Spacing.s3, vertical = Spacing.s1),
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text(text = "Check-In", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Expanded Check-in History
            if (expanded && goal.checkIns.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.s3))
                Text(
                    text = "Check-In History (${goal.checkIns.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(Spacing.s2))
                goal.checkIns.take(3).forEach { ci ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.s1),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .offset(y = 6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Column {
                            Text(
                                text = "${ci.previousValue} → ${ci.newValue} ${goal.unit} (${ci.progressPercentage}%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ci.note?.let { note ->
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: GoalItem.Category) {
    val (label, bg) = when (category) {
        GoalItem.Category.ORGANIZATIONAL -> "Org OKR" to MaterialTheme.colorScheme.tertiaryContainer
        GoalItem.Category.DEPARTMENTAL -> "Dept KPI" to MaterialTheme.colorScheme.secondaryContainer
        GoalItem.Category.INDIVIDUAL -> "Individual" to MaterialTheme.colorScheme.primaryContainer
        GoalItem.Category.DEVELOPMENTAL -> "Development" to MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(bg)
            .padding(horizontal = Spacing.s2, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatusChip(status: GoalItem.Status) {
    val (color, text) = when (status) {
        GoalItem.Status.NOT_STARTED -> MaterialTheme.colorScheme.outline to "Not Started"
        GoalItem.Status.IN_PROGRESS -> MaterialTheme.colorScheme.primary to "In Progress"
        GoalItem.Status.ON_TRACK -> Color(0xFF2E7D32) to "On Track"
        GoalItem.Status.AT_RISK -> Color(0xFFC62828) to "At Risk"
        GoalItem.Status.COMPLETED -> Color(0xFF1565C0) to "Completed"
        GoalItem.Status.CANCELLED -> MaterialTheme.colorScheme.outline to "Cancelled"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(Spacing.s1))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ============================================================================
// TAB 2: My Appraisal
// ============================================================================

@Composable
private fun MyAppraisalTabContent(
    appraisals: List<AppraisalSummaryItem>,
    onViewDetail: (AppraisalSummaryItem) -> Unit,
    onStartSelfReview: (AppraisalSummaryItem) -> Unit,
    onAcknowledge: (AppraisalSummaryItem) -> Unit,
) {
    if (appraisals.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.s8),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No active performance appraisal cycles found for your account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        items(appraisals) { item ->
            AppraisalSummaryCard(
                appraisal = item,
                isManagerView = false,
                onView = { onViewDetail(item) },
                onAction = {
                    when (item.status) {
                        AppraisalSummaryItem.Status.SELF_REVIEW_PENDING -> onStartSelfReview(item)
                        AppraisalSummaryItem.Status.MANAGER_REVIEW_SUBMITTED,
                        AppraisalSummaryItem.Status.IN_CALIBRATION -> onAcknowledge(item)
                        else -> onViewDetail(item)
                    }
                },
            )
        }
    }
}

// ============================================================================
// TAB 3: Team Appraisals
// ============================================================================

@Composable
private fun TeamAppraisalsTabContent(
    teamAppraisals: List<AppraisalSummaryItem>,
    onEvaluate: (AppraisalSummaryItem) -> Unit,
) {
    if (teamAppraisals.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.s8),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No subordinate direct reports currently pending your evaluation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        items(teamAppraisals) { item ->
            AppraisalSummaryCard(
                appraisal = item,
                isManagerView = true,
                onView = { onEvaluate(item) },
                onAction = { onEvaluate(item) },
            )
        }
    }
}

@Composable
private fun AppraisalSummaryCard(
    appraisal: AppraisalSummaryItem,
    isManagerView: Boolean,
    onView: () -> Unit,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(Radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = appraisal.cycleName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                AppraisalStatusBadge(status = appraisal.status)
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            if (isManagerView) {
                Text(
                    text = "Employee: ${appraisal.employeeName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    text = "Evaluator: ${appraisal.managerName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s1))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Self Deadline: ${appraisal.selfReviewDeadline}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Mgr Deadline: ${appraisal.managerReviewDeadline}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            appraisal.finalRating?.let { rating ->
                Spacer(modifier = Modifier.height(Spacing.s2))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.control))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(Spacing.s2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Outcome: $rating",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    appraisal.finalScore?.let { score ->
                        Text(
                            text = "Score: $score / 5.0",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s3))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onView,
                    shape = RoundedCornerShape(Radius.control),
                    contentPadding = PaddingValues(horizontal = Spacing.s3, vertical = Spacing.s1),
                ) {
                    Text("View Detail")
                }

                Spacer(modifier = Modifier.width(Spacing.s2))

                val (actionLabel, actionEnabled) = when {
                    isManagerView && appraisal.status == AppraisalSummaryItem.Status.MANAGER_REVIEW_PENDING ->
                        "Evaluate & Score" to true
                    !isManagerView && appraisal.status == AppraisalSummaryItem.Status.SELF_REVIEW_PENDING ->
                        "Submit Self-Review" to true
                    !isManagerView && appraisal.status == AppraisalSummaryItem.Status.MANAGER_REVIEW_SUBMITTED ->
                        "Sign & Acknowledge" to true
                    appraisal.status == AppraisalSummaryItem.Status.ACKNOWLEDGED ->
                        "Acknowledged" to false
                    else -> "Review" to true
                }

                Button(
                    onClick = onAction,
                    enabled = actionEnabled,
                    shape = RoundedCornerShape(Radius.control),
                    contentPadding = PaddingValues(horizontal = Spacing.s3, vertical = Spacing.s1),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun AppraisalStatusBadge(status: AppraisalSummaryItem.Status) {
    val (label, bg, fg) = when (status) {
        AppraisalSummaryItem.Status.NOT_STARTED ->
            Triple("Not Started", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        AppraisalSummaryItem.Status.SELF_REVIEW_PENDING ->
            Triple("Self-Review Pending", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        AppraisalSummaryItem.Status.SELF_REVIEW_SUBMITTED ->
            Triple("Self Submitted", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        AppraisalSummaryItem.Status.MANAGER_REVIEW_PENDING ->
            Triple("Manager Pending", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        AppraisalSummaryItem.Status.MANAGER_REVIEW_SUBMITTED ->
            Triple("Manager Scored", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        AppraisalSummaryItem.Status.IN_CALIBRATION ->
            Triple("In Calibration", Color(0xFFFFF3E0), Color(0xFFE65100))
        AppraisalSummaryItem.Status.ACKNOWLEDGED ->
            Triple("Acknowledged", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        AppraisalSummaryItem.Status.CLOSED ->
            Triple("Closed", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(bg)
            .padding(horizontal = Spacing.s2, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

// ============================================================================
// TAB 4: Continuous Feedback & 1-on-1 Notes
// ============================================================================

@Composable
private fun ContinuousFeedbackTabContent(
    feedbackList: List<ContinuousFeedbackItem>,
) {
    if (feedbackList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.s8),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No continuous feedback, praise, or coaching notes recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        items(feedbackList) { item ->
            ContinuousFeedbackCard(item = item)
        }
    }
}

@Composable
private fun ContinuousFeedbackCard(item: ContinuousFeedbackItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(Radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeedbackTypeBadge(type = item.feedbackType)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isPrivate) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Private",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(modifier = Modifier.width(Spacing.s1))
                    }
                    Text(
                        text = item.createdAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(Spacing.s1))

            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(Spacing.s3))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "From: ${item.senderEmployeeName} → ${item.recipientEmployeeName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )

                if (item.sharedWithManager) {
                    Text(
                        text = "Shared with Manager",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackTypeBadge(type: ContinuousFeedbackItem.FeedbackType) {
    val (label, icon, color) = when (type) {
        ContinuousFeedbackItem.FeedbackType.PRAISE ->
            Triple("Praise & Recognition", Icons.Default.ThumbUp, Color(0xFF2E7D32))
        ContinuousFeedbackItem.FeedbackType.COACHING ->
            Triple("Coaching Observation", Icons.Default.Lightbulb, Color(0xFFE65100))
        ContinuousFeedbackItem.FeedbackType.ONE_ON_ONE_NOTE ->
            Triple("1-on-1 Check-In", Icons.Default.Chat, Color(0xFF1565C0))
        ContinuousFeedbackItem.FeedbackType.CHECK_IN ->
            Triple("General Note", Icons.Default.Note, Color(0xFF455A64))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = Spacing.s2, vertical = 2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.s1))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

// ============================================================================
// MODALS AND DIALOGS
// ============================================================================

@Composable
private fun CheckInDialog(
    goal: GoalItem?,
    newValue: String,
    note: String,
    onNewValueChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Record Progress Check-In",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                goal?.let {
                    Text(
                        text = it.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Target: ${it.targetValue} ${it.unit} (Current: ${it.currentValue} ${it.unit})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = newValue,
                    onValueChange = onNewValueChange,
                    label = { Text("New Metric Value (${goal?.unit ?: ""})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    label = { Text("Check-In Note (Optional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSubmit) {
                Text("Submit Check-In")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CreateGoalDialog(
    title: String,
    description: String,
    target: String,
    unit: String,
    weight: String,
    category: GoalCreateRequest.Category,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTargetChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onCategoryChange: (GoalCreateRequest.Category) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Performance Goal / OKR",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Goal Title *") },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description & Success Criteria") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    OutlinedTextField(
                        value = target,
                        onValueChange = onTargetChange,
                        label = { Text("Target") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = onUnitChange,
                        label = { Text("Unit (%, count)") },
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = weight,
                    onValueChange = onWeightChange,
                    label = { Text("Weight Percentage (%)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    FilterChip(
                        selected = category == GoalCreateRequest.Category.INDIVIDUAL,
                        onClick = { onCategoryChange(GoalCreateRequest.Category.INDIVIDUAL) },
                        label = { Text("Indiv", fontSize = 11.sp) },
                    )
                    FilterChip(
                        selected = category == GoalCreateRequest.Category.DEPARTMENTAL,
                        onClick = { onCategoryChange(GoalCreateRequest.Category.DEPARTMENTAL) },
                        label = { Text("Dept", fontSize = 11.sp) },
                    )
                    FilterChip(
                        selected = category == GoalCreateRequest.Category.DEVELOPMENTAL,
                        onClick = { onCategoryChange(GoalCreateRequest.Category.DEVELOPMENTAL) },
                        label = { Text("Growth", fontSize = 11.sp) },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit) {
                Text("Create Goal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AppraisalDetailDialog(
    detail: AppraisalDetailResponse,
    onDismiss: () -> Unit,
    onSelfReview: () -> Unit,
    onManagerReview: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${detail.appraisal.cycleName} - Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Status:", fontWeight = FontWeight.Bold)
                    AppraisalStatusBadge(status = detail.appraisal.status)
                }

                // 360 MRA Summary Section
                detail.mraSummary?.let { mra ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        ),
                    ) {
                        Column(modifier = Modifier.padding(Spacing.s3)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "360° Multi-Rater Assessment",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${mra.completedRequests}/${mra.totalRequests} Responded",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.s1))
                            mra.averageScore?.let { avg ->
                                Text(
                                    text = "Average 360 Score: $avg / 5.0",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (mra.completedRequests < 3) {
                                Text(
                                    text = "Breakdown protected: anonymized until N ≥ 3 respondents.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            } else {
                                mra.relationshipBreakdown.forEach { rel ->
                                    Text(
                                        text = "${rel.relationship}: ${rel.respondentCount} respondents (Score: ${rel.averageScore ?: "anonymized"})",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }

                // Goals Evaluation
                Text(
                    text = "Goal Ratings (${detail.goals.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                detail.goals.forEach { g ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(modifier = Modifier.padding(Spacing.s2)) {
                            Text(text = g.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Self: ${g.selfRating ?: "-"} | Mgr: ${g.managerRating ?: "-"} | Weighted: ${g.weightedScore ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                // Competencies Evaluation
                Text(
                    text = "Competency Proficiencies (${detail.competencies.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                detail.competencies.forEach { c ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(modifier = Modifier.padding(Spacing.s2)) {
                            Text(text = "${c.code} - ${c.name}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Target: ${c.targetLevel} | Self: ${c.selfProficiencyLevel ?: "-"} | Mgr: ${c.managerProficiencyLevel ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }

                // Calibration & Feedback Notes
                detail.selfOverallComments?.let { selfNotes ->
                    Text(text = "Self Comments: $selfNotes", style = MaterialTheme.typography.bodySmall)
                }
                detail.managerOverallComments?.let { mgrNotes ->
                    Text(text = "Manager Comments: $mgrNotes", style = MaterialTheme.typography.bodySmall)
                }
                detail.calibrationNotes?.let { calib ->
                    Text(text = "Calibration Committee Notes: $calib", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                if (detail.appraisal.status == AppraisalSummaryItem.Status.SELF_REVIEW_PENDING) {
                    Button(onClick = onSelfReview) {
                        Text("Self-Review")
                    }
                } else if (detail.appraisal.status == AppraisalSummaryItem.Status.MANAGER_REVIEW_PENDING) {
                    Button(onClick = onManagerReview) {
                        Text("Manager Score")
                    }
                } else if (detail.appraisal.status == AppraisalSummaryItem.Status.MANAGER_REVIEW_SUBMITTED ||
                           detail.appraisal.status == AppraisalSummaryItem.Status.IN_CALIBRATION) {
                    Button(onClick = onAcknowledge) {
                        Text("Acknowledge")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
    )
}

@Composable
private fun SelfReviewDialog(
    comments: String,
    onCommentsChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Submit Employee Self-Assessment",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Text(
                    text = "Reflect on your key accomplishments, challenges, and growth over this performance cycle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = comments,
                    onValueChange = onCommentsChange,
                    label = { Text("Self Overall Evaluation Comments *") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSubmit) {
                Text("Submit Self-Review")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ManagerReviewDialog(
    comments: String,
    calibrationNotes: String,
    onCommentsChange: (String) -> Unit,
    onCalibrationNotesChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Manager Evaluation & Calibration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                OutlinedTextField(
                    value = comments,
                    onValueChange = onCommentsChange,
                    label = { Text("Manager Evaluation Comments *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = calibrationNotes,
                    onValueChange = onCalibrationNotesChange,
                    label = { Text("Calibration Notes & Recommendations") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSubmit) {
                Text("Submit Score")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SendFeedbackDialog(
    type: SendFeedbackRequest.FeedbackType,
    title: String,
    content: String,
    isPrivate: Boolean,
    onTypeChange: (SendFeedbackRequest.FeedbackType) -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Send Continuous Feedback",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Text(
                    text = "Feedback Type",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    FilterChip(
                        selected = type == SendFeedbackRequest.FeedbackType.PRAISE,
                        onClick = { onTypeChange(SendFeedbackRequest.FeedbackType.PRAISE) },
                        label = { Text("Praise", fontSize = 11.sp) },
                    )
                    FilterChip(
                        selected = type == SendFeedbackRequest.FeedbackType.COACHING,
                        onClick = { onTypeChange(SendFeedbackRequest.FeedbackType.COACHING) },
                        label = { Text("Coaching", fontSize = 11.sp) },
                    )
                    FilterChip(
                        selected = type == SendFeedbackRequest.FeedbackType.ONE_ON_ONE_NOTE,
                        onClick = { onTypeChange(SendFeedbackRequest.FeedbackType.ONE_ON_ONE_NOTE) },
                        label = { Text("1-on-1", fontSize = 11.sp) },
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Feedback Title *") },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = onContentChange,
                    label = { Text("Feedback Note / Praise *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onPrivateChange(!isPrivate) },
                ) {
                    Checkbox(checked = isPrivate, onCheckedChange = onPrivateChange)
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text(text = "Private (Visible only to recipient & managers)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
