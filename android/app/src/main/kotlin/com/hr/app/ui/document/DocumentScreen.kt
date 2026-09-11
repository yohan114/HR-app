package com.hr.app.ui.document

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
fun DocumentScreen(
    viewModel: DocumentViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Documents & E-Signatures",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Vault, Templates & Digital Signatures",
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
                    DocumentStatsHeader(uiState = uiState)
                }

                // Tab Selector
                item {
                    TabRow(
                        selectedTabIndex = uiState.selectedTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                    ) {
                        Tab(
                            selected = uiState.selectedTab == DocumentTab.VAULT,
                            onClick = { viewModel.setTab(DocumentTab.VAULT) },
                            text = { Text("Vault (${uiState.documents.size})") },
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        )
                        Tab(
                            selected = uiState.selectedTab == DocumentTab.LETTERS,
                            onClick = { viewModel.setTab(DocumentTab.LETTERS) },
                            text = { Text("Letters (${uiState.letterRequests.size})") },
                            icon = { Icon(Icons.Default.MailOutline, contentDescription = null) },
                        )
                        Tab(
                            selected = uiState.selectedTab == DocumentTab.SIGNATURES,
                            onClick = { viewModel.setTab(DocumentTab.SIGNATURES) },
                            text = { Text("E-Sign (${uiState.signatureRequests.count { it.status == "PENDING" }})") },
                            icon = { Icon(Icons.Default.Create, contentDescription = null) },
                        )
                        Tab(
                            selected = uiState.selectedTab == DocumentTab.UPLOADS,
                            onClick = { viewModel.setTab(DocumentTab.UPLOADS) },
                            text = { Text("My Files") },
                            icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                        )
                    }
                }

                // Tab Contents
                when (uiState.selectedTab) {
                    DocumentTab.VAULT -> {
                        item {
                            VaultTabContent(
                                uiState = uiState,
                                onFolderSelect = viewModel::selectFolder,
                                onCategorySelect = viewModel::selectCategory,
                                onSearchQueryChange = viewModel::setSearchQuery,
                            )
                        }
                    }
                    DocumentTab.LETTERS -> {
                        item {
                            LetterRequestsTabContent(
                                uiState = uiState,
                                onRequestLetterClick = { viewModel.openLetterRequestDialog() },
                            )
                        }
                    }
                    DocumentTab.SIGNATURES -> {
                        item {
                            SignaturesTabContent(
                                uiState = uiState,
                                onSignClick = viewModel::openSignatureDialog,
                            )
                        }
                    }
                    DocumentTab.UPLOADS -> {
                        item {
                            MyUploadsTabContent(uiState = uiState)
                        }
                    }
                }
            }

            // Notifications / Floating Messages
            uiState.errorMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.s4),
                    action = {
                        TextButton(onClick = { viewModel.clearMessages() }) {
                            Text("DISMISS", color = MaterialTheme.colorScheme.inversePrimary)
                        }
                    },
                ) {
                    Text(msg)
                }
            }

            uiState.successMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.s4),
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White,
                    action = {
                        TextButton(onClick = { viewModel.clearMessages() }) {
                            Text("OK", color = Color.White)
                        }
                    },
                ) {
                    Text(msg)
                }
            }

            // Letter Request Dialog
            if (uiState.isLetterRequestDialogVisible) {
                LetterRequestModalDialog(
                    uiState = uiState,
                    onDismiss = viewModel::closeLetterRequestDialog,
                    onTemplateSelect = viewModel::setLetterRequestTemplate,
                    onReasonChange = viewModel::setLetterRequestReason,
                    onRecipientChange = viewModel::setLetterRequestRecipient,
                    onSubmit = viewModel::submitLetterRequest,
                )
            }

            // Digital Signature Modal Dialog
            if (uiState.signingRequestId != null) {
                DigitalSignatureModalDialog(
                    uiState = uiState,
                    onDismiss = viewModel::closeSignatureDialog,
                    onModeChange = viewModel::setSignatureMode,
                    onTypedNameChange = viewModel::setTypedSignatureName,
                    onAddStrokePath = viewModel::addCanvasStrokePath,
                    onClearCanvas = viewModel::clearCanvasStrokes,
                    onConsentToggle = viewModel::setLegalConsent,
                    onSubmitSignature = viewModel::submitDigitalSignature,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Header & Quick Metric Cards
// -----------------------------------------------------------------------------

@Composable
private fun DocumentStatsHeader(uiState: DocumentUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.s4),
        shape = RoundedCornerShape(Radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.s4)) {
            Text(
                text = "Enterprise Document Lifecycle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Policy vault, official employee letters & ESIGN-compliant digital sign-offs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(Spacing.s3))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                StatTile(
                    title = "Documents",
                    value = uiState.documents.size.toString(),
                    icon = Icons.Default.Description,
                    color = Color(0xFF1976D2),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    title = "Pending Letters",
                    value = uiState.letterRequests.count { it.status == "SUBMITTED" }.toString(),
                    icon = Icons.Default.MailOutline,
                    color = Color(0xFFF57C00),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    title = "To Sign",
                    value = uiState.signatureRequests.count { it.status == "PENDING" }.toString(),
                    icon = Icons.Default.Create,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.md),
        color = color.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.s2))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 1: Document Vault
