package com.hr.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hr.app.ui.navigation.TopLevelDestination
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.infrastructure.Serializer
import com.hr.client.model.AnnouncementItem
import com.hr.client.model.ExpiringDocumentItem
import com.hr.client.model.HomeCard
import com.hr.client.model.MilestoneItem
import com.hr.client.model.PendingApprovalsPayload
import com.hr.client.model.QuickActionItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/**
 * Server-driven card dispatcher.
 *
 * Checks the open-string card [type] and renders the matching card component.
 * Any unrecognised card type is handled gracefully by [UnknownCardFallback], preventing
 * older app builds from crashing when new card kinds are introduced (docs/home-composite.md §4).
 */
@Composable
fun HomeCardRenderer(
    card: HomeCard,
    pendingOutboxEntities: Set<Pair<String, String>>,
    onNavigateToTab: (TopLevelDestination) -> Unit,
    onOpenProfile: (UUID) -> Unit,
    onNavigateToCompliance: ((String?) -> Unit)? = null,
    onNavigateToLeave: () -> Unit = {},
    onNavigateToPayslips: () -> Unit = {},
    onNavigateToLoans: () -> Unit = {},
    onNavigateToClaims: () -> Unit = {},
    onNavigateToBenefits: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (card.type) {
        "PENDING_APPROVALS" ->
            PendingApprovalsCard(
                card = card,
                pendingOutboxEntities = pendingOutboxEntities,
                onNavigateToApprovals = { onNavigateToTab(TopLevelDestination.APPROVALS) },
                modifier = modifier,
            )

        "EXPIRING_DOCUMENTS" ->
            ExpiringDocumentsCard(
                card = card,
                onRenewDocument = { doc -> onNavigateToCompliance?.invoke(doc.id.toString()) },
                modifier = modifier,
            )

        "QUICK_ACTIONS" ->
            QuickActionsCard(
                card = card,
                onNavigateToTab = onNavigateToTab,
                onNavigateToLeave = onNavigateToLeave,
                onNavigateToPayslips = onNavigateToPayslips,
                onNavigateToLoans = onNavigateToLoans,
                onNavigateToClaims = onNavigateToClaims,
                onNavigateToBenefits = onNavigateToBenefits,
                modifier = modifier,
            )


        "ANNOUNCEMENTS" ->
            AnnouncementsCard(
                card = card,
                modifier = modifier,
            )

        "MILESTONES" ->
            MilestonesCard(
                card = card,
                onOpenProfile = onOpenProfile,
                modifier = modifier,
            )

        else ->
            UnknownCardFallback(
                card = card,
                modifier = modifier,
            )
    }
}

/**
 * Approvals count card.
 *
 * Implements docs/home-composite.md §3: subtracts in-flight outbox approvals from the
 * server-delivered total (`totalCount − |matching outbox entries in PENDING/IN_FLIGHT|`).
 */
