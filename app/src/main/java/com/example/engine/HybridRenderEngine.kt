package com.example.engine

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CameraMove
import com.example.model.ConsoleSpec
import com.example.model.ParticleFxType
import com.example.model.ShadingPipeline
import com.example.model.ShowScene
import com.example.model.SpriteActorType
import com.example.model.TexturePreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private sealed interface RenderPrimitive {
    val depthZ: Float

    data class FacePrimitive(
        override val depthZ: Float,
        val screenPoints: List<Offset>,
        val litColor: Color,
        val edgeColor: Color,
        val uvHint: Int,
        val normalZ: Float,
        val worldCenter: Vec3
    ) : RenderPrimitive

    data class SpriteBillboardPrimitive(
        override val depthZ: Float,
        val screenCenter: Offset,
        val scaleFactor: Float,
        val actor: SpriteActorType,
        val frameIndex: Int,
        val isPrimary: Boolean
    ) : RenderPrimitive
}

/**
 * Snaps a screen-space coordinate to the active console's pixel grid or integer vertex snap step.
 * When `pixelGridScale <= 1` and `vertexSnapStrength <= 0.5f`, coordinates remain razor sharp.
 */
fun snapToPixelGrid(
    value: Float,
    pixelGridScale: Int,
    vertexSnapStrength: Float = 0f
): Float {
    val gridStep = if (pixelGridScale > 1) pixelGridScale.toFloat() else 0f
    val effectiveSnap = maxOf(vertexSnapStrength, gridStep)
    return if (effectiveSnap > 0.5f) {
        (value / effectiveSnap).roundToInt() * effectiveSnap
    } else {
        value
    }
}

