package com.hr.app.ui.training

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.*
import java.util.UUID

private val Radius.sm get() = Radius.control
private val Radius.md get() = Radius.card
private val Radius.lg get() = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    viewModel: TrainingViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Training & Development",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Upskilling, Certifications & Competency Growth",
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
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                // Banner / Stats Header
                item {
                    TrainingStatsHeader(uiState = uiState)
                }

                // Tab Selector
                item {
                    ScrollableTabRow(
                        selectedTabIndex = uiState.selectedTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        edgePadding = Spacing.s4,
                        modifier = Modifier.padding(horizontal = Spacing.s2, vertical = Spacing.s2),
                    ) {
                        TrainingTab.entries.forEach { tab ->
                            Tab(
                                selected = uiState.selectedTab == tab,
                                onClick = { viewModel.selectTab(tab) },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when (tab) {
                                                TrainingTab.CATALOG -> Icons.Default.MenuBook
                                                TrainingTab.MY_LEARNING -> Icons.Default.School
                                                TrainingTab.CERTIFICATES -> Icons.Default.Verified
                                                TrainingTab.SKILL_GAPS -> Icons.Default.TrendingUp
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(tab.label, fontWeight = FontWeight.SemiBold)
                                    }
                                },
                            )
                        }
                    }
                }

                // User Notification Banner
                if (uiState.userMessage != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            ),
                            shape = RoundedCornerShape(Radius.sm),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.s3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(Spacing.s2))
                                Text(
                                    text = uiState.userMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { viewModel.clearUserMessage() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Error Banner
                if (uiState.error != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(Radius.sm),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.s3),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.width(Spacing.s2))
                                Text(
                                    text = uiState.error ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { viewModel.clearError() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Tab Content
                when (uiState.selectedTab) {
                    TrainingTab.CATALOG -> {
                        item {
                            CourseCatalogTabContent(
                                uiState = uiState,
                                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                                onCategorySelected = viewModel::selectCategory,
                                onCourseDetail = viewModel::openCourseDetail,
                                onEnrollClick = viewModel::openEnrollDialog,
                            )
                        }
                    }
                    TrainingTab.MY_LEARNING -> {
                        item {
                            MyLearningTabContent(
                                uiState = uiState,
                                onCheckInAttendance = viewModel::checkInAttendance,
                                onEvaluateClick = viewModel::openEvaluationDialog,
                            )
                        }
                    }
                    TrainingTab.CERTIFICATES -> {
                        item {
                            CertificatesTabContent(
                                uiState = uiState,
                                onViewCertificate = viewModel::openCertificateViewer,
                            )
                        }
                    }
                    TrainingTab.SKILL_GAPS -> {
                        item {
                            SkillGapsTabContent(
                                uiState = uiState,
                                onCourseClicked = viewModel::openCourseDetail,
                            )
                        }
                    }
                }
            }

            // Loading Indicator
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    // Dialogs
    if (uiState.selectedCourseDetail != null) {
        CourseDetailDialog(
            detail = uiState.selectedCourseDetail!!,
            onDismiss = viewModel::closeCourseDetail,
            onEnrollBatch = { scheduleId ->
                viewModel.closeCourseDetail()
                viewModel.enrollInBatch(scheduleId)
            },
        )
    }

    if (uiState.isEnrollingCourse != null) {
        EnrollmentDialog(
            course = uiState.isEnrollingCourse!!,
            schedules = uiState.schedules.filter { it.courseId == uiState.isEnrollingCourse!!.id },
            onDismiss = viewModel::closeEnrollDialog,
            onEnrollBatch = viewModel::enrollInBatch,
        )
    }

    if (uiState.isEvaluatingEnrollment != null) {
        EvaluationDialog(
            enrollment = uiState.isEvaluatingEnrollment!!,
            onDismiss = viewModel::closeEvaluationDialog,
            onSubmit = { rating, content, instructor, feedback ->
                viewModel.submitEvaluation(
                    uiState.isEvaluatingEnrollment!!.id,
                    rating,
                    content,
                    instructor,
                    feedback,
                )
            },
        )
    }

    if (uiState.isViewingCertificate != null) {
        CertificateViewerDialog(
            certificate = uiState.isViewingCertificate!!,
            onDismiss = viewModel::closeCertificateViewer,
        )
    }
}

// -----------------------------------------------------------------------------
// Headers & Tabs
// -----------------------------------------------------------------------------

@Composable
private fun TrainingStatsHeader(uiState: TrainingUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.s4),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val enrolledCount = uiState.enrollments.count { it.status == "ENROLLED" }
            val completedCount = uiState.enrollments.count { it.status == "COMPLETED" }
            val certsCount = uiState.certificates.size
            val gapsCount = uiState.trainingNeeds.size

            StatCounterItem(label = "Enrolled", count = enrolledCount.toString(), icon = Icons.Default.School, color = Color(0xFF1976D2))
            StatCounterItem(label = "Completed", count = completedCount.toString(), icon = Icons.Default.CheckCircle, color = Color(0xFF388E3C))
            StatCounterItem(label = "Certificates", count = certsCount.toString(), icon = Icons.Default.Verified, color = Color(0xFFF57C00))
            StatCounterItem(label = "Skill Gaps", count = gapsCount.toString(), icon = Icons.Default.TrendingUp, color = Color(0xFF7B1FA2))
        }
    }
}

