package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ChronoStageColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = StudioObsidian,
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = HyperMagenta,
    onSecondary = StudioTextPrimary,
    secondaryContainer = MagentaContainer,
    onSecondaryContainer = OnMagentaContainer,
    tertiary = ArcadeAmber,
    onTertiary = StudioObsidian,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    surfaceContainerHigh = StudioSurfaceBright,
    outline = StudioOutline,
    error = ErrorCoral
)

val ChronoStageShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ChronoStageTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ChronoStageColorScheme,
        typography = Typography,
        shapes = ChronoStageShapes,
        content = content
    )
}