@Composable
fun ChronoStageViewport(
    scene: ShowScene,
    consoleSpec: ConsoleSpec,
    isPlaying: Boolean,
    wireframeOverlay: Boolean,
    crtAndLcdFilterEnabled: Boolean,
    aiCustomBitmap: Bitmap?,
    userYawOffset: Float,
    userPitchOffset: Float,
    onCameraDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var animTimeSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L && isPlaying) {
                    val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    animTimeSec += dt
                }
                lastNanos = nanos
            }
        }
    }

    val isGameBoyDmg = consoleSpec.shadingPipeline == ShadingPipeline.DOT_MATRIX_LCD

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(consoleSpec.stageSkyTop)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onCameraDrag(dragAmount.x * 0.008f, dragAmount.y * 0.006f)
                }
            }
            .testTag("chronostage_viewport")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 1f || h <= 1f) return@Canvas

            // 1. Stage Sky & Atmospheric Horizon
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(consoleSpec.stageSkyTop, consoleSpec.stageSkyBottom),
                    startY = 0f,
                    endY = h * 0.68f
                ),
                size = Size(w, h)
            )

            // Optional AI Custom Bitmap Backdrop Banner in upper sky when present
            if (aiCustomBitmap != null && !isGameBoyDmg) {
                drawImage(
                    image = aiCustomBitmap.asImageBitmap(),
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(w.toInt(), (h * 0.62f).toInt()),
                    alpha = if (scene.texturePreset == TexturePreset.AI_CUSTOM_BITMAP) 0.42f else 0.22f
                )
            }

            // 2. Mode 7 / 3D Perspective Stage Floor Grid
            drawPerspectiveStageFloor(
                w = w,
                h = h,
                timeSec = animTimeSec,
                consoleSpec = consoleSpec
            )

            // 3. Background Particle / Documentation FX
            drawStageParticleFx(
                w = w,
                h = h,
                timeSec = animTimeSec,
                fx = scene.particleFx,
                consoleSpec = consoleSpec
            )

            // 4. Camera Choreography + User Touch Orbit
            val (camYaw, camPitch, camDist) = computeCameraTransform(
                move = scene.cameraMove,
                timeSec = animTimeSec,
                userYaw = userYawOffset,
                userPitch = userPitchOffset
            )

            // 5. Build 3D Mesh Faces + 2D Sprite Billboards in unified 3D space
            val faces = Mesh3DBuilder.buildAnimatedMesh(
                type = scene.model3D,
                motion = scene.modelMotion,
                timeSec = animTimeSec,
                scale = scene.modelScale
            )

            val lightRad = (scene.lightAzimuth + animTimeSec * 25f) * (PI.toFloat() / 180f)
            val lightDir = Vec3(cos(lightRad) * 0.7f, 0.65f, sin(lightRad) * 0.7f).normalized()
            val fovScale = minOf(w, h) * 0.88f

            val primitives = mutableListOf<RenderPrimitive>()

            for (face in faces) {
                val transformed = face.vertices.map { v ->
                    v.rotateY(camYaw).rotateX(camPitch)
                }
                val v0 = transformed[0]
                val v1 = transformed[1]
                val v2 = transformed[2]
                val normal = (v1 - v0).cross(v2 - v0).normalized()

                val center = transformed.fold(Vec3(0f, 0f, 0f)) { acc, item -> acc + item } *
                    (1f / transformed.size.toFloat())
                val viewZ = camDist - center.z
                if (viewZ < 0.8f) continue

                val screenPts = transformed.map { tv ->
                    val pz = (camDist - tv.z).coerceAtLeast(0.6f)
                    val sx = w * 0.5f + (tv.x / pz) * fovScale
                    val sy = h * 0.50f - (tv.y / pz) * fovScale
                    // Apply PS1/Saturn vertex snapping AND console pixelGridScale quantization
                    Offset(
                        snapToPixelGrid(sx, consoleSpec.pixelGridScale, consoleSpec.vertexSnapStrength),
                        snapToPixelGrid(sy, consoleSpec.pixelGridScale, consoleSpec.vertexSnapStrength)
                    )
                }

                val baseColor = resolveRoleColor(
                    role = face.colorRole,
                    consoleSpec = consoleSpec,
                    aiBitmap = aiCustomBitmap,
                    useAiTexture = scene.texturePreset == TexturePreset.AI_CUSTOM_BITMAP,
                    uvHint = face.uvHint
                )
                val litColor = applyConsoleShading(
                    baseColor = baseColor,
                    normal = normal,
                    lightDir = lightDir,
                    depthZ = viewZ,
                    consoleSpec = consoleSpec,
                    isEmissive = face.colorRole == ColorRole.EMISSIVE
                )

                val edgeColor = when {
                    wireframeOverlay -> consoleSpec.accentColor
                    consoleSpec.shadingPipeline == ShadingPipeline.RETRO_8BIT_TILE -> consoleSpec.tintPalette.first()
                    consoleSpec.shadingPipeline == ShadingPipeline.CEL_ARCADE_128 -> Color(0xFF05070E)
                    else -> litColor.copy(alpha = 0.85f)
                }

                primitives += RenderPrimitive.FacePrimitive(
                    depthZ = viewZ,
                    screenPoints = screenPts,
                    litColor = litColor,
                    edgeColor = edgeColor,
                    uvHint = face.uvHint,
                    normalZ = normal.z,
                    worldCenter = center
                )
            }

            // Add Primary & Secondary 2D Sprite Actors orbiting/staging around the 3D Model
            val spriteFrame = (animTimeSec * 6.5f).toInt()
            val primaryWorld = Vec3(
                x = -1.35f + cos(animTimeSec * 1.3f) * 0.25f,
                y = -0.35f + abs(sin(animTimeSec * 4.5f)) * 0.18f,
                z = 0.55f + sin(animTimeSec * 1.3f) * 0.45f
            ).rotateY(camYaw * 0.35f).rotateX(camPitch * 0.5f)

            val secondaryWorld = Vec3(
                x = 1.38f + sin(animTimeSec * 1.5f) * 0.25f,
                y = -0.25f + abs(cos(animTimeSec * 4.0f)) * 0.16f,
                z = -0.25f + cos(animTimeSec * 1.5f) * 0.55f
            ).rotateY(camYaw * 0.35f).rotateX(camPitch * 0.5f)

            listOf(
                Triple(primaryWorld, scene.primarySprite, true),
                Triple(secondaryWorld, scene.secondarySprite, false)
            ).forEach { (worldPos, actor, isPrimary) ->
                val pz = (camDist - worldPos.z).coerceAtLeast(1.0f)
                val sx = w * 0.5f + (worldPos.x / pz) * fovScale
                val sy = h * 0.52f - (worldPos.y / pz) * fovScale
                val spriteScale = (fovScale / pz) * 0.028f
                primitives += RenderPrimitive.SpriteBillboardPrimitive(
                    depthZ = pz,
                    screenCenter = Offset(sx, sy),
                    scaleFactor = spriteScale,
                    actor = actor,
                    frameIndex = if (isPrimary) spriteFrame else spriteFrame + 2,
                    isPrimary = isPrimary
                )
            }

            // HD-2D Volumetric Point Light Glow behind the stage center
            if (consoleSpec.shadingPipeline == ShadingPipeline.HD2D_VOXEL_BLOOM) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            consoleSpec.accentColor.copy(alpha = 0.32f),
                            consoleSpec.tintPalette[1].copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h * 0.48f),
                        radius = minOf(w, h) * 0.45f
                    ),
                    center = Offset(w * 0.5f, h * 0.48f),
                    radius = minOf(w, h) * 0.45f
                )
            }

            // 6. Painter's Algorithm Z-Buffer Sort (back to front)
            primitives.sortByDescending { it.depthZ }

            for (prim in primitives) {
                when (prim) {
                    is RenderPrimitive.FacePrimitive -> {
                        draw3DFacePrimitive(
                            face = prim,
                            texturePreset = scene.texturePreset,
                            consoleSpec = consoleSpec,
                            wireframeOnly = wireframeOverlay
                        )
                    }
                    is RenderPrimitive.SpriteBillboardPrimitive -> {
                        draw2DSpriteBillboard(
                            sprite = prim,
                            consoleSpec = consoleSpec,
                            timeSec = animTimeSec
                        )
                    }
                }
            }

            // 7. Hardware Post-Processing (CRT Scanlines / Handheld Dot-Matrix LCD Grid)
            if (crtAndLcdFilterEnabled) {
                drawHardwareDisplayFilter(w = w, h = h, consoleSpec = consoleSpec)
            }
        }

        // Top Telemetry Overlay Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xCC070A12),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(consoleSpec.accentColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${consoleSpec.shortCode} • ${consoleSpec.resolutionLabel} • ${consoleSpec.shadingPipeline.badge}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            Surface(
                color = Color(0xCC070A12),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${scene.model3D.polyCount} POLYS + 2 SPRITES • 60 FPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = consoleSpec.accentColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Bottom Broadcast Show Teleprompter & Documentation Callout
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Surface(
                color = Color(0xD90A0E1A),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SCENE ${scene.index + 1}: ${scene.title.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = consoleSpec.accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "DOC SOURCE: ${scene.documentationExcerpt}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF9BA8C7),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(170.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "\"${scene.narratorScript}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun computeCameraTransform(
    move: CameraMove,
    timeSec: Float,
    userYaw: Float,
    userPitch: Float
): Triple<Float, Float, Float> {
    val autoYaw: Float
    val autoPitch: Float
    val autoDist: Float

    when (move) {
        CameraMove.ORBIT_360 -> {
            autoYaw = timeSec * 0.75f
            autoPitch = 0.22f + sin(timeSec * 0.6f) * 0.08f
            autoDist = 4.3f
        }
        CameraMove.DRAMATIC_PUSH_IN -> {
            autoYaw = sin(timeSec * 0.5f) * 0.55f
            autoPitch = 0.16f
            autoDist = 4.1f - sin(timeSec * 0.9f) * 0.75f
        }
        CameraMove.WIDE_CRANE_SWEEP -> {
            autoYaw = cos(timeSec * 0.45f) * 0.85f
            autoPitch = 0.35f + sin(timeSec * 0.8f) * 0.20f
            autoDist = 4.6f
        }
        CameraMove.ISOMETRIC_TACTICAL -> {
            autoYaw = 0.78f + sin(timeSec * 0.3f) * 0.15f
            autoPitch = 0.52f
            autoDist = 4.5f
        }
        CameraMove.HANDHELD_ACTION_CAM -> {
            autoYaw = timeSec * 0.95f + sin(timeSec * 4.2f) * 0.06f
            autoPitch = 0.18f + cos(timeSec * 3.7f) * 0.05f
            autoDist = 3.9f
        }
    }

    return Triple(
        autoYaw + userYaw,
        (autoPitch + userPitch).coerceIn(-0.65f, 0.85f),
        autoDist
    )
}

private fun resolveRoleColor(
    role: ColorRole,
    consoleSpec: ConsoleSpec,
    aiBitmap: Bitmap?,
    useAiTexture: Boolean,
    uvHint: Int
): Color {
    if (useAiTexture && aiBitmap != null && aiBitmap.width > 4 && aiBitmap.height > 4) {
        val sampleX = ((uvHint * 37 + 19) % aiBitmap.width).coerceIn(0, aiBitmap.width - 1)
        val sampleY = ((uvHint * 53 + 11) % aiBitmap.height).coerceIn(0, aiBitmap.height - 1)
        val pixel = aiBitmap.getPixel(sampleX, sampleY)
        return Color(pixel)
    }

    val p = consoleSpec.tintPalette
    return when (role) {
        ColorRole.PRIMARY -> p.getOrElse(0) { Color(0xFF00F0FF) }
        ColorRole.SECONDARY -> p.getOrElse(1) { Color(0xFFFF2A85) }
        ColorRole.ACCENT -> p.getOrElse(2) { Color(0xFFFFB800) }
        ColorRole.EMISSIVE -> p.getOrElse(3) { Color(0xFF00E676) }
        ColorRole.DARK_METAL -> Color(0xFF26324B)
    }
}

private fun applyConsoleShading(
    baseColor: Color,
    normal: Vec3,
    lightDir: Vec3,
    depthZ: Float,
    consoleSpec: ConsoleSpec,
    isEmissive: Boolean
): Color {
    val ndotl = normal.dot(lightDir).coerceIn(-1f, 1f)
    val rawIntensity = if (isEmissive) 1.15f else (0.38f + (ndotl + 1f) * 0.35f)

    return when (consoleSpec.shadingPipeline) {
        ShadingPipeline.DOT_MATRIX_LCD -> {
            // Quantize strictly to the 4 Game Boy DMG olive shades
            val palette = consoleSpec.tintPalette
            when {
                rawIntensity > 0.92f -> palette[0]
                rawIntensity > 0.68f -> palette[1]
                rawIntensity > 0.45f -> palette[2]
                else -> palette[3]
            }
        }
        ShadingPipeline.CEL_ARCADE_128 -> {
            // 3-band Cel Shading
            val band = when {
                isEmissive -> 1.1f
                rawIntensity > 0.82f -> 1.05f
                rawIntensity > 0.55f -> 0.78f
                else -> 0.45f
            }
            scaleColor(baseColor, band)
        }
        ShadingPipeline.N64_ZBUFFER_FOG -> {
            val lit = scaleColor(baseColor, rawIntensity)
            val fogFactor = ((depthZ - 3.6f) / 3.2f).coerceIn(0f, 0.65f)
            lerpColor(lit, consoleSpec.stageSkyBottom, fogFactor)
        }
        else -> scaleColor(baseColor, rawIntensity)
    }
}

private fun scaleColor(c: Color, factor: Float): Color {
    return Color(
        red = (c.red * factor).coerceIn(0f, 1f),
        green = (c.green * factor).coerceIn(0f, 1f),
        blue = (c.blue * factor).coerceIn(0f, 1f),
        alpha = c.alpha
    )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t
    )
}