// -----------------------------------------------------------------------------

@Composable
private fun VaultTabContent(
    uiState: DocumentUiState,
    onFolderSelect: (UUID?) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.s4)) {
        // Search & Filter
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search by title, policy or keyword...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.s2),
            shape = RoundedCornerShape(Radius.md),
            singleLine = true,
        )

        // Folders Row
        Text(
            text = "Categories & Folders",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = Spacing.s2),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                FilterChip(
                    selected = uiState.selectedFolderId == null,
                    onClick = { onFolderSelect(null) },
                    label = { Text("All Documents (${uiState.documents.size})") },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                )
            }
            items(uiState.folders) { folder ->
                FilterChip(
                    selected = uiState.selectedFolderId == folder.id,
                    onClick = { onFolderSelect(folder.id) },
                    label = { Text("${folder.name} (${folder.documentCount})") },
                    leadingIcon = {
                        Icon(
                            when (folder.icon) {
                                "gavel" -> Icons.Default.Gavel
                                "handshake" -> Icons.Default.Handshake
                                "mail" -> Icons.Default.MailOutline
                                else -> Icons.Default.Folder
                            },
                            contentDescription = null,
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.s3))

        // Document Cards List
        if (uiState.documents.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Description,
                title = "No documents found",
                subtitle = "Try adjusting your search query or folder filter.",
            )
        } else {
            uiState.documents.forEach { doc ->
                DocumentItemCard(document = doc)
                Spacer(modifier = Modifier.height(Spacing.s2))
            }
        }
    }
}

