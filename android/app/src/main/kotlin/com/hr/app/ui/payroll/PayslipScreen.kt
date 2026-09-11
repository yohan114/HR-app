package com.hr.app.ui.payroll

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.PayslipComparisonResponse
import com.hr.client.model.PayslipDetailResponse
import com.hr.client.model.PayslipLineItem
import com.hr.client.model.PayslipSummaryItem
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayslipScreen(
    initialPayslipId: UUID? = null,
    onNavigateBack: () -> Unit = {},
    viewModel: PayslipViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialPayslipId) {
        if (initialPayslipId != null) {
            viewModel.loadPayslips(initialPayslipId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Payslips & Compensation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to home",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleMask) {
                        Icon(
                            imageVector = if (state.isMasked) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (state.isMasked) "Reveal amounts" else "Conceal amounts",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (state.isLoading && state.selectedPayslipDetail == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                ) {
                    // 1. Pay Period Horizontal Strip
                    PeriodSelectorStrip(
                        payslips = state.payslips,
                        selectedId = state.selectedPayslipId,
                        onSelectPeriod = viewModel::selectPayslip,
                    )

                    val detail = state.selectedPayslipDetail
                    if (detail != null) {
                        // 2. Headline Net Pay Card
                        HeadlineNetPayCard(
                            detail = detail,
                            isMasked = state.isMasked,
                            comparison = state.comparison,
                            onOpenComparison = viewModel::openComparison,
                        )

                        // 3. Gross vs Deductions summary pills
                        GrossVsDeductionsSummaryRow(
                            detail = detail,
                            isMasked = state.isMasked,
                        )

                        // 4. Earnings Breakdown
                        PayslipSectionCard(
                            title = "Earnings",
                            totalAmount = detail.grossPay,
                            currency = detail.currency,
                            lines = detail.earnings,
                            isMasked = state.isMasked,
                            amountColor = MaterialTheme.colorScheme.primary,
                            onLineClick = viewModel::openExplainer,
                        )

                        // 5. Statutory Deductions & Taxes Breakdown
                        val allDeductions = detail.deductions + detail.taxes
                        PayslipSectionCard(
                            title = "Statutory Deductions & Taxes",
                            totalAmount = detail.totalDeductions,
                            currency = detail.currency,
                            lines = allDeductions,
                            isMasked = state.isMasked,
                            amountColor = MaterialTheme.colorScheme.error,
                            onLineClick = viewModel::openExplainer,
                        )

                        // 6. Employer Contributions (Informational)
                        EmployerContributionsCard(
                            lines = detail.employerContributions,
                            totalEmployerCost = detail.grossPay + detail.totalStatutoryEmployer,
                            currency = detail.currency,
                            isMasked = state.isMasked,
                            onLineClick = viewModel::openExplainer,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.s4))
                }
            }
        }
    }

    // Interactive Payslip Explainer Dialog
    if (state.isExplainerOpen && state.selectedExplainerLine != null) {
        PayslipExplainerDialog(
            line = state.selectedExplainerLine!!,
            currency = state.selectedPayslipDetail?.currency ?: "LKR",
            onDismiss = viewModel::closeExplainer,
        )
    }

    // Month-over-Month Comparison Dialog
    if (state.isComparisonOpen && state.comparison != null) {
        PayslipComparisonDialog(
            comparison = state.comparison!!,
            currency = state.selectedPayslipDetail?.currency ?: "LKR",
            isMasked = state.isMasked,
            onDismiss = viewModel::closeComparison,
        )
    }
}

// ---------------------------------------------------------------------------
// UI Components
// ---------------------------------------------------------------------------

@Composable
private fun PeriodSelectorStrip(
    payslips: List<PayslipSummaryItem>,
    selectedId: UUID?,
    onSelectPeriod: (UUID) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        payslips.forEach { item ->
            val isSelected = item.id == selectedId
            FilterChip(
                selected = isSelected,
                onClick = { onSelectPeriod(item.id) },
                label = { Text(item.periodName) },
                shape = RoundedCornerShape(Radius.pill),
            )
        }
    }
}