private fun DrawScope.drawPerspectiveStageFloor(
    w: Float,
    h: Float,
    timeSec: Float,
    consoleSpec: ConsoleSpec
) {
    val horizonY = h * 0.64f
    // Floor base fill
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                consoleSpec.stageFloorColor.copy(alpha = 0.55f),
                consoleSpec.stageSkyTop
            ),
            startY = horizonY,
            endY = h
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h - horizonY)
    )

    val gridColor = consoleSpec.accentColor.copy(alpha = 0.36f)
    // Radial perspective lines from vanishing point
    val vanishX = w * 0.5f
    for (i in -8..8) {
        val bottomX = vanishX + i * (w * 0.14f)
        drawLine(
            color = gridColor,
            start = Offset(vanishX, horizonY),
            end = Offset(bottomX, h),
            strokeWidth = 1.5f
        )
    }

    // Scrolling horizontal depth lines (Mode 7 / OutRun style)
    val scrollPhase = (timeSec * 0.85f) % 1f
    for (row in 0..8) {
        val norm = ((row + scrollPhase) / 9f).coerceIn(0.02f, 1f)
        val y = horizonY + (norm * norm) * (h - horizonY)
        drawLine(
            color = gridColor.copy(alpha = (norm * 0.55f).coerceIn(0.1f, 0.6f)),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1.5f
        )
    }
}