@Composable
private fun DocumentItemCard(document: CompanyDocumentItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.s3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = when (document.category.uppercase()) {
                        "POLICY" -> Color(0xFF1976D2).copy(alpha = 0.12f)
                        "CONTRACT" -> Color(0xFF7B1FA2).copy(alpha = 0.12f)
                        "LETTER" -> Color(0xFFE65100).copy(alpha = 0.12f)
                        else -> Color(0xFF455A64).copy(alpha = 0.12f)
                    },
                ) {
                    Text(
                        text = document.category.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (document.category.uppercase()) {
                            "POLICY" -> Color(0xFF1976D2)
                            "CONTRACT" -> Color(0xFF7B1FA2)
                            "LETTER" -> Color(0xFFE65100)
                            else -> Color(0xFF455A64)
                        },
                    )
                }

                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "v${document.currentVersionNumber}",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            Text(
                text = document.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            document.description?.let { desc ->
                Spacer(modifier = Modifier.height(Spacing.s1))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.s2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SHA-256 Verified",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${document.fileSizeBytes / 1024} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FilledTonalButton(
                    onClick = { /* Open viewer */ },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 2: Letter Requests
// -----------------------------------------------------------------------------

@Composable
private fun LetterRequestsTabContent(
    uiState: DocumentUiState,
    onRequestLetterClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.s4)) {
        Button(
            onClick = onRequestLetterClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(Spacing.s2))
            Text("Request Official HR Letter")
        }

        Spacer(modifier = Modifier.height(Spacing.s3))

        Text(
            text = "My Letter Requests",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(Spacing.s2))

        if (uiState.letterRequests.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.MailOutline,
                title = "No letter requests",
                subtitle = "Request service letters, visa support letters or salary verifications.",
            )
        } else {
            uiState.letterRequests.forEach { req ->
                LetterRequestCard(request = req)
                Spacer(modifier = Modifier.height(Spacing.s2))
            }
        }
    }
}

