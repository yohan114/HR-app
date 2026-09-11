package com.hr.app.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hr.app.ui.theme.Radius
import com.hr.app.ui.theme.Spacing
import com.hr.client.model.EmployeeDocumentItem
import com.hr.client.model.FormField
import java.time.LocalDate
import java.util.UUID

/**
 * An employee profile with schema-driven fields and statutory document compliance viewer.
 */
@Composable
fun ProfileScreen(
    employeeId: UUID?,
    onBack: () -> Unit,
    targetDocumentId: String? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenPayslips: (() -> Unit)? = null,
    onOpenLoans: (() -> Unit)? = null,
    onOpenClaims: (() -> Unit)? = null,
    onOpenBenefits: (() -> Unit)? = null,
    onOpenCareer: (() -> Unit)? = null,
    onOpenDisciplinary: (() -> Unit)? = null,
    onOpenPerformance: (() -> Unit)? = null,
    onOpenRecruitment: (() -> Unit)? = null,
    onOpenOnboarding: (() -> Unit)? = null,
    onOpenDocuments: (() -> Unit)? = null,
    onOpenTraining: (() -> Unit)? = null,
    onOpenTimesheets: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(employeeId) {
        viewModel.load(employeeId)
    }

    LaunchedEffect(targetDocumentId, state.documents) {
        if (!targetDocumentId.isNullOrBlank() && state.documents.isNotEmpty()) {
            viewModel.openRenewalDialogById(targetDocumentId)
        }
    }

    val error = state.error
    val profile = state.profile

    when {
        error != null ->
            Centred {
                Text(
                    text = error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                TextButton(onClick = viewModel::retry) { Text("Try again") }
                TextButton(onClick = onBack) { Text("Back") }
            }

        state.loading || profile == null -> Centred { CircularProgressIndicator() }

        else -> {
            val fields: List<FormField> =
                state.schema?.sections?.flatMap { it.fields } ?: OWN_PROFILE_FIELDS

            LazyColumn(modifier = Modifier.fillMaxSize().padding(Spacing.s4)) {
                item {
                    Text(
                        text = profile.displayName ?: "Employee",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = Spacing.s1),
                    )
                    Text(
                        text = profile.valueFor("workEmail") ?: "Staff Member",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.s3),
                    )
                }

                // Profile Fields Section
                items(fields, key = { it.key }) { field ->
                    ProfileRow(label = field.label, value = profile.valueFor(field.key))
                    HorizontalDivider()
                }

                // Compliance & Statutory Documents Section (Available for own profile)
                if (employeeId == null) {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.s4))
                        ComplianceSection(
                            documents = state.documents,
                            onRenewDocument = { viewModel.openRenewalDialog(it) },
                            onAddNewDocument = { viewModel.openRenewalDialog(null) },
                        )
                    }
                }

                if (state.renewalMessage != null) {
                    item {
                        Spacer(modifier = Modifier.height(Spacing.s3))
                        Surface(
                            shape = RoundedCornerShape(Radius.control),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.s3),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = state.renewalMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = viewModel::clearRenewalMessage) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }

                if (onOpenPayslips != null) {
                    item {
                        OutlinedButton(
                            onClick = onOpenPayslips,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s4),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("View Payslips & Statutory Breakdown")
                        }
                    }
                }

                if (onOpenLoans != null) {
                    item {
                        OutlinedButton(
                            onClick = onOpenLoans,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("Loans & Salary Advances")
                        }
                    }
                }

                if (onOpenClaims != null) {
                    item {
                        OutlinedButton(
                            onClick = onOpenClaims,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("Expense Claims & Reimbursements")
                        }
                    }
                }

                if (onOpenBenefits != null) {
                    item {
                        OutlinedButton(
                            onClick = onOpenBenefits,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("Benefits & Health Insurance")
                        }
                    }
                }

                if (onOpenCareer != null) {
                    item {
                        OutlinedButton(
                            onClick = onOpenCareer,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("Career Journey & Movements")
                        }
                    }
                }

                if (onOpenDisciplinary != null) {
                    item {
                        OutlinedButton(
                            onClick = onOpenDisciplinary,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("Disciplinary & Grievance Redressal")
                        }
                    }
                }

                if (onOpenPerformance != null) {
                    item {
                        OutlinedButton(
                            onClick = onOpenPerformance,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("Performance, OKRs & Appraisals")
                        }
                    }
                }

                if (onOpenRecruitment != null) {
                    item {
                        OutlinedButton(
                            onClick = onOpenRecruitment,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("Recruitment, ATS & Hiring Portal")
                        }
                    }
                }

                if (onOpenOnboarding != null) {
                    item {
                        OutlinedButton(
                            onClick = onOpenOnboarding,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("Onboarding & Exit Clearance")
                        }
                    }
                }

                if (onOpenDocuments != null) {
                    item {
                        OutlinedButton(
                            onClick = onOpenDocuments,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("Documents, Letters & Digital Signatures")
                        }
                    }
                }

                if (onOpenTraining != null) {
                    item {
                        OutlinedButton(
                            onClick = onOpenTraining,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("Training, Certifications & Learning")
                        }
                    }
                }

                if (onOpenTimesheets != null) {
                    item {
                        OutlinedButton(
                            onClick = onOpenTimesheets,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s2),
                            shape = RoundedCornerShape(Radius.control),
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.s2))
                            Text("Timesheets, Billing & Matrix")
                        }
                    }
                }


                if (onOpenSettings != null) {
                    item {
                        TextButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.padding(top = Spacing.s2),
                        ) {
                            Text("Notification settings")
                        }
                    }
                }

                item {
                    TextButton(onClick = onBack, modifier = Modifier.padding(top = Spacing.s1)) {
                        Text("Sign Out")
                    }
                }
            }
        }
    }

    // Document Renewal Dialog Modal
    val docToRenew = state.selectedDocumentForRenewal
    if (docToRenew != null) {
        DocumentRenewalDialog(
            document = docToRenew,
            inProgress = state.renewalInProgress,
            onDismiss = viewModel::closeRenewalDialog,
            onSubmit = { docType, docNum, expDate, issueDate, country, attachment ->
                viewModel.submitRenewal(
                    docType = docType,
                    docNumber = docNum,
                    expiryDate = expDate,
                    issueDate = issueDate,
                    issuingCountry = country,
                    attachmentKey = attachment,
                )
            },
        )
    }
}