private fun DrawScope.drawStageParticleFx(
    w: Float,
    h: Float,
    timeSec: Float,
    fx: ParticleFxType,
    consoleSpec: ConsoleSpec
) {
    val c1 = consoleSpec.tintPalette.firstOrNull() ?: Color.Cyan
    val c2 = consoleSpec.accentColor

    when (fx) {
        ParticleFxType.STARFIELD_WARP -> {
            for (i in 0 until 28) {
                val seed = (i * 73 + 17)
                val speed = 0.15f + (i % 5) * 0.08f
                val px = ((seed * 19) % 1000) / 1000f * w
                val py = ((((seed * 31) % 1000) / 1000f + timeSec * speed) % 1f) * (h * 0.72f)
                val sz = if (i % 3 == 0) 4f else 2.5f
                drawRect(
                    color = if (i % 2 == 0) c1.copy(alpha = 0.75f) else c2.copy(alpha = 0.75f),
                    topLeft = Offset(px, py),
                    size = Size(sz, sz)
                )
            }
        }
        ParticleFxType.CODE_MATRIX_RAIN -> {
            for (col in 0 until 18) {
                val x = (col + 0.5f) * (w / 18f)
                val headY = ((timeSec * (80f + col * 9f) + col * 47f) % (h * 0.78f))
                for (tail in 0..4) {
                    val ty = headY - tail * 11f
                    if (ty > 0f) {
                        drawRect(
                            color = c1.copy(alpha = (1f - tail * 0.2f) * 0.55f),
                            topLeft = Offset(x, ty),
                            size = Size(4f, 7f)
                        )
                    }
                }
            }
        }
        ParticleFxType.PIXEL_SPARK_BURST -> {
            for (i in 0 until 20) {
                val angle = i * (2f * PI.toFloat() / 20f) + timeSec * 1.2f
                val radius = ((timeSec * 90f + i * 18f) % (minOf(w, h) * 0.42f))
                val sx = w * 0.5f + cos(angle) * radius
                val sy = h * 0.45f + sin(angle) * radius * 0.65f
                drawRect(
                    color = if (i % 2 == 0) c2 else c1,
                    topLeft = Offset(sx, sy),
                    size = Size(5f, 5f)
                )
            }
        }
        ParticleFxType.RETRO_LASER_GRID -> {
            for (b in 0..5) {
                val sweepY = (h * 0.15f) + b * (h * 0.08f) + sin(timeSec * 3f + b) * 14f
                drawLine(
                    color = c2.copy(alpha = 0.28f),
                    start = Offset(0f, sweepY),
                    end = Offset(w, sweepY),
                    strokeWidth = 2f
                )
            }
        }
        ParticleFxType.HOLO_DATA_NODES -> {
            for (n in 0 until 10) {
                val nx = (w * 0.12f) + (n * (w * 0.085f))
                val ny = (h * 0.22f) + sin(timeSec * 2.0f + n) * 28f
                drawCircle(
                    color = c1.copy(alpha = 0.45f),
                    radius = 5f,
                    center = Offset(nx, ny)
                )
                if (n > 0) {
                    val prevX = (w * 0.12f) + ((n - 1) * (w * 0.085f))
                    val prevY = (h * 0.22f) + sin(timeSec * 2.0f + (n - 1)) * 28f
                    drawLine(
                        color = c1.copy(alpha = 0.22f),
                        start = Offset(prevX, prevY),
                        end = Offset(nx, ny),
                        strokeWidth = 1.5f
                    )
                }
            }
        }
    }
}

