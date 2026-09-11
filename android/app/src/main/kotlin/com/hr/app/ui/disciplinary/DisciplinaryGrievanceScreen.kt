package com.hr.app.ui.disciplinary

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.CorrectiveActionItem
import com.hr.client.model.DisciplinaryIncidentItem
import com.hr.client.model.GrievanceChannelItem
import com.hr.client.model.GrievanceDetailResponse
import com.hr.client.model.GrievanceGroundItem
import com.hr.client.model.GrievanceItem
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisciplinaryGrievanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: DisciplinaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groundsResponse by viewModel.grounds.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val grievances by viewModel.grievances.collectAsStateWithLifecycle()
    val selectedGrievance by viewModel.selectedGrievance.collectAsStateWithLifecycle()
    val pendingGrievanceCount by viewModel.pendingGrievanceCount.collectAsStateWithLifecycle()

    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    val selectedIncident by viewModel.selectedIncident.collectAsStateWithLifecycle()
    val openIncidentCount by viewModel.openIncidentCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Disciplinary & Grievances",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Fair Workplace & Whistleblower Redressal",
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Error / Success Banners
            uiState.errorBanner?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.dismissBanners() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            uiState.successBanner?.let { msg ->
                Surface(
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                        )
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text(
                            text = msg,
                            color = Color(0xFF1B5E20),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.dismissBanners() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            // Primary 4 Tabs
            PrimaryTabRow(
                selectedTabIndex = uiState.activeTab.ordinal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = uiState.activeTab == DisciplinaryGrievanceTab.MY_GRIEVANCES,
                    onClick = { viewModel.selectTab(DisciplinaryGrievanceTab.MY_GRIEVANCES) },
                    text = {
                        Text(
                            "Grievances" + if (pendingGrievanceCount > 0) " ($pendingGrievanceCount)" else "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                Tab(
                    selected = uiState.activeTab == DisciplinaryGrievanceTab.FILE_GRIEVANCE,
                    onClick = { viewModel.selectTab(DisciplinaryGrievanceTab.FILE_GRIEVANCE) },
                    text = { Text("File", maxLines = 1) },
                )
                Tab(
                    selected = uiState.activeTab == DisciplinaryGrievanceTab.DISCIPLINARY_NOTICES,
                    onClick = { viewModel.selectTab(DisciplinaryGrievanceTab.DISCIPLINARY_NOTICES) },
                    text = {
                        Text(
                            "Notices" + if (openIncidentCount > 0) " ($openIncidentCount)" else "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                Tab(
                    selected = uiState.activeTab == DisciplinaryGrievanceTab.HOTLINE,
                    onClick = { viewModel.selectTab(DisciplinaryGrievanceTab.HOTLINE) },
                    text = { Text("Hotline", maxLines = 1) },
                )
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (uiState.activeTab) {
                        DisciplinaryGrievanceTab.MY_GRIEVANCES -> {
                            MyGrievancesTab(
                                grievances = grievances,
                                pendingCount = pendingGrievanceCount,
                                totalCount = grievances.size,
                                onGrievanceClick = { viewModel.openGrievanceDetail(it) },
                                onFileGrievanceClick = { viewModel.selectTab(DisciplinaryGrievanceTab.FILE_GRIEVANCE) },
                            )
                        }
                        DisciplinaryGrievanceTab.FILE_GRIEVANCE -> {
                            FileGrievanceTab(
                                uiState = uiState,
                                grounds = groundsResponse?.grounds ?: emptyList(),
                                channels = channels,
                                onTitleChange = { viewModel.updateGrievanceForm(title = it) },
                                onDescriptionChange = { viewModel.updateGrievanceForm(description = it) },
                                onGroundSelect = { viewModel.updateGrievanceForm(groundId = it) },
                                onChannelSelect = { viewModel.updateGrievanceForm(channelId = it) },
                                onToggleAnonymous = { viewModel.toggleAnonymous(it) },
                                onSubmit = { viewModel.submitGrievance() },
                            )
                        }
                        DisciplinaryGrievanceTab.DISCIPLINARY_NOTICES -> {
                            DisciplinaryNoticesTab(
                                incidents = incidents,
                                onIncidentClick = { viewModel.openIncidentDetail(it) },
                            )
                        }
                        DisciplinaryGrievanceTab.HOTLINE -> {
                            WhistleblowerHotlineTab()
                        }
                    }
                }
            }
        }
    }

    // Dialog: Grievance Detail
    if (uiState.showGrievanceDetailDialog && selectedGrievance != null) {
        val grievance = selectedGrievance!!
        AlertDialog(
            onDismissRequest = { viewModel.closeGrievanceDetail() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Text(grievance.grievanceNumber, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    StatusBadge(
                        label = grievance.status.name.replace("_", " "),
                        color = getGrievanceDetailStatusColor(grievance.status),
                    )
                    Text(
                        text = grievance.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.s1))
                    DetailRow(label = "Category", value = "${grievance.ground.name} (${grievance.ground.groupName})")
                    DetailRow(label = "Channel", value = grievance.channel.name)
                    DetailRow(label = "Raised By", value = grievance.raisedByEmployeeName ?: "Employee")
                    DetailRow(label = "Target SLA Resolution", value = grievance.targetResolutionDate.toString().take(10))

                    Spacer(modifier = Modifier.height(Spacing.s1))
                    Text("Description Details:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(Radius.control),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = grievance.description,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(Spacing.s2),
                        )
                    }

                    grievance.resolution?.let { notes ->
                        Spacer(modifier = Modifier.height(Spacing.s1))
                        Text("Resolution Findings:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(Radius.control),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1B5E20),
                                modifier = Modifier.padding(Spacing.s2),
                            )
                        }
                    }

                    grievance.appeal?.let { appeal ->
                        Spacer(modifier = Modifier.height(Spacing.s1))
                        Text("Appealed by Employee:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Surface(
                            color = Color(0xFFEDE7F6),
                            shape = RoundedCornerShape(Radius.control),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.s2)) {
                                Text(
                                    text = "Status: ${appeal.status}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4A148C),
                                )
                                Text(text = appeal.reason, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (grievance.status == GrievanceDetailResponse.Status.RESOLVED && grievance.appeal == null) {
                    Button(
                        onClick = {
                            viewModel.closeGrievanceDetail()
                            viewModel.openGrievanceAppeal(grievance.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text("Lodge Appeal")
                    }
                } else {
                    TextButton(onClick = { viewModel.closeGrievanceDetail() }) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (grievance.status == GrievanceDetailResponse.Status.RESOLVED && grievance.appeal == null) {
                    TextButton(onClick = { viewModel.closeGrievanceDetail() }) {
                        Text("Dismiss")
                    }
                }
            },
        )
    }

    // Dialog: Grievance Appeal
    if (uiState.showGrievanceAppealDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.closeGrievanceAppeal() },
            title = { Text("Lodge Grievance Appeal", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Text(
                        "If you find the grievance resolution decision inadequate or flawed, you have the right under enterprise policy to request secondary investigation review.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = uiState.appealReasonText,
                        onValueChange = { viewModel.updateGrievanceAppealReason(it) },
                        label = { Text("Grounds & Reasons for Appeal") },
                        placeholder = { Text("Explain why the resolution was insufficient...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitGrievanceAppeal() },
                    enabled = !uiState.isSubmittingAppeal,
                ) {
                    if (uiState.isSubmittingAppeal) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Submit Appeal")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeGrievanceAppeal() }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Dialog: Respond to Show Cause Action
    if (uiState.showActionResponseDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.closeActionResponse() },
            title = { Text("Respond to Show Cause Notice", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Text(
                        "Provide your formal statement and explanation in response to the issued show cause notice. Your submission will be entered into the official domestic inquiry record.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = uiState.responseExplanationText,
                        onValueChange = { viewModel.updateResponseExplanation(it) },
                        label = { Text("Employee Explanation Statement") },
                        placeholder = { Text("Detail the circumstances and any mitigating factors...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitActionResponse() },
                    enabled = !uiState.isSubmittingResponse,
                ) {
                    if (uiState.isSubmittingResponse) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Submit Response")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeActionResponse() }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Dialog: Appeal Disciplinary Action
    if (uiState.showActionAppealDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.closeActionAppeal() },
            title = { Text("Lodge Disciplinary Action Appeal", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Text(
                        "You may appeal confirmed disciplinary sanctions to the Disciplinary Review Board on grounds of procedural defect, disproportionate penalty, or newly uncovered evidence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = uiState.actionAppealReasonText,
                        onValueChange = { viewModel.updateActionAppealReason(it) },
                        label = { Text("Reasons & Evidence for Disciplinary Appeal") },
                        placeholder = { Text("State your grounds of appeal clearly...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitActionAppeal() },
                    enabled = !uiState.isSubmittingActionAppeal,
                ) {
                    if (uiState.isSubmittingActionAppeal) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("Lodge Appeal")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeActionAppeal() }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Modal Bottom Sheet: Incident Details, Corrective Actions & Domestic Inquiry Journal
    if (uiState.showIncidentDetailSheet && selectedIncident != null) {
        val detail = selectedIncident!!
        val incident = detail.incident
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeIncidentDetail() },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.s4)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s4),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(incident.incidentNumber, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${incident.typeName} • ${incident.subtypeName ?: "General"}", style = MaterialTheme.typography.bodySmall)
                    }
                    StatusBadge(label = incident.status.name.replace("_", " "), color = getIncidentStatusColor(incident.status))
                }

                HorizontalDivider()

                DetailRow(label = "Employee Involved", value = incident.employeeName)
                DetailRow(label = "Reported By", value = incident.reportedByEmployeeName)
                DetailRow(label = "Date Occurred", value = incident.incidentDate.toString())
                incident.location?.let { DetailRow(label = "Location", value = it) }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(Radius.control),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(Spacing.s2)) {
                        Text("Incident Summary:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text(incident.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Corrective Actions within Bottom Sheet
                if (detail.correctiveActions.isNotEmpty()) {
                    Text("Issued Corrective Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    detail.correctiveActions.forEach { action ->
                        CorrectiveActionCard(
                            action = action,
                            onRespond = {
                                viewModel.closeIncidentDetail()
                                viewModel.openActionResponse(action.id)
                            },
                            onAppeal = {
                                viewModel.closeIncidentDetail()
                                viewModel.openActionAppeal(action.id)
                            },
                        )
                    }
                }

                Text("Domestic Inquiry Chronology & Journal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                detail.journalEntries.forEach { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(Radius.control),
                    ) {
                        Column(modifier = Modifier.padding(Spacing.s2)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(entry.enteredByName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(entry.enteredAt.toString().take(10), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(Spacing.s1))
                            Text(entry.entry, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s6))
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: MY GRIEVANCES
// -------------------------------------------------------------
@Composable
private fun MyGrievancesTab(
    grievances: List<GrievanceItem>,
    pendingCount: Int,
    totalCount: Int,
    onGrievanceClick: (UUID) -> Unit,
    onFileGrievanceClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(Radius.card),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.s4),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetricItem(count = totalCount.toString(), label = "Total Filed")
                    MetricItem(count = pendingCount.toString(), label = "Active Inquiries", isHighlight = pendingCount > 0)
                    MetricItem(
                        count = grievances.count { it.status == GrievanceItem.Status.RESOLVED }.toString(),
                        label = "Resolved",
                    )
                }
            }
        }

        if (grievances.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.s6),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(Radius.card),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.s6),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "No Grievances Filed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "All workplace concerns and issues submitted via confidential channels will appear here with real-time SLA tracking.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(Spacing.s1))
                        Button(
                            onClick = onFileGrievanceClick,
                            shape = RoundedCornerShape(Radius.pill),
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.s1))
                            Text("File a Grievance")
                        }
                    }
                }
            }
        } else {
            items(grievances, key = { it.id }) { grievance ->
                GrievanceCard(grievance = grievance, onClick = { onGrievanceClick(grievance.id) })
            }
        }
    }
}

@Composable
private fun GrievanceCard(
    grievance: GrievanceItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(Radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = grievance.grievanceNumber,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                StatusBadge(
                    label = grievance.status.name.replace("_", " "),
                    color = getGrievanceStatusColor(grievance.status),
                )
            }

            Text(
                text = grievance.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${grievance.groundName} • ${grievance.channelName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (grievance.anonymous) {
                    Surface(
                        color = Color(0xFFE0F2F1),
                        shape = RoundedCornerShape(Radius.control),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color(0xFF00796B),
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Anonymous", style = MaterialTheme.typography.labelSmall, color = Color(0xFF00796B))
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Resolution SLA: " + grievance.targetResolutionDate.toString().take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "View details →",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: FILE GRIEVANCE FORM
// -------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileGrievanceTab(
    uiState: DisciplinaryGrievanceUiState,
    grounds: List<GrievanceGroundItem>,
    channels: List<GrievanceChannelItem>,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onGroundSelect: (UUID) -> Unit,
    onChannelSelect: (UUID) -> Unit,
    onToggleAnonymous: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.isAnonymous) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = RoundedCornerShape(Radius.card),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (uiState.isAnonymous) Icons.Default.Lock else Icons.Default.Security,
                    contentDescription = null,
                    tint = if (uiState.isAnonymous) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.s4))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.isAnonymous) "Anonymous Whistleblower Protection Active" else "Standard Grievance Filing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isAnonymous) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (uiState.isAnonymous) "Your identity will NOT be disclosed to management or committee members." else "Submit under your employee record with standard confidentiality.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.isAnonymous,
                    onCheckedChange = onToggleAnonymous,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF2E7D32)),
                )
            }
        }

        Text("1. Select Grievance Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            verticalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            grounds.forEach { ground ->
                val isSelected = uiState.selectedGroundId == ground.id || (uiState.selectedGroundId == null && ground == grounds.firstOrNull())
                FilterChip(
                    selected = isSelected,
                    onClick = { onGroundSelect(ground.id) },
                    label = { Text("${ground.name} (${ground.slaDays}d SLA)") },
                    leadingIcon = if (isSelected) {
                        { Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }

        Text("2. Preferred Confidential Channel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            verticalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            channels.forEach { channel ->
                val isSelected = uiState.selectedChannelId == channel.id || (uiState.selectedChannelId == null && channel == channels.firstOrNull())
                FilterChip(
                    selected = isSelected,
                    onClick = { onChannelSelect(channel.id) },
                    label = { Text(channel.name) },
                    leadingIcon = if (isSelected) {
                        { Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }

        Text("3. Details of Concern", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = uiState.grievanceTitle,
            onValueChange = onTitleChange,
            label = { Text("Grievance Headline / Summary") },
            placeholder = { Text("e.g. Unfair overtime deduction, Safety railing breach") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = uiState.grievanceDescription,
            onValueChange = onDescriptionChange,
            label = { Text("Detailed Statement & Evidence") },
            placeholder = { Text("Describe dates, locations, persons involved, and sequence of events...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
        )

        Spacer(modifier = Modifier.height(Spacing.s1))

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(Radius.control),
            enabled = !uiState.isSubmittingGrievance,
        ) {
            if (uiState.isSubmittingGrievance) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            } else {
                Text("Submit Formal Grievance", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(Spacing.s6))
    }
}

// -------------------------------------------------------------
// TAB 3: DISCIPLINARY NOTICES
// -------------------------------------------------------------
@Composable
private fun DisciplinaryNoticesTab(
    incidents: List<DisciplinaryIncidentItem>,
    onIncidentClick: (UUID) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(Radius.card),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.s4))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enterprise Disciplinary Records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Official corrective actions, domestic inquiries, and formal show-cause notices issued under progressive discipline policy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (incidents.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.s6),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                    shape = RoundedCornerShape(Radius.card),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.s6),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF2E7D32),
                        )
                        Text(
                            text = "Clean Disciplinary Record",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20),
                        )
                        Text(
                            text = "You have no active disciplinary inquiries, warnings, or show cause notices on your employment file.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF33691E),
                        )
                    }
                }
            }
        } else {
            items(incidents, key = { it.id }) { incident ->
                IncidentCard(
                    incident = incident,
                    onClick = { onIncidentClick(incident.id) },
                )
            }
        }
    }
}