@Composable
private fun StatCounterItem(
    label: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(count, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// -----------------------------------------------------------------------------
// Course Catalog Tab
// -----------------------------------------------------------------------------

@Composable
private fun CourseCatalogTabContent(
    uiState: TrainingUiState,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onCourseDetail: (TrainingCourseItem) -> Unit,
    onEnrollClick: (TrainingCourseItem) -> Unit,
) {
    val categories = listOf("ALL", "TECHNICAL", "LEADERSHIP", "COMPLIANCE", "SOFT_SKILLS", "SECURITY")

    Column(modifier = Modifier.padding(horizontal = Spacing.s4)) {
        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("Search courses by title, topic, or code...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.s2),
        )

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            modifier = Modifier.padding(vertical = Spacing.s2),
        ) {
            items(categories) { cat ->
                val isSelected = if (cat == "ALL") uiState.selectedCategory == null else uiState.selectedCategory == cat
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onCategorySelected(if (cat == "ALL") null else cat)
                    },
                    label = { Text(cat.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.s2))

        if (uiState.courses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No courses match your filter criteria",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            uiState.courses.forEach { course ->
                CourseCard(
                    course = course,
                    onDetailClick = { onCourseDetail(course) },
                    onEnrollClick = { onEnrollClick(course) },
                )
                Spacer(modifier = Modifier.height(Spacing.s3))
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: TrainingCourseItem,
    onDetailClick: () -> Unit,
    onEnrollClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(Radius.md),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(category = course.category)
                Text(
                    text = course.courseCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            Text(
                text = course.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            if (!course.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.s1))
                Text(
                    text = course.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s3))

            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                MetadataBadge(icon = Icons.Default.Schedule, label = "${course.durationHours}h")
                MetadataBadge(icon = Icons.Default.Laptop, label = course.deliveryMode.replace("_", " "))
                MetadataBadge(icon = Icons.Default.People, label = "Cap: ${course.maxCapacity}")
            }

            Spacer(modifier = Modifier.height(Spacing.s3))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onDetailClick,
                    shape = RoundedCornerShape(Radius.sm),
                    modifier = Modifier.padding(end = Spacing.s2),
                ) {
                    Text("Details")
                }
                Button(
                    onClick = onEnrollClick,
                    shape = RoundedCornerShape(Radius.sm),
                ) {
                    Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enroll")
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: String) {
    val (bg, fg) = when (category) {
        "TECHNICAL" -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
        "LEADERSHIP" -> Pair(Color(0xFFEDE7F6), Color(0xFF512DA8))
        "COMPLIANCE" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "SECURITY" -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
        else -> Pair(Color(0xFFF3E5F5), Color(0xFF6A1B9A))
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(Radius.sm),
    ) {
        Text(
            text = category.replace("_", " "),
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MetadataBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// -----------------------------------------------------------------------------
// My Learning Tab
// -----------------------------------------------------------------------------

@Composable
private fun MyLearningTabContent(
    uiState: TrainingUiState,
    onCheckInAttendance: (UUID) -> Unit,
    onEvaluateClick: (TrainingEnrollmentItem) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.s4)) {
        if (uiState.enrollments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You haven't enrolled in any courses yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            uiState.enrollments.forEach { enrollment ->
                EnrollmentCard(
                    enrollment = enrollment,
                    onCheckInAttendance = { onCheckInAttendance(enrollment.id) },
                    onEvaluateClick = { onEvaluateClick(enrollment) },
                )
                Spacer(modifier = Modifier.height(Spacing.s3))
            }
        }
    }
}

@Composable
private fun EnrollmentCard(
    enrollment: TrainingEnrollmentItem,
    onCheckInAttendance: () -> Unit,
    onEvaluateClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(Radius.md),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Batch: ${enrollment.batchCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                StatusBadge(status = enrollment.status)
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            Text(
                text = enrollment.courseTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(Spacing.s2))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${enrollment.startDate.toLocalDate()}  to  ${enrollment.endDate.toLocalDate()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s3))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (enrollment.status == "ENROLLED") {
                    OutlinedButton(
                        onClick = onCheckInAttendance,
                        shape = RoundedCornerShape(Radius.sm),
                        modifier = Modifier.padding(end = Spacing.s2),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Check-In")
                    }
                    Button(
                        onClick = onEvaluateClick,
                        shape = RoundedCornerShape(Radius.sm),
                    ) {
                        Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Evaluate Course")
                    }
                } else if (enrollment.status == "COMPLETED") {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(Radius.sm),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Course Completed & Certified", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bg, fg) = when (status) {
        "ENROLLED" -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
        "COMPLETED" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "REQUESTED" -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
        "REJECTED" -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
        else -> Pair(Color(0xFFECEFF1), Color(0xFF37474F))
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(Radius.sm),
    ) {
        Text(
            text = status,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// -----------------------------------------------------------------------------
// Certificates Tab
// -----------------------------------------------------------------------------

@Composable
private fun CertificatesTabContent(
    uiState: TrainingUiState,
    onViewCertificate: (TrainingCertificateItem) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.s4)) {
        if (uiState.certificates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No certificates earned yet. Complete courses with >= 3.0 rating to earn certificates!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            uiState.certificates.forEach { cert ->
                CertificateCard(
                    certificate = cert,
                    onViewClick = { onViewCertificate(cert) },
                )
                Spacer(modifier = Modifier.height(Spacing.s3))
            }
        }
    }
}

@Composable
private fun CertificateCard(
    certificate: TrainingCertificateItem,
    onViewClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(Radius.md),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewClick),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFF8E1)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(0xFFFFA000),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.s3))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = certificate.courseTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Awarded to: ${certificate.employeeName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s2))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(Spacing.s2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "ID: ${certificate.certificateNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Issued: ${certificate.issuedDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            // SHA-256 hash seal preview
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(Radius.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SHA-256: ${certificate.verificationHash.take(16)}...",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Skill Gaps & TNA Tab
// -----------------------------------------------------------------------------

@Composable
private fun SkillGapsTabContent(
    uiState: TrainingUiState,
    onCourseClicked: (TrainingCourseItem) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.s4)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(Radius.md),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.AutoGraph, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(Spacing.s2))
                Text(
                    text = "Training Needs Analysis based on 360-degree appraisal feedback & competency targets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.s3))

        uiState.trainingNeeds.forEach { need ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(Radius.md),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(Spacing.s4)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = need.competencyName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(Radius.sm),
                        ) {
                            Text(
                                text = "+${need.gap} Gap",
                                color = Color(0xFFC62828),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.s2))

                    // Progress bar
                    val progress = (need.currentProficiency.toDouble() / 5.0).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(Spacing.s1))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Current: ${need.currentProficiency} / 5.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Target: ${need.targetProficiency} / 5.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (need.recommendedCourses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.s3))
                        Text(
                            text = "Recommended Courses to Close Gap:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(Spacing.s1))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                            items(need.recommendedCourses) { course ->
                                SuggestionChip(
                                    onClick = { onCourseClicked(course) },
                                    label = { Text(course.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    icon = { Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.s3))
        }
    }
}

