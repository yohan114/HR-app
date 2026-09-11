package com.hr.app.ui.expense

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hr.app.ui.theme.LightColors
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.ExpenseCategoryItem
import com.hr.client.model.ExpenseClaimDetailResponse
import com.hr.client.model.ExpenseClaimItem
import com.hr.client.model.ExpenseClaimLineItem
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(
    viewModel: ExpenseViewModel,
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
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Expense Claims",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = "Reimbursements & Multi-Line Claims",
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
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = LightColors.brandPrimary,
            ) {
                Tab(
                    selected = state.selectedTab == ExpenseTab.MY_CLAIMS,
                    onClick = { viewModel.setTab(ExpenseTab.MY_CLAIMS) },
                    text = {
                        Text(
                            "My Claims",
                            fontWeight = if (state.selectedTab == ExpenseTab.MY_CLAIMS) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
                Tab(
                    selected = state.selectedTab == ExpenseTab.SUBMIT_CLAIM,
                    onClick = { viewModel.setTab(ExpenseTab.SUBMIT_CLAIM) },
                    text = {
                        Text(
                            "Submit Claim",
                            fontWeight = if (state.selectedTab == ExpenseTab.SUBMIT_CLAIM) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
                Tab(
                    selected = state.selectedTab == ExpenseTab.POLICY_LIMITS,
                    onClick = { viewModel.setTab(ExpenseTab.POLICY_LIMITS) },
                    text = {
                        Text(
                            "Policy & Limits",
                            fontWeight = if (state.selectedTab == ExpenseTab.POLICY_LIMITS) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }

            when (state.selectedTab) {
                ExpenseTab.MY_CLAIMS -> MyClaimsContent(
                    state = state,
                    onOpenDetails = { viewModel.openClaimDetails(it) },
                    onOpenCancel = { viewModel.openCancelDialog(it) },
                    onNavigateToSubmit = { viewModel.setTab(ExpenseTab.SUBMIT_CLAIM) },
                    onFilterStatus = { viewModel.filterByStatus(it) },
                )
                ExpenseTab.SUBMIT_CLAIM -> SubmitClaimContent(
                    state = state,
                    onTitleChange = { viewModel.setDraftTitle(it) },
                    onRemarksChange = { viewModel.setDraftRemarks(it) },
                    onAddLine = { viewModel.addLine() },
                    onRemoveLine = { viewModel.removeLine(it) },
                    onUpdateLine = { id, block -> viewModel.updateLine(id, block) },
                    onSimulateOcr = { lineId, sample -> viewModel.simulateReceiptScan(lineId, sample) },
                    onSubmit = { viewModel.submitClaim() },
                )
                ExpenseTab.POLICY_LIMITS -> PolicyLimitsContent(
                    state = state,
                    onSelectCategory = { cat ->
                        viewModel.updateLine(state.draftLines.first().id) { line ->
                            line.copy(categoryId = cat.id)
                        }
                        viewModel.setTab(ExpenseTab.SUBMIT_CLAIM)
                    },
                )
            }
        }
    }

    // Itemized Claim Detail Modal
    if (state.isDetailModalOpen && state.selectedClaimDetail != null) {
        ExpenseClaimDetailDialog(
            detail = state.selectedClaimDetail!!,
            onDismiss = { viewModel.closeClaimDetails() },
            onCancelClaim = { claim ->
                viewModel.closeClaimDetails()
                viewModel.openCancelDialog(claim)
            },
        )
    }

    // Cancel Claim Dialog
    if (state.isCancelDialogOpen && state.claimToCancel != null) {
        AlertDialog(
            onDismissRequest = { viewModel.closeCancelDialog() },
            title = { Text("Cancel Expense Claim") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Text(
                        "Are you sure you want to cancel claim ${state.claimToCancel?.claimNumber}? This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = state.cancelReason,
                        onValueChange = { viewModel.setCancelReason(it) },
                        label = { Text("Reason for cancellation (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmCancelClaim() },
                    colors = ButtonDefaults.buttonColors(containerColor = LightColors.danger),
                ) {
                    Text("Confirm Cancellation")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeCancelDialog() }) {
                    Text("Keep Claim")
                }
            },
        )
    }
}

@Composable
private fun MyClaimsContent(
    state: ExpenseState,
    onOpenDetails: (ExpenseClaimItem) -> Unit,
    onOpenCancel: (ExpenseClaimItem) -> Unit,
    onNavigateToSubmit: () -> Unit,
    onFilterStatus: (String?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        item {
            Spacer(modifier = Modifier.height(Spacing.s2))
            // Hero KPI Metrics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                ),
            ) {
                Column(modifier = Modifier.padding(Spacing.s3)) {
                    Text(
                        text = "REIMBURSEMENT OVERVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "LKR ${formatMoney(state.totalClaimedAmount)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = LightColors.brandPrimary,
                            )
                            Text(
                                text = "Total Claimed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "LKR ${formatMoney(state.totalReimbursedAmount)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = LightColors.success,
                            )
                            Text(
                                text = "Reimbursed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (state.pendingCount > 0) {
                        Spacer(modifier = Modifier.height(Spacing.s2))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(LightColors.warning.copy(alpha = 0.15f))
                                .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = LightColors.warning,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(Spacing.s1))
                            Text(
                                text = "${state.pendingCount} claim(s) awaiting approval",
                                style = MaterialTheme.typography.labelSmall,
                                color = LightColors.warning,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        // Status Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val filters = listOf(
                    null to "All",
                    "SUBMITTED" to "Submitted",
                    "APPROVED" to "Approved",
                    "REIMBURSED" to "Reimbursed",
                    "CANCELLED" to "Cancelled",
                    "REJECTED" to "Rejected",
                )
                items(filters) { (status, label) ->
                    FilterChip(
                        selected = state.selectedStatusFilter == status,
                        onClick = { onFilterStatus(status) },
                        label = { Text(label) },
                    )
                }
            }
        }

        if (state.claims.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.s6),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Text(
                        text = "No Expense Claims Found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Submit travel, meals, or out-of-pocket expenses for reimbursement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.s4))
                    Button(onClick = onNavigateToSubmit) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.s1))
                        Text("Submit Expense Claim")
                    }
                }
            }
        } else {
            items(state.claims, key = { it.id }) { claim ->
                ExpenseClaimCard(
                    claim = claim,
                    onOpenDetails = { onOpenDetails(claim) },
                    onOpenCancel = { onOpenCancel(claim) },
                )
            }
        }

        item { Spacer(modifier = Modifier.height(Spacing.s4)) }
    }
}

@Composable
private fun ExpenseClaimCard(
    claim: ExpenseClaimItem,
    onOpenDetails: () -> Unit,
    onOpenCancel: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.s3)
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = claim.claimNumber,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = LightColors.brandPrimary,
                    )
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Text(
                        text = claim.claimDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(status = claim.status.value)
            }

            Spacer(modifier = Modifier.height(Spacing.s2))
            Text(
                text = claim.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )

            if (!claim.remarks.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.s1))
                Text(
                    text = claim.remarks,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text(
                        text = "${claim.lineCount} item(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${claim.currency} ${formatMoney(claim.totalAmount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (claim.approvedAmount != null && claim.approvedAmount != claim.totalAmount) {
                        Text(
                            text = "Approved: ${claim.currency} ${formatMoney(claim.approvedAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = LightColors.success,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s2))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (claim.status == ExpenseClaimItem.Status.SUBMITTED) {
                    TextButton(
                        onClick = onOpenCancel,
                        colors = ButtonDefaults.textButtonColors(contentColor = LightColors.danger),
                    ) {
                        Text("Cancel Claim")
                    }
                }
                TextButton(onClick = onOpenDetails) {
                    Text("View Particulars")
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bgColor, textColor) = when (status) {
        "SUBMITTED", "UNDER_REVIEW" -> LightColors.warning.copy(alpha = 0.15f) to LightColors.warning
        "APPROVED" -> LightColors.brandPrimary.copy(alpha = 0.15f) to LightColors.brandPrimary
        "REIMBURSED" -> LightColors.success.copy(alpha = 0.15f) to LightColors.success
        "REJECTED" -> LightColors.danger.copy(alpha = 0.15f) to LightColors.danger
        "CANCELLED" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(bgColor)
            .padding(horizontal = Spacing.s2, vertical = 2.dp),
    ) {
        Text(
            text = status.replace('_', ' '),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}

@Composable
private fun SubmitClaimContent(
    state: ExpenseState,
    onTitleChange: (String) -> Unit,
    onRemarksChange: (String) -> Unit,
    onAddLine: () -> Unit,
    onRemoveLine: (UUID) -> Unit,
    onUpdateLine: (UUID, (DraftLineItem) -> DraftLineItem) -> Unit,
    onSimulateOcr: (UUID, String) -> Unit,
    onSubmit: () -> Unit,
) {
    val totalDraftAmount = remember(state.draftLines) {
        state.draftLines.fold(BigDecimal.ZERO) { acc, line ->
            acc.add(line.amount.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        item {
            Spacer(modifier = Modifier.height(Spacing.s2))
            Text(
                text = "Claim Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(Spacing.s2))
            OutlinedTextField(
                value = state.draftTitle,
                onValueChange = onTitleChange,
                label = { Text("Claim Title (e.g., Client Tech Summit Galle)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(Spacing.s2))
            OutlinedTextField(
                value = state.draftRemarks,
                onValueChange = onRemarksChange,
                label = { Text("General Notes & Travel Purpose (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }

        item {
            Spacer(modifier = Modifier.height(Spacing.s2))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Expense Items (${state.draftLines.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onAddLine) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text("Add Line")
                }
            }
        }

        items(state.draftLines, key = { it.id }) { line ->
            DraftLineItemCard(
                line = line,
                categories = state.categories,
                canDelete = state.draftLines.size > 1,
                onRemove = { onRemoveLine(line.id) },
                onUpdate = { block -> onUpdateLine(line.id, block) },
                onSimulateOcr = { sample -> onSimulateOcr(line.id, sample) },
            )
        }

        // Summary Card & Submit CTA
        item {
            Spacer(modifier = Modifier.height(Spacing.s2))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(
                    containerColor = LightColors.brandPrimaryContainer.copy(alpha = 0.3f),
                ),
            ) {
                Column(modifier = Modifier.padding(Spacing.s3)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Total Claim Amount",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${state.draftCurrency} ${formatMoney(totalDraftAmount)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = LightColors.brandPrimary,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.s3))
                    Button(
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSubmitting && totalDraftAmount > BigDecimal.ZERO,
                        shape = RoundedCornerShape(Radius.control),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("Submitting Claim…")
                        } else {
                            Text("Submit Claim for Approval")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.s4))
        }
    }
}

@Composable
private fun DraftLineItemCard(
    line: DraftLineItem,
    categories: List<ExpenseCategoryItem>,
    canDelete: Boolean,
    onRemove: () -> Unit,
    onUpdate: ((DraftLineItem) -> DraftLineItem) -> Unit,
    onSimulateOcr: (String) -> Unit,
) {
    val selectedCategory = categories.find { it.id == line.categoryId } ?: categories.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.s3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Expense Line",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = LightColors.brandPrimary,
                )
                if (canDelete) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Line",
                            tint = LightColors.danger,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            // OCR Scanner Simulation Action
            Surface(
                shape = RoundedCornerShape(Radius.control),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(Spacing.s2)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = LightColors.brandPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.s1))
                        Text(
                            text = "AI Receipt OCR Autofill",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = LightColors.brandPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (line.isScanningOcr) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text(
                                text = "Extracting merchant, total, date from receipt…",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                            OutlinedButton(
                                onClick = { onSimulateOcr("HOTEL") },
                                modifier = Modifier.height(30.dp),
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                            ) {
                                Text("Hotel", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { onSimulateOcr("TRANSIT") },
                                modifier = Modifier.height(30.dp),
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                            ) {
                                Text("Uber Ride", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { onSimulateOcr("MEAL") },
                                modifier = Modifier.height(30.dp),
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                            ) {
                                Text("Lunch", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            // Category Chips
            Text(
                text = "Select Category",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = (line.categoryId == cat.id) || (line.categoryId == null && cat == selectedCategory),
                        onClick = {
                            onUpdate { current ->
                                val updated = current.copy(categoryId = cat.id)
                                // If category has fixed unit rate, auto-calculate
                                if (cat.ratePerUnit != null && updated.unitQuantity.isNotBlank()) {
                                    val qty = updated.unitQuantity.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                    val calc = qty.multiply(cat.ratePerUnit).setScale(2, RoundingMode.HALF_UP)
                                    updated.copy(amount = calc.toPlainString())
                                } else {
                                    updated
                                }
                            }
                        },
                        label = { Text(cat.name, fontSize = 12.sp) },
                    )
                }
            }

            if (selectedCategory != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (selectedCategory.requiresReceipt) "Receipt required" else "No receipt required",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedCategory.requiresReceipt) LightColors.warning else LightColors.success,
                    )
                    if (selectedCategory.maxAmountPerClaim != null) {
                        Text(
                            text = "Max: LKR ${formatMoney(selectedCategory.maxAmountPerClaim)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            OutlinedTextField(
                value = line.merchantName,
                onValueChange = { str -> onUpdate { it.copy(merchantName = str) } },
                label = { Text("Merchant / Service Provider") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(Spacing.s2))

            // If unit rate is applicable (e.g. Mileage)
            if (selectedCategory?.ratePerUnit != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    OutlinedTextField(
                        value = line.unitQuantity,
                        onValueChange = { qtyStr ->
                            onUpdate { current ->
                                val qty = qtyStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                                val total = qty.multiply(selectedCategory.ratePerUnit).setScale(2, RoundingMode.HALF_UP)
                                current.copy(
                                    unitQuantity = qtyStr,
                                    amount = if (qty > BigDecimal.ZERO) total.toPlainString() else current.amount,
                                )
                            }
                        },
                        label = { Text("Distance (${selectedCategory.unitName ?: "Units"})") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = line.amount,
                        onValueChange = { amt -> onUpdate { it.copy(amount = amt) } },
                        label = { Text("Amount (LKR)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
            } else {
                OutlinedTextField(
                    value = line.amount,
                    onValueChange = { amt -> onUpdate { it.copy(amount = amt) } },
                    label = { Text("Amount (LKR)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            OutlinedTextField(
                value = line.description,
                onValueChange = { desc -> onUpdate { it.copy(description = desc) } },
                label = { Text("Description / Business Justification") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (!line.receiptFileName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.s2))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(LightColors.brandPrimary.copy(alpha = 0.1f))
                        .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = LightColors.brandPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text(
                        text = line.receiptFileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = LightColors.brandPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyLimitsContent(
    state: ExpenseState,
    onSelectCategory: (ExpenseCategoryItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        item {
            Spacer(modifier = Modifier.height(Spacing.s2))
            Text(
                text = "Company Expense Policies & Limits",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Expense rules, maximum reimbursement ceilings, and receipt criteria per category.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(state.categories, key = { it.id }) { cat ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(Spacing.s3)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = cat.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = Spacing.s2, vertical = 2.dp),
                        ) {
                            Text(
                                text = cat.code,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    if (!cat.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(Spacing.s1))
                        Text(
                            text = cat.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.s3))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "RECEIPT RULE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (cat.requiresReceipt) "Mandatory Receipt" else "Optional (Self-Attested)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (cat.requiresReceipt) LightColors.warning else LightColors.success,
                            )
                        }

                        if (cat.ratePerUnit != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "MILEAGE RATE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "LKR ${formatMoney(cat.ratePerUnit)} / ${cat.unitName ?: "unit"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = LightColors.brandPrimary,
                                )
                            }
                        } else if (cat.maxAmountPerClaim != null) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "MAX CEILING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "LKR ${formatMoney(cat.maxAmountPerClaim)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.s2))
                    OutlinedButton(
                        onClick = { onSelectCategory(cat) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.control),
                    ) {
                        Text("Apply in New Claim")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(Spacing.s4)) }
    }
}

@Composable
private fun ExpenseClaimDetailDialog(
    detail: ExpenseClaimDetailResponse,
    onDismiss: () -> Unit,
    onCancelClaim: (ExpenseClaimItem) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = Spacing.s4),
            shape = RoundedCornerShape(Radius.card),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            LazyColumn(
                modifier = Modifier.padding(Spacing.s4),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = detail.claim.claimNumber,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = LightColors.brandPrimary,
                            )
                            Text(
                                text = detail.claim.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.s1))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusBadge(status = detail.claim.status.value)
                        Text(
                            text = "${detail.claim.currency} ${formatMoney(detail.claim.totalAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Text(
                        text = "Itemized Expense Lines",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                items(detail.lines, key = { it.id }) { line ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        ),
                        shape = RoundedCornerShape(Radius.control),
                    ) {
                        Column(modifier = Modifier.padding(Spacing.s2)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = line.categoryName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "LKR ${formatMoney(line.amount)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = line.description,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (!line.merchantName.isNullOrBlank()) {
                                Text(
                                    text = "Merchant: ${line.merchantName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (line.receiptKey != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = LightColors.brandPrimary,
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "Receipt Verified",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LightColors.brandPrimary,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(Spacing.s3))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (detail.claim.status == ExpenseClaimItem.Status.SUBMITTED) {
                            TextButton(
                                onClick = { onCancelClaim(detail.claim) },
                                colors = ButtonDefaults.textButtonColors(contentColor = LightColors.danger),
                            ) {
                                Text("Cancel Claim")
                            }
                            Spacer(modifier = Modifier.width(Spacing.s2))
                        }
                        Button(onClick = onDismiss) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

private fun formatMoney(amount: BigDecimal?): String {
    if (amount == null) return "0.00"
    return String.format(Locale.US, "%,.2f", amount)
}
