package com.hr.app.ui.leave

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hr.app.ui.theme.LightColors
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.LeaveApplicationItem
import com.hr.client.model.LeaveBalanceItem
import com.hr.client.model.LeaveLedgerEntryItem
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveScreen(
    viewModel: LeaveViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Leave & Balances",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = "Leave Year ${state.leaveYear}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.pendingOutboxCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = Spacing.s2, vertical = 2.dp),
                        ) {
                            Text(
                                text = "${state.pendingOutboxCount} queued",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Tab(
                    selected = state.selectedTab == LeaveTab.BALANCES_APPLY,
                    onClick = { viewModel.selectTab(LeaveTab.BALANCES_APPLY) },
                    text = { Text("Balances") },
                )
                Tab(
                    selected = state.selectedTab == LeaveTab.MY_APPLICATIONS,
                    onClick = { viewModel.selectTab(LeaveTab.MY_APPLICATIONS) },
                    text = { Text("My Requests") },
                )
                Tab(
                    selected = state.selectedTab == LeaveTab.STATEMENT_LEDGER,
                    onClick = { viewModel.selectTab(LeaveTab.STATEMENT_LEDGER) },
                    text = { Text("Ledger") },
                )
            }

            if (state.loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (state.selectedTab) {
                    LeaveTab.BALANCES_APPLY -> {
                        BalancesContent(
                            state = state,
                            onApplyClick = { typeId -> viewModel.openApplyDialog(typeId) },
                        )
                    }
                    LeaveTab.MY_APPLICATIONS -> {
                        ApplicationsContent(
                            state = state,
                            onFilterSelected = { viewModel.filterApplications(it) },
                            onCancelClick = { viewModel.openCancelDialog(it) },
                            onApplyClick = { viewModel.openApplyDialog() },
                        )
                    }
                    LeaveTab.STATEMENT_LEDGER -> {
                        LedgerContent(
                            state = state,
                            onFilterTypeSelected = { viewModel.filterLedger(it) },
                        )
                    }
                }
            }
        }
    }

    // Apply Leave Dialog
    if (state.isApplyDialogOpen) {
        ApplyLeaveDialog(
            state = state,
            onDismiss = { viewModel.dismissApplyDialog() },
            onSelectType = { viewModel.updateApplyLeaveType(it) },
            onDatesChanged = { start, end -> viewModel.updateApplyDates(start, end) },
            onPortionChanged = { viewModel.updateApplyDayPortion(it) },
            onReasonChanged = { viewModel.updateApplyReason(it) },
            onSubmit = { viewModel.submitApplication() },
        )
    }

    // Cancel Leave Confirmation Dialog
    state.applicationToCancel?.let { app ->
        CancelLeaveDialog(
            application = app,
            reason = state.cancelReason,
            isCancelling = state.isCancellingApplication,
            onReasonChange = { viewModel.updateCancelReason(it) },
            onConfirm = { viewModel.confirmCancelApplication() },
            onDismiss = { viewModel.dismissCancelDialog() },
        )
    }
}

// ---------------------------------------------------------------------------
// Balances & Apply Tab
// ---------------------------------------------------------------------------

@Composable
private fun BalancesContent(
    state: LeaveState,
    onApplyClick: (String?) -> Unit,
) {
    val totalAvailable = state.balances.sumOf { it.availableDays }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        item {
            // Header summary banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.s4),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Total Available Leave",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "$totalAvailable Days",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Button(
                        onClick = { onApplyClick(null) },
                        shape = RoundedCornerShape(Radius.control),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.s1))
                        Text("Apply")
                    }
                }
            }
        }

        item {
            Text(
                text = "Entitlement Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Spacing.s2),
            )
        }

        items(state.balances) { balance ->
            BalanceCard(
                balance = balance,
                onApplyClick = { onApplyClick(balance.leaveTypeId) },
            )
        }
    }
}