@Composable
private fun LetterRequestCard(request: LetterRequestItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
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
                    text = request.templateName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                StatusBadge(status = request.status)
            }

            Spacer(modifier = Modifier.height(Spacing.s1))

            Text(
                text = "Ref: ${request.templateCode}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Purpose: ${request.reason}",
                style = MaterialTheme.typography.bodySmall,
            )

            request.recipientAddress?.let { addressee ->
                Text(
                    text = "Addressee: $addressee",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (request.status == "APPROVED") {
                Spacer(modifier = Modifier.height(Spacing.s2))
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = Color(0xFF2E7D32).copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Letter Generated & Issued", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                        }
                        TextButton(onClick = { /* Download letter */ }) {
                            Text("View PDF", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 3: Digital E-Signatures
// -----------------------------------------------------------------------------

@Composable
private fun SignaturesTabContent(
    uiState: DocumentUiState,
    onSignClick: (UUID) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.s4)) {
        Text(
            text = "E-Signature Workflows",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Legally binding signatures with SHA-256 cryptographic audit seal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.s3))

        if (uiState.signatureRequests.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.Create,
                title = "No pending signatures",
                subtitle = "All contracts, policy acknowledgments, and agreements are fully executed.",
            )
        } else {
            uiState.signatureRequests.forEach { sigReq ->
                SignatureRequestCard(
                    request = sigReq,
                    onSignClick = { onSignClick(sigReq.id) },
                )
                Spacer(modifier = Modifier.height(Spacing.s2))
            }
        }
    }
}

@Composable
private fun SignatureRequestCard(
    request: SignatureRequestItem,
    onSignClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
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
                    text = request.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = request.status)
            }

            Spacer(modifier = Modifier.height(Spacing.s1))

            Text(
                text = "Document: ${request.documentTitle ?: "Official Agreement"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(Spacing.s2))

            // Signer progression bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Signers: ${request.signedCount}/${request.totalSigners} completed",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = Color.DarkGray.copy(alpha = 0.08f),
                ) {
                    Text(
                        text = "Seal: ${request.fileChecksumSha256.take(8)}...",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s3))

            if (request.status == "PENDING" || request.status == "PARTIALLY_SIGNED") {
                Button(
                    onClick = onSignClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                ) {
                    Icon(Icons.Default.Draw, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Text("Execute Digital E-Signature")
                }
            } else {
                OutlinedButton(
                    onClick = { /* View certificate */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Text("View Certificate & Audit Trail")
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 4: My Uploads
// -----------------------------------------------------------------------------

@Composable
private fun MyUploadsTabContent(uiState: DocumentUiState) {
    Column(modifier = Modifier.padding(horizontal = Spacing.s4)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.s4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(Spacing.s2))
                Text(
                    text = "Upload Personal Documents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Upload identity cards, educational certificates, or medical forms.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(Spacing.s3))
                FilledTonalButton(onClick = { /* Upload simulator */ }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Text("Select Document File")
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.s3))

        Text(
            text = "My Uploaded Vault Files",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(Spacing.s2))

        val myDocs = uiState.documents.filter { it.category == "FORM" || it.category == "CERTIFICATE" }
        if (myDocs.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.FolderShared,
                title = "No personal uploads yet",
                subtitle = "Documents uploaded will be stored securely in your employee profile vault.",
            )
        } else {
            myDocs.forEach { doc ->
                DocumentItemCard(document = doc)
                Spacer(modifier = Modifier.height(Spacing.s2))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Digital E-Signature Modal Dialog (Canvas + Typed + Consent + SHA-256 Seal)
// -----------------------------------------------------------------------------

@Composable
private fun DigitalSignatureModalDialog(
    uiState: DocumentUiState,
    onDismiss: () -> Unit,
    onModeChange: (SignatureInputMode) -> Unit,
    onTypedNameChange: (String) -> Unit,
    onAddStrokePath: (List<Pair<Float, Float>>) -> Unit,
    onClearCanvas: () -> Unit,
    onConsentToggle: (Boolean) -> Unit,
    onSubmitSignature: () -> Unit,
) {
    var currentStroke by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(Radius.lg),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.s4),
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Digital E-Signature",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Legally binding electronic signature",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s2))

                // Toggle Mode: Draw vs Type
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    FilterChip(
                        selected = uiState.signatureMode == SignatureInputMode.CANVAS_DRAW,
                        onClick = { onModeChange(SignatureInputMode.CANVAS_DRAW) },
                        label = { Text("Draw with Finger/Stylus") },
                        leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = uiState.signatureMode == SignatureInputMode.TYPED_NAME,
                        onClick = { onModeChange(SignatureInputMode.TYPED_NAME) },
                        label = { Text("Type Legal Name") },
                        leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.s2))

                // Signature Canvas or Text Field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(Color(0xFFFBFBFB))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(Radius.md)),
                ) {
                    if (uiState.signatureMode == SignatureInputMode.CANVAS_DRAW) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentStroke = listOf(offset.x to offset.y)
                                        },
                                        onDrag = { change, _ ->
                                            currentStroke = currentStroke + listOf(change.position.x to change.position.y)
                                        },
                                        onDragEnd = {
                                            if (currentStroke.isNotEmpty()) {
                                                onAddStrokePath(currentStroke)
                                                currentStroke = emptyList()
                                            }
                                        },
                                    )
                                },
                        ) {
                            // Draw completed paths
                            uiState.canvasStrokePaths.forEach { stroke ->
                                if (stroke.size > 1) {
                                    val path = Path()
                                    path.moveTo(stroke[0].first, stroke[0].second)
                                    for (i in 1 until stroke.size) {
                                        path.lineTo(stroke[i].first, stroke[i].second)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(0xFF0D47A1),
                                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                                    )
                                }
                            }

                            // Draw ongoing active stroke
                            if (currentStroke.size > 1) {
                                val activePath = Path()
                                activePath.moveTo(currentStroke[0].first, currentStroke[0].second)
                                for (i in 1 until currentStroke.size) {
                                    activePath.lineTo(currentStroke[i].first, currentStroke[i].second)
                                }
                                drawPath(
                                    path = activePath,
                                    color = Color(0xFF0D47A1),
                                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }

                        if (uiState.canvasStrokePaths.isEmpty() && currentStroke.isEmpty()) {
                            Text(
                                text = "Sign above using finger or stylus",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }

                        IconButton(
                            onClick = onClearCanvas,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(Spacing.s2),
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Canvas")
                        }
                    } else {
                        // Typed Mode
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Spacing.s3),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            OutlinedTextField(
                                value = uiState.typedSignatureName,
                                onValueChange = onTypedNameChange,
                                label = { Text("Full Legal Name") },
                                placeholder = { Text("e.g. Kasun Mendis") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            Spacer(modifier = Modifier.height(Spacing.s3))
                            if (uiState.typedSignatureName.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFFE3F2FD),
                                    shape = RoundedCornerShape(Radius.md),
                                ) {
                                    Text(
                                        text = uiState.typedSignatureName,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontStyle = FontStyle.Italic,
                                        color = Color(0xFF0D47A1),
                                        modifier = Modifier.padding(Spacing.s3),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s2))

                // Legal Consent Checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = uiState.legalConsentChecked,
                        onCheckedChange = onConsentToggle,
                    )
                    Text(
                        text = "I confirm that this electronic signature represents my legally binding sign-off pursuant to the Electronic Transactions Act & ESIGN regulations.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable { onConsentToggle(!uiState.legalConsentChecked) }
                            .padding(start = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.s2))

                // SHA-256 Cryptographic Seal Notice
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = Color.DarkGray.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF455A64), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.s2))
                        Text(
                            text = "A tamper-evident SHA-256 seal will be cryptographically attached with timestamp & IP address.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF455A64),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s3))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Button(
                        onClick = onSubmitSignature,
                        enabled = !uiState.isSigningInProgress && uiState.legalConsentChecked,
                    ) {
                        if (uiState.isSigningInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            Spacer(modifier = Modifier.width(Spacing.s2))
                        }
                        Text("Sign & Seal Document")
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Letter Request Modal Dialog
// -----------------------------------------------------------------------------

@Composable
private fun LetterRequestModalDialog(
    uiState: DocumentUiState,
    onDismiss: () -> Unit,
    onTemplateSelect: (DocumentTemplateItem) -> Unit,
    onReasonChange: (String) -> Unit,
    onRecipientChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.lg),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(Spacing.s4)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Request Official Letter",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s2))

                Text(
                    text = "Select Letter Template",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(Spacing.s1))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(uiState.templates) { tpl ->
                        FilterChip(
                            selected = uiState.selectedTemplateForRequest?.id == tpl.id,
                            onClick = { onTemplateSelect(tpl) },
                            label = { Text(tpl.name) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.s2))

                OutlinedTextField(
                    value = uiState.letterRequestReason,
                    onValueChange = onReasonChange,
                    label = { Text("Purpose / Reason *") },
                    placeholder = { Text("e.g. Visa application to Embassy") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(Spacing.s2))

                OutlinedTextField(
                    value = uiState.letterRequestRecipient,
                    onValueChange = onRecipientChange,
                    label = { Text("Recipient / Addressee") },
                    placeholder = { Text("e.g. The Visa Section, French Embassy") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(Spacing.s4))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(Spacing.s2))
                    Button(
                        onClick = onSubmit,
                        enabled = uiState.letterRequestReason.isNotBlank() && uiState.selectedTemplateForRequest != null,
                    ) {
                        Text("Submit Request")
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Common Components
// -----------------------------------------------------------------------------

@Composable
private fun StatusBadge(status: String) {
    val (bgColor, textColor) = when (status.uppercase()) {
        "APPROVED", "COMPLETED", "SIGNED" -> Color(0xFF2E7D32).copy(alpha = 0.12f) to Color(0xFF2E7D32)
        "SUBMITTED", "PENDING" -> Color(0xFFF57C00).copy(alpha = 0.12f) to Color(0xFFF57C00)
        "PARTIALLY_SIGNED" -> Color(0xFF1976D2).copy(alpha = 0.12f) to Color(0xFF1976D2)
        "REJECTED", "DECLINED" -> Color(0xFFD32F2F).copy(alpha = 0.12f) to Color(0xFFD32F2F)
        else -> Color.Gray.copy(alpha = 0.12f) to Color.DarkGray
    }

    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = bgColor,
    ) {
        Text(
            text = status.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.s4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(Spacing.s2))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
