package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.OmniAiConfig
import com.example.data.StarterShowPresets
import com.example.model.ConsoleHardwareCatalog
import com.example.viewmodel.DirectorStreamState
import com.example.viewmodel.DocToShowUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDocDirectorTab(
    documentationInput: String,
    preferredTargetConsoleId: String?,
    omniAiConfig: OmniAiConfig,
    enabledContextSourcesCount: Int,
    docToShowState: DocToShowUiState,
    directorStreamState: DirectorStreamState,
    onUpdateDocumentation: (String) -> Unit,
    onSelectPreferredConsole: (String?) -> Unit,
    onGenerateShow: () -> Unit,
    onStreamDirectorNotes: () -> Unit,
    onOpenStageTab: () -> Unit,
    onOpenAiHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    var consoleDropdownExpanded by remember { mutableStateOf(false) }
    val selectedConsoleLabel = remember(preferredTargetConsoleId) {
        if (preferredTargetConsoleId == null) {
            "Multi-Console Mix (Auto per Scene)"
        } else {
            val spec = ConsoleHardwareCatalog.findById(preferredTargetConsoleId)
            "${spec.name} (${spec.releaseYear} • ${spec.formFactor.label})"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_doc_director_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Studio Hero Banner Card + Compact AI Hub Launcher
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(158.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_banner_1794393960969),
                    contentDescription = stringResource(R.string.cd_studio_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x44070A12),
                                    Color(0xEB070A12)
                                )
                            )
                        )
                        .padding(14.dp)
                ) {
                    // Compact AI Hub button in top-right of banner
                    Button(
                        onClick = onOpenAiHub,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xDD171F36),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                RoundedCornerShape(10.dp)
                            )
                            .testTag("open_ai_hub_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = stringResource(R.string.cd_ai_hub),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(R.string.btn_ai_hub)} (${enabledContextSourcesCount})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(modifier = Modifier.align(Alignment.BottomStart)) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "TEXT: ${omniAiConfig.activeTextProvider.shortBadge.uppercase()} • IMG: ${omniAiConfig.activeImageProvider.shortBadge.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Documentation-to-Animation Show Studio",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Transforms docs, SAF files, HTTPS specs & notes into multi-scene 3D + 2D sprite shows.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD0DCFF)
                        )
                    }
                }
            }
        }

        // 2. Quick Starter Documentation Presets
        item {
            Column {
                Text(
                    text = "STARTER DOCUMENTATION TEMPLATES",
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
                    StarterShowPresets.documentationTemplates.forEachIndexed { idx, preset ->
                        OutlinedButton(
                            onClick = {
                                onUpdateDocumentation(preset.documentationText)
                                onSelectPreferredConsole(preset.preferredConsoleId)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("doc_preset_button_$idx")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = preset.title,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${preset.badge}: ${preset.title}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        // 3. Target Console / Handheld Dropdown Menu + Quick Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TARGET RETRO OR MODERN CONSOLE STYLE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExposedDropdownMenuBox(
                    expanded = consoleDropdownExpanded,
                    onExpandedChange = { consoleDropdownExpanded = !consoleDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedConsoleLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.label_console_dropdown)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = consoleDropdownExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("target_console_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = consoleDropdownExpanded,
                        onDismissRequest = { consoleDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Multi-Console Mix (Auto per Scene)") },
                            onClick = {
                                onSelectPreferredConsole(null)
                                consoleDropdownExpanded = false
                            }
                        )
                        ConsoleHardwareCatalog.allConsoles.forEach { spec ->
                            DropdownMenuItem(
                                text = {
                                    Text("${spec.name} (${spec.releaseYear} • ${spec.shortCode})")
                                },
                                onClick = {
                                    onSelectPreferredConsole(spec.id)
                                    consoleDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = preferredTargetConsoleId == null,
                        onClick = { onSelectPreferredConsole(null) },
                        label = { Text("Multi-Console Mix") },
                        modifier = Modifier.testTag("ai_target_console_mix")
                    )
                    ConsoleHardwareCatalog.allConsoles.forEach { spec ->
                        FilterChip(
                            selected = preferredTargetConsoleId == spec.id,
                            onClick = { onSelectPreferredConsole(spec.id) },
                            label = { Text("${spec.shortCode} (${spec.releaseYear})") },
                            modifier = Modifier.testTag("ai_target_console_${spec.id}")
                        )
                    }
                }
            }
        }

        // 4. Documentation Input Field
        item {
            OutlinedTextField(
                value = documentationInput,
                onValueChange = onUpdateDocumentation,
                label = { Text("Paste Technical Documentation, Lore, or Online Show Script") },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("documentation_input_field")
            )
        }

        // 5. Primary AI Action Buttons (Generate Structured Show + Stream Director Notes)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onGenerateShow,
                    enabled = docToShowState !is DocToShowUiState.Generating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("generate_show_button")
                ) {
                    if (docToShowState is DocToShowUiState.Generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Synthesizing 3D Models, 2D Sprites & Scenes (up to 60s)...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.MovieFilter,
                            contentDescription = stringResource(R.string.btn_generate_show)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_generate_show),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = onStreamDirectorNotes,
                    enabled = directorStreamState !is DirectorStreamState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("stream_director_notes_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = stringResource(R.string.btn_polish_script),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_polish_script),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 6. Structured Show Generation Status Banner
        item {
            when (docToShowState) {
                is DocToShowUiState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("doc_to_show_success_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "AI SHOW READY ON STAGE",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = docToShowState.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onOpenStageTab,
                                modifier = Modifier.testTag("jump_to_stage_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleFilled,
                                    contentDescription = "Watch Animated Show"
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Watch Live on 3D/2D Stage")
                            }
                        }
                    }
                }
                is DocToShowUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("doc_to_show_error_card")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "AI Generation Error",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = docToShowState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
                else -> Unit
            }
        }

        // 7. Streaming AI Director Notes Output Card
        item {
            when (directorStreamState) {
                is DirectorStreamState.Loading -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Connecting to ${omniAiConfig.activeTextProvider.displayName}...")
                        }
                    }
                }
                is DirectorStreamState.Streaming -> {
                    DirectorNotesCard(
                        badge = "LIVE STREAMING CHOREOGRAPHY NOTES...",
                        notes = directorStreamState.text
                    )
                }
                is DirectorStreamState.Success -> {
                    DirectorNotesCard(
                        badge = "AI SHOW DIRECTOR CHOREOGRAPHY NOTES",
                        notes = directorStreamState.text
                    )
                }
                is DirectorStreamState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Director Stream Error: ${directorStreamState.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
                is DirectorStreamState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun DirectorNotesCard(badge: String, notes: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("director_notes_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}
