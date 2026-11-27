package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.ChronoStageViewport
import com.example.model.ConsoleHardwareCatalog
import com.example.model.ConsoleSpec
import com.example.model.ShowProject
import com.example.model.ShowScene

@Composable
fun StageAndTimelineTab(
    activeShow: ShowProject,
    selectedSceneIndex: Int,
    currentScene: ShowScene,
    effectiveConsole: ConsoleSpec,
    consoleOverrideId: String?,
    isPlaying: Boolean,
    autoAdvanceScenes: Boolean,
    sceneProgress: Float,
    wireframeOverlay: Boolean,
    crtAndLcdFilter: Boolean,
    aiCustomBitmap: Bitmap?,
    userYawOffset: Float,
    userPitchOffset: Float,
    onCameraDrag: (Float, Float) -> Unit,
    onResetCamera: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPreviousScene: () -> Unit,
    onNextScene: () -> Unit,
    onSelectScene: (Int) -> Unit,
    onToggleAutoAdvance: () -> Unit,
    onToggleWireframe: () -> Unit,
    onToggleCrtFilter: () -> Unit,
    onSelectConsoleOverride: (String?) -> Unit,
    onEnterBroadcastFullscreen: () -> Unit,
    onSaveShowProject: () -> Unit,
    onExportMarkdown: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("stage_timeline_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Show Header Banner & Save Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeShow.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeShow.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onExportMarkdown,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("export_markdown_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.cd_export_markdown),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.btn_export_markdown),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    OutlinedButton(
                        onClick = onSaveShowProject,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("save_show_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = stringResource(R.string.btn_save_show),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // 2. Interactive 3D + 2D Sprite Hybrid Viewport
        item {
            Column {
                ChronoStageViewport(
                    scene = currentScene,
                    consoleSpec = effectiveConsole,
                    isPlaying = isPlaying,
                    wireframeOverlay = wireframeOverlay,
                    crtAndLcdFilterEnabled = crtAndLcdFilter,
                    aiCustomBitmap = aiCustomBitmap,
                    userYawOffset = userYawOffset,
                    userPitchOffset = userPitchOffset,
                    onCameraDrag = onCameraDrag,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(275.dp)
                        .border(
                            width = 1.5.dp,
                            color = effectiveConsole.accentColor.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(16.dp)
                        )
                )

                // Scene Sequencer Progress Bar
                LinearProgressIndicator(
                    progress = { sceneProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = effectiveConsole.accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // 3. Transport & Viewport Shader Controls Bar
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onPreviousScene,
                            modifier = Modifier.testTag("prev_scene_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = stringResource(R.string.cd_prev_scene)
                            )
                        }

                        Button(
                            onClick = onTogglePlayPause,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play_pause),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPlaying) "Pause" else "Play",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        IconButton(
                            onClick = onNextScene,
                            modifier = Modifier.testTag("next_scene_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = stringResource(R.string.cd_next_scene)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleAutoAdvance,
                            modifier = Modifier.testTag("auto_advance_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Toggle Auto-Advance Scenes",
                                tint = if (autoAdvanceScenes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onToggleWireframe,
                            modifier = Modifier.testTag("wireframe_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = stringResource(R.string.cd_wireframe_toggle),
                                tint = if (wireframeOverlay) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onToggleCrtFilter,
                            modifier = Modifier.testTag("crt_filter_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = stringResource(R.string.cd_crt_toggle),
                                tint = if (crtAndLcdFilter) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onResetCamera,
                            modifier = Modifier.testTag("reset_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CenterFocusStrong,
                                contentDescription = stringResource(R.string.cd_camera_reset)
                            )
                        }
                        IconButton(
                            onClick = onEnterBroadcastFullscreen,
                            modifier = Modifier.testTag("broadcast_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.cd_broadcast_fullscreen),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 4. Instant Console & Handheld Hardware Switcher Bar
        item {
            Column {
                Text(
                    text = "LIVE CONSOLE & HANDHELD RENDERING PIPELINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = consoleOverrideId == null,
                        onClick = { onSelectConsoleOverride(null) },
                        label = { Text("Per-Scene Auto") },
                        modifier = Modifier.testTag("chip_console_auto")
                    )
                    ConsoleHardwareCatalog.allConsoles.forEach { spec ->
                        val isSelected = consoleOverrideId == spec.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectConsoleOverride(spec.id) },
                            label = {
                                Text("${spec.shortCode} (${spec.formFactor.label.substringBefore(" ")})")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = spec.accentColor.copy(alpha = 0.24f),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("chip_console_${spec.id}")
                        )
                    }
                }
            }
        }

        // 5. Multi-Scene Online Show Timeline Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SHOW TIMELINE SEQUENCER (${activeShow.scenes.size} SCENES)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tap any scene to cue stage",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        itemsIndexed(activeShow.scenes, key = { idx, s -> "${s.id}_$idx" }) { index, scene ->
            val sceneConsole = ConsoleHardwareCatalog.findById(consoleOverrideId ?: scene.consoleId)
            val isSelected = index == selectedSceneIndex

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectScene(index) }
                    .border(
                        width = if (isSelected) 1.5.dp else 0.dp,
                        color = if (isSelected) sceneConsole.accentColor else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .testTag("timeline_scene_card_$index"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) sceneConsole.accentColor else MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = scene.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${sceneConsole.name} • ${scene.durationSeconds}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sceneConsole.accentColor
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = scene.cameraMove.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"${scene.narratorScript}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SceneAssetBadge(
                            label = "3D: ${scene.model3D.displayName}",
                            accent = MaterialTheme.colorScheme.primary
                        )
                        SceneAssetBadge(
                            label = "Sprites: ${scene.primarySprite.displayName.substringBefore(" (")}",
                            accent = MaterialTheme.colorScheme.secondary
                        )
                    }

                    AnimatedVisibility(visible = isSelected) {
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            Text(
                                text = "Texture: ${scene.texturePreset.displayName} • FX: ${scene.particleFx.displayName} • Doc: ${scene.documentationExcerpt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneAssetBadge(label: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
