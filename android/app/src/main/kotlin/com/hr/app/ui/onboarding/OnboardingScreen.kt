package com.hr.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    initialTab: String? = null,
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(initialTab) {
        if (!initialTab.isNullOrBlank()) {
            val tab = when (initialTab.lowercase()) {
                "team", "team_onboarding", "team_new_hires" -> OnboardingTab.TEAM_ONBOARDING
                "exit", "exit_notices", "offboarding", "resignation" -> OnboardingTab.EXIT_NOTICES
                "clearance", "clearance_matrix", "clearance_signoffs" -> OnboardingTab.CLEARANCE_SIGNOFFS
                else -> OnboardingTab.MY_ONBOARDING
            }
            viewModel.selectTab(tab)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Onboarding & Offboarding", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = state.activeTab.ordinal,
                edgePadding = Spacing.s4,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                OnboardingTab.entries.forEach { tab ->
                    Tab(
                        selected = state.activeTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label, fontWeight = FontWeight.Medium) }
                    )
                }
            }

            // User message feedback banner
            state.userMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s3),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearMessage() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (state.activeTab) {
                    OnboardingTab.MY_ONBOARDING -> MyOnboardingTabContent(
                        detail = state.myOnboarding,
                        onTaskClick = { viewModel.openTaskCompleteDialog(it) },
                    )
                    OnboardingTab.TEAM_ONBOARDING -> TeamOnboardingTabContent(
                        instances = state.teamInstances,
                    )
                    OnboardingTab.EXIT_NOTICES -> ExitNoticesTabContent(
                        notices = state.exitNotices,
                        onOpenSubmit = { viewModel.openExitNoticeDialog() },
                    )
                    OnboardingTab.CLEARANCE_SIGNOFFS -> ClearanceTabContent(
                        clearance = state.selectedClearance,
                        onTaskClick = { viewModel.openClearanceSignOffDialog(it) },
                    )
                }
            }
        }
    }

    // Modals
    if (state.isTaskCompleteDialogOpen && state.selectedTaskForCompletion != null) {
        CompleteTaskDialog(
            task = state.selectedTaskForCompletion!!,
            onDismiss = { viewModel.dismissTaskCompleteDialog() },
            onSubmit = { notes -> viewModel.submitTaskComplete(notes) }
        )
    }

    if (state.isExitNoticeDialogOpen) {
        SubmitExitNoticeDialog(
            exitTypes = state.exitTypes,
            onDismiss = { viewModel.dismissExitNoticeDialog() },
            onSubmit = { typeId, reasonId, date, remarks ->
                viewModel.submitExitNotice(typeId, reasonId, date, remarks)
            }
        )
    }

    if (state.isClearanceSignOffDialogOpen && state.selectedClearanceTask != null) {
        ClearanceSignOffDialog(
            task = state.selectedClearanceTask!!,
            onDismiss = { viewModel.dismissClearanceSignOffDialog() },
            onSubmit = { status, remarks, amount ->
                viewModel.submitClearanceSignOff(status, remarks, amount)
            }
        )
    }
}

// -----------------------------------------------------------------------------
// Tab 1: My Onboarding
// -----------------------------------------------------------------------------

