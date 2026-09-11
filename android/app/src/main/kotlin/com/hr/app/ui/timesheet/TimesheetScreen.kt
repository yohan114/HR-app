package com.hr.app.ui.timesheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hr.client.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimesheetScreen(
    viewModel: TimesheetViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error, state.successMessage) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Timesheets & Billing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        val weekStart = state.selectedWeekStart
                        val weekEnd = weekStart.plusDays(6)
                        val fmt = DateTimeFormatter.ofPattern("MMM dd")
                        Text(
                            text = "${weekStart.format(fmt)} - ${weekEnd.format(fmt)}, ${weekStart.year}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.previousWeek() }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Week")
                    }
                    IconButton(onClick = { viewModel.nextWeek() }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Week")
                    }
                    IconButton(onClick = { viewModel.copyPreviousWeek() }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Previous Week")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                TimesheetTab.values().forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = tab.label,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                when (state.selectedTab) {
                    TimesheetTab.CURRENT_WEEK -> WeeklyMatrixTab(
                        state = state,
                        onAddRow = { viewModel.openAddRowDialog() },
                        onCellClick = { row, date -> viewModel.openEditCellDialog(row, date) },
                        onSaveDraft = { viewModel.saveDraft() },
                        onSubmit = { viewModel.submitCurrentTimesheet() },
                    )
                    TimesheetTab.MY_TIMESHEETS -> HistoryTab(
                        history = state.timesheetsHistory,
                    )
                    TimesheetTab.RECONCILIATION -> ReconciliationTab(
                        reconciliation = state.reconciliation,
                    )
                    TimesheetTab.PROJECTS -> ProjectsTab(
                        projects = state.projects,
                        clients = state.clients,
                    )
                }
            }
        }
    }

    // Dialogs
    if (state.isAddRowDialogOpen) {
        AddProjectRowDialog(
            projects = state.projects,
            activitiesByProject = state.activitiesByProject,
            onLoadActivities = { viewModel.loadActivitiesForProject(it) },
            onDismiss = { viewModel.closeAddRowDialog() },
            onConfirm = { projId, actId, billable ->
                viewModel.addRow(projId, actId, billable)
            },
        )
    }

    if (state.isEditCellDialogOpen && state.selectedRowForEdit != null && state.selectedDateForEdit != null) {
        EditHoursDialog(
            row = state.selectedRowForEdit!!,
            date = state.selectedDateForEdit!!,
            hoursInput = state.currentCellHoursInput,
            notesInput = state.currentCellNotesInput,
            onHoursChange = { h, n -> viewModel.updateCellInput(h, n) },
            onDismiss = { viewModel.closeEditCellDialog() },
            onSave = { viewModel.commitCellEdit() },
        )
    }
}

// =============================================================================
// Tab 1: Weekly Matrix
// =============================================================================

@Composable
fun WeeklyMatrixTab(
    state: TimesheetUiState,
    onAddRow: () -> Unit,
    onCellClick: (TimesheetGridRow, LocalDate) -> Unit,
    onSaveDraft: () -> Unit,
    onSubmit: () -> Unit,
) {
    val status = state.currentTimesheet?.status?.value ?: "DRAFT"
    val days = (0..6).map { state.selectedWeekStart.plusDays(it.toLong()) }
    val dayFmt = DateTimeFormatter.ofPattern("EEE\ndd")

    Column(modifier = Modifier.fillMaxSize()) {
        // Summary Header Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryStatCard(
                title = "Total",
                value = String.format("%.1fh", state.weekTotalHours),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SummaryStatCard(
                title = "Billable",
                value = String.format("%.1fh", state.weekBillableHours),
                color = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f),
            )
            SummaryStatCard(
                title = "Non-Billable",
                value = String.format("%.1fh", state.weekNonBillableHours),
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            SummaryStatCard(
                title = "Status",
                value = status,
                color = when (status) {
                    "APPROVED" -> Color(0xFF2E7D32)
                    "SUBMITTED" -> Color(0xFF0288D1)
                    "REJECTED" -> Color(0xFFC62828)
                    else -> Color(0xFFEF6C00)
                },
                modifier = Modifier.weight(1f),
            )
        }

        Divider()

        if (state.gridRows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No activities logged for this week",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onAddRow) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Project Activity")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.gridRows) { row ->
                    ProjectActivityRowCard(
                        row = row,
                        days = days,
                        onCellClick = { date -> onCellClick(row, date) },
                    )
                }

                item {
                    // Daily Totals Row
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Daily Totals",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            days.forEach { day ->
                                val dayTotal = state.dayTotals[day] ?: 0.0
                                Box(
                                    modifier = Modifier
                                        .width(42.dp)
                                        .padding(horizontal = 2.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (dayTotal > 0.0) String.format("%.1f", dayTotal) else "-",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dayTotal > 8.0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(44.dp))
                        }
                    }
                }
            }
        }

        // Bottom Action Bar
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onAddRow,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Row")
                }

                OutlinedButton(
                    onClick = onSaveDraft,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Draft")
                }

                Button(
                    onClick = onSubmit,
                    enabled = status == "DRAFT" && state.weekTotalHours > 0.0,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Submit")
                }
            }
        }
    }
}