private fun DrawScope.draw3DFacePrimitive(
    face: RenderPrimitive.FacePrimitive,
    texturePreset: TexturePreset,
    consoleSpec: ConsoleSpec,
    wireframeOnly: Boolean
) {
    val pts = face.screenPoints
    if (pts.size < 3) return

    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            lineTo(pts[i].x, pts[i].y)
        }
        close()
    }

    val isWireframeConsole = consoleSpec.shadingPipeline == ShadingPipeline.RETRO_8BIT_TILE
    if (!wireframeOnly) {
        drawPath(
            path = path,
            color = if (isWireframeConsole) face.litColor.copy(alpha = 0.32f) else face.litColor,
            style = Fill
        )
    }

    // Procedural UV Texture Detail Overlay on Polygon Surface
    if (!wireframeOnly && pts.size == 4) {
        val mid01 = Offset((pts[0].x + pts[1].x) * 0.5f, (pts[0].y + pts[1].y) * 0.5f)
        val mid23 = Offset((pts[2].x + pts[3].x) * 0.5f, (pts[2].y + pts[3].y) * 0.5f)
        val mid12 = Offset((pts[1].x + pts[2].x) * 0.5f, (pts[1].y + pts[2].y) * 0.5f)
        val mid30 = Offset((pts[3].x + pts[0].x) * 0.5f, (pts[3].y + pts[0].y) * 0.5f)

        when (texturePreset) {
            TexturePreset.NEON_GRID, TexturePreset.CHECKER_UV -> {
                drawLine(
                    color = consoleSpec.accentColor.copy(alpha = 0.45f),
                    start = mid01,
                    end = mid23,
                    strokeWidth = 1.2f
                )
                drawLine(
                    color = consoleSpec.accentColor.copy(alpha = 0.45f),
                    start = mid12,
                    end = mid30,
                    strokeWidth = 1.2f
                )
            }
            TexturePreset.CYBER_CIRCUIT -> {
                val center = Offset((mid01.x + mid23.x) * 0.5f, (mid01.y + mid23.y) * 0.5f)
                drawLine(
                    color = Color(0xFF00E676).copy(alpha = 0.55f),
                    start = mid01,
                    end = center,
                    strokeWidth = 1.5f
                )
                drawCircle(
                    color = Color(0xFF00E676).copy(alpha = 0.7f),
                    radius = 2.5f,
                    center = center
                )
            }
            TexturePreset.LAVA_MAGMA -> {
                drawLine(
                    color = Color(0xFFFF6D00).copy(alpha = 0.65f),
                    start = pts[0],
                    end = pts[2],
                    strokeWidth = 1.8f
                )
            }
            TexturePreset.HOLO_SCANLINE -> {
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = mid12,
                    end = mid30,
                    strokeWidth = 1.6f
                )
            }
            else -> Unit
        }
    }

    val strokeWidth = when {
        wireframeOnly || isWireframeConsole -> 2.2f
        consoleSpec.shadingPipeline == ShadingPipeline.CEL_ARCADE_128 -> 2.4f
        else -> 1.1f
    }
    drawPath(
        path = path,
        color = face.edgeColor,
        style = Stroke(width = strokeWidth)
    )
}

