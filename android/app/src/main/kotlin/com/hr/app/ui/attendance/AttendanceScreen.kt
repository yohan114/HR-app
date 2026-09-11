package com.hr.app.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.AttendancePunchItem
import com.hr.client.model.AttendanceShiftInfo
import com.hr.client.model.BranchGeofenceInfo
import com.hr.client.model.ShiftRosterDayItem
import com.hr.client.model.TeamCoverageMemberItem
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startClockTicker()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.s4),
        contentPadding = PaddingValues(vertical = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        // 1. Screen Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Time & Attendance",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Self-service mobile punch & geofence sync",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = { viewModel.loadToday(isRefresh = true) }) {
                    if (state.refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh attendance",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 2. Offline Outbox Sync Banner (if pending mutations exist)
        if (state.pendingOutboxCount > 0) {
            item {
                OfflineOutboxBanner(pendingCount = state.pendingOutboxCount)
            }
        }

        // 3. Action Feedback Toast / Alert
        if (state.actionMessage != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(Radius.control),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text(
                            text = state.actionMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        // 3.5. Mode Selector Tabs: "Today's Clock" vs "Shift Roster & Team"
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(Radius.control),
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = state.selectedTab == AttendanceTab.TODAY_CLOCK,
                    onClick = { viewModel.selectTab(AttendanceTab.TODAY_CLOCK) },
                    label = { Text("Today's Clock", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(Radius.control),
                )
                FilterChip(
                    selected = state.selectedTab == AttendanceTab.SHIFT_ROSTER,
                    onClick = { viewModel.selectTab(AttendanceTab.SHIFT_ROSTER) },
                    label = { Text("Shift Roster & Team", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(Radius.control),
                )
            }
        }

        if (state.selectedTab == AttendanceTab.TODAY_CLOCK) {
            // 4. Shift Header Card
            item {
                ShiftCard(shift = state.attendance?.shift)
            }

            // 5. Digital Clock & Status Card
            item {
                DigitalClockCard(
                    time = state.currentTime,
                    status = state.attendance?.currentStatus ?: "NOT_CHECKED_IN",
                )
            }

            // 6. Tactile Action Buttons
            item {
                PunchActionControls(
                    status = state.attendance?.currentStatus ?: "NOT_CHECKED_IN",
                    onPunch = viewModel::punch,
                )
            }

            // 7. Geofence & Location Simulator Card
            item {
                GeofenceSimulatorCard(
                    geofence = state.attendance?.geofence,
                    selectedPreset = state.selectedLocationPreset,
                    onSelectPreset = viewModel::selectLocationPreset,
                )
            }

            // 8. Worked Hours Summary
            item {
                WorkedSummaryCard(
                    workedMinutes = state.attendance?.workedMinutes ?: 0,
                    firstInAt = state.attendance?.firstInAt,
                    lastOutAt = state.attendance?.lastOutAt,
                )
            }

            // 9. Punch Activity Timeline
            item {
                Text(
                    text = "Today's Punch Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = Spacing.s2),
                )
            }

            val punches = state.attendance?.punches.orEmpty()
            if (punches.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(Radius.control),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "No clock events recorded yet today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.s4),
                        )
                    }
                }
            } else {
                items(punches.reversed()) { punch ->
                    PunchTimelineItem(punch = punch)
                }
            }
        } else {
            // Shift Roster & Team Schedule Section
            item {
                WeekNavigatorHeader(
                    currentWeekStart = state.currentWeekStart,
                    onPrevWeek = { viewModel.changeWeek(-1) },
                    onNextWeek = { viewModel.changeWeek(1) },
                )
            }

            item {
                WeeklyCalendarStrip(
                    days = state.rosterDays,
                    selectedDate = state.selectedRosterDate,
                    onSelectDate = viewModel::selectRosterDate,
                )
            }

            val selectedDayItem = state.rosterDays.find { it.date == state.selectedRosterDate }
            item {
                SelectedDayShiftDetailCard(
                    dayItem = selectedDayItem,
                    selectedDate = state.selectedRosterDate,
                    onSwapClick = viewModel::openSwapDialog,
                    onRegulariseClick = viewModel::openRegulariseDialog,
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.s2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Team Coverage (${state.selectedRosterDate.format(DateTimeFormatter.ofPattern("MMM dd"))})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        val totalSched = state.teamCoverage?.totalScheduled ?: 0
                        val totalDuty = state.teamCoverage?.totalOnDuty ?: 0
                        Text(
                            text = "$totalSched scheduled • $totalDuty on duty",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (state.teamCoverageLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }

            val members = state.teamCoverage?.members.orEmpty()
            if (members.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(Radius.control),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "No team coverage data recorded for this date.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.s4),
                        )
                    }
                }
            } else {
                items(members) { member ->
                    TeamCoverageMemberCard(member = member)
                }
            }
        }
    }

    if (state.isSwapDialogOpen) {
        ShiftSwapDialog(
            selectedDate = state.selectedRosterDate,
            teamMembers = state.teamCoverage?.members.orEmpty(),
            isSubmitting = state.swapSubmitting,
            onDismiss = viewModel::closeSwapDialog,
            onSubmit = { targetEmpId, targetDate, reason ->
                viewModel.submitSwap(targetEmpId, targetDate, reason)
            },
        )
    }

    if (state.isRegulariseDialogOpen) {
        AttendanceRegulariseDialog(
            workDate = state.selectedRosterDate,
            isSubmitting = state.regulariseSubmitting,
            onDismiss = viewModel::closeRegulariseDialog,
            onSubmit = { inTime, outTime, reason ->
                viewModel.submitRegularise(inTime, outTime, reason)
            },
        )
    }
}

