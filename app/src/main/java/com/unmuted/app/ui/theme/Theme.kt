package com.unmuted.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val UnmutedDarkColorScheme = darkColorScheme(
    primary = ElectricCoral,
    onPrimary = StudioWhite,
    primaryContainer = ElectricCoralContainer,
    onPrimaryContainer = StudioWhite,
    secondary = StudioViolet,
    onSecondary = OnStudioViolet,
    secondaryContainer = StudioVioletContainer,
    onSecondaryContainer = StudioWhite,
    tertiary = SignalMint,
    onTertiary = ObsidianBackground,
    tertiaryContainer = SignalMintContainer,
    onTertiaryContainer = StudioWhite,
    background = ObsidianBackground,
    onBackground = StudioWhite,
    surface = ObsidianSurface,
    onSurface = StudioWhite,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TitaniumMuted,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianHairline,
    error = AcousticError,
    onError = ObsidianBackground,
    errorContainer = AcousticErrorContainer,
    onErrorContainer = StudioWhite
)

private val UnmutedShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun UnmutedTheme(
    accentPalette: ProfileAccentPalette = ProfileAccentPalette.CORAL,
    content: @Composable () -> Unit
) {
    val scheme = UnmutedDarkColorScheme.copy(
        primary = accentPalette.primary,
        secondary = accentPalette.secondary
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        shapes = UnmutedShapes,
        content = content
    )
}
