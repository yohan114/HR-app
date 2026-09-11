package com.hr.app.ui.lifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CorporateFare
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.CareerMovementItem
import com.hr.client.model.CareerTimelineEvent
import com.hr.client.model.CareerTimelineResponse
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Enterprise Career Journey & Movements Screen benchmarked against PeoplesHR.
 * Features an interactive vertical milestone progression timeline, before/after
 * workstation diff snapshots, and automated cascade proposal/reversion engine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareerTimelineScreen(
    employeeId: UUID? = null,
    onNavigateBack: () -> Unit,
    viewModel: LifecycleViewModel = hiltViewModel(),
) {
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val movements by viewModel.movements.collectAsStateWithLifecycle()
    val totalGrowth by viewModel.totalSalaryGrowthPercentage.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "LK")).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Career Journey & Movements",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = timeline?.employeeName ?: "Employee Life Cycle",
                            style = MaterialTheme.typography.bodySmall,
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
                    IconButton(onClick = { viewModel.refresh(employeeId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openProposalDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(Radius.card),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Propose Movement", fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Status banners
                uiState.successBanner?.let { msg ->
                    BannerMessage(
                        message = msg,
                        isError = false,
                        onDismiss = viewModel::dismissBanner,
                    )
                }
                uiState.errorBanner?.let { msg ->
                    BannerMessage(
                        message = msg,
                        isError = true,
                        onDismiss = viewModel::dismissBanner,
                    )
                }

                // Summary Career Header Card
                timeline?.let { t ->
                    CareerHeaderCard(
                        timeline = t,
                        salaryGrowth = totalGrowth,
                        currencyFormatter = currencyFormatter,
                    )
                }

                // Segmented Tabs
                PrimaryTabRow(
                    selectedTabIndex = uiState.activeTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = uiState.activeTab == LifecycleTab.TIMELINE,
                        onClick = { viewModel.selectTab(LifecycleTab.TIMELINE) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                            ) {
                                Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Career Timeline (${timeline?.events?.size ?: 0})")
                            }
                        },
                    )
                    Tab(
                        selected = uiState.activeTab == LifecycleTab.MOVEMENTS,
                        onClick = { viewModel.selectTab(LifecycleTab.MOVEMENTS) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Movements & Cascade (${movements.size})")
                            }
                        },
                    )
                }

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (uiState.activeTab) {
                        LifecycleTab.TIMELINE -> {
                            CareerTimelineContent(
                                events = timeline?.events.orEmpty(),
                                currencyFormatter = currencyFormatter,
                            )
                        }
                        LifecycleTab.MOVEMENTS -> {
                            CareerMovementsContent(
                                movements = movements,
                                selectedType = uiState.movementTypeFilter,
                                selectedStatus = uiState.movementStatusFilter,
                                onSelectType = viewModel::filterByType,
                                onSelectStatus = viewModel::filterByStatus,
                                onApprove = viewModel::approveMovement,
                                onRevert = viewModel::openRevertDialog,
                                currencyFormatter = currencyFormatter,
                            )
                        }
                    }
                }
            }

            // Proposal Dialog
            if (uiState.showProposalDialog) {
                CareerProposalDialog(
                    uiState = uiState,
                    onDismiss = viewModel::closeProposalDialog,
                    onUpdateType = viewModel::updateProposalMovementType,
                    onUpdateDate = viewModel::updateProposalEffectiveDate,
                    onUpdateJustification = viewModel::updateProposalJustification,
                    onUpdateDept = viewModel::updateProposalDepartment,
                    onUpdateDesignation = viewModel::updateProposalDesignation,
                    onUpdateGrade = viewModel::updateProposalGrade,
                    onUpdateSalary = viewModel::updateProposalSalary,
                    onUpdateRemarks = viewModel::updateProposalRemarks,
                    onSubmit = {
                        val empId = employeeId ?: timeline?.employeeId ?: UUID.fromString("00000000-0000-0000-0000-000000000001")
                        viewModel.submitProposal(empId)
                    },
                )
            }

            // Revert Dialog
            if (uiState.showRevertDialog) {
                AlertDialog(
                    onDismissRequest = viewModel::closeRevertDialog,
                    title = { Text("Revert Career Movement", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            Text(
                                text = "Reverting this movement will roll back the employee's current designation, department, salary grade, and salary record to its prior snapshot.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = uiState.revertReasonText,
                                onValueChange = viewModel::updateRevertReason,
                                label = { Text("Reversion Reason *") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = viewModel::submitRevert,
                            enabled = !uiState.isSubmittingRevert,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) {
                            if (uiState.isSubmittingRevert) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onError)
                            } else {
                                Text("Confirm Reversion")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::closeRevertDialog) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CareerHeaderCard(
    timeline: CareerTimelineResponse,
    salaryGrowth: BigDecimal,
    currencyFormatter: NumberFormat,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.s4),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = timeline.employeeName.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Column {
                        Text(
                            text = timeline.employeeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = timeline.currentDesignation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "Grade ${timeline.currentGrade}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s3))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(Spacing.s3))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Department",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = timeline.currentDepartment,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column {
                    Text(
                        text = "Join Date / Tenure",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = timeline.joinDate.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Compensation Growth",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF1B7F4B),
                        )
                        Text(
                            text = "+${salaryGrowth}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B7F4B),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CareerTimelineContent(
    events: List<CareerTimelineEvent>,
    currencyFormatter: NumberFormat,
) {
    if (events.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No career progression events recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.s4, vertical = Spacing.s4),
    ) {
        items(events) { event ->
            MilestoneTimelineItem(
                event = event,
                isLast = event == events.last(),
                currencyFormatter = currencyFormatter,
            )
        }
        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun MilestoneTimelineItem(
    event: CareerTimelineEvent,
    isLast: Boolean,
    currencyFormatter: NumberFormat,
) {
    val (icon, iconBg, iconTint) = when (event.eventType) {
        CareerTimelineEvent.EventType.HIRE -> Triple(Icons.Default.Work, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
        CareerTimelineEvent.EventType.CONFIRMATION -> Triple(Icons.Default.CheckCircle, Color(0xFFE8F5E9), Color(0xFF2E7D32))
        CareerTimelineEvent.EventType.PROMOTION -> Triple(Icons.Default.MilitaryTech, Color(0xFFFFF8E1), Color(0xFFF57F17))
        CareerTimelineEvent.EventType.LATERAL_TRANSFER -> Triple(Icons.Default.CorporateFare, Color(0xFFE1F5FE), Color(0xFF0288D1))
        CareerTimelineEvent.EventType.SALARY_REVISION -> Triple(Icons.Default.TrendingUp, Color(0xFFE8F5E9), Color(0xFF2E7D32))
        else -> Triple(Icons.Default.Star, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // Vertical timeline marker and connector
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(110.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
            }
        }

        Spacer(modifier = Modifier.width(Spacing.s3))

        // Milestone Details Card
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = Spacing.s4),
            shape = RoundedCornerShape(Radius.card),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
        ) {
            Column(modifier = Modifier.padding(Spacing.s3)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = event.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                event.designationName?.let { des ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = des,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.s1))
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (event.baseSalary != null || event.changePercentage != null) {
                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        event.baseSalary?.let { sal ->
                            Text(
                                text = "Base: ${event.currency ?: "LKR"} ${currencyFormatter.format(sal).replace("LKR", "").trim()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        event.changePercentage?.let { pct ->
                            Surface(
                                shape = RoundedCornerShape(Radius.pill),
                                color = Color(0xFFE8F5E9),
                            ) {
                                Text(
                                    text = "+${pct}% increase",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
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
private fun CareerMovementsContent(
    movements: List<CareerMovementItem>,
    selectedType: CareerMovementItem.MovementType?,
    selectedStatus: CareerMovementItem.Status?,
    onSelectType: (CareerMovementItem.MovementType?) -> Unit,
    onSelectStatus: (CareerMovementItem.Status?) -> Unit,
    onApprove: (UUID) -> Unit,
    onRevert: (UUID) -> Unit,
    currencyFormatter: NumberFormat,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.s4, vertical = Spacing.s2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            item {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { onSelectType(null) },
                    label = { Text("All Types") },
                    shape = RoundedCornerShape(Radius.pill),
                )
            }
            item {
                FilterChip(
                    selected = selectedType == CareerMovementItem.MovementType.PROMOTION,
                    onClick = { onSelectType(CareerMovementItem.MovementType.PROMOTION) },
                    label = { Text("Promotions") },
                    shape = RoundedCornerShape(Radius.pill),
                )
            }
            item {
                FilterChip(
                    selected = selectedType == CareerMovementItem.MovementType.LATERAL_TRANSFER,
                    onClick = { onSelectType(CareerMovementItem.MovementType.LATERAL_TRANSFER) },
                    label = { Text("Transfers") },
                    shape = RoundedCornerShape(Radius.pill),
                )
            }
            item {
                FilterChip(
                    selected = selectedType == CareerMovementItem.MovementType.CONFIRMATION,
                    onClick = { onSelectType(CareerMovementItem.MovementType.CONFIRMATION) },
                    label = { Text("Confirmations") },
                    shape = RoundedCornerShape(Radius.pill),
                )
            }
        }

        if (movements.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No career movements match the filter criteria.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.s4, vertical = Spacing.s2),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            items(movements, key = { it.id }) { item ->
                CareerMovementCard(
                    movement = item,
                    onApprove = { onApprove(item.id) },
                    onRevert = { onRevert(item.id) },
                    currencyFormatter = currencyFormatter,
                )
            }
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CareerMovementCard(
    movement: CareerMovementItem,
    onApprove: () -> Unit,
    onRevert: () -> Unit,
    currencyFormatter: NumberFormat,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.s3)) {
            // Header: Movement Number, Type & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    Text(
                        text = movement.movementNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    MovementTypeBadge(type = movement.movementType)
                }
                MovementStatusPill(status = movement.status)
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            // Workstation Diff (Previous vs New)
            WorkstationDiffBlock(movement = movement, currencyFormatter = currencyFormatter)

            Spacer(modifier = Modifier.height(Spacing.s2))

            // Justification & Effective Date
            Text(
                text = "Justification: ${movement.justification}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(Spacing.s2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Effective: ${movement.effectiveDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (movement.cascadeApplied) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF1B7F4B),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Cascade Applied",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1B7F4B),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Action Buttons (Approve / Revert)
            if (movement.status == CareerMovementItem.Status.SUBMITTED || movement.status == CareerMovementItem.Status.DRAFT) {
                Spacer(modifier = Modifier.height(Spacing.s2))
                Button(
                    onClick = onApprove,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text("Approve & Trigger Cascade")
                }
            } else if (movement.status == CareerMovementItem.Status.APPLIED || movement.status == CareerMovementItem.Status.APPROVED) {
                Spacer(modifier = Modifier.height(Spacing.s2))
                OutlinedButton(
                    onClick = onRevert,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.control),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text("Revert & Rollback Cascade")
                }
            }
        }
    }
}

@Composable
private fun WorkstationDiffBlock(
    movement: CareerMovementItem,
    currencyFormatter: NumberFormat,
) {
    Surface(
        shape = RoundedCornerShape(Radius.control),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.s2)) {
            // Designation diff
            DiffRow(
                label = "Designation",
                prev = movement.prevDesignationName ?: "—",
                new = movement.newDesignationName ?: "—",
            )
            // Department diff
            DiffRow(
                label = "Department",
                prev = movement.prevDepartmentName ?: "—",
                new = movement.newDepartmentName ?: "—",
            )
            // Salary Grade diff
            DiffRow(
                label = "Grade",
                prev = movement.prevSalaryGradeCode ?: "—",
                new = movement.newSalaryGradeCode ?: "—",
            )
            // Base Salary diff
            if (movement.newBaseSalary != null) {
                val prevSal = movement.prevBaseSalary?.let { "LKR " + currencyFormatter.format(it).replace("LKR", "").trim() } ?: "—"
                val newSal = "LKR " + currencyFormatter.format(movement.newBaseSalary).replace("LKR", "").trim()
                DiffRow(label = "Base Salary", prev = prevSal, new = newSal)
            }
        }
    }
}

@Composable
private fun DiffRow(label: String, prev: String, new: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.3f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            modifier = Modifier.weight(0.7f),
        ) {
            Text(
                text = prev,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = new,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MovementTypeBadge(type: CareerMovementItem.MovementType) {
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = type.value.replace('_', ' '),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
        )
    }
}

@Composable
private fun MovementStatusPill(status: CareerMovementItem.Status) {
    val (bgColor, textColor) = when (status) {
        CareerMovementItem.Status.APPLIED -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        CareerMovementItem.Status.APPROVED -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
        CareerMovementItem.Status.SCHEDULED -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
        CareerMovementItem.Status.SUBMITTED -> Pair(Color(0xFFEDE7F6), Color(0xFF512DA8))
        CareerMovementItem.Status.REVERTED -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
        CareerMovementItem.Status.REJECTED -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = bgColor,
    ) {
        Text(
            text = status.value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
        )
    }
}

@Composable
private fun CareerProposalDialog(
    uiState: LifecycleUiState,
    onDismiss: () -> Unit,
    onUpdateType: (CareerMovementItem.MovementType) -> Unit,
    onUpdateDate: (LocalDate) -> Unit,
    onUpdateJustification: (String) -> Unit,
    onUpdateDept: (String) -> Unit,
    onUpdateDesignation: (String) -> Unit,
    onUpdateGrade: (String) -> Unit,
    onUpdateSalary: (String) -> Unit,
    onUpdateRemarks: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Propose Career Movement",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text(
                    text = "Initiate a promotion, lateral transfer, or salary adjustment with automated organizational cascade.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Movement Type Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    listOf(
                        CareerMovementItem.MovementType.PROMOTION to "Promotion",
                        CareerMovementItem.MovementType.LATERAL_TRANSFER to "Transfer",
                        CareerMovementItem.MovementType.CONFIRMATION to "Confirmation",
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = uiState.proposalMovementType == type,
                            onClick = { onUpdateType(type) },
                            label = { Text(label) },
                            shape = RoundedCornerShape(Radius.pill),
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.proposalDesignationName,
                    onValueChange = onUpdateDesignation,
                    label = { Text("Proposed Designation *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    OutlinedTextField(
                        value = uiState.proposalDepartmentName,
                        onValueChange = onUpdateDept,
                        label = { Text("Department") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.proposalSalaryGradeCode,
                        onValueChange = onUpdateGrade,
                        label = { Text("Grade (e.g. M1)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = uiState.proposalBaseSalary,
                    onValueChange = onUpdateSalary,
                    label = { Text("Proposed Base Salary (LKR)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = uiState.proposalJustification,
                    onValueChange = onUpdateJustification,
                    label = { Text("Justification / Business Case *") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !uiState.isSubmittingProposal,
            ) {
                if (uiState.isSubmittingProposal) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Submit Proposal")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !uiState.isSubmittingProposal) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun BannerMessage(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.control),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.s4, vertical = Spacing.s1),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