private fun DrawScope.draw2DSpriteBillboard(
    sprite: RenderPrimitive.SpriteBillboardPrimitive,
    consoleSpec: ConsoleSpec,
    timeSec: Float
) {
    val grid = PixelSpriteBank.getSpriteFrame(sprite.actor, sprite.frameIndex)
    val gridScale = consoleSpec.pixelGridScale.coerceAtLeast(1)
    val rawPixelSize = sprite.scaleFactor.coerceIn(2.5f, 11f)
    // Quantize 2D sprite pixel blocks to multiples of pixelGridScale on retro/handheld consoles
    val pixelSize = if (gridScale > 1) {
        ((rawPixelSize / gridScale).roundToInt().coerceAtLeast(1) * gridScale).toFloat()
    } else {
        rawPixelSize
    }
    val totalW = 12 * pixelSize
    val totalH = 12 * pixelSize
    val startX = snapToPixelGrid(sprite.screenCenter.x - totalW * 0.5f, gridScale)
    val startY = snapToPixelGrid(sprite.screenCenter.y - totalH * 0.5f, gridScale)

    // Ground shadow oval under the 2D sprite billboard
    drawOval(
        color = Color.Black.copy(alpha = 0.42f),
        topLeft = Offset(startX + totalW * 0.15f, startY + totalH * 0.92f),
        size = Size(totalW * 0.7f, pixelSize * 1.8f)
    )

    val palette = consoleSpec.tintPalette
    val c1 = palette.getOrElse(0) { Color(0xFF00F0FF) }
    val c2 = palette.getOrElse(1) { Color(0xFFFF2A85) }
    val c3 = palette.getOrElse(2) { Color(0xFFFFEA00) }
    val c4 = if (consoleSpec.shadingPipeline == ShadingPipeline.DOT_MATRIX_LCD) {
        palette.getOrElse(3) { Color(0xFF0F380F) }
    } else {
        Color(0xFFFFF8E1)
    }

    val pixelOverlap = if (gridScale > 1) 0f else 0.6f
    for (rowIdx in grid.indices) {
        val rowStr = grid[rowIdx]
        for (colIdx in rowStr.indices) {
            val ch = rowStr[colIdx]
            if (ch == '.') continue
            val color = when (ch) {
                '1' -> if (sprite.isPrimary) c1 else c2
                '2' -> if (sprite.isPrimary) c2 else c1
                '3' -> c3
                '4' -> c4
                else -> c1
            }
            val px = snapToPixelGrid(startX + colIdx * pixelSize, gridScale)
            val py = snapToPixelGrid(startY + rowIdx * pixelSize, gridScale)
            drawRect(
                color = color,
                topLeft = Offset(px, py),
                size = Size(pixelSize + pixelOverlap, pixelSize + pixelOverlap)
            )
        }
    }

    // Floating Sprite Actor Tag Pill above primary host/hero sprite
    if (sprite.isPrimary && sin(timeSec * 3f) > -0.6f) {
        drawRect(
            color = consoleSpec.accentColor.copy(alpha = 0.85f),
            topLeft = Offset(
                snapToPixelGrid(sprite.screenCenter.x - pixelSize * 3f, gridScale),
                snapToPixelGrid(startY - pixelSize * 2.2f, gridScale)
            ),
            size = Size(pixelSize * 6f, pixelSize * 1.1f)
        )
    }
}

private fun DrawScope.drawHardwareDisplayFilter(
    w: Float,
    h: Float,
    consoleSpec: ConsoleSpec
) {
    if (consoleSpec.hasScanlines) {
        val step = 4.dp.toPx().coerceAtLeast(3f)
        var y = 0f
        while (y < h) {
            drawLine(
                color = Color.Black.copy(alpha = 0.24f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.2f
            )
            y += step
        }
    }

    if (consoleSpec.hasLcdSubpixelGrid) {
        val cell = 5.dp.toPx().coerceAtLeast(4f)
        val gridAlpha = if (consoleSpec.shadingPipeline == ShadingPipeline.DOT_MATRIX_LCD) 0.22f else 0.12f
        var x = 0f
        while (x < w) {
            drawLine(
                color = Color.Black.copy(alpha = gridAlpha),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 0.8f
            )
            x += cell
        }
        var y = 0f
        while (y < h) {
            drawLine(
                color = Color.Black.copy(alpha = gridAlpha),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 0.8f
            )
            y += cell
        }
    }
}
