package com.hr.app.ui.recruitment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecruitmentScreen(
    viewModel: RecruitmentViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Recruitment & ATS",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "PeoplesHR Talent Acquisition & Pipeline",
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
                    when (state.selectedTab) {
                        RecruitmentTab.VACANCIES -> {
                            IconButton(onClick = { viewModel.toggleCreateVacancyDialog(true) }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Vacancy",
                                )
                            }
                        }
                        RecruitmentTab.INTERVIEWS -> {
                            IconButton(onClick = { viewModel.toggleScheduleInterviewDialog(true) }) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Schedule Interview",
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
            // Notification Banners
            state.successMessage?.let { success ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(Radius.card),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text(success, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            state.errorMessage?.let { err ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(Radius.card),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text(err, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // Tab Navigation
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                RecruitmentTab.values().forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = tab.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (state.selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            // Tab Contents
            Box(modifier = Modifier.fillMaxSize()) {
                when (state.selectedTab) {
                    RecruitmentTab.VACANCIES -> VacanciesTab(state, viewModel)
                    RecruitmentTab.PIPELINE -> PipelineTab(state, viewModel)
                    RecruitmentTab.INTERVIEWS -> InterviewsTab(state, viewModel)
                    RecruitmentTab.OFFERS -> OffersTab(state, viewModel)
                }
            }
        }
    }

    // Dialogs
    if (state.showCreateVacancyDialog) {
        CreateVacancyDialog(
            onDismiss = { viewModel.toggleCreateVacancyDialog(false) },
            onSubmit = { jobCode, title, desc, min, max ->
                viewModel.createVacancy(jobCode, title, desc, min, max)
            },
        )
    }

    if (state.showScheduleInterviewDialog) {
        ScheduleInterviewDialog(
            applications = state.applications,
            onDismiss = { viewModel.toggleScheduleInterviewDialog(false) },
            onSubmit = { appId, title, type, start, end, link ->
                viewModel.scheduleInterview(appId, title, type, start, end, link)
            },
        )
    }

    if (state.showScorecardDialog && state.selectedInterview != null) {
        ScorecardDialog(
            interview = state.selectedInterview!!,
            onDismiss = { viewModel.toggleScorecardDialog(false) },
            onSubmit = { rec, tech, comm, problem, fit, strengths, weaknesses, notes ->
                viewModel.submitScorecard(state.selectedInterview!!.id, rec, tech, comm, problem, fit, strengths, weaknesses, notes)
            },
        )
    }

    if (state.showCreateOfferDialog && state.selectedApplication != null) {
        CreateOfferDialog(
            application = state.selectedApplication!!,
            onDismiss = { viewModel.toggleCreateOfferDialog(false) },
            onSubmit = { salary, bonus, start, expiry ->
                viewModel.createOffer(state.selectedApplication!!.id, salary, bonus, start, expiry)
            },
        )
    }
}

// =============================================================================
// 1. Vacancies Tab
// =============================================================================
@Composable
private fun VacanciesTab(state: RecruitmentState, viewModel: RecruitmentViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Active Job Openings (${state.vacancies.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = { viewModel.toggleCreateVacancyDialog(true) },
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text("Post Vacancy")
                }
            }
        }

        items(state.vacancies) { vacancy ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.selectVacancy(vacancy) },
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(Spacing.s4)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(Radius.pill),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = vacancy.jobCode,
                                modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(Radius.pill),
                            color = Color(0xFFE8F5E9),
                        ) {
                            Text(
                                text = vacancy.status.value,
                                modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Text(
                        text = vacancy.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${vacancy.departmentName ?: "Engineering"} • ${vacancy.location} • ${vacancy.experienceLevel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Text(
                        text = vacancy.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(Spacing.s3))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Openings: ${vacancy.openPositions}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        if (vacancy.minSalary != null && vacancy.maxSalary != null) {
                            Text(
                                text = "${vacancy.currency} ${vacancy.minSalary} - ${vacancy.maxSalary}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
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
// 2. Candidate Pipeline Tab
// =============================================================================
@Composable
private fun PipelineTab(state: RecruitmentState, viewModel: RecruitmentViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.s4)) {
        // Stage Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s3),
        ) {
            item {
                FilterChip(
                    selected = state.selectedStageFilter == null,
                    onClick = { viewModel.setStageFilter(null) },
                    label = { Text("All Stages (${state.applications.size})") },
                )
            }
            items(ApplicationItem.Stage.values()) { stage ->
                val count = state.applications.count { it.stage == stage }
                FilterChip(
                    selected = state.selectedStageFilter == stage,
                    onClick = { viewModel.setStageFilter(stage) },
                    label = { Text("${stage.value.replace('_', ' ')} ($count)") },
                )
            }
        }

        val filteredApps = state.applications.filter {
            state.selectedStageFilter == null || it.stage == state.selectedStageFilter
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filteredApps) { app ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.selectApplication(app) },
                    shape = RoundedCornerShape(Radius.card),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(Spacing.s4)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = app.candidateName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Surface(
                                shape = RoundedCornerShape(Radius.pill),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = app.stage.value.replace('_', ' '),
                                    modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }

                        Text(
                            text = "Applied for: ${app.vacancyTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(Spacing.s2))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Source: ${app.source}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (app.rating != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFA000), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${app.rating} / 5.0",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.s3))
                        // Quick Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.selectApplication(app)
                                    viewModel.toggleScheduleInterviewDialog(true)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Radius.control),
                            ) {
                                Text("Interview", style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = {
                                    viewModel.selectApplication(app)
                                    viewModel.toggleCreateOfferDialog(true)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(Radius.control),
                            ) {
                                Text("Extend Offer", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 3. Interviews Tab
// =============================================================================
@Composable
private fun InterviewsTab(state: RecruitmentState, viewModel: RecruitmentViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Scheduled Interview Rounds (${state.interviews.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Button(
                    onClick = { viewModel.toggleScheduleInterviewDialog(true) },
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text("Schedule")
                }
            }
        }

        items(state.interviews) { interview ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(Spacing.s4)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(Radius.pill),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "Round ${interview.interviewRound}: ${interview.interviewType.value}",
                                modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        val isDone = interview.status == InterviewItem.Status.COMPLETED
                        Surface(
                            shape = RoundedCornerShape(Radius.pill),
                            color = if (isDone) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                        ) {
                            Text(
                                text = interview.status.value,
                                modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDone) Color(0xFF2E7D32) else Color(0xFFE65100),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Text(
                        text = interview.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Candidate: ${interview.candidateName} • ${interview.vacancyTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(Spacing.s1))
                        Text(
                            text = interview.scheduledStart.format(DateTimeFormatter.ofPattern("MMM dd, yyyy • HH:mm")),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    interview.locationOrLink?.let { loc ->
                        Spacer(modifier = Modifier.height(Spacing.s1))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VideoCall, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(Spacing.s1))
                            Text(
                                text = loc,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.s3))
                    // Scorecard Evaluation button
                    Button(
                        onClick = {
                            viewModel.selectInterview(interview)
                            viewModel.toggleScorecardDialog(true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.control),
                    ) {
                        Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.s1))
                        Text(if (interview.scorecards.isEmpty()) "Evaluate & Submit Scorecard" else "Update Scorecard")
                    }
                }
            }
        }
    }
}

// =============================================================================
// 4. Offers & Scorecards Tab
// =============================================================================
@Composable
private fun OffersTab(state: RecruitmentState, viewModel: RecruitmentViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        item {
            Text(
                text = "Formal Offers Extended",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // Show offer card if available
        state.selectedOffer?.let { offer ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.card),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(modifier = Modifier.padding(Spacing.s4)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = offer.offerNumber,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Surface(
                                shape = RoundedCornerShape(Radius.pill),
                                color = Color(0xFFE8F5E9),
                            ) {
                                Text(
                                    text = offer.status.value,
                                    modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.s2))
                        Text(
                            text = "Base Compensation: ${offer.currency} ${offer.baseSalary}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (offer.variableBonus != null) {
                            Text(
                                text = "Annual Bonus: ${offer.currency} ${offer.variableBonus}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.s2))
                        Text(
                            text = "Target Start: ${offer.startDate} • Offer Expiry: ${offer.expiryDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )

                        offer.decisionNotes?.let { notes ->
                            Spacer(modifier = Modifier.height(Spacing.s2))
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        } ?: item {
            Text(
                text = "Select a candidate in the Pipeline tab to view or extend formal employment offers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Spacer(modifier = Modifier.height(Spacing.s2))
            Text(
                text = "Candidate Evaluation Scorecards",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        val allScorecards = state.interviews.flatMap { it.scorecards }
        items(allScorecards) { sc ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(Spacing.s4)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Evaluator: ${sc.interviewerName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        val isStrong = sc.overallRecommendation == InterviewScorecardItem.OverallRecommendation.STRONG_HIRE
                        Surface(
                            shape = RoundedCornerShape(Radius.pill),
                            color = if (isStrong) Color(0xFFE8F5E9) else Color(0xFFE3F2FD),
                        ) {
                            Text(
                                text = sc.overallRecommendation.value.replace('_', ' '),
                                modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isStrong) Color(0xFF2E7D32) else Color(0xFF1565C0),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.s2))
                    Text(
                        text = "Technical: ${sc.technicalSkillRating ?: "-"} / 5.0 • Problem Solving: ${sc.problemSolvingRating ?: "-"} / 5.0",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Communication: ${sc.communicationRating ?: "-"} / 5.0 • Cultural Fit: ${sc.culturalFitRating ?: "-"} / 5.0",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    sc.strengths?.let {
                        Spacer(modifier = Modifier.height(Spacing.s2))
                        Text(text = "Strengths: $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }

                    sc.summaryNotes?.let {
                        Spacer(modifier = Modifier.height(Spacing.s1))
                        Text(text = "Summary: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// =============================================================================
// Modals & Dialogs
// =============================================================================

@Composable
private fun CreateVacancyDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, BigDecimal?, BigDecimal?) -> Unit,
) {
    var jobCode by remember { mutableStateOf("VAC-2026-003") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var minSalary by remember { mutableStateOf("300000") }
    var maxSalary by remember { mutableStateOf("450000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post New Job Vacancy", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                OutlinedTextField(
                    value = jobCode,
                    onValueChange = { jobCode = it },
                    label = { Text("Job Code") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Job Title") },
                    placeholder = { Text("e.g. Senior Backend Engineer") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Job Description") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    OutlinedTextField(
                        value = minSalary,
                        onValueChange = { minSalary = it },
                        label = { Text("Min Salary (LKR)") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxSalary,
                        onValueChange = { maxSalary = it },
                        label = { Text("Max Salary (LKR)") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val min = minSalary.toBigDecimalOrNull()
                    val max = maxSalary.toBigDecimalOrNull()
                    if (title.isNotBlank() && description.isNotBlank()) {
                        onSubmit(jobCode, title, description, min, max)
                    }
                },
            ) {
                Text("Publish Vacancy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ScheduleInterviewDialog(
    applications: List<ApplicationItem>,
    onDismiss: () -> Unit,
    onSubmit: (UUID, String, InterviewScheduleRequest.InterviewType, OffsetDateTime, OffsetDateTime, String?) -> Unit,
) {
    var selectedAppId by remember { mutableStateOf(applications.firstOrNull()?.id ?: UUID.randomUUID()) }
    var title by remember { mutableStateOf("System Architecture & Deep Dive") }
    var meetingLink by remember { mutableStateOf("https://meet.google.com/ats-round-eval") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Interview Round", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Text("Candidate Application:", style = MaterialTheme.typography.labelMedium)
                applications.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAppId = app.id }
                            .padding(vertical = Spacing.s1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedAppId == app.id, onClick = { selectedAppId = app.id })
                        Spacer(modifier = Modifier.width(Spacing.s1))
                        Text("${app.candidateName} (${app.vacancyTitle})", style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Interview Session Title") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = meetingLink,
                    onValueChange = { meetingLink = it },
                    label = { Text("Video Conference Link") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = OffsetDateTime.now().plusDays(1).withHour(10).withMinute(0)
                    val end = start.plusHours(1)
                    onSubmit(selectedAppId, title, InterviewScheduleRequest.InterviewType.TECHNICAL, start, end, meetingLink)
                },
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ScorecardDialog(
    interview: InterviewItem,
    onDismiss: () -> Unit,
    onSubmit: (ScorecardSubmitRequest.OverallRecommendation, BigDecimal?, BigDecimal?, BigDecimal?, BigDecimal?, String?, String?, String?) -> Unit,
) {
    var recommendation by remember { mutableStateOf(ScorecardSubmitRequest.OverallRecommendation.STRONG_HIRE) }
    var techRating by remember { mutableStateOf("4.8") }
    var commRating by remember { mutableStateOf("4.5") }
    var problemRating by remember { mutableStateOf("4.9") }
    var fitRating by remember { mutableStateOf("4.7") }
    var strengths by remember { mutableStateOf("Excellent technical depth and systems thinking") }
    var weaknesses by remember { mutableStateOf("None observed") }
    var notes by remember { mutableStateOf("Strong candidate; recommend hire without reservation.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Interview Evaluation Scorecard", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Text("Recommendation:", style = MaterialTheme.typography.labelMedium)
                listOf(
                    ScorecardSubmitRequest.OverallRecommendation.STRONG_HIRE to "Strong Hire",
                    ScorecardSubmitRequest.OverallRecommendation.HIRE to "Hire",
                    ScorecardSubmitRequest.OverallRecommendation.NEUTRAL to "Neutral",
                    ScorecardSubmitRequest.OverallRecommendation.NO_HIRE to "No Hire",
                ).forEach { (rec, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { recommendation = rec },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = recommendation == rec, onClick = { recommendation = rec })
                        Spacer(modifier = Modifier.width(Spacing.s1))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    OutlinedTextField(
                        value = techRating,
                        onValueChange = { techRating = it },
                        label = { Text("Technical (1-5)") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = problemRating,
                        onValueChange = { problemRating = it },
                        label = { Text("Problem Solving") },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    OutlinedTextField(
                        value = commRating,
                        onValueChange = { commRating = it },
                        label = { Text("Communication") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fitRating,
                        onValueChange = { fitRating = it },
                        label = { Text("Cultural Fit") },
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = strengths,
                    onValueChange = { strengths = it },
                    label = { Text("Key Strengths") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Summary Evaluation Notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        recommendation,
                        techRating.toBigDecimalOrNull(),
                        commRating.toBigDecimalOrNull(),
                        problemRating.toBigDecimalOrNull(),
                        fitRating.toBigDecimalOrNull(),
                        strengths,
                        weaknesses,
                        notes,
                    )
                },
            ) {
                Text("Submit Evaluation")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CreateOfferDialog(
    application: ApplicationItem,
    onDismiss: () -> Unit,
    onSubmit: (BigDecimal, BigDecimal?, LocalDate, LocalDate) -> Unit,
) {
    var salary by remember { mutableStateOf("450000") }
    var bonus by remember { mutableStateOf("50000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extend Employment Offer", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Text("Candidate: ${application.candidateName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("Position: ${application.vacancyTitle}", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = salary,
                    onValueChange = { salary = it },
                    label = { Text("Monthly Base Salary (LKR)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bonus,
                    onValueChange = { bonus = it },
                    label = { Text("Performance Bonus (LKR)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Joining Date: In 30 days (standard notice)", style = MaterialTheme.typography.bodySmall)
                Text("Offer Validity: 14 days", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = salary.toBigDecimalOrNull() ?: BigDecimal("400000")
                    val b = bonus.toBigDecimalOrNull()
                    val start = LocalDate.now().plusMonths(1)
                    val exp = LocalDate.now().plusWeeks(2)
                    onSubmit(s, b, start, exp)
                },
            ) {
                Text("Extend Formal Offer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