@Composable
fun SummaryStatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun ProjectActivityRowCard(
    row: TimesheetGridRow,
    days: List<LocalDate>,
    onCellClick: (LocalDate) -> Unit,
) {
    val dayNameFmt = DateTimeFormatter.ofPattern("EE")
    val dayNumFmt = DateTimeFormatter.ofPattern("dd")

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${row.projectCode} • ${row.projectName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${row.activityCode}: ${row.activityName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (row.billable) Color(0xFFE8F5E9) else Color(0xFFECEFF1),
                ) {
                    Text(
                        text = if (row.billable) "Billable" else "Internal",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (row.billable) Color(0xFF2E7D32) else Color(0xFF546E7A),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 7 Day Grid Cells
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                days.forEach { date ->
                    val hours = row.dailyHours[date] ?: 0.0
                    val hasHours = hours > 0.0

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (hasHours) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                            .clickable { onCellClick(date) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                            .width(36.dp),
                    ) {
                        Text(
                            text = date.format(dayNameFmt).take(2),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = date.format(dayNumFmt),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (hasHours) String.format("%.1f", hours) else "-",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (hasHours) FontWeight.Bold else FontWeight.Normal,
                            color = if (hasHours) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                    }
                }

                // Row Total
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 6.dp),
                ) {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format("%.1fh", row.rowTotalHours),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

// =============================================================================
// Tab 2: History
// =============================================================================

@Composable
fun HistoryTab(
    history: List<TimesheetListItem>,
) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No timesheet history found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val dateFmt = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(history) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "${item.weekStartDate.format(dateFmt)} - ${item.weekEndDate.format(dateFmt)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Total: ${item.totalHours}h (Billable: ${item.billableHours}h • Non-billable: ${item.nonBillableHours}h)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when (item.status.value) {
                                "APPROVED" -> Color(0xFFE8F5E9)
                                "SUBMITTED" -> Color(0xFFE1F5FE)
                                "REJECTED" -> Color(0xFFFFEBEE)
                                else -> Color(0xFFFFF3E0)
                            },
                        ) {
                            Text(
                                text = item.status.value,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (item.status.value) {
                                    "APPROVED" -> Color(0xFF2E7D32)
                                    "SUBMITTED" -> Color(0xFF0288D1)
                                    "REJECTED" -> Color(0xFFC62828)
                                    else -> Color(0xFFEF6C00)
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Tab 3: Attendance Reconciliation
// =============================================================================

@Composable
fun ReconciliationTab(
    reconciliation: TimesheetReconciliationResponse?,
) {
    if (reconciliation == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM dd")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            // Header stats
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Logged",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = String.format("%.1fh", reconciliation.totalLoggedHours),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Attendance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = String.format("%.1fh", reconciliation.totalAttendanceHours),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Variance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val variance = reconciliation.totalVarianceHours
                        Text(
                            text = String.format("%+.1fh", variance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (variance == 0.0) Color(0xFF2E7D32) else Color(0xFFC62828),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Daily Attendance Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(reconciliation.dailyBreakdown) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = item.date.format(dateFmt),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Logged: ${item.loggedHours}h • Clocked: ${item.attendanceHours}h",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when (item.status.value) {
                                    "MATCHED" -> Color(0xFFE8F5E9)
                                    "UNDER_LOGGED" -> Color(0xFFFFF3E0)
                                    "OVER_LOGGED" -> Color(0xFFFFEBEE)
                                    else -> Color(0xFFECEFF1)
                                },
                            ) {
                                Text(
                                    text = "${item.status.value} (${String.format("%+.1f", item.varianceHours)}h)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when (item.status.value) {
                                        "MATCHED" -> Color(0xFF2E7D32)
                                        "UNDER_LOGGED" -> Color(0xFFEF6C00)
                                        "OVER_LOGGED" -> Color(0xFFC62828)
                                        else -> Color(0xFF546E7A)
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Tab 4: Projects Directory
// =============================================================================

@Composable
fun ProjectsTab(
    projects: List<TimesheetProjectItem>,
    clients: List<TimesheetClientItem>,
) {
    if (projects.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No projects found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(projects) { project ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${project.projectCode} • ${project.projectName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (project.billable) Color(0xFFE8F5E9) else Color(0xFFECEFF1),
                            ) {
                                Text(
                                    text = if (project.billable) "Billable" else "Internal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (project.billable) Color(0xFF2E7D32) else Color(0xFF546E7A),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Client: ${project.clientName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (project.budgetHours != null) {
                            Text(
                                text = "Budget: ${project.budgetHours} hours",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Dialogs: Add Row & Edit Cell
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProjectRowDialog(
    projects: List<TimesheetProjectItem>,
    activitiesByProject: Map<UUID, List<TimesheetActivityItem>>,
    onLoadActivities: (UUID) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (projectId: UUID, activityId: UUID, billable: Boolean) -> Unit,
) {
    var selectedProjectId by remember { mutableStateOf(projects.firstOrNull()?.id) }
    var selectedActivityId by remember { mutableStateOf<UUID?>(null) }
    var billable by remember { mutableStateOf(true) }

    LaunchedEffect(selectedProjectId) {
        selectedProjectId?.let {
            onLoadActivities(it)
            selectedActivityId = null
        }
    }

    val availableActivities = selectedProjectId?.let { activitiesByProject[it] } ?: emptyList()
    LaunchedEffect(availableActivities) {
        if (selectedActivityId == null) {
            selectedActivityId = availableActivities.firstOrNull()?.id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Project Activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Project Selection
                Text("Select Project", style = MaterialTheme.typography.labelMedium)
                projects.forEach { proj ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedProjectId = proj.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedProjectId == proj.id,
                            onClick = { selectedProjectId = proj.id },
                        )
                        Text("${proj.projectCode} - ${proj.projectName}")
                    }
                }

                // Activity Selection
                if (availableActivities.isNotEmpty()) {
                    Text("Select Activity", style = MaterialTheme.typography.labelMedium)
                    availableActivities.forEach { act ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedActivityId = act.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedActivityId == act.id,
                                onClick = { selectedActivityId = act.id },
                            )
                            Text("${act.activityCode} - ${act.activityName}")
                        }
                    }
                }

                // Billable Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Billable to Client")
                    Switch(checked = billable, onCheckedChange = { billable = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedProjectId != null && selectedActivityId != null,
                onClick = {
                    onConfirm(selectedProjectId!!, selectedActivityId!!, billable)
                },
            ) {
                Text("Add Row")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun EditHoursDialog(
    row: TimesheetGridRow,
    date: LocalDate,
    hoursInput: String,
    notesInput: String,
    onHoursChange: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMM dd")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = date.format(dateFmt),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${row.projectCode} • ${row.activityName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = hoursInput,
                    onValueChange = { onHoursChange(it, notesInput) },
                    label = { Text("Hours Worked (0 - 24)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { onHoursChange(hoursInput, it) },
                    label = { Text("Task Notes / Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Save Hours")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
