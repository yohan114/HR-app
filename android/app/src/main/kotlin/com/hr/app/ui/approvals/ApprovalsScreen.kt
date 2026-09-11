package com.hr.app.ui.approvals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.ApprovalItem
import com.hr.client.model.AttendanceApprovalDetails
import com.hr.client.model.LeaveApprovalDetails

/**
 * Mobile Manager Approvals Screen (TopLevelDestination.APPROVALS).
 *
 * Implements role-adaptive approval inbox with:
 * - Category filter tabs (All, Leave, Attendance)
 * - Urgency badges & team coverage conflict warnings
 * - Remaining leave balances & missed clock-in/out traces
 * - Offline outbox queue banner and single-tap optimistic approval / rejection
 */
@Composable
fun ApprovalsScreen(
    viewModel: ApprovalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.successSnackbarMessage) {
        state.successSnackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Header bar
            ApprovalsHeader(
                totalCount = state.totalCount,
                refreshing = state.refreshing,
                onRefresh = { viewModel.refresh(initial = false) },
            )

            // Category Filter Tabs
            ApprovalsFilterRow(
                selectedTab = state.selectedTab,
                totalCount = state.totalCount,
                leaveCount = state.leaveCount,
                attendanceCount = state.attendanceCount,
                onSelectTab = { viewModel.selectTab(it) },
            )

            // Offline sync indicator
            if (state.pendingOutboxCount > 0) {
                OfflineSyncBanner(pendingCount = state.pendingOutboxCount)
            }

            // Error banner if any
            if (state.error != null) {
                ErrorBanner(
                    message = state.error!!,
                    onRetry = { viewModel.refresh(initial = false) },
                )
            }

            // Content body
            when {
                state.loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                state.filteredItems.isEmpty() -> {
                    EmptyApprovalsInbox(
                        filter = state.selectedTab,
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = Spacing.s4,
                            vertical = Spacing.s3,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                    ) {
                        items(
                            items = state.filteredItems,
                            key = { it.id },
                        ) { item ->
                            ApprovalCard(
                                item = item,
                                isActionInProgress = state.actionInProgressId == item.id,
                                onApprove = { viewModel.approve(item) },
                                onReject = { viewModel.promptReject(item) },
                                onOpenDetail = { viewModel.openDetail(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Rejection Remarks Dialog
    if (state.rejectionTargetItem != null) {
        val target = state.rejectionTargetItem!!
        AlertDialog(
            onDismissRequest = { viewModel.cancelReject() },
            title = {
                Text(
                    text = "Reject Request",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Text(
                        text = "Reject ${target.title} submitted by ${target.requesterName}?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = state.rejectionRemarks,
                        onValueChange = { viewModel.setRejectionRemarks(it) },
                        label = { Text("Reason / Manager Notes (Optional)") },
                        placeholder = { Text("e.g. Urgent project deliverable deadline") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmReject() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Reject", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.cancelReject() }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Full Details Modal Dialog
    if (state.selectedItemForDetail != null) {
        val item = state.selectedItemForDetail!!
        ApprovalDetailDialog(
            item = item,
            onDismiss = { viewModel.closeDetail() },
            onApprove = {
                viewModel.approve(item)
            },
            onReject = {
                viewModel.closeDetail()
                viewModel.promptReject(item)
            },
        )
    }
}

@Composable
private fun ApprovalsHeader(
    totalCount: Int,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(
                text = "Approvals",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (totalCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "$totalCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = Spacing.s2, vertical = Spacing.s1 / 2),
                    )
                }
            }
        }

        IconButton(
            onClick = onRefresh,
            enabled = !refreshing,
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Approvals",
                )
            }
        }
    }
}

@Composable
private fun ApprovalsFilterRow(
    selectedTab: ApprovalFilterTab,
    totalCount: Int,
    leaveCount: Int,
    attendanceCount: Int,
    onSelectTab: (ApprovalFilterTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.s4, vertical = Spacing.s1),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        FilterChip(
            selected = selectedTab == ApprovalFilterTab.ALL,
            onClick = { onSelectTab(ApprovalFilterTab.ALL) },
            label = { Text("All ($totalCount)") },
            shape = RoundedCornerShape(Radius.pill),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        FilterChip(
            selected = selectedTab == ApprovalFilterTab.LEAVE,
            onClick = { onSelectTab(ApprovalFilterTab.LEAVE) },
            label = { Text("Leave ($leaveCount)") },
            shape = RoundedCornerShape(Radius.pill),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFD3E4F7),
                selectedLabelColor = Color(0xFF001D34),
            ),
        )
        FilterChip(
            selected = selectedTab == ApprovalFilterTab.ATTENDANCE,
            onClick = { onSelectTab(ApprovalFilterTab.ATTENDANCE) },
            label = { Text("Attendance ($attendanceCount)") },
            shape = RoundedCornerShape(Radius.pill),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFFFFE0B2),
                selectedLabelColor = Color(0xFFE65100),
            ),
        )
    }
}

@Composable
private fun OfflineSyncBanner(pendingCount: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.s4, vertical = Spacing.s1),
        shape = RoundedCornerShape(Radius.control),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s3, vertical = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "$pendingCount decision(s) queued · will sync with server",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
        shape = RoundedCornerShape(Radius.control),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            TextButton(onClick = onRetry) {
                Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    item: ApprovalItem,
    isActionInProgress: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val isLeave = item.type.equals("LEAVE", ignoreCase = true)
    val isUrgent = item.urgency == ApprovalItem.Urgency.URGENT || item.urgency == ApprovalItem.Urgency.OVERDUE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .clickable { onOpenDetail() },
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            // Header Tag Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    // Type Badge
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = if (isLeave) Color(0xFFD3E4F7) else Color(0xFFFFE0B2),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                            modifier = Modifier.padding(horizontal = Spacing.s2, vertical = Spacing.s1 / 2),
                        ) {
                            Icon(
                                imageVector = if (isLeave) Icons.Default.DateRange else Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = if (isLeave) Color(0xFF0D47A1) else Color(0xFFE65100),
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = if (isLeave) "LEAVE" else "ATTENDANCE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isLeave) Color(0xFF0D47A1) else Color(0xFFE65100),
                            )
                        }
                    }

                    // Urgency Badge
                    if (isUrgent) {
                        Surface(
                            shape = RoundedCornerShape(Radius.pill),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = "URGENT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = Spacing.s2, vertical = Spacing.s1 / 2),
                            )
                        }
                    }
                }

                // Submitted relative time
                Text(
                    text = formatSubmittedTime(item.submittedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Requester Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = getInitials(item.requesterName),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.requesterName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = listOfNotNull(item.requesterDesignation, item.departmentName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Specific Details Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.control),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.s3),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    if (isLeave && item.leaveDetails != null) {
                        LeaveDetailsSnippet(item.leaveDetails)
                    } else if (!isLeave && item.attendanceDetails != null) {
                        AttendanceDetailsSnippet(item.attendanceDetails)
                    } else {
                        Text(
                            text = item.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Quick Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = !isActionInProgress,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.control),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text("Reject")
                }

                Button(
                    onClick = onApprove,
                    enabled = !isActionInProgress,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.control),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B7F4B)),
                ) {
                    if (isActionInProgress) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.s1))
                        Text("Approve", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveDetailsSnippet(details: LeaveApprovalDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${details.leaveTypeName} · ${details.workingDays} day(s)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${details.startDate} → ${details.endDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "\"${details.reason}\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            Text(
                text = "Remaining balance:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${details.employeeBalanceDays} days",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (details.employeeBalanceDays >= details.workingDays) Color(0xFF1B7F4B) else MaterialTheme.colorScheme.error,
            )
        }

        if (!details.teamCoverageWarning.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(Radius.control),
                color = Color(0xFFFFF3E0),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s1 / 2),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.s2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = details.teamCoverageWarning,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendanceDetailsSnippet(details: AttendanceApprovalDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${details.requestedPunchType} at ${details.requestedTime}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = details.workDate.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!details.shiftName.isNullOrBlank()) {
            Text(
                text = "Shift: ${details.shiftName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "\"${details.reason}\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyApprovalsInbox(
    filter: ApprovalFilterTab,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            modifier = Modifier.padding(Spacing.s6),
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFE8F5E9),
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF1B7F4B),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Text(
                text = "All Caught Up!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = when (filter) {
                    ApprovalFilterTab.ALL -> "No pending requests awaiting your decision."
                    ApprovalFilterTab.LEAVE -> "No pending leave applications for your direct reports."
                    ApprovalFilterTab.ATTENDANCE -> "No attendance regularisation requests to review."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.s4),
            )
        }
    }
}