@Composable
fun PendingApprovalsCard(
    card: HomeCard,
    pendingOutboxEntities: Set<Pair<String, String>>,
    onNavigateToApprovals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val payload = runCatching {
        Serializer.kotlinxSerializationJson.decodeFromJsonElement(
            PendingApprovalsPayload.serializer(),
            JsonObject(card.payload),
        )
    }.getOrNull() ?: return

    val matchingOutboxCount = if (!payload.countIsApproximate) {
        payload.countedEntities.count { entity ->
            (entity.entityType to entity.entityId) in pendingOutboxEntities
        }
    } else 0

    val effectiveTotal = maxOf(0, payload.totalCount - matchingOutboxCount)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = if (effectiveTotal > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ) {
                    Text(
                        text = "$effectiveTotal",
                        color = if (effectiveTotal > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            if (effectiveTotal == 0) {
                Text(
                    text = "All clear! You have no pending approvals requiring action.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "$effectiveTotal request${if (effectiveTotal == 1) "" else "s"} waiting for your review.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(Spacing.s2))

                // Breakdown chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    if (payload.leaveCount > 0) {
                        ApprovalChip(label = "Leave", count = payload.leaveCount)
                    }
                    if (payload.attendanceCount > 0) {
                        ApprovalChip(label = "Attendance", count = payload.attendanceCount)
                    }
                    if (payload.claimsCount > 0) {
                        ApprovalChip(label = "Claims", count = payload.claimsCount)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s3))

                Button(
                    onClick = onNavigateToApprovals,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Text("Review Pending Requests")
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ApprovalChip(label: String, count: Int) {
    Surface(
        shape = RoundedCornerShape(Radius.control),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.s2, vertical = Spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Expiring documents warning card.
 */
@Composable
fun ExpiringDocumentsCard(
    card: HomeCard,
    onRenewDocument: ((ExpiringDocumentItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val items = runCatching {
        val raw = card.payload["items"] ?: card.payload["documents"] ?: JsonArray(emptyList())
        Serializer.kotlinxSerializationJson.decodeFromJsonElement(
            ListSerializer(ExpiringDocumentItem.serializer()),
            raw,
        )
    }.getOrElse { emptyList() }

    if (items.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.s2))
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            items.forEachIndexed { index, doc ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.s2))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.docType.replace("_", " "),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Expires ${doc.expiryDate}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(Radius.pill),
                            color = if (doc.daysRemaining <= 7) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = if (doc.daysRemaining <= 0) "Expired" else "${doc.daysRemaining} days left",
                                color = if (doc.daysRemaining <= 7) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 4.dp),
                            )
                        }

                        if (onRenewDocument != null) {
                            OutlinedButton(
                                onClick = { onRenewDocument(doc) },
                                shape = RoundedCornerShape(Radius.control),
                            ) {
                                Text("Renew", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick action shortcuts grid.
 */
@Composable
fun QuickActionsCard(
    card: HomeCard,
    onNavigateToTab: (TopLevelDestination) -> Unit,
    onNavigateToLeave: () -> Unit = {},
    onNavigateToPayslips: () -> Unit = {},
    onNavigateToLoans: () -> Unit = {},
    onNavigateToClaims: () -> Unit = {},
    onNavigateToBenefits: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val actions = runCatching {
        val raw = card.payload["actions"] ?: JsonArray(emptyList())
        Serializer.kotlinxSerializationJson.decodeFromJsonElement(
            ListSerializer(QuickActionItem.serializer()),
            raw,
        )
    }.getOrElse { emptyList() }

    if (actions.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = card.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = Spacing.s1, vertical = Spacing.s2),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            actions.forEach { action ->
                QuickActionButton(
                    item = action,
                    onClick = {
                        when (action.key) {
                            "clock_in" -> onNavigateToTab(TopLevelDestination.TIME)
                            "request_leave" -> onNavigateToLeave()
                            "my_payslips", "payslip", "payslips" -> onNavigateToPayslips()
                            "loans", "loan", "my_loans", "apply_loan" -> onNavigateToLoans()
                            "claims", "claim", "expenses", "expense", "my_claims", "expense_claims" -> onNavigateToClaims()
                            "benefits", "benefit", "my_benefits", "insurance" -> onNavigateToBenefits()
                            "directory" -> onNavigateToTab(TopLevelDestination.PEOPLE)
                            "my_profile" -> onNavigateToTab(TopLevelDestination.ME)
                            else -> Unit
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}


@Composable
private fun QuickActionButton(
    item: QuickActionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(Radius.control),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.s2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = when (item.key) {
                    "clock_in" -> Icons.Default.AccessTime
                    "request_leave" -> Icons.Default.BeachAccess
                    "my_payslips", "payslip", "payslips" -> Icons.Default.Payments
                    "loans", "loan", "my_loans", "apply_loan" -> Icons.Default.Payments
                    "claims", "claim", "expenses", "expense", "my_claims", "expense_claims" -> Icons.Default.ReceiptLong
                    "benefits", "benefit", "my_benefits", "insurance" -> Icons.Default.HealthAndSafety
                    "directory" -> Icons.Default.People
                    "my_profile" -> Icons.Default.Person
                    else -> Icons.AutoMirrored.Filled.ArrowForward
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Company announcements card.
 */
@Composable
fun AnnouncementsCard(
    card: HomeCard,
    modifier: Modifier = Modifier,
) {
    val items = runCatching {
        val raw = card.payload["items"] ?: JsonArray(emptyList())
        Serializer.kotlinxSerializationJson.decodeFromJsonElement(
            ListSerializer(AnnouncementItem.serializer()),
            raw,
        )
    }.getOrElse { emptyList() }

    if (items.isEmpty()) return

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.s2))
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            items.forEachIndexed { index, announcement ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.s2))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = announcement.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (announcement.priority == "HIGH") {
                            Surface(
                                shape = RoundedCornerShape(Radius.pill),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Text(
                                    text = "HIGH",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = Spacing.s1, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = announcement.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(Spacing.s1))

                    Text(
                        text = "From ${announcement.authorName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

/**
 * Milestones card (birthdays and work anniversaries).
 */
@Composable
fun MilestonesCard(
    card: HomeCard,
    onOpenProfile: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = runCatching {
        val raw = card.payload["items"] ?: JsonArray(emptyList())
        Serializer.kotlinxSerializationJson.decodeFromJsonElement(
            ListSerializer(MilestoneItem.serializer()),
            raw,
        )
    }.getOrElse { emptyList() }

    if (items.isEmpty()) return

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Celebration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.s2))
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            items.forEachIndexed { index, milestone ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.s2))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.control))
                        .clickable { onOpenProfile(milestone.employeeId) }
                        .padding(vertical = Spacing.s1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Colleague initials avatar
                    val initials = milestone.displayName
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .map { it.first() }
                        .joinToString("")
                        .uppercase()

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.s3))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = milestone.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        milestone.designation?.let { designation ->
                            Text(
                                text = designation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = when (milestone.type) {
                                "BIRTHDAY" -> "Birthday 🎂"
                                "WORK_ANNIVERSARY" -> "${milestone.yearsCount ?: ""} Years 🎉"
                                else -> "Milestone"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.s2, vertical = Spacing.s1),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fallback renderer for unknown card types sent by newer server versions.
 */
@Composable
fun UnknownCardFallback(
    card: HomeCard,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.width(Spacing.s2))
            Column {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Update the app to see full details for this card.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