@Composable
private fun IncidentCard(
    incident: DisciplinaryIncidentItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(Radius.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = incident.incidentNumber,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                StatusBadge(
                    label = incident.status.name.replace("_", " "),
                    color = getIncidentStatusColor(incident.status),
                )
            }

            Text(
                text = "${incident.typeName} • ${incident.subtypeName ?: "General"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = incident.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Incident Date: ${incident.incidentDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "View Details & Journal →",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CorrectiveActionCard(
    action: CorrectiveActionItem,
    onRespond: () -> Unit,
    onAppeal: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(Radius.control),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.s2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = action.actionType.name.replace("_", " "),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                StatusBadge(
                    label = action.status.name,
                    color = getActionStatusColor(action.status),
                )
            }
            Text(action.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(action.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            action.responseDueDate?.let { dueDate ->
                Text("Response Due: $dueDate", style = MaterialTheme.typography.labelSmall, color = Color.Red)
            }

            action.employeeResponse?.let { resp ->
                Spacer(modifier = Modifier.height(Spacing.s1))
                Text("Your Submitted Response:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text(resp, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1B5E20))
            }

            if (action.actionType == CorrectiveActionItem.ActionType.SHOW_CAUSE && action.status == CorrectiveActionItem.Status.ISSUED) {
                Spacer(modifier = Modifier.height(Spacing.s1))
                Button(
                    onClick = onRespond,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Text("Submit Show Cause Explanation")
                }
            }

            if ((action.actionType == CorrectiveActionItem.ActionType.WRITTEN_WARNING || action.actionType == CorrectiveActionItem.ActionType.SUSPENSION) &&
                action.status == CorrectiveActionItem.Status.CONFIRMED
            ) {
                Spacer(modifier = Modifier.height(Spacing.s1))
                OutlinedButton(
                    onClick = onAppeal,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Text("Lodge Disciplinary Appeal")
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 4: WHISTLEBLOWER HOTLINE & INTEGRITY
// -------------------------------------------------------------
@Composable
private fun WhistleblowerHotlineTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E)),
            shape = RoundedCornerShape(Radius.card),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.s6),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = "Ethics & Compliance Hotline",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "Confidential 24/7 channel for reporting fraud, bribery, workplace discrimination, and regulatory violations.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }

        Text("Emergency Contact Points", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        ContactCard(
            icon = Icons.Default.Call,
            title = "Toll-Free Whistleblower Hotline",
            subtitle = "+1-800-HR-ETHICS (Available 24/7/365)",
        )

        ContactCard(
            icon = Icons.Default.Email,
            title = "Encrypted Whistleblower Inbox",
            subtitle = "whistleblower@acmecorp.com (PGP Key available)",
        )

        ContactCard(
            icon = Icons.Default.Policy,
            title = "Independent Ethics Ombudsperson",
            subtitle = "Direct liaison outside operational hierarchy",
        )

        Spacer(modifier = Modifier.height(Spacing.s1))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(Radius.card),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.s4),
                verticalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Text("Strict Non-Retaliation Policy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    "Our enterprise code of conduct strictly prohibits retaliation of any form against employees who lodge grievances or whistleblowing disclosures in good faith. Reports are investigated with utmost confidentiality.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ContactCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.control),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(Spacing.s4))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// -------------------------------------------------------------
// HELPER COMPONENTS & BADGES
// -------------------------------------------------------------
@Composable
private fun MetricItem(
    count: String,
    label: String,
    isHighlight: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(Radius.pill),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

private fun getGrievanceStatusColor(status: GrievanceItem.Status): Color = when (status) {
    GrievanceItem.Status.SUBMITTED -> Color(0xFFF57C00) // Amber
    GrievanceItem.Status.ASSIGNED,
    GrievanceItem.Status.UNDER_INVESTIGATION -> Color(0xFF1976D2) // Blue
    GrievanceItem.Status.RESOLVED -> Color(0xFF388E3C) // Green
    GrievanceItem.Status.APPEALED -> Color(0xFF7B1FA2) // Purple
    GrievanceItem.Status.CLOSED -> Color(0xFF616161) // Gray
}

private fun getGrievanceDetailStatusColor(status: GrievanceDetailResponse.Status): Color = when (status) {
    GrievanceDetailResponse.Status.SUBMITTED -> Color(0xFFF57C00)
    GrievanceDetailResponse.Status.ASSIGNED,
    GrievanceDetailResponse.Status.UNDER_INVESTIGATION -> Color(0xFF1976D2)
    GrievanceDetailResponse.Status.RESOLVED -> Color(0xFF388E3C)
    GrievanceDetailResponse.Status.APPEALED -> Color(0xFF7B1FA2)
    GrievanceDetailResponse.Status.CLOSED -> Color(0xFF616161)
}

private fun getIncidentStatusColor(status: DisciplinaryIncidentItem.Status): Color = when (status) {
    DisciplinaryIncidentItem.Status.REPORTED -> Color(0xFFF57C00)
    DisciplinaryIncidentItem.Status.UNDER_INVESTIGATION,
    DisciplinaryIncidentItem.Status.ACTION_PROPOSED -> Color(0xFF1976D2)
    DisciplinaryIncidentItem.Status.ACTION_ISSUED -> Color(0xFFD32F2F)
    DisciplinaryIncidentItem.Status.APPEALED -> Color(0xFF7B1FA2)
    DisciplinaryIncidentItem.Status.CONCLUDED -> Color(0xFF388E3C)
}

private fun getActionStatusColor(status: CorrectiveActionItem.Status): Color = when (status) {
    CorrectiveActionItem.Status.ISSUED -> Color(0xFFF57C00)
    CorrectiveActionItem.Status.RESPONDED,
    CorrectiveActionItem.Status.UNDER_REVIEW -> Color(0xFF1976D2)
    CorrectiveActionItem.Status.CONFIRMED -> Color(0xFFD32F2F)
    CorrectiveActionItem.Status.APPEALED -> Color(0xFF7B1FA2)
    CorrectiveActionItem.Status.REVOKED -> Color(0xFF616161)
}
