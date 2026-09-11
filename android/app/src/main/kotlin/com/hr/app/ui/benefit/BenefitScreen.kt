package com.hr.app.ui.benefit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hr.app.ui.theme.LightColors
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenefitScreen(
    viewModel: BenefitViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val catalogue by viewModel.catalogue.collectAsState()
    val myBenefits by viewModel.myBenefits.collectAsState()
    val claims by viewModel.claims.collectAsState()
    val totalAnnual by viewModel.totalAnnualEntitlement.collectAsState()
    val totalUsed by viewModel.totalUsedAmount.collectAsState()
    val totalPending by viewModel.totalPendingAmount.collectAsState()
    val totalRemaining by viewModel.totalRemainingBalance.collectAsState()
    val pendingCount by viewModel.pendingClaimsCount.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorBanner) {
        uiState.errorBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBanners()
        }
    }

    LaunchedEffect(uiState.successBanner) {
        uiState.successBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearBanners()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Benefits & Insurance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Text(
                            text = "Flexible benefits, OPD & family coverage",
                            fontSize = 12.sp,
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
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            if (uiState.activeTab == BenefitTab.CLAIMS || uiState.activeTab == BenefitTab.MY_BENEFITS) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openClaimDialog() },
                    icon = { Icon(Icons.Default.Add, contentDescription = "New Claim") },
                    text = { Text("File a Claim") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Navigation Tabs
            TabRow(
                selectedTabIndex = uiState.activeTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(
                    selected = uiState.activeTab == BenefitTab.MY_BENEFITS,
                    onClick = { viewModel.selectTab(BenefitTab.MY_BENEFITS) },
                    text = {
                        Text(
                            text = "My Policies (${myBenefits?.enrollments?.size ?: 0})",
                            fontWeight = if (uiState.activeTab == BenefitTab.MY_BENEFITS) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
                Tab(
                    selected = uiState.activeTab == BenefitTab.CLAIMS,
                    onClick = { viewModel.selectTab(BenefitTab.CLAIMS) },
                    text = {
                        Text(
                            text = "Claims (${claims.size})",
                            fontWeight = if (uiState.activeTab == BenefitTab.CLAIMS) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
                Tab(
                    selected = uiState.activeTab == BenefitTab.CATALOGUE,
                    onClick = { viewModel.selectTab(BenefitTab.CATALOGUE) },
                    text = {
                        Text(
                            text = "Catalogue (${catalogue?.policies?.size ?: 0})",
                            fontWeight = if (uiState.activeTab == BenefitTab.CATALOGUE) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState.activeTab) {
                    BenefitTab.MY_BENEFITS -> MyBenefitsView(
                        totalAnnual = totalAnnual,
                        totalUsed = totalUsed,
                        totalPending = totalPending,
                        totalRemaining = totalRemaining,
                        enrollments = myBenefits?.enrollments.orEmpty(),
                        onFileClaim = { enrollmentId -> viewModel.openClaimDialog(enrollmentId) },
                    )
                    BenefitTab.CLAIMS -> ClaimsListView(
                        claims = claims,
                        statusFilter = uiState.statusFilter,
                        pendingCount = pendingCount,
                        onFilterStatus = { viewModel.filterByStatus(it) },
                        onCancelClaim = { id -> viewModel.cancelClaim(id) },
                        onNewClaim = { viewModel.openClaimDialog() },
                    )
                    BenefitTab.CATALOGUE -> CatalogueView(
                        categories = catalogue?.categories.orEmpty(),
                        policies = catalogue?.policies.orEmpty(),
                        searchQuery = uiState.searchQuery,
                        selectedCategory = uiState.selectedCategoryFilter,
                        onSearchChange = { viewModel.updateSearchQuery(it) },
                        onCategorySelect = { viewModel.filterByCategory(it) },
                        onEnrollClick = { policy ->
                            viewModel.selectTab(BenefitTab.MY_BENEFITS)
                        },
                    )
                }
            }
        }
    }

    // Modal Dialog: File a Claim
    if (uiState.showClaimDialog) {
        ClaimSubmissionDialog(
            uiState = uiState,
            enrollments = myBenefits?.enrollments.orEmpty(),
            onDismiss = { viewModel.closeClaimDialog() },
            onEnrollmentChange = { viewModel.updateEnrollmentSelection(it) },
            onDependentChange = { viewModel.updateDependentSelection(it) },
            onProviderChange = { viewModel.updateServiceProvider(it) },
            onDiagnosisChange = { viewModel.updateDiagnosisOrReason(it) },
            onInvoiceChange = { viewModel.updateInvoiceNumber(it) },
            onAmountChange = { viewModel.updateClaimedAmount(it) },
            onRemarksChange = { viewModel.updateRemarks(it) },
            onAttachReceipt = { viewModel.attachMockReceipt() },
            onSubmit = { viewModel.submitClaim() },
        )
    }
}

// ---------------------------------------------------------------------------
// Tab 1: My Policies & Balance
// ---------------------------------------------------------------------------

@Composable
private fun MyBenefitsView(
    totalAnnual: BigDecimal,
    totalUsed: BigDecimal,
    totalPending: BigDecimal,
    totalRemaining: BigDecimal,
    enrollments: List<EmployeeBenefitEnrollmentItem>,
    onFileClaim: (UUID) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero Entitlement Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "ANNUAL BENEFIT BALANCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "LKR",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "LKR ${formatCurrency(totalRemaining)}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Remaining available for claims in 2026",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = "Total Annual",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                            Text(
                                text = formatCurrency(totalAnnual),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Column {
                            Text(
                                text = "Claimed to Date",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                            Text(
                                text = formatCurrency(totalUsed),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFD32F2F),
                            )
                        }
                        Column {
                            Text(
                                text = "In Review",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                            Text(
                                text = formatCurrency(totalPending),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFED6C02),
                            )
                        }
                    }
                }
            }
        }

        // Enrolled Policies List
        item {
            Text(
                text = "ACTIVE ENROLLMENTS & COVERAGE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        if (enrollments.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.card),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Active Policies Found", fontWeight = FontWeight.Bold)
                        Text(
                            "Check the catalogue to view corporate schemes",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(enrollments) { enrollment ->
                EnrollmentCard(enrollment = enrollment, onFileClaim = { onFileClaim(enrollment.id) })
            }
        }
    }
}

@Composable
private fun EnrollmentCard(
    enrollment: EmployeeBenefitEnrollmentItem,
    onFileClaim: () -> Unit,
) {
    var expandedDependents by remember { mutableStateOf(false) }

    val usedRatio = if (enrollment.annualEntitlement > BigDecimal.ZERO) {
        enrollment.usedAmount.divide(enrollment.annualEntitlement, 2, RoundingMode.HALF_UP).toFloat()
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = enrollment.categoryName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9),
                ) {
                    Text(
                        text = enrollment.status.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = enrollment.policyName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Policy: ${enrollment.policyNumber} · ${enrollment.coverageTier.name.replace("_", " ")}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { usedRatio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (usedRatio > 0.8f) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Used: LKR ${formatCurrency(enrollment.usedAmount)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Available: LKR ${formatCurrency(enrollment.remainingBalance)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32),
                )
            }

            // Dependents Expansion
            if (enrollment.dependents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedDependents = !expandedDependents },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Covered Family Dependents (${enrollment.dependents.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = if (expandedDependents) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }

                AnimatedVisibility(visible = expandedDependents) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        enrollment.dependents.forEach { dep ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = dep.fullName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "${dep.relationship.name} · DOB: ${dep.dateOfBirth}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFE8F5E9),
                                ) {
                                    Text(
                                        text = "Covered",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onFileClaim,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.control),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("File Claim Under Policy")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab 2: Claims List & Reimbursements
// ---------------------------------------------------------------------------

@Composable
private fun ClaimsListView(
    claims: List<BenefitClaimItem>,
    statusFilter: String?,
    pendingCount: Int,
    onFilterStatus: (String?) -> Unit,
    onCancelClaim: (String) -> Unit,
    onNewClaim: () -> Unit,
) {
    val filterOptions = listOf(
        null to "All",
        "SUBMITTED" to "Submitted",
        "UNDER_REVIEW" to "Under Review",
        "APPROVED" to "Approved",
        "PAID" to "Paid",
        "CANCELLED" to "Cancelled",
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(filterOptions) { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { onFilterStatus(key) },
                        label = { Text(label) },
                    )
                }
            }
        }

        if (claims.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.card),
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Benefit Claims Found", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Submit a reimbursement claim for medical, optical, or dental expenses.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNewClaim) {
                            Text("Submit First Claim")
                        }
                    }
                }
            }
        } else {
            items(claims) { claim ->
                ClaimCard(claim = claim, onCancel = { onCancelClaim(claim.id.toString()) })
            }
        }
    }
}

