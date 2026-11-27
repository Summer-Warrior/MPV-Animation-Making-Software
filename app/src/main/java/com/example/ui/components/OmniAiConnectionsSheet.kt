package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AiProviderId
import com.example.ai.ContextSourceItem
import com.example.ai.OmniAiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniAiConnectionsSheet(
    config: OmniAiConfig,
    contextSources: List<ContextSourceItem>,
    hubStatusMessage: String?,
    isHubBusy: Boolean,
    onDismiss: () -> Unit,
    onSelectTextProvider: (AiProviderId) -> Unit,
    onSelectImageProvider: (AiProviderId) -> Unit,
    onToggleFirebaseFallback: (Boolean) -> Unit,
    onSaveProviderKey: (AiProviderId, String) -> Unit,
    onClearProviderKey: (AiProviderId) -> Unit,
    onUpdateProviderModelAndUrl: (AiProviderId, String, String, String) -> Unit,
    onStartOpenRouterPkce: (onLaunchUrl: (String) -> Unit) -> Unit,
    onImportSafDocument: (Uri) -> Unit,
    onAddHttpsSource: (String, String) -> Unit,
    onAddNoteSource: (String, String) -> Unit,
    onToggleSourceEnabled: (String) -> Unit,
    onRemoveSource: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val safDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportSafDocument(uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("omni_ai_connections_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .padding(horizontal = 16.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = "Multi-AI Hub",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Multi-AI & Context Hub",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Text: ${config.activeTextProvider.shortBadge} • Image: ${config.activeImageProvider.shortBadge} • ${contextSources.count { it.enabled }} Sources",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_ai_hub_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close AI Hub"
                    )
                }
            }

            if (!hubStatusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isHubBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = hubStatusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("AI Providers") },
                    modifier = Modifier.testTag("hub_tab_providers")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Context (${contextSources.count { it.enabled }})") },
                    modifier = Modifier.testTag("hub_tab_sources")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == 0) {
                ProvidersSection(
                    config = config,
                    isHubBusy = isHubBusy,
                    onSelectTextProvider = onSelectTextProvider,
                    onSelectImageProvider = onSelectImageProvider,
                    onToggleFirebaseFallback = onToggleFirebaseFallback,
                    onSaveProviderKey = onSaveProviderKey,
                    onClearProviderKey = onClearProviderKey,
                    onUpdateProviderModelAndUrl = onUpdateProviderModelAndUrl,
                    onStartOpenRouterPkce = {
                        onStartOpenRouterPkce { authUrl ->
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        }
                    }
                )
            } else {
                ContextSourcesSection(
                    sources = contextSources,
                    isHubBusy = isHubBusy,
                    onLaunchSafPicker = {
                        safDocumentLauncher.launch(
                            arrayOf("text/plain", "text/markdown", "application/json", "*/*")
                        )
                    },
                    onAddHttpsSource = onAddHttpsSource,
                    onAddNoteSource = onAddNoteSource,
                    onToggleSourceEnabled = onToggleSourceEnabled,
                    onRemoveSource = onRemoveSource
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProvidersSection(
    config: OmniAiConfig,
    isHubBusy: Boolean,
    onSelectTextProvider: (AiProviderId) -> Unit,
    onSelectImageProvider: (AiProviderId) -> Unit,
    onToggleFirebaseFallback: (Boolean) -> Unit,
    onSaveProviderKey: (AiProviderId, String) -> Unit,
    onClearProviderKey: (AiProviderId) -> Unit,
    onUpdateProviderModelAndUrl: (AiProviderId, String, String, String) -> Unit,
    onStartOpenRouterPkce: () -> Unit
) {
    var editingProvider by remember { mutableStateOf(AiProviderId.OPENROUTER) }
    val currentSettings = config.settingsFor(editingProvider)
    var keyDraft by remember(editingProvider) { mutableStateOf("") }
    var textModelDraft by remember(editingProvider, currentSettings.textModel) {
        mutableStateOf(currentSettings.textModel)
    }
    var imageModelDraft by remember(editingProvider, currentSettings.imageModel) {
        mutableStateOf(currentSettings.imageModel)
    }
    var baseUrlDraft by remember(editingProvider, currentSettings.baseUrl) {
        mutableStateOf(currentSettings.baseUrl)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Active Text / Show Scripting Provider
        item {
            Column {
                Text(
                    text = "ACTIVE TEXT & SHOW-SCRIPTING PROVIDER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiProviderId.entries.filter { it.supportsText }.forEach { provider ->
                        val configured = config.settingsFor(provider).hasApiKey
                        FilterChip(
                            selected = config.activeTextProvider == provider,
                            onClick = { onSelectTextProvider(provider) },
                            label = {
                                Text(
                                    if (configured) "${provider.shortBadge} ✓" else provider.shortBadge
                                )
                            },
                            modifier = Modifier.testTag("text_provider_chip_${provider.id}")
                        )
                    }
                }
            }
        }

        // Active Image / Texture Synthesis Provider
        item {
            Column {
                Text(
                    text = "ACTIVE IMAGE & TEXTURE PROVIDER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiProviderId.entries.filter { it.supportsImage }.forEach { provider ->
                        val configured = config.settingsFor(provider).hasApiKey
                        FilterChip(
                            selected = config.activeImageProvider == provider,
                            onClick = { onSelectImageProvider(provider) },
                            label = {
                                Text(
                                    if (configured) "${provider.shortBadge} ✓" else provider.shortBadge
                                )
                            },
                            modifier = Modifier.testTag("image_provider_chip_${provider.id}")
                        )
                    }
                }
            }
        }

        // Automatic Sideload Fallback Toggle
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Fallback on Sideloaded Installs",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "If Firebase AI Logic fails attestation, automatically route to a configured external provider.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = config.enableFirebaseFallback,
                        onCheckedChange = onToggleFirebaseFallback,
                        modifier = Modifier.testTag("firebase_fallback_switch")
                    )
                }
            }
        }

        // Provider Key Vault & Model Editor
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CONFIGURE PROVIDER KEYS & ENDPOINTS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiProviderId.entries.forEach { provider ->
                            FilterChip(
                                selected = editingProvider == provider,
                                onClick = { editingProvider = provider },
                                label = { Text(provider.shortBadge) }
                            )
                        }
                    }

                    Text(
                        text = editingProvider.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = editingProvider.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (editingProvider == AiProviderId.OPENROUTER) {
                        Button(
                            onClick = onStartOpenRouterPkce,
                            enabled = !isHubBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("openrouter_pkce_connect_button")
                        ) {
                            Icon(Icons.Default.Link, contentDescription = "Connect OpenRouter PKCE")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("1-Tap Connect OpenRouter (PKCE OAuth)")
                        }
                    }

                    if (editingProvider != AiProviderId.FIREBASE_STUDIO) {
                        if (currentSettings.maskedKeyPreview.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Key stored",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Encrypted Key: ${currentSettings.maskedKeyPreview}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onClearProviderKey(editingProvider) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Clear Key", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = keyDraft,
                            onValueChange = { keyDraft = it },
                            label = {
                                Text(
                                    if (editingProvider.requiresApiKey) {
                                        "${editingProvider.shortBadge} API Key (Encrypted in AndroidKeyStore)"
                                    } else {
                                        "Optional Bearer Token (Local Server)"
                                    }
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("provider_key_input")
                        )

                        OutlinedTextField(
                            value = textModelDraft,
                            onValueChange = { textModelDraft = it },
                            label = { Text("Text / Script Model ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (editingProvider.supportsImage) {
                            OutlinedTextField(
                                value = imageModelDraft,
                                onValueChange = { imageModelDraft = it },
                                label = { Text("Image / Texture Model ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (editingProvider == AiProviderId.LOCAL_OPENAI_COMPAT || editingProvider == AiProviderId.OPENROUTER) {
                            OutlinedTextField(
                                value = baseUrlDraft,
                                onValueChange = { baseUrlDraft = it },
                                label = { Text("Base API Endpoint URL") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                if (keyDraft.isNotBlank()) {
                                    onSaveProviderKey(editingProvider, keyDraft)
                                    keyDraft = ""
                                }
                                onUpdateProviderModelAndUrl(
                                    editingProvider,
                                    textModelDraft,
                                    imageModelDraft,
                                    baseUrlDraft
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_provider_settings_button")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = "Save Provider Config")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save ${editingProvider.shortBadge} Settings")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextSourcesSection(
    sources: List<ContextSourceItem>,
    isHubBusy: Boolean,
    onLaunchSafPicker: () -> Unit,
    onAddHttpsSource: (String, String) -> Unit,
    onAddNoteSource: (String, String) -> Unit,
    onToggleSourceEnabled: (String) -> Unit,
    onRemoveSource: (String) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Import SAF Document + HTTPS URL + Notebook Note Actions
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ATTACH DOCUMENTATION & LORE SOURCES",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedButton(
                        onClick = onLaunchSafPicker,
                        enabled = !isHubBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_saf_doc_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import SAF Document")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import .md / .txt / .json File (SAF)")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Attach HTTPS Docs URL (https:// only)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("https_source_input")
                    )
                    Button(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                onAddHttpsSource(urlInput, "")
                                urlInput = ""
                            }
                        },
                        enabled = !isHubBusy && urlInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_https_source_button")
                    ) {
                        Icon(Icons.Default.AddLink, contentDescription = "Fetch HTTPS Doc")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetch & Attach Safe HTTPS Source")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Notebook / Lore Note Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteBody,
                        onValueChange = { noteBody = it },
                        label = { Text("Paste Gemini Notebook / Show Lore Notes") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_source_body_input")
                    )
                    OutlinedButton(
                        onClick = {
                            if (noteBody.isNotBlank()) {
                                onAddNoteSource(noteTitle, noteBody)
                                noteTitle = ""
                                noteBody = ""
                            }
                        },
                        enabled = noteBody.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_note_source_button")
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "Add Note Source")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Pasted Note to Context Hub")
                    }
                }
            }
        }

        // Attached Sources List
        item {
            Text(
                text = "ATTACHED SOURCES (${sources.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (sources.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No external sources attached yet. Live show context is automatically included.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(sources, key = { it.id }) { source ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (source.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onToggleSourceEnabled(source.id) }
                        .testTag("context_source_item_${source.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = source.type.label,
                                tint = if (source.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = source.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${source.type.label} • ${source.content.length} chars",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = source.enabled,
                                onCheckedChange = { onToggleSourceEnabled(source.id) }
                            )
                            IconButton(onClick = { onRemoveSource(source.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Context Source",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