@Composable
private fun ComplianceSection(
    documents: List<EmployeeDocumentItem>,
    onRenewDocument: (EmployeeDocumentItem) -> Unit,
    onAddNewDocument: () -> Unit,
) {
    val expiringOrExpiredCount = documents.count { it.status == "EXPIRED" || it.status == "EXPIRING" }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                    text = "Compliance & Documents",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "${documents.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 2.dp),
                    )
                }
            }

            OutlinedButton(
                onClick = onAddNewDocument,
                shape = RoundedCornerShape(Radius.control),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(Spacing.s1))
                Text("Upload")
            }
        }

        Spacer(modifier = Modifier.height(Spacing.s2))

        if (expiringOrExpiredCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s2),
                shape = RoundedCornerShape(Radius.card),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Attention Required: $expiringOrExpiredCount statutory document(s) require renewal to maintain employment compliance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        if (documents.isEmpty()) {
            Text(
                text = "No compliance documents registered yet. Tap 'Upload' to add statutory records.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.s2),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                documents.forEach { doc ->
                    ComplianceDocumentCard(document = doc, onRenew = { onRenewDocument(doc) })
                }
            }
        }
    }
}

@Composable
private fun ComplianceDocumentCard(
    document: EmployeeDocumentItem,
    onRenew: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.s3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Column {
                        Text(
                            text = document.docType.replace('_', ' '),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "No. ${document.docNumberMasked}${document.issuingCountry?.let { " · $it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                DocumentStatusChip(status = document.status, daysRemaining = document.daysRemaining)
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Expiry: ${document.expiryDate ?: "No expiry"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (document.hasAttachment) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = document.attachmentKey ?: "Attachment on file",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "No file attached",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }

                Button(
                    onClick = onRenew,
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Text("Renew / Update")
                }
            }
        }
    }
}