@Composable
private fun ApprovalDetailDialog(
    item: ApprovalItem,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val isLeave = item.type.equals("LEAVE", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Requested by ${item.requesterName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.control),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.s3),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
                    ) {
                        Text(
                            text = "EMPLOYEE DETAILS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${item.requesterName} (${item.requesterDesignation.orEmpty()})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Department: ${item.departmentName ?: "Engineering"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isLeave && item.leaveDetails != null) {
                    LeaveDetailsSnippet(item.leaveDetails)
                } else if (!isLeave && item.attendanceDetails != null) {
                    AttendanceDetailsSnippet(item.attendanceDetails)
                }

                Surface(
                    shape = RoundedCornerShape(Radius.control),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(Spacing.s3)) {
                        Text(
                            text = "Submitted: ${item.submittedAt}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Urgency: ${item.urgency.value}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.urgency == ApprovalItem.Urgency.URGENT || item.urgency == ApprovalItem.Urgency.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B7F4B)),
            ) {
                Text("Approve", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onReject,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Reject")
            }
        },
    )
}

private fun getInitials(name: String): String {
    val parts = name.trim().split("\\s+".toRegex())
    return when {
        parts.size >= 2 -> "${parts[0].firstOrNull()?.uppercaseChar() ?: ""}${parts[1].firstOrNull()?.uppercaseChar() ?: ""}"
        parts.isNotEmpty() -> parts[0].take(2).uppercase()
        else -> "HR"
    }
}

private fun formatSubmittedTime(time: java.time.OffsetDateTime): String {
    return runCatching {
        val instant = time.toInstant()
        val now = java.time.Instant.now()
        val duration = java.time.Duration.between(instant, now)
        when {
            duration.toMinutes() < 60 -> "${duration.toMinutes().coerceAtLeast(1)}m ago"
            duration.toHours() < 24 -> "${duration.toHours()}h ago"
            duration.toDays() < 7 -> "${duration.toDays()}d ago"
            else -> time.toLocalDate().toString()
        }
    }.getOrElse { "Recently" }
}
