package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ConsoleFormFactor
import com.example.model.ConsoleHardwareCatalog
import com.example.model.ConsoleSpec

@Composable
fun ConsolesAndHandheldsTab(
    activeConsoleId: String,
    consoleOverrideId: String?,
    onApplyConsoleToStage: (String?) -> Unit,
    onApplyConsoleToCurrentScene: (String) -> Unit,
    onJumpToStage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<ConsoleFormFactor?>(null) }

    val filteredConsoles = remember(selectedFilter) {
        if (selectedFilter == null) {
            ConsoleHardwareCatalog.allConsoles
        } else {
            ConsoleHardwareCatalog.allConsoles.filter { it.formFactor == selectedFilter }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("consoles_handhelds_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "VIDEO GAME CONSOLE & HANDHELD ENGINES",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Authentic rasterizers, vertex snapping, dot-matrix LCDs, and sprite limits from 1983 to Modern HD-2D.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("All Systems (${ConsoleHardwareCatalog.allConsoles.size})") },
                        modifier = Modifier.testTag("filter_all_consoles")
                    )
                    ConsoleFormFactor.entries.forEach { factor ->
                        FilterChip(
                            selected = selectedFilter == factor,
                            onClick = { selectedFilter = factor },
                            label = { Text(factor.label) },
                            modifier = Modifier.testTag("filter_${factor.name.lowercase()}")
                        )
                    }
                }
            }
        }

        items(filteredConsoles, key = { it.id }) { spec ->
            val isActive = activeConsoleId == spec.id
            ConsoleHardwareCard(
                spec = spec,
                isActive = isActive,
                isGlobalOverride = consoleOverrideId == spec.id,
                onLockGlobalOverride = {
                    onApplyConsoleToStage(spec.id)
                    onJumpToStage()
                },
                onAssignToCurrentScene = {
                    onApplyConsoleToStage(null)
                    onApplyConsoleToCurrentScene(spec.id)
                    onJumpToStage()
                }
            )
        }
    }
}

@Composable
private fun ConsoleHardwareCard(
    spec: ConsoleSpec,
    isActive: Boolean,
    isGlobalOverride: Boolean,
    onLockGlobalOverride: () -> Unit,
    onAssignToCurrentScene: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) spec.accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("console_card_${spec.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (spec.formFactor == ConsoleFormFactor.HANDHELD) {
                            Icons.Default.VideogameAsset
                        } else {
                            Icons.Default.SportsEsports
                        },
                        contentDescription = spec.formFactor.label,
                        tint = spec.accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = spec.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${spec.manufacturer} (${spec.releaseYear}) • ${spec.formFactor.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = spec.accentColor
                        )
                    }
                }

                // Hardware Palette Swatches
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    spec.tintPalette.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hardware Specs Grid
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SpecRow("Resolution & Shader", "${spec.resolutionLabel} • ${spec.shadingPipeline.title}")
                    SpecRow("Color Architecture", spec.maxColorsLabel)
                    SpecRow("3D Geometry Pipeline", spec.polyBudgetLabel)
                    SpecRow("2D Sprite Hardware", spec.spriteHardwareLabel)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAssignToCurrentScene,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = spec.accentColor,
                        contentColor = Color(0xFF070A12)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("apply_scene_console_${spec.id}")
                ) {
                    if (isActive && !isGlobalOverride) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "Use in Scene",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onLockGlobalOverride,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lock_show_console_${spec.id}")
                ) {
                    Text(
                        text = if (isGlobalOverride) "Locked to Show" else "Lock Whole Show",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(128.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
    }
}