@Composable
private fun DocumentStatusChip(status: String, daysRemaining: Int?) {
    val (bgColor, textColor, label) = when {
        status == "EXPIRED" || (daysRemaining != null && daysRemaining <= 0) ->
            Triple(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, "EXPIRED")
        status == "EXPIRING" || (daysRemaining != null && daysRemaining <= 30) ->
            Triple(Color(0xFFE65100), Color.White, "${daysRemaining ?: 0}d left")
        status == "REPLACED" ->
            Triple(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.onSurfaceVariant, "REPLACED")
        else ->
            Triple(Color(0xFF1B7F4B), Color.White, "VALID")
    }

    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = bgColor,
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = Spacing.s2, vertical = 3.dp),
        )
    }
}

@Composable
private fun DocumentRenewalDialog(
    document: EmployeeDocumentItem,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (docType: String, docNumber: String, expiryDate: LocalDate, issueDate: LocalDate?, issuingCountry: String?, attachmentKey: String?) -> Unit,
) {
    var docNumber by remember(document) { mutableStateOf(if (document.docNumberMasked.contains('*')) "" else document.docNumberMasked) }
    var expiryDateText by remember(document) { mutableStateOf(document.expiryDate?.plusYears(5)?.toString() ?: LocalDate.now().plusYears(5).toString()) }
    var issuingCountry by remember(document) { mutableStateOf(document.issuingCountry ?: "LK") }
    var simulatedAttachment by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Renew ${document.docType.replace('_', ' ')}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text(
                    text = "Previous record: ${document.docNumberMasked} (expired/expires ${document.expiryDate ?: "N/A"}). Enter updated particulars below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = docNumber,
                    onValueChange = { docNumber = it },
                    label = { Text("New Document Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = expiryDateText,
                    onValueChange = { expiryDateText = it },
                    label = { Text("New Expiry Date (YYYY-MM-DD) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = issuingCountry,
                    onValueChange = { issuingCountry = it },
                    label = { Text("Issuing Country Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Simulated Attachment Upload
                OutlinedButton(
                    onClick = {
                        simulatedAttachment = "${document.docType.lowercase()}_renewed_${System.currentTimeMillis().toString().takeLast(4)}.pdf"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.control),
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.s1))
                    Text(if (simulatedAttachment == null) "Attach Document PDF / Scan" else "Replace File")
                }

                if (simulatedAttachment != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            text = simulatedAttachment!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (docNumber.isBlank()) {
                        validationError = "Document number is required."
                        return@Button
                    }
                    val parsedDate = runCatching { LocalDate.parse(expiryDateText.trim()) }.getOrNull()
                    if (parsedDate == null) {
                        validationError = "Invalid expiry date format. Use YYYY-MM-DD."
                        return@Button
                    }
                    if (!parsedDate.isAfter(LocalDate.now())) {
                        validationError = "New expiry date must be in the future."
                        return@Button
                    }
                    validationError = null
                    onSubmit(
                        document.docType,
                        docNumber.trim(),
                        parsedDate,
                        LocalDate.now(),
                        issuingCountry.trim().take(2).uppercase(),
                        simulatedAttachment ?: "${document.docType.lowercase()}_scan.pdf",
                    )
                },
                enabled = !inProgress,
            ) {
                if (inProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Submit Renewal")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ProfileRow(
    label: String,
    value: String?,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s2)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

private val OWN_PROFILE_FIELDS =
    listOf(
        FormField(key = "employeeCode", label = "Employee code", type = FormField.Type.TEXT),
        FormField(key = "workEmail", label = "Work email", type = FormField.Type.EMAIL),
        FormField(key = "mobile", label = "Mobile", type = FormField.Type.PHONE),
        FormField(key = "joinDate", label = "Join date", type = FormField.Type.DATE),
        FormField(key = "yearsOfService", label = "Years of service", type = FormField.Type.NUMBER),
        FormField(key = "status", label = "Status", type = FormField.Type.TEXT),
    )