@Composable
private fun ClaimCard(
    claim: BenefitClaimItem,
    onCancel: () -> Unit,
) {
    var showCancelConfirm by remember { mutableStateOf(false) }

    val statusColor = when (claim.status) {
        BenefitClaimItem.Status.PAID -> Color(0xFF2E7D32)
        BenefitClaimItem.Status.APPROVED -> Color(0xFF1976D2)
        BenefitClaimItem.Status.SUBMITTED, BenefitClaimItem.Status.UNDER_REVIEW -> Color(0xFFED6C02)
        BenefitClaimItem.Status.REJECTED, BenefitClaimItem.Status.CANCELLED -> Color(0xFF757575)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val statusBg = when (claim.status) {
        BenefitClaimItem.Status.PAID -> Color(0xFFE8F5E9)
        BenefitClaimItem.Status.APPROVED -> Color(0xFFE3F2FD)
        BenefitClaimItem.Status.SUBMITTED, BenefitClaimItem.Status.UNDER_REVIEW -> Color(0xFFFFF3E0)
        else -> Color(0xFFF5F5F5)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = claim.claimNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "${claim.claimDate} · ${claim.policyName}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusBg,
                ) {
                    Text(
                        text = claim.status.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = claim.diagnosisOrReason,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Provider: ${claim.serviceProvider}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                claim.dependentName?.let {
                    Text(
                        text = "For: $it",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))

            val coPay = claim.coPayAmount ?: BigDecimal.ZERO

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Claimed: LKR ${formatCurrency(claim.claimedAmount)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (coPay > BigDecimal.ZERO) {
                        Text(
                            text = "Co-Pay (10%): LKR ${formatCurrency(coPay)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Payable: LKR ${formatCurrency(claim.payableAmount ?: claim.claimedAmount)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (claim.status == BenefitClaimItem.Status.PAID) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (claim.status == BenefitClaimItem.Status.SUBMITTED || claim.status == BenefitClaimItem.Status.DRAFT) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = { showCancelConfirm = true },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text("Withdraw", fontSize = 11.sp)
                    }
                }
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Withdraw Claim?") },
            text = { Text("Are you sure you want to withdraw ${claim.claimNumber}? The claimed balance will be restored to your annual limit.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirm = false
                        onCancel()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                ) {
                    Text("Confirm Withdrawal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("Keep Claim")
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Tab 3: Catalogue & Eligibility
// ---------------------------------------------------------------------------

@Composable
private fun CatalogueView(
    categories: List<BenefitCategoryItem>,
    policies: List<BenefitPolicyItem>,
    searchQuery: String,
    selectedCategory: String?,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onEnrollClick: (BenefitPolicyItem) -> Unit,
) {
    val filteredPolicies = policies.filter { policy ->
        val matchesSearch = searchQuery.isBlank() ||
            policy.name.contains(searchQuery, ignoreCase = true) ||
            policy.categoryName.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == null || policy.categoryCode == selectedCategory
        matchesSearch && matchesCategory
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Search TextField
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search benefit policies...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(Radius.control),
            )
        }

        // Category Pills
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { onCategorySelect(null) },
                        label = { Text("All Categories") },
                    )
                }
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.code,
                        onClick = { onCategorySelect(cat.code) },
                        label = { Text(cat.name) },
                    )
                }
            }
        }

        items(filteredPolicies) { policy ->
            PolicyCatalogueCard(policy = policy, onEnroll = { onEnrollClick(policy) })
        }
    }
}

