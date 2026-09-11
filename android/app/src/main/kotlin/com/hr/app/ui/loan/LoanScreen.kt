package com.hr.app.ui.loan

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
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hr.app.ui.theme.LightColors
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.EmployeeLoanItem
import com.hr.client.model.LoanRepaymentScheduleItem
import com.hr.client.model.LoanTypeItem
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanScreen(
    viewModel: LoanViewModel,
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
                            text = "Loans & Advances",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = "Self-Service Repayment & Eligibility",
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
                    if (state.pendingOutboxCount > 0) {
                        Box(
                            modifier = Modifier
                                .padding(end = Spacing.s2)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(LightColors.warning.copy(alpha = 0.2f))
                                .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Outbox: ${state.pendingOutboxCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LightColors.warning,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        if (state.refreshing) {
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
                    selected = state.selectedTab == LoanTab.MY_LOANS,
                    onClick = { viewModel.setTab(LoanTab.MY_LOANS) },
                    text = {
                        Text(
                            "My Loans",
                            fontWeight = if (state.selectedTab == LoanTab.MY_LOANS) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
                Tab(
                    selected = state.selectedTab == LoanTab.APPLY_LOAN,
                    onClick = { viewModel.setTab(LoanTab.APPLY_LOAN) },
                    text = {
                        Text(
                            "Apply for Loan",
                            fontWeight = if (state.selectedTab == LoanTab.APPLY_LOAN) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
                Tab(
                    selected = state.selectedTab == LoanTab.LOAN_PRODUCTS,
                    onClick = { viewModel.setTab(LoanTab.LOAN_PRODUCTS) },
                    text = {
                        Text(
                            "Products & Policy",
                            fontWeight = if (state.selectedTab == LoanTab.LOAN_PRODUCTS) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }

            when (state.selectedTab) {
                LoanTab.MY_LOANS -> MyLoansContent(
                    state = state,
                    onOpenSchedule = { viewModel.openLoanSchedule(it) },
                    onOpenSettlement = { viewModel.openSettlementDialog(it) },
                    onNavigateToApply = { viewModel.setTab(LoanTab.APPLY_LOAN) },
                )
                LoanTab.APPLY_LOAN -> ApplyLoanContent(
                    state = state,
                    onSelectType = { viewModel.setApplyLoanType(it) },
                    onPrincipalChange = { viewModel.setApplyPrincipal(it) },
                    onTenureChange = { viewModel.setApplyTenureMonths(it) },
                    onReasonChange = { viewModel.setApplyReason(it) },
                    onSubmit = { viewModel.submitLoanApplication() },
                )
                LoanTab.LOAN_PRODUCTS -> LoanProductsContent(
                    state = state,
                    onSelectProductToApply = { type ->
                        viewModel.setApplyLoanType(type.id)
                        viewModel.setTab(LoanTab.APPLY_LOAN)
                    },
                )
            }
        }
    }

    // Amortization Repayment Schedule Modal
    if (state.isScheduleModalOpen) {
        AmortizationScheduleDialog(
            loan = state.selectedLoan,
            schedule = state.selectedLoanDetail?.schedule ?: emptyList(),
            loading = state.scheduleLoading,
            onDismiss = { viewModel.closeLoanSchedule() },
            onSettleClick = {
                state.selectedLoan?.let { loan ->
                    viewModel.openSettlementDialog(loan)
                }
            },
        )
    }

    // Early Settlement Confirmation Dialog
    if (state.isSettlementDialogOpen) {
        EarlySettlementDialog(
            loan = state.loanToSettle,
            notes = state.settlementNotes,
            isSettling = state.isSettling,
            onNotesChange = { viewModel.setSettlementNotes(it) },
            onConfirm = { viewModel.confirmSettlement() },
            onDismiss = { viewModel.closeSettlementDialog() },
        )
    }
}

// -----------------------------------------------------------------------------
// TAB 1: MY LOANS CONTENT
// -----------------------------------------------------------------------------

@Composable
private fun MyLoansContent(
    state: LoanState,
    onOpenSchedule: (EmployeeLoanItem) -> Unit,
    onOpenSettlement: (EmployeeLoanItem) -> Unit,
    onNavigateToApply: () -> Unit,
) {
    val activeLoans = state.loans.filter { it.status == EmployeeLoanItem.Status.ACTIVE }
    val settledLoans = state.loans.filter { it.status == EmployeeLoanItem.Status.SETTLED }

    val totalActiveBalance = activeLoans.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.remainingBalance) }
    val totalMonthlyDeduction = activeLoans.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.monthlyInstallment) }
    val totalRepaidAll = state.loans.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.totalRepaid) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        // KPI Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(
                    containerColor = LightColors.brandPrimaryContainer.copy(alpha = 0.5f),
                ),
            ) {
                Column(modifier = Modifier.padding(Spacing.s4)) {
                    Text(
                        text = "LOAN PORTFOLIO OVERVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = LightColors.brandPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Outstanding Balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = LightColors.onSurfaceMuted,
                            )
                            Text(
                                text = "LKR ${formatMoney(totalActiveBalance)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightColors.danger,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Monthly Deduction",
                                style = MaterialTheme.typography.bodySmall,
                                color = LightColors.onSurfaceMuted,
                            )
                            Text(
                                text = "LKR ${formatMoney(totalMonthlyDeduction)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightColors.brandPrimary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Text(
                        text = "Lifetime Repaid: LKR ${formatMoney(totalRepaidAll)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LightColors.success,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Active Loans Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Active Advances (${activeLoans.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                TextButton(onClick = onNavigateToApply) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text("New Request", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (activeLoans.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.card),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.s6),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = LightColors.success,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(modifier = Modifier.height(Spacing.s2))
                        Text(
                            text = "No Active Loans",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        Text(
                            text = "You currently have zero outstanding advances.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LightColors.onSurfaceMuted,
                        )
                    }
                }
            }
        } else {
            items(activeLoans) { loan ->
                ActiveLoanCard(
                    loan = loan,
                    onOpenSchedule = { onOpenSchedule(loan) },
                    onOpenSettlement = { onOpenSettlement(loan) },
                )
            }
        }

        // Settled / Historical Loans Header
        if (settledLoans.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(Spacing.s2))
                Text(
                    text = "Historical & Settled (${settledLoans.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
            items(settledLoans) { loan ->
                SettledLoanCard(
                    loan = loan,
                    onOpenSchedule = { onOpenSchedule(loan) },
                )
            }
        }
    }
}

@Composable
private fun ActiveLoanCard(
    loan: EmployeeLoanItem,
    onOpenSchedule: () -> Unit,
    onOpenSettlement: () -> Unit,
) {
    val progress = if (loan.totalRepayable > BigDecimal.ZERO) {
        loan.totalRepaid.divide(loan.totalRepayable, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = loan.loanTypeName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "${loan.loanCode} · ${loan.interestMethod.value}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LightColors.onSurfaceMuted,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(LightColors.success.copy(alpha = 0.15f))
                        .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
                ) {
                    Text(
                        text = loan.status.value,
                        style = MaterialTheme.typography.labelSmall,
                        color = LightColors.success,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s3))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(Radius.pill)),
                color = LightColors.brandPrimary,
                trackColor = LightColors.brandPrimaryContainer.copy(alpha = 0.4f),
            )

            Spacer(modifier = Modifier.height(Spacing.s2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Repaid: LKR ${formatMoney(loan.totalRepaid)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LightColors.success,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Remaining: LKR ${formatMoney(loan.remainingBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LightColors.danger,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s3))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.control))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(Spacing.s2),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Monthly Installment:",
                    style = MaterialTheme.typography.bodySmall,
                    color = LightColors.onSurfaceMuted,
                )
                Text(
                    text = "LKR ${formatMoney(loan.monthlyInstallment)} × ${loan.tenureMonths} mos",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s3))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                OutlinedButton(
                    onClick = onOpenSchedule,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text("Schedule", fontSize = 13.sp)
                }

                Button(
                    onClick = onOpenSettlement,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.control),
                    colors = ButtonDefaults.buttonColors(containerColor = LightColors.brandPrimary),
                ) {
                    Text("Early Settle", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SettledLoanCard(
    loan: EmployeeLoanItem,
    onOpenSchedule: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSchedule() },
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = loan.loanTypeName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Text(
                    text = "${loan.loanCode} · Settled on ${loan.settledDate ?: "Complete"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LightColors.onSurfaceMuted,
                )
                Text(
                    text = "Total Repaid: LKR ${formatMoney(loan.totalRepaid)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LightColors.success,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(LightColors.secondary.copy(alpha = 0.15f))
                    .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
            ) {
                Text(
                    text = "SETTLED",
                    style = MaterialTheme.typography.labelSmall,
                    color = LightColors.secondary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 2: APPLY FOR LOAN WITH LIVE ELIGIBILITY
// -----------------------------------------------------------------------------

@Composable
private fun ApplyLoanContent(
    state: LoanState,
    onSelectType: (java.util.UUID) -> Unit,
    onPrincipalChange: (String) -> Unit,
    onTenureChange: (Int) -> Unit,
    onReasonChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val selectedType = state.loanTypes.find { it.id == state.applyLoanTypeId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        item {
            Text(
                text = "Apply for Company Advance",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                text = "Instant policy pre-check with automated payroll schedule projection.",
                style = MaterialTheme.typography.bodySmall,
                color = LightColors.onSurfaceMuted,
            )
        }

        // Product Selector
        item {
            Text(
                text = "1. Select Loan Product",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(Spacing.s1))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                items(state.loanTypes) { type ->
                    val isSelected = type.id == state.applyLoanTypeId
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectType(type.id) },
                        label = {
                            Text(
                                text = type.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        }

        // Principal Amount Input
        item {
            Text(
                text = "2. Requested Principal (LKR)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(Spacing.s1))
            OutlinedTextField(
                value = state.applyPrincipal,
                onValueChange = onPrincipalChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. 30000") },
                supportingText = {
                    selectedType?.let {
                        Text("Limits: LKR ${formatMoney(it.minPrincipal)} – ${formatMoney(it.maxPrincipal)}")
                    }
                },
            )

            // Quick increment chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                listOf("15000", "30000", "50000", "100000").forEach { preset ->
                    OutlinedButton(
                        onClick = { onPrincipalChange(preset) },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text(preset, fontSize = 11.sp)
                    }
                }
            }
        }

        // Tenure Selector
        item {
            Text(
                text = "3. Repayment Tenure: ${state.applyTenureMonths} Months",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(Spacing.s1))
            val minT = selectedType?.minTenureMonths ?: 1
            val maxT = selectedType?.maxTenureMonths ?: 24
            val tenures = listOf(3, 6, 10, 12, 24).filter { it in minT..maxT }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                items(if (tenures.isEmpty()) listOf(state.applyTenureMonths) else tenures) { months ->
                    val isSelected = months == state.applyTenureMonths
                    FilterChip(
                        selected = isSelected,
                        onClick = { onTenureChange(months) },
                        label = { Text("$months mos") },
                    )
                }
            }
        }

        // Purpose / Reason
        item {
            Text(
                text = "4. Purpose of Advance",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(Spacing.s1))
            OutlinedTextField(
                value = state.applyReason,
                onValueChange = onReasonChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Annual festival expenses, emergency medical aid") },
                maxLines = 2,
            )
        }

        // Live Eligibility Simulation Result Box
        item {
            Text(
                text = "5. Live Policy Eligibility & EMI Preview",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(Spacing.s1))

            val eligibility = state.eligibility
            if (state.eligibilityLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.card),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.s4),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text("Simulating eligibility...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (eligibility != null) {
                EligibilityPreviewCard(eligibility = eligibility)
            }
        }

        // Submit Button
        item {
            val canSubmit = state.eligibility?.eligible == true && !state.isSubmittingApplication
            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(Radius.control),
                colors = ButtonDefaults.buttonColors(containerColor = LightColors.brandPrimary),
            ) {
                if (state.isSubmittingApplication) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (state.eligibility?.eligible == false) "Ineligible — Cannot Submit" else "Submit Loan Application",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EligibilityPreviewCard(eligibility: com.hr.client.model.LoanEligibilityResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = if (eligibility.eligible) LightColors.brandPrimaryContainer.copy(alpha = 0.3f) else LightColors.danger.copy(alpha = 0.1f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (eligibility.eligible) LightColors.brandPrimary.copy(alpha = 0.3f) else LightColors.danger.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (eligibility.eligible) "ELIGIBLE FOR ADVANCE" else "POLICY INELIGIBLE",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (eligibility.eligible) LightColors.success else LightColors.danger,
                )
                Icon(
                    imageVector = if (eligibility.eligible) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (eligibility.eligible) LightColors.success else LightColors.danger,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (!eligibility.eligible) {
                Spacer(modifier = Modifier.height(Spacing.s2))
                eligibility.reasons.forEach { reason ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("• ", color = LightColors.danger, fontWeight = FontWeight.Bold)
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = LightColors.danger)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(Spacing.s3))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Projected Installment", style = MaterialTheme.typography.bodySmall, color = LightColors.onSurfaceMuted)
                        Text("LKR ${formatMoney(eligibility.projectedMonthlyInstallment)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LightColors.brandPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total Repayable", style = MaterialTheme.typography.bodySmall, color = LightColors.onSurfaceMuted)
                        Text("LKR ${formatMoney(eligibility.projectedTotalRepayable)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.s2))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Total Interest: LKR ${formatMoney(eligibility.projectedTotalInterest)}", style = MaterialTheme.typography.labelSmall, color = LightColors.onSurfaceMuted)
                    Text("Max Allowed: LKR ${formatMoney(eligibility.maxAllowedPrincipal)}", style = MaterialTheme.typography.labelSmall, color = LightColors.success, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TAB 3: LOAN PRODUCTS & POLICY
// -----------------------------------------------------------------------------

@Composable
private fun LoanProductsContent(
    state: LoanState,
    onSelectProductToApply: (LoanTypeItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        item {
            Text(
                text = "Company Loan Products",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                text = "Standard loan schemes, tenure windows, and entitlement rules established by organization policy.",
                style = MaterialTheme.typography.bodySmall,
                color = LightColors.onSurfaceMuted,
            )
        }

        items(state.loanTypes) { type ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(Spacing.s4)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = type.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(LightColors.brandPrimary.copy(alpha = 0.12f))
                                .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
                        ) {
                            Text(
                                text = "${type.annualInterestRate}% ${type.interestMethod.value}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LightColors.brandPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    type.description?.let { desc ->
                        Spacer(modifier = Modifier.height(Spacing.s1))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = LightColors.onSurfaceMuted,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.s3))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.control))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(Spacing.s2),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PolicyRow("Principal Limits:", "LKR ${formatMoney(type.minPrincipal)} – ${formatMoney(type.maxPrincipal)}")
                        PolicyRow("Tenure Range:", "${type.minTenureMonths} – ${type.maxTenureMonths} Months")
                        PolicyRow("Salary Limit:", "Max ${type.salaryMultipleLimit}x Basic Salary")
                        PolicyRow("Service Requirement:", "Min ${type.minServiceMonths} Months Service")
                    }

                    Spacer(modifier = Modifier.height(Spacing.s3))

                    OutlinedButton(
                        onClick = { onSelectProductToApply(type) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.control),
                    ) {
                        Text("Apply for ${type.name}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LightColors.onSurfaceMuted)
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

// -----------------------------------------------------------------------------
// AMORTIZATION SCHEDULE DIALOG
// -----------------------------------------------------------------------------

@Composable
private fun AmortizationScheduleDialog(
    loan: EmployeeLoanItem?,
    schedule: List<LoanRepaymentScheduleItem>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSettleClick: () -> Unit,
) {
    if (loan == null) return
    val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

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
                    Column {
                        Text(
                            text = "Amortization Schedule",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = "${loan.loanCode} · ${loan.loanTypeName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = LightColors.onSurfaceMuted,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s2))

                // Schedule table header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.control))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("#", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Text("Month", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Principal", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Total", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Status", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(Spacing.s1))

                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(Spacing.s6), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(schedule) { line ->
                            val statusColor = when (line.status) {
                                LoanRepaymentScheduleItem.Status.DEDUCTED -> LightColors.success
                                LoanRepaymentScheduleItem.Status.WAIVED -> LightColors.secondary
                                else -> LightColors.brandPrimary
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.s2, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${line.installmentNumber}", fontSize = 12.sp, modifier = Modifier.width(24.dp), color = LightColors.onSurfaceMuted)
                                Text(line.dueDate.format(formatter), fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(formatMoney(line.principalAmount), fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(formatMoney(line.totalInstallment), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.pill))
                                        .background(statusColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = line.status.value,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s3))

                if (loan.status == EmployeeLoanItem.Status.ACTIVE) {
                    Button(
                        onClick = onSettleClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.control),
                        colors = ButtonDefaults.buttonColors(containerColor = LightColors.brandPrimary),
                    ) {
                        Text("Request Early Payoff (Remaining: LKR ${formatMoney(loan.remainingBalance)})")
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// EARLY SETTLEMENT CONFIRMATION DIALOG
// -----------------------------------------------------------------------------

@Composable
private fun EarlySettlementDialog(
    loan: EmployeeLoanItem?,
    notes: String,
    isSettling: Boolean,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (loan == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Early Loan Settlement",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text(
                    text = "You are requesting an early payoff for ${loan.loanTypeName} (${loan.loanCode}).",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.control),
                    colors = CardDefaults.cardColors(containerColor = LightColors.brandPrimaryContainer.copy(alpha = 0.3f)),
                ) {
                    Column(modifier = Modifier.padding(Spacing.s3)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Payoff Amount:", style = MaterialTheme.typography.bodySmall)
                            Text("LKR ${formatMoney(loan.remainingBalance)}", fontWeight = FontWeight.Bold, color = LightColors.brandPrimary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Settlement Route:", style = MaterialTheme.typography.bodySmall)
                            Text("Payroll Deduction", fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s1))

                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Optional notes or reason for early settlement") },
                    maxLines = 2,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSettling,
                shape = RoundedCornerShape(Radius.control),
                colors = ButtonDefaults.buttonColors(containerColor = LightColors.brandPrimary),
            ) {
                if (isSettling) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Confirm Early Payoff", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSettling) {
                Text("Cancel")
            }
        },
    )
}

private fun formatMoney(amount: BigDecimal): String {
    return String.format(Locale.US, "%,.2f", amount)
}
