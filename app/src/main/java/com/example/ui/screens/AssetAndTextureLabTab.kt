package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AnimationMotion
import com.example.model.CameraMove
import com.example.model.Model3DType
import com.example.model.ParticleFxType
import com.example.model.ShowScene
import com.example.model.SpriteActorType
import com.example.model.TexturePreset
import com.example.viewmodel.TextureGenUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssetAndTextureLabTab(
    currentScene: ShowScene,
    texturePromptInput: String,
    textureGenState: TextureGenUiState,
    onUpdateScene: ((ShowScene) -> ShowScene) -> Unit,
    onUpdateTexturePrompt: (String) -> Unit,
    onGenerateAiTexture: () -> Unit,
    onJumpToStage: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("asset_texture_lab_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "3D MESH, SPRITE & TEXTURE LAB",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Editing Scene ${currentScene.index + 1}: ${currentScene.title}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = onJumpToStage,
                    modifier = Modifier.testTag("lab_preview_stage_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = "Preview on Stage",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Stage")
                }
            }
        }

        // 1. 3D Model Actor Selection
        item {
            LabSectionCard(
                title = "1. 3D MODEL MESH ACTOR",
                subtitle = "Articulated low-poly & high-fidelity 3D models with real-time vertex lighting"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Model3DType.entries.forEach { modelType ->
                        val selected = currentScene.model3D == modelType
                        Surface(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    onUpdateScene { it.copy(model3D = modelType) }
                                }
                                .testTag("select_model_${modelType.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = modelType.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = modelType.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${modelType.polyCount} polys",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "3D KEYFRAME ANIMATION CYCLE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AnimationMotion.entries.forEach { motion ->
                            FilterChip(
                                selected = currentScene.modelMotion == motion,
                                onClick = { onUpdateScene { it.copy(modelMotion = motion) } },
                                label = { Text(motion.displayName) },
                                modifier = Modifier.testTag("select_motion_${motion.id}")
                            )
                        }
                    }

                    // Scale & Light Sliders
                    Text(
                        text = "3D Model Scale: ${"%.2f".format(currentScene.modelScale)}x",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    Slider(
                        value = currentScene.modelScale,
                        onValueChange = { s -> onUpdateScene { it.copy(modelScale = s) } },
                        valueRange = 0.65f..1.45f,
                        modifier = Modifier.testTag("slider_model_scale")
                    )
                }
            }
        }

        // 2. 2D Pixel Sprite Actors (Primary & Secondary Billboards)
        item {
            LabSectionCard(
                title = "2. 2D PIXEL SPRITE ACTORS (DEPTH-SORTED IN 3D)",
                subtitle = "Multi-frame console & handheld sprites composited into the 3D stage"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PRIMARY HERO / NARRATOR SPRITE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SpriteActorType.entries.forEach { actor ->
                            FilterChip(
                                selected = currentScene.primarySprite == actor,
                                onClick = { onUpdateScene { it.copy(primarySprite = actor) } },
                                label = { Text("${actor.displayName} [${actor.eraTag}]") },
                                modifier = Modifier.testTag("select_primary_sprite_${actor.id}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SECONDARY COMPANION / BOSS SPRITE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SpriteActorType.entries.forEach { actor ->
                            FilterChip(
                                selected = currentScene.secondarySprite == actor,
                                onClick = { onUpdateScene { it.copy(secondarySprite = actor) } },
                                label = { Text(actor.displayName) },
                                modifier = Modifier.testTag("select_secondary_sprite_${actor.id}")
                            )
                        }
                    }
                }
            }
        }

        // 3. Camera Choreography & Stage Particle FX
        item {
            LabSectionCard(
                title = "3. CAMERA CHOREOGRAPHY & STAGE FX",
                subtitle = "Broadcast camera rig movement and background telemetry particles"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "3D CAMERA RIG MOVEMENT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CameraMove.entries.forEach { cam ->
                            FilterChip(
                                selected = currentScene.cameraMove == cam,
                                onClick = { onUpdateScene { it.copy(cameraMove = cam) } },
                                label = { Text(cam.displayName) },
                                modifier = Modifier.testTag("select_camera_${cam.id}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "STAGE PARTICLE & TELEMETRY FX",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ParticleFxType.entries.forEach { fx ->
                            FilterChip(
                                selected = currentScene.particleFx == fx,
                                onClick = { onUpdateScene { it.copy(particleFx = fx) } },
                                label = { Text(fx.displayName) },
                                modifier = Modifier.testTag("select_fx_${fx.id}")
                            )
                        }
                    }
                }
            }
        }

        // 4. Procedural UV Shaders & Runtime AI Texture Studio (Nano Banana 2.1)
        item {
            LabSectionCard(
                title = "4. SURFACE TEXTURES & AI TEXTURE SYNTHESIS",
                subtitle = "Procedural UV maps + Gemini Nano Banana 2.1 runtime texture/sprite generator"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TexturePreset.entries.forEach { tex ->
                            FilterChip(
                                selected = currentScene.texturePreset == tex,
                                onClick = { onUpdateScene { it.copy(texturePreset = tex) } },
                                label = { Text(tex.displayName) },
                                modifier = Modifier.testTag("select_texture_${tex.id}")
                            )
                        }
                    }

                    // Quick AI Texture Prompt Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "16-bit SNES crystal dungeon mosaic texture",
                            "PS1 mecha brushed titanium armor plates UV texture",
                            "Game Boy 4-shade olive pixel sprite sheet",
                            "Dreamcast cel-shaded neon cyber city skybox"
                        ).forEachIndexed { i, presetPrompt ->
                            OutlinedButton(
                                onClick = { onUpdateTexturePrompt(presetPrompt) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("ai_tex_preset_$i")
                            ) {
                                Text(presetPrompt, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = texturePromptInput,
                        onValueChange = onUpdateTexturePrompt,
                        label = { Text("Runtime AI Texture / Sprite Sheet Prompt (Nano Banana 2.1)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("texture_prompt_input")
                    )

                    Button(
                        onClick = onGenerateAiTexture,
                        enabled = textureGenState !is TextureGenUiState.Loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_ai_texture_button")
                    ) {
                        if (textureGenState is TextureGenUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Synthesizing Texture with Nano Banana 2.1 (30-60s)...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = stringResource(R.string.btn_generate_texture)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_generate_texture),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    when (textureGenState) {
                        is TextureGenUiState.Success -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "AI Texture Mapped to 3D Model & Stage Backdrop:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Image(
                                    bitmap = textureGenState.bitmap.asImageBitmap(),
                                    contentDescription = "Generated AI Texture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(12.dp)
                                        )
                                )
                            }
                        }
                        is TextureGenUiState.Error -> {
                            Text(
                                text = "AI Texture Error: ${textureGenState.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("texture_gen_error_text")
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun LabSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