@Composable
private fun BalanceCard(
    balance: LeaveBalanceItem,
    onApplyClick: () -> Unit,
) {
    val cardColor = parseColor(balance.color)
    val usedRatio = if (balance.entitledDays > BigDecimal.ZERO) {
        ((balance.takenDays + balance.pendingDays).toDouble() / balance.entitledDays.toDouble()).toFloat().coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(cardColor),
                    )
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Text(
                        text = balance.leaveTypeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(cardColor.copy(alpha = 0.15f))
                        .padding(horizontal = Spacing.s2, vertical = 2.dp),
                ) {
                    Text(
                        text = balance.leaveTypeCode,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = cardColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s3))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "${balance.availableDays} Days",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Available to take",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedButton(
                    onClick = onApplyClick,
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Text("Apply")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s3))

            // Progress bar
            LinearProgressIndicator(
                progress = { usedRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = cardColor,
                trackColor = cardColor.copy(alpha = 0.2f),
            )

            Spacer(modifier = Modifier.height(Spacing.s2))

            // Pills breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricChip("Entitled", "${balance.entitledDays}d")
                MetricChip("Accrued", "${balance.accruedDays}d")
                MetricChip("Taken", "${balance.takenDays}d")
                MetricChip("Pending", "${balance.pendingDays}d")
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Applications Tab
// ---------------------------------------------------------------------------

@Composable
private fun ApplicationsContent(
    state: LeaveState,
    onFilterSelected: (String?) -> Unit,
    onCancelClick: (LeaveApplicationItem) -> Unit,
    onApplyClick: () -> Unit,
) {
    val filters = listOf(
        Pair("All", null),
        Pair("Submitted", "SUBMITTED"),
        Pair("Approved", "APPROVED"),
        Pair("Cancelled", "CANCELLED"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
    ) {
        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            modifier = Modifier.padding(bottom = Spacing.s2),
        ) {
            items(filters) { (title, statusKey) ->
                val isSelected = state.applicationsFilter == statusKey
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterSelected(statusKey) },
                    label = { Text(title) },
                )
            }
        }

        if (state.applicationsLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.applications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.s8),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Text(
                        text = "No leave applications found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(Spacing.s1))
                    Text(
                        text = "Plan ahead and request leave whenever you need time off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.s4))
                    Button(
                        onClick = onApplyClick,
                        shape = RoundedCornerShape(Radius.control),
                    ) {
                        Text("Request Leave")
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.applications) { app ->
                    ApplicationCard(
                        application = app,
                        onCancelClick = { onCancelClick(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplicationCard(
    application: LeaveApplicationItem,
    onCancelClick: () -> Unit,
) {
    val status = application.status.value
    val statusColor = when (status) {
        "APPROVED" -> LightColors.success
        "SUBMITTED" -> Color(0xFFD97706)
        "REJECTED" -> LightColors.danger
        "CANCELLED", "WITHDRAWN" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }

    val canCancel = status == "SUBMITTED" || status == "APPROVED"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = application.leaveTypeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = Spacing.s2, vertical = 2.dp),
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            Text(
                text = "${formatDate(application.startDate)} - ${formatDate(application.endDate)} (${application.totalDays} days • ${application.dayPortion.value})",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (application.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.s1))
                Text(
                    text = "Reason: ${application.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Applied ${application.submittedAt.toLocalDate()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                if (canCancel) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        shape = RoundedCornerShape(Radius.control),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Cancel Request")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Statement Ledger Tab
// ---------------------------------------------------------------------------

@Composable
private fun LedgerContent(
    state: LeaveState,
    onFilterTypeSelected: (String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
    ) {
        // Audit info card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.s3),
            shape = RoundedCornerShape(Radius.card),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(Spacing.s2))
                Column {
                    Text(
                        text = "Immutable Audit Ledger",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Every credit and debit is itemized with cryptographic audit traceability.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Leave type filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            modifier = Modifier.padding(bottom = Spacing.s2),
        ) {
            item {
                FilterChip(
                    selected = state.ledgerFilterTypeId == null,
                    onClick = { onFilterTypeSelected(null) },
                    label = { Text("All Types") },
                )
            }
            items(state.balances) { b ->
                FilterChip(
                    selected = state.ledgerFilterTypeId == b.leaveTypeId,
                    onClick = { onFilterTypeSelected(b.leaveTypeId) },
                    label = { Text(b.leaveTypeName) },
                )
            }
        }

        if (state.ledgerLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (state.ledger.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No ledger entries found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.ledger) { entry ->
                    LedgerEntryCard(entry)
                }
            }
        }
    }
}

@Composable
private fun LedgerEntryCard(entry: LeaveLedgerEntryItem) {
    val isCredit = entry.daysCredited > BigDecimal.ZERO
    val deltaText = if (isCredit) "+${entry.daysCredited}d" else "-${entry.daysDebited}d"
    val deltaColor = if (isCredit) LightColors.success else LightColors.danger

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s3),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.leaveTypeName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = Spacing.s2, vertical = 2.dp),
                    ) {
                        Text(
                            text = entry.eventType.value,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${entry.date} • ${entry.notes ?: entry.referenceId ?: "System entry"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = deltaText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = deltaColor,
                )
                Text(
                    text = "Bal: ${entry.balanceAfter}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Apply Leave Dialog
// ---------------------------------------------------------------------------

@Composable
private fun ApplyLeaveDialog(
    state: LeaveState,
    onDismiss: () -> Unit,
    onSelectType: (String) -> Unit,
    onDatesChanged: (LocalDate, LocalDate) -> Unit,
    onPortionChanged: (String) -> Unit,
    onReasonChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Apply for Leave",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    Text(
                        text = "Leave Category",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                        modifier = Modifier.padding(top = Spacing.s1),
                    ) {
                        items(state.balances) { b ->
                            val isSelected = state.applyLeaveTypeId == b.leaveTypeId
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectType(b.leaveTypeId) },
                                label = { Text("${b.leaveTypeName} (${b.availableDays}d)") },
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Duration Dates",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.s1),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        DateAdjuster(
                            label = "Start Date",
                            date = state.applyStartDate,
                            onDateChange = { newStart ->
                                val newEnd = if (newStart.isAfter(state.applyEndDate)) newStart else state.applyEndDate
                                onDatesChanged(newStart, newEnd)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        DateAdjuster(
                            label = "End Date",
                            date = state.applyEndDate,
                            onDateChange = { newEnd ->
                                val newStart = if (newEnd.isBefore(state.applyStartDate)) newEnd else state.applyStartDate
                                onDatesChanged(newStart, newEnd)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    Text(
                        text = "Day Portion",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.s1),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        listOf(
                            Pair("Full Day", "FULL_DAY"),
                            Pair("First Half", "FIRST_HALF"),
                            Pair("Second Half", "SECOND_HALF"),
                        ).forEach { (title, key) ->
                            FilterChip(
                                selected = state.applyDayPortion == key,
                                onClick = { onPortionChanged(key) },
                                label = { Text(title) },
                            )
                        }
                    }
                }

                item {
                    // Live Projection Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.card),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.s3),
                        ) {
                            Text(
                                text = "Live Balance Projection",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(Spacing.s1))

                            if (state.eligibilityLoading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = Spacing.s1),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(Spacing.s2))
                                    Text("Calculating working days...", style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (state.eligibility != null) {
                                val elig = state.eligibility
                                val isEligible = elig.eligible
                                val statusBg = if (isEligible) LightColors.success.copy(alpha = 0.15f) else LightColors.danger.copy(alpha = 0.15f)
                                val statusFg = if (isEligible) LightColors.success else LightColors.danger

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Working days requested: ${elig.workingDaysRequested}d",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "Balance: ${elig.balanceAvailable}d -> ${elig.remainingAfter}d",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                Spacer(modifier = Modifier.height(Spacing.s2))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(Radius.control))
                                        .background(statusBg)
                                        .padding(Spacing.s2),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (isEligible) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = statusFg,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(Spacing.s1))
                                        Text(
                                            text = if (isEligible) "Eligible for submission" else elig.reasons.firstOrNull() ?: "Ineligible",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = statusFg,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = state.applyReason,
                        onValueChange = onReasonChanged,
                        label = { Text("Reason for Leave") },
                        placeholder = { Text("e.g. Personal errand, family function") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !state.isSubmittingApplication && state.applyReason.isNotBlank() && (state.eligibility?.eligible != false),
                shape = RoundedCornerShape(Radius.control),
            ) {
                if (state.isSubmittingApplication) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(Spacing.s1))
                }
                Text("Submit Request")
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
private fun DateAdjuster(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.control),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s2),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(
                text = date.format(DateTimeFormatter.ofPattern("dd MMM")),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onDateChange(date.minusDays(1)) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Text("-", fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { onDateChange(date.plusDays(1)) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Text("+", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cancel Leave Dialog
// ---------------------------------------------------------------------------

@Composable
private fun CancelLeaveDialog(
    application: LeaveApplicationItem,
    reason: String,
    isCancelling: Boolean,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Cancel Leave Application", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text(
                    text = "Are you sure you want to cancel your ${application.leaveTypeName} from ${formatDate(application.startDate)} to ${formatDate(application.endDate)} (${application.totalDays} days)?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "The booked days will be restored back to your available balance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = onReasonChange,
                    label = { Text("Reason for cancellation (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isCancelling,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                if (isCancelling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                    Spacer(modifier = Modifier.width(Spacing.s1))
                }
                Text("Confirm Cancellation")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Request")
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun parseColor(hex: String): Color {
    return runCatching {
        val clean = hex.removePrefix("#")
        val colorInt = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000 or colorInt)
        } else {
            Color(colorInt)
        }
    }.getOrDefault(Color(0xFF1B5E9C))
}

private fun formatDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
}