@Composable
private fun HeadlineNetPayCard(
    detail: PayslipDetailResponse,
    isMasked: Boolean,
    comparison: PayslipComparisonResponse?,
    onOpenComparison: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Net Take-Home Pay",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = Color(0xFF16A34A).copy(alpha = 0.15f),
                ) {
                    Text(
                        text = detail.paymentStatus,
                        color = Color(0xFF15803D),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s1))

            Text(
                text = if (isMasked) "••••••••••" else formatCurrency(detail.netPay, detail.currency),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(Spacing.s2))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(Spacing.s2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text(
                        text = "${detail.bankName ?: "Bank"} ${detail.bankAccountNumberMasked ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }

                if (comparison != null && comparison.priorGross > BigDecimal.ZERO) {
                    val isPositive = comparison.netVariance >= BigDecimal.ZERO
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { onOpenComparison() },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CompareArrows,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${if (isPositive) "+" else ""}${comparison.netVariancePercent}% vs ${comparison.priorPeriodCode}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrossVsDeductionsSummaryRow(
    detail: PayslipDetailResponse,
    isMasked: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        OutlinedCard(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(Radius.control),
        ) {
            Column(modifier = Modifier.padding(Spacing.s2)) {
                Text(
                    text = "Gross Earnings",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (isMasked) "••••••" else formatCurrency(detail.grossPay, detail.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        OutlinedCard(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(Radius.control),
        ) {
            Column(modifier = Modifier.padding(Spacing.s2)) {
                Text(
                    text = "Total Deductions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (isMasked) "••••••" else "-${formatCurrency(detail.totalDeductions, detail.currency)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PayslipSectionCard(
    title: String,
    totalAmount: BigDecimal,
    currency: String,
    lines: List<PayslipLineItem>,
    isMasked: Boolean,
    amountColor: Color,
    onLineClick: (PayslipLineItem) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isMasked) "••••••" else formatCurrency(totalAmount, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s2))
            HorizontalDivider()

            lines.forEachIndexed { index, line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLineClick(line) }
                        .padding(vertical = Spacing.s2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = line.itemName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(modifier = Modifier.width(Spacing.s1))
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Tap to inspect formula",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            if (line.hours != null && line.hours > BigDecimal.ZERO) {
                                Text(
                                    text = "${line.hours} hrs @ ${line.rate?.let { "${it}x" } ?: "std"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (line.isStatutory) {
                                Text(
                                    text = "Statutory contribution",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isMasked) "••••••" else formatCurrency(line.amount, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (index < lines.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = Spacing.s2))
                }
            }
        }
    }
}

@Composable
private fun EmployerContributionsCard(
    lines: List<PayslipLineItem>,
    totalEmployerCost: BigDecimal,
    currency: String,
    isMasked: Boolean,
    onLineClick: (PayslipLineItem) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.s3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text(
                        text = "Employer Statutory Contributions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                text = "Paid directly by employer on your behalf — not deducted from your net salary.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.s1),
            )

            lines.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLineClick(line) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = line.itemName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.width(Spacing.s1))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Inspect calculation",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp),
                        )
                    }

                    Text(
                        text = if (isMasked) "••••••" else formatCurrency(line.amount, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s2))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Spacing.s1))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Total Employer Investment (Cost to Company)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (isMasked) "••••••" else formatCurrency(totalEmployerCost, currency),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Interactive Dialogs: Explainer & MoM Comparison
// ---------------------------------------------------------------------------

@Composable
fun PayslipExplainerDialog(
    line: PayslipLineItem,
    currency: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.s2))
                Column {
                    Text(
                        text = line.itemName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Calculation Explainer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Calculated Amount:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = formatCurrency(line.amount, currency),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Formula & Statutory Rule Applied:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    shape = RoundedCornerShape(Radius.control),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = line.calculationTrace
                            ?: "Calculated in accordance with national statutory regulations and standard contractual terms.",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(Spacing.s2),
                    )
                }

                if (line.isStatutory) {
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = "Mandatory statutory compliance item",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got It")
            }
        },
    )
}

@Composable
fun PayslipComparisonDialog(
    comparison: PayslipComparisonResponse,
    currency: String,
    isMasked: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Month-over-Month Comparison",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${comparison.currentPeriodCode} vs ${comparison.priorPeriodCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                // Headline Summary Comparison
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(Spacing.s2)) {
                        ComparisonRow(
                            label = "Gross Pay",
                            current = comparison.currentGross,
                            prior = comparison.priorGross,
                            variance = comparison.grossVariance,
                            variancePct = comparison.grossVariancePercent,
                            currency = currency,
                            isMasked = isMasked,
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.s1))

                        ComparisonRow(
                            label = "Net Take-Home",
                            current = comparison.currentNet,
                            prior = comparison.priorNet,
                            variance = comparison.netVariance,
                            variancePct = comparison.netVariancePercent,
                            currency = currency,
                            isMasked = isMasked,
                        )
                    }
                }

                Text(
                    text = "Itemized Variances:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = Spacing.s1),
                )

                comparison.lines.forEach { line ->
                    if (line.varianceAmount.compareTo(BigDecimal.ZERO) != 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = line.itemName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${line.category} • Prior: ${if (isMasked) "••••" else formatCurrency(line.priorAmount, currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            val isPos = line.varianceAmount > BigDecimal.ZERO
                            Text(
                                text = if (isMasked) "••••" else "${if (isPos) "+" else ""}${formatCurrency(line.varianceAmount, currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPos) Color(0xFF16A34A) else Color(0xFFDC2626),
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ComparisonRow(
    label: String,
    current: BigDecimal,
    prior: BigDecimal,
    variance: BigDecimal,
    variancePct: BigDecimal,
    currency: String,
    isMasked: Boolean,
) {
    val isPos = variance >= BigDecimal.ZERO
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (isMasked) "••••••" else formatCurrency(current, currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Surface(
            shape = RoundedCornerShape(Radius.pill),
            color = if (isPos) Color(0xFF16A34A).copy(alpha = 0.15f) else Color(0xFFDC2626).copy(alpha = 0.15f),
        ) {
            Text(
                text = "${if (isPos) "+" else ""}${variancePct}% (${if (isPos) "+" else ""}${formatCurrency(variance, currency)})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isPos) Color(0xFF15803D) else Color(0xFFB91C1C),
                modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
            )
        }
    }
}

private fun formatCurrency(amount: BigDecimal?, currency: String): String {
    if (amount == null) return "$currency 0.00"
    val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "$currency ${formatter.format(amount)}"
}

private fun formatCurrency(amount: Double, currency: String): String =
    formatCurrency(BigDecimal.valueOf(amount), currency)