@Composable
private fun PolicyCatalogueCard(
    policy: BenefitPolicyItem,
    onEnroll: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = policy.categoryName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = policy.coverageTier.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = policy.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )

            policy.description?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Annual Cap", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("LKR ${formatCurrency(policy.annualLimit)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column {
                    Text("Co-Pay", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (policy.coPayPercentage > BigDecimal.ZERO) "${policy.coPayPercentage}%" else "None (100% Paid)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
                Column {
                    Text("Service Requirement", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (policy.minServiceMonths > 0) "${policy.minServiceMonths} months" else "Immediate",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ELIGIBILITY BADGE: Clean pass or reason if ineligible
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = if (policy.isEligible) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (policy.isEligible) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (policy.isEligible) Color(0xFF2E7D32) else Color(0xFFED6C02),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (policy.isEligible) {
                            "Eligible: Available for self-service reimbursement"
                        } else {
                            "Ineligible: ${policy.ineligibilityReason ?: "Policy requirements not met"}"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (policy.isEligible) Color(0xFF2E7D32) else Color(0xFFB26A00),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dialog: File a Claim Wizard
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaimSubmissionDialog(
    uiState: BenefitUiState,
    enrollments: List<EmployeeBenefitEnrollmentItem>,
    onDismiss: () -> Unit,
    onEnrollmentChange: (UUID) -> Unit,
    onDependentChange: (UUID?) -> Unit,
    onProviderChange: (String) -> Unit,
    onDiagnosisChange: (String) -> Unit,
    onInvoiceChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onRemarksChange: (String) -> Unit,
    onAttachReceipt: () -> Unit,
    onSubmit: () -> Unit,
) {
    val selectedEnrollment = enrollments.find { it.id == uiState.selectedEnrollmentId }
    val dependents = selectedEnrollment?.dependents.orEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "File Benefit Claim",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Policy Selector
                    item {
                        Text("Select Policy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(enrollments) { enrollment ->
                                FilterChip(
                                    selected = uiState.selectedEnrollmentId == enrollment.id,
                                    onClick = { onEnrollmentChange(enrollment.id) },
                                    label = { Text(enrollment.policyName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                    }

                    // Remaining Balance hint
                    selectedEnrollment?.let { enr ->
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Policy Balance Available:", fontSize = 11.sp)
                                    Text(
                                        "LKR ${formatCurrency(enr.remainingBalance)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF2E7D32),
                                    )
                                }
                            }
                        }
                    }

                    // Dependent Selector (if family policy)
                    if (dependents.isNotEmpty()) {
                        item {
                            Text("Beneficiary", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = uiState.selectedDependentId == null,
                                    onClick = { onDependentChange(null) },
                                    label = { Text("Self (Employee)") },
                                )
                                dependents.forEach { dep ->
                                    FilterChip(
                                        selected = uiState.selectedDependentId == dep.id,
                                        onClick = { onDependentChange(dep.id) },
                                        label = { Text("${dep.fullName} (${dep.relationship.name})") },
                                    )
                                }
                            }
                        }
                    }

                    // Provider Name
                    item {
                        OutlinedTextField(
                            value = uiState.serviceProvider,
                            onValueChange = onProviderChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Healthcare Provider / Hospital") },
                            placeholder = { Text("e.g. Asiri Hospital, Vision Care") },
                            singleLine = true,
                            shape = RoundedCornerShape(Radius.control),
                        )
                    }

                    // Diagnosis / Treatment
                    item {
                        OutlinedTextField(
                            value = uiState.diagnosisOrReason,
                            onValueChange = onDiagnosisChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Diagnosis or Treatment Reason") },
                            placeholder = { Text("e.g. Consultation & laboratory diagnostics") },
                            shape = RoundedCornerShape(Radius.control),
                        )
                    }

                    // Invoice Number & Amount
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = uiState.invoiceNumber,
                                onValueChange = onInvoiceChange,
                                modifier = Modifier.weight(1f),
                                label = { Text("Invoice / Ref #") },
                                placeholder = { Text("INV-9901") },
                                singleLine = true,
                                shape = RoundedCornerShape(Radius.control),
                            )
                            OutlinedTextField(
                                value = uiState.claimedAmount,
                                onValueChange = onAmountChange,
                                modifier = Modifier.weight(1f),
                                label = { Text("Amount (LKR)") },
                                placeholder = { Text("15000.00") },
                                singleLine = true,
                                shape = RoundedCornerShape(Radius.control),
                            )
                        }
                    }

                    // Receipt Upload Simulation
                    item {
                        OutlinedButton(
                            onClick = onAttachReceipt,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (uiState.simulatedReceiptName != null) {
                                    "Attached: ${uiState.simulatedReceiptName}"
                                } else {
                                    "Attach Medical Receipt / Invoice"
                                },
                            )
                        }
                    }

                    // Remarks
                    item {
                        OutlinedTextField(
                            value = uiState.remarks,
                            onValueChange = onRemarksChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Additional Remarks (Optional)") },
                            shape = RoundedCornerShape(Radius.control),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.control),
                    enabled = !uiState.isSubmitting,
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Submit Reimbursement Claim")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatCurrency(amount: BigDecimal): String =
    String.format(Locale.US, "%,.2f", amount)