// -----------------------------------------------------------------------------
// Dialogs
// -----------------------------------------------------------------------------

@Composable
private fun CourseDetailDialog(
    detail: CourseDetailResponse,
    onDismiss: () -> Unit,
    onEnrollBatch: (UUID) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            shape = RoundedCornerShape(Radius.lg),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.s4),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        CategoryBadge(category = detail.course.category)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = detail.course.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider()

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(Spacing.s4),
                ) {
                    item {
                        Text(text = "Course Description", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = detail.course.description ?: "Comprehensive hands-on curriculum.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(Spacing.s3))
                    }

                    if (!detail.course.targetAudience.isNullOrBlank()) {
                        item {
                            Text(text = "Target Audience", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = detail.course.targetAudience, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(Spacing.s3))
                        }
                    }

                    if (!detail.course.prerequisites.isNullOrBlank()) {
                        item {
                            Text(text = "Prerequisites", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = detail.course.prerequisites, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(Spacing.s3))
                        }
                    }

                    if (detail.provider != null) {
                        item {
                            Text(text = "Training Provider", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "${detail.provider.providerName} (${detail.provider.providerType})", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(Spacing.s3))
                        }
                    }

                    item {
                        Text(text = "Upcoming Scheduled Batches", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(Spacing.s2))
                    }

                    if (detail.schedules.isEmpty()) {
                        item {
                            Text("No active batches currently scheduled.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(detail.schedules) { batch ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(Radius.sm),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.s3),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(text = batch.batchCode, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${batch.startDate.toLocalDate()} - ${batch.endDate.toLocalDate()}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            text = "Seats: ${batch.enrolledSeats}/${batch.totalSeats}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Button(
                                        onClick = { onEnrollBatch(batch.id) },
                                        shape = RoundedCornerShape(Radius.sm),
                                        enabled = batch.enrolledSeats < batch.totalSeats,
                                    ) {
                                        Text("Enroll")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnrollmentDialog(
    course: TrainingCourseItem,
    schedules: List<TrainingScheduleItem>,
    onDismiss: () -> Unit,
    onEnrollBatch: (UUID) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(Spacing.s4)) {
                Text(
                    text = "Enroll in ${course.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(Spacing.s2))
                Text(
                    text = "Select an available batch schedule below to confirm your seat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.s3))

                if (schedules.isEmpty()) {
                    Text(
                        text = "No schedules found. Please contact HR or check back later.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    schedules.forEach { batch ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(Radius.sm),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.s3),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(text = batch.batchCode, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${batch.startDate.toLocalDate()}  to  ${batch.endDate.toLocalDate()}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = "${batch.enrolledSeats} of ${batch.totalSeats} booked",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                Button(
                                    onClick = { onEnrollBatch(batch.id) },
                                    shape = RoundedCornerShape(Radius.sm),
                                    enabled = batch.enrolledSeats < batch.totalSeats,
                                ) {
                                    Text("Select")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s3))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun EvaluationDialog(
    enrollment: TrainingEnrollmentItem,
    onDismiss: () -> Unit,
    onSubmit: (ratingScore: Int, contentRating: Int, instructorRating: Int, feedback: String) -> Unit,
) {
    var overallRating by remember { mutableIntStateOf(5) }
    var contentRating by remember { mutableIntStateOf(5) }
    var instructorRating by remember { mutableIntStateOf(5) }
    var feedback by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(Spacing.s4)) {
                Text(
                    text = "Kirkpatrick Course Evaluation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = enrollment.courseTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.s3))

                Text("Overall Course Rating", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                StarRatingBar(rating = overallRating, onRatingChanged = { overallRating = it })

                Spacer(modifier = Modifier.height(Spacing.s2))
                Text("Content & Materials Quality", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                StarRatingBar(rating = contentRating, onRatingChanged = { contentRating = it })

                Spacer(modifier = Modifier.height(Spacing.s2))
                Text("Instructor Effectiveness", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                StarRatingBar(rating = instructorRating, onRatingChanged = { instructorRating = it })

                Spacer(modifier = Modifier.height(Spacing.s3))
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    label = { Text("Feedback & Key Takeaways (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )

                Spacer(modifier = Modifier.height(Spacing.s3))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Button(
                        onClick = {
                            onSubmit(overallRating, contentRating, instructorRating, feedback)
                        },
                        shape = RoundedCornerShape(Radius.sm),
                    ) {
                        Text("Submit & Issue Cert")
                    }
                }
            }
        }
    }
}

@Composable
private fun StarRatingBar(
    rating: Int,
    onRatingChanged: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (1..5).forEach { index ->
            IconButton(
                onClick = { onRatingChanged(index) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (index <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "$index Stars",
                    tint = if (index <= rating) Color(0xFFFFB300) else Color.Gray,
                )
            }
        }
    }
}

@Composable
private fun CertificateViewerDialog(
    certificate: TrainingCertificateItem,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(Radius.lg),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.s4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFF8E1)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(0xFFFFA000),
                        modifier = Modifier.size(36.dp),
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.s2))
                Text(
                    text = "Certificate of Completion",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Official Enterprise Credential",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(Spacing.s3))
                Text(
                    text = "This certifies that",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = certificate.employeeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(Spacing.s1))
                Text(
                    text = "has successfully completed the training course",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = certificate.courseTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(Spacing.s3))
                Divider()
                Spacer(modifier = Modifier.height(Spacing.s2))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Certificate No:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(certificate.certificateNumber, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Date Issued:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${certificate.issuedDate}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s2))

                // Verification Hash Seal
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(Radius.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(Spacing.s2)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cryptographically Verified Credential", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        Text(
                            text = certificate.verificationHash,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s3))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(Radius.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Close")
                }
            }
        }
    }
}