@Composable
fun MyOnboardingTabContent(
    detail: OnboardingInstanceDetail?,
    onTaskClick: (OnboardingTaskItem) -> Unit,
) {
    if (detail == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active onboarding track assigned.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3)
    ) {
        // Track Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(Radius.card),
            ) {
                Column(modifier = Modifier.padding(Spacing.s4)) {
                    Text(
                        detail.profileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(Spacing.s1))
                    Text(
                        "Joined: ${detail.joinDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))} • Buddy: ${detail.buddyName ?: "Unassigned"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(Spacing.s3))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Progress: ${detail.progressPct.toInt()}%",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "${detail.tasks.count { it.status == OnboardingTaskItem.Status.COMPLETED }} of ${detail.tasks.size} tasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.s2))
                    LinearProgressIndicator(
                        progress = { (detail.progressPct.toDouble() / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                    )
                }
            }
        }

        // Section Title
        item {
            Text(
                "Action Items & Checklist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Spacing.s2)
            )
        }

        // Task Cards
        items(detail.tasks) { task ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = task.status != OnboardingTaskItem.Status.COMPLETED) { onTaskClick(task) },
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.s4),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.status == OnboardingTaskItem.Status.COMPLETED) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.s3))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            task.title,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        task.description?.let { desc ->
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.s1))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    task.ownerRole.name,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                "Due: ${task.dueDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        task.notes?.let { n ->
                            Spacer(modifier = Modifier.height(Spacing.s1))
                            Text("Note: $n", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 2: Team New Hires
// -----------------------------------------------------------------------------

@Composable
fun TeamOnboardingTabContent(
    instances: List<OnboardingInstanceSummary>,
) {
    if (instances.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No new hires currently in onboarding.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3)
    ) {
        items(instances) { inst ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(Spacing.s4)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            inst.employeeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when (inst.status) {
                                OnboardingInstanceSummary.Status.COMPLETED -> Color(0xFFE8F5E9)
                                OnboardingInstanceSummary.Status.IN_PROGRESS -> Color(0xFFE3F2FD)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                inst.status.name,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = when (inst.status) {
                                    OnboardingInstanceSummary.Status.COMPLETED -> Color(0xFF2E7D32)
                                    OnboardingInstanceSummary.Status.IN_PROGRESS -> Color(0xFF1565C0)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    Text(
                        "${inst.profileName} • Joined: ${inst.joinDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Progress: ${inst.progressPct.toInt()}%", style = MaterialTheme.typography.labelSmall)
                        Text("${inst.completedTasks} / ${inst.totalTasks} completed", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.height(Spacing.s1))
                    LinearProgressIndicator(
                        progress = { (inst.progressPct.toDouble() / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 3: Exit Notices
// -----------------------------------------------------------------------------

@Composable
fun ExitNoticesTabContent(
    notices: List<ExitNoticeItem>,
    onOpenSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.s4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Resignation & Exit Requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = onOpenSubmit,
                shape = RoundedCornerShape(Radius.control)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.s1))
                Text("Submit Notice")
            }
        }

        if (notices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("No exit notices filed.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(horizontal = Spacing.s4, vertical = Spacing.s2),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3)
            ) {
                items(notices) { notice ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.card),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(Spacing.s4)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    notice.noticeNumber,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = when (notice.status) {
                                        ExitNoticeItem.Status.APPROVED -> Color(0xFFE8F5E9)
                                        ExitNoticeItem.Status.SUBMITTED -> Color(0xFFFFF3E0)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        notice.status.name,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (notice.status) {
                                            ExitNoticeItem.Status.APPROVED -> Color(0xFF2E7D32)
                                            ExitNoticeItem.Status.SUBMITTED -> Color(0xFFE65100)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                            Text(
                                "${notice.employeeName} (${notice.employeeCode ?: ""}) • ${notice.exitTypeName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(Spacing.s2))
                            Text("Notice Date: ${notice.noticeDate} • Requested Last Day: ${notice.requestedLastWorkingDate}", style = MaterialTheme.typography.bodySmall)
                            notice.approvedLastWorkingDate?.let { appDate ->
                                Text("Approved Last Day: $appDate (Approved by: ${notice.approvedByName ?: "Manager"})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Color(0xFF2E7D32))
                            }
                            notice.remarks?.let { rem ->
                                Spacer(modifier = Modifier.height(Spacing.s1))
                                Text("Remarks: $rem", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 4: Clearance Matrix
// -----------------------------------------------------------------------------

@Composable
fun ClearanceTabContent(
    clearance: ClearanceDetailResponse?,
    onTaskClick: (ClearanceTaskItem) -> Unit,
) {
    if (clearance == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active clearance checklist available.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3)
    ) {
        // Summary Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(Spacing.s4)) {
                    Text(
                        "Exit Clearance Matrix — ${clearance.employeeName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cleared: ${clearance.clearedTasks} / ${clearance.totalTasks}", style = MaterialTheme.typography.bodyMedium)
                        Text("Pending: ${clearance.pendingTasks}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE65100))
                    }
                    if (clearance.totalRecoverableAmount > BigDecimal.ZERO) {
                        Spacer(modifier = Modifier.height(Spacing.s2))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFEBEE),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.s2),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(Spacing.s2))
                                Text(
                                    "Total Recoverable Dues: LKR %,.2f".format(clearance.totalRecoverableAmount.toDouble()),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFC62828)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Text("Departmental Handover Items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        items(clearance.tasks) { task ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTaskClick(task) },
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(Spacing.s4)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                task.department.name.replace("_", " "),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when (task.status) {
                                ClearanceTaskItem.Status.CLEARED -> Color(0xFFE8F5E9)
                                ClearanceTaskItem.Status.WAIVED -> Color(0xFFEDE7F6)
                                ClearanceTaskItem.Status.REJECTED -> Color(0xFFFFEBEE)
                                else -> Color(0xFFFFF3E0)
                            }
                        ) {
                            Text(
                                task.status.name,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = when (task.status) {
                                    ClearanceTaskItem.Status.CLEARED -> Color(0xFF2E7D32)
                                    ClearanceTaskItem.Status.WAIVED -> Color(0xFF512DA8)
                                    ClearanceTaskItem.Status.REJECTED -> Color(0xFFC62828)
                                    else -> Color(0xFFE65100)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Text(task.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    task.remarks?.let { rem ->
                        Text("Notes: $rem", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (task.recoverableAmount > BigDecimal.ZERO) {
                        Text("Recovery: LKR %,.2f".format(task.recoverableAmount.toDouble()), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Dialogs
// -----------------------------------------------------------------------------

@Composable
fun CompleteTaskDialog(
    task: OnboardingTaskItem,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var notes by remember { mutableStateOf(task.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete Checklist Task") },
        text = {
            Column {
                Text(task.title, fontWeight = FontWeight.SemiBold)
                task.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(Spacing.s3))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Completion Notes / Reference") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(notes) }) {
                Text("Mark Complete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SubmitExitNoticeDialog(
    exitTypes: List<ExitTypeItem>,
    onDismiss: () -> Unit,
    onSubmit: (UUID, UUID?, LocalDate, String?) -> Unit,
) {
    var selectedTypeId by remember { mutableStateOf(exitTypes.firstOrNull()?.id ?: UUID.randomUUID()) }
    var requestedDays by remember { mutableStateOf("30") }
    var remarks by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Resignation Notice") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Text("Specify your planned departure terms.", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = requestedDays,
                    onValueChange = { requestedDays = it },
                    label = { Text("Notice Period (Days)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Reason & Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val days = requestedDays.toLongOrNull() ?: 30L
                val targetDate = LocalDate.now().plusDays(days)
                onSubmit(selectedTypeId, null, targetDate, remarks)
            }) {
                Text("Submit Notice")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ClearanceSignOffDialog(
    task: ClearanceTaskItem,
    onDismiss: () -> Unit,
    onSubmit: (ClearanceTaskStatusUpdateRequest.Status, String?, BigDecimal?) -> Unit,
) {
    var selectedStatus by remember { mutableStateOf(ClearanceTaskStatusUpdateRequest.Status.CLEARED) }
    var remarks by remember { mutableStateOf(task.remarks ?: "") }
    var amountStr by remember { mutableStateOf(if (task.recoverableAmount > BigDecimal.ZERO) task.recoverableAmount.toPlainString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Departmental Sign-off") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Text(task.title, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Button(
                        onClick = { selectedStatus = ClearanceTaskStatusUpdateRequest.Status.CLEARED },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedStatus == ClearanceTaskStatusUpdateRequest.Status.CLEARED) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) { Text("Clear") }
                    Button(
                        onClick = { selectedStatus = ClearanceTaskStatusUpdateRequest.Status.WAIVED },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedStatus == ClearanceTaskStatusUpdateRequest.Status.WAIVED) Color(0xFF512DA8) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) { Text("Waive") }
                    Button(
                        onClick = { selectedStatus = ClearanceTaskStatusUpdateRequest.Status.REJECTED },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedStatus == ClearanceTaskStatusUpdateRequest.Status.REJECTED) Color(0xFFC62828) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) { Text("Reject") }
                }

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Sign-off Remarks") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Recoverable Amount (LKR)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amountStr.toDoubleOrNull()?.let { BigDecimal.valueOf(it) }
                onSubmit(selectedStatus, remarks, amt)
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