@Composable
private fun OfflineOutboxBanner(pendingCount: Int) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(Radius.control),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.s2))
            Column {
                Text(
                    text = "Offline Outbox Active",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "$pendingCount punch mutation queued locally. Idempotency guaranteed; will sync automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ShiftCard(shift: AttendanceShiftInfo?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(Radius.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shift?.name ?: "General Day Shift",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = shift?.code ?: "GEN_0830",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Shift Hours",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${shift?.startTime?.take(5) ?: "08:30"} - ${shift?.endTime?.take(5) ?: "17:30"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Column {
                    Text(
                        text = "Grace Limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "08:45 AM (${shift?.graceInMinutes ?: 15}m grace)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1B7F4B),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Column {
                    Text(
                        text = "Meal Break",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${shift?.breakMinutes ?: 60} mins",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DigitalClockCard(time: LocalTime, status: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.s6, horizontal = Spacing.s4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val formattedDate = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"))
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(Spacing.s1))

            // Large Digital Monospace Time
            val formattedTime = time.format(DateTimeFormatter.ofPattern("hh:mm:ss a"))
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(Spacing.s3))

            // Status Chip
            val (statusColor, statusText, iconDot) = when (status) {
                "CHECKED_IN" -> Triple(Color(0xFF1B7F4B), "Checked In (On Duty)", "●")
                "ON_BREAK" -> Triple(Color(0xFF8A5A00), "On Meal Break", "●")
                "CHECKED_OUT" -> Triple(Color(0xFF1B5E9C), "Shift Completed", "✔")
                else -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "Not Checked In", "○")
            }

            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(Radius.pill),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.s3, vertical = Spacing.s1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$iconDot ",
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun PunchActionControls(
    status: String,
    onPunch: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        when (status) {
            "NOT_CHECKED_IN" -> {
                Button(
                    onClick = { onPunch("IN") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(Radius.control),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B7F4B)),
                ) {
                    Icon(imageVector = Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Text("Clock In for Shift", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            "CHECKED_IN" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    FilledTonalButton(
                        onClick = { onPunch("BREAK_OUT") },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(Radius.control),
                    ) {
                        Icon(imageVector = Icons.Default.Coffee, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text("Start Break", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { onPunch("OUT") },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(Radius.control),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(imageVector = Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text("Clock Out", fontWeight = FontWeight.Bold)
                    }
                }
            }

            "ON_BREAK" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    Button(
                        onClick = { onPunch("BREAK_IN") },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(Radius.control),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B7F4B)),
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text("Resume Work", fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { onPunch("OUT") },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(Radius.control),
                    ) {
                        Icon(imageVector = Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text("Clock Out", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            "CHECKED_OUT" -> {
                OutlinedButton(
                    onClick = { onPunch("IN") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Icon(imageVector = Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Text("Record Additional In-Punch", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GeofenceSimulatorCard(
    geofence: BranchGeofenceInfo?,
    selectedPreset: GeoSimulationPreset,
    onSelectPreset: (GeoSimulationPreset) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(Radius.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Text(
                        text = geofence?.branchName ?: "Colombo Head Office",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                val statusText = if (selectedPreset.id == "HQ_INSIDE") "INSIDE (200m)" else "OUTSIDE"
                val statusColor = if (selectedPreset.id == "HQ_INSIDE") Color(0xFF1B7F4B) else Color(0xFF8A5A00)
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            Text(
                text = "Simulated Location: ${selectedPreset.description}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Warning Banner if Mock GPS is active
            if (selectedPreset.isMock) {
                Spacer(modifier = Modifier.height(Spacing.s2))
                Surface(
                    color = Color(0xFFFFB4AB).copy(alpha = 0.35f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.s1))
                        Text(
                            text = "Mock Location Provider detected. Punch will be flagged for compliance audit.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s3))

            // Location Presets Switcher for Test & Evaluation
            Text(
                text = "GPS Simulation Preset (Demo Testing):",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(Spacing.s1))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                GeoSimulationPreset.ALL.forEach { preset ->
                    FilterChip(
                        selected = selectedPreset.id == preset.id,
                        onClick = { onSelectPreset(preset) },
                        label = {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkedSummaryCard(
    workedMinutes: Int,
    firstInAt: java.time.OffsetDateTime?,
    lastOutAt: java.time.OffsetDateTime?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(Radius.card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            val hours = workedMinutes / 60
            val minutes = workedMinutes % 60
            val targetMinutes = 8 * 60
            val progress = (workedMinutes.toFloat() / targetMinutes.toFloat()).coerceIn(0f, 1f)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Hours Worked Today",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${hours}h ${minutes}m / 8h 00m",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )

            Spacer(modifier = Modifier.height(Spacing.s3))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "First Clock In",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = firstInAt?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: "--:--",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Last Clock Out",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = lastOutAt?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: "--:--",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PunchTimelineItem(punch: AttendancePunchItem) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.control),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (typeIcon, typeColor, typeLabel) = when (punch.punchType.value) {
                    "IN" -> Triple(Icons.Default.Login, Color(0xFF1B7F4B), "Clock In")
                    "OUT" -> Triple(Icons.Default.Logout, MaterialTheme.colorScheme.error, "Clock Out")
                    "BREAK_OUT" -> Triple(Icons.Default.Coffee, Color(0xFF8A5A00), "Break Out")
                    "BREAK_IN" -> Triple(Icons.Default.PlayArrow, Color(0xFF1B7F4B), "Break In")
                    else -> Triple(Icons.Default.AccessTime, MaterialTheme.colorScheme.primary, punch.punchType.value)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(typeColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.s3))

                Column {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = punch.locationName ?: "Mobile GPS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val timeString = String.format("%02d:%02d", punch.punchedAt.hour, punch.punchedAt.minute)
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (punch.isMockLocation) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = "MOCK GPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    val geoColor = if (punch.geofenceStatus == AttendancePunchItem.GeofenceStatus.INSIDE) Color(0xFF1B7F4B) else Color(0xFF8A5A00)
                    Surface(
                        color = geoColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = punch.geofenceStatus.value,
                            style = MaterialTheme.typography.labelSmall,
                            color = geoColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekNavigatorHeader(
    currentWeekStart: LocalDate,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val weekEnd = currentWeekStart.plusDays(6)
    val formatter = DateTimeFormatter.ofPattern("dd MMM")
    val title = "${currentWeekStart.format(formatter)} - ${weekEnd.format(formatter)}, ${weekEnd.year}"

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Radius.card),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevWeek) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous Week",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Weekly Shift Schedule",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onNextWeek) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next Week",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeeklyCalendarStrip(
    days: List<ShiftRosterDayItem>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        days.forEach { day ->
            val isSelected = day.date == selectedDate
            val isToday = day.date == LocalDate.now()
            val isRest = day.isRestDay

            val containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isToday -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }

            Surface(
                color = containerColor,
                shape = RoundedCornerShape(Radius.control),
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(Radius.control),
                    )
                    .clickable { onSelectDate(day.date) },
            ) {
                Column(
                    modifier = Modifier.padding(vertical = Spacing.s2, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = day.dayOfWeek.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format("%02d", day.date.dayOfMonth),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val statusDotColor = when {
                        isRest -> MaterialTheme.colorScheme.outline
                        day.dayStatus == "ON_DUTY" -> Color(0xFF1B7F4B)
                        day.dayStatus == "COMPLETED" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusDotColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isRest) "OFF" else "${day.teamMembersOnDuty}👥",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDayShiftDetailCard(
    dayItem: ShiftRosterDayItem?,
    selectedDate: LocalDate,
    onSwapClick: () -> Unit,
    onRegulariseClick: () -> Unit,
) {
    val formatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")
    val dateTitle = selectedDate.format(formatter)
    val isRest = dayItem?.isRestDay ?: (selectedDate.dayOfWeek.value >= 6)
    val shiftName = dayItem?.shiftName ?: if (isRest) "Rest Day" else "General Day (08:30 - 17:30)"
    val timing = if (isRest) "No Shift Scheduled" else "${dayItem?.startTime ?: "08:30"} - ${dayItem?.endTime ?: "17:30"}"
    val status = dayItem?.dayStatus ?: if (isRest) "REST_DAY" else "SCHEDULED"

    val statusColor = when (status) {
        "ON_DUTY" -> Color(0xFF1B7F4B)
        "COMPLETED" -> Color(0xFF1B7F4B)
        "REST_DAY" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dateTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = shiftName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Text(
                        text = status.replace('_', ' '),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 4.dp),
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(Radius.control),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.s3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text(
                            text = timing,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.s1))
                        Text(
                            text = "${dayItem?.teamMembersOnDuty ?: 4} on duty",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Quick Actions: Shift Swap & Regularise Punch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                FilledTonalButton(
                    onClick = onSwapClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text("Swap Shift")
                }

                OutlinedButton(
                    onClick = onRegulariseClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Icon(
                        imageVector = Icons.Default.EditCalendar,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text("Regularise")
                }
            }
        }
    }
}

@Composable
private fun TeamCoverageMemberCard(member: TeamCoverageMemberItem) {
    val isRest = member.status == "REST_DAY"
    val statusColor = when (member.status) {
        "ON_DUTY" -> Color(0xFF1B7F4B)
        "SCHEDULED" -> MaterialTheme.colorScheme.primary
        "REST_DAY" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Avatar initials circle
                val initials = member.employeeName.split(" ")
                    .mapNotNull { it.firstOrNull()?.toString() }
                    .take(2)
                    .joinToString("")
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.s3))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = member.employeeName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(Spacing.s1))
                        Text(
                            text = "• ${member.employeeCode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = member.jobTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (isRest) "Rest Day" else "${member.shiftName} (${member.startTime} - ${member.endTime})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Surface(
                color = statusColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(Radius.control),
            ) {
                Text(
                    text = member.status.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ShiftSwapDialog(
    selectedDate: LocalDate,
    teamMembers: List<TeamCoverageMemberItem>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (UUID, LocalDate, String) -> Unit,
) {
    val candidateMembers = teamMembers.filter { it.employeeCode != "LK010" }.ifEmpty {
        listOf(
            TeamCoverageMemberItem(
                employeeId = UUID.fromString("00000000-0000-0000-0000-000000000011"),
                employeeCode = "LK011",
                employeeName = "Amara Perera",
                jobTitle = "Lead Database Architect",
                shiftCode = "MORNING_0700",
                shiftName = "Early Morning (07:00 - 15:30)",
                status = "SCHEDULED",
            ),
            TeamCoverageMemberItem(
                employeeId = UUID.fromString("00000000-0000-0000-0000-000000000012"),
                employeeCode = "LK012",
                employeeName = "Nuwan Silva",
                jobTitle = "Operations Specialist",
                shiftCode = "EVENING_1400",
                shiftName = "Evening Shift (14:00 - 22:30)",
                status = "SCHEDULED",
            ),
        )
    }

    var selectedMember by remember { mutableStateOf(candidateMembers.first()) }
    var targetDateText by remember { mutableStateOf(selectedDate.plusDays(1).toString()) }
    var reasonText by remember { mutableStateOf("Shift swap for personal obligation") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Request Shift Swap",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text(
                    text = "Request a mutual shift swap for your shift on $selectedDate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "Select Peer Colleague:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                candidateMembers.forEach { member ->
                    val isChosen = member.employeeId == selectedMember.employeeId
                    Surface(
                        color = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(Radius.control),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMember = member },
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.s2),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = "${member.employeeName} (${member.employeeCode})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = member.shiftName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isChosen) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = targetDateText,
                    onValueChange = { targetDateText = it },
                    label = { Text("Peer Shift Work Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Reason for Swap") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDate = runCatching { LocalDate.parse(targetDateText) }.getOrDefault(selectedDate.plusDays(1))
                    onSubmit(selectedMember.employeeId, parsedDate, reasonText)
                },
                enabled = !isSubmitting && reasonText.isNotBlank(),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit Request")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AttendanceRegulariseDialog(
    workDate: LocalDate,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String?, String?, String) -> Unit,
) {
    var inTime by remember { mutableStateOf("08:30") }
    var outTime by remember { mutableStateOf("17:30") }
    var reason by remember { mutableStateOf("Forgot punch card / Client onsite visit") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Regularise Attendance",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text(
                    text = "Request punch correction for $workDate. Submits to manager approval workflow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = inTime,
                    onValueChange = { inTime = it },
                    label = { Text("Requested In Time (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = outTime,
                    onValueChange = { outTime = it },
                    label = { Text("Requested Out Time (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for Regularisation *") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(inTime.takeIf { it.isNotBlank() }, outTime.takeIf { it.isNotBlank() }, reason)
                },
                enabled = !isSubmitting && reason.isNotBlank(),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit Regularisation")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        },
    )
}
