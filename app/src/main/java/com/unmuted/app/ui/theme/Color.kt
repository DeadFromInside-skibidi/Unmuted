package com.unmuted.app.ui.theme

import androidx.compose.ui.graphics.Color

// Midnight Studio Core Obsidian Palette
val ObsidianBackground = Color(0xFF0B0C10)
val ObsidianSurface = Color(0xFF13151D)
val ObsidianSurfaceElevated = Color(0xFF1B1E29)
val ObsidianSurfaceHighest = Color(0xFF242838)
val ObsidianHairline = Color(0x18FFFFFF)
val ObsidianOutline = Color(0xFF33394D)

// Signature Accents
val ElectricCoral = Color(0xFFFF4F5E)
val ElectricCoralContainer = Color(0xFF3F161D)
val OnElectricCoral = Color(0xFF1A0407)

val StudioViolet = Color(0xFF8B5CF6)
val StudioVioletContainer = Color(0xFF281B47)
val OnStudioViolet = Color(0xFFF6F2FF)

val SignalMint = Color(0xFF2DD4BF)
val SignalMintContainer = Color(0xFF103431)

// Profile Studio Custom Accents
val AmberVinyl = Color(0xFFF59E0B)
val SignalCyan = Color(0xFF38BDF8)

// Text & Contrast
val StudioWhite = Color(0xFFF5F7FA)
val TitaniumMuted = Color(0xFF9BA3B4)
val TitaniumSubtle = Color(0xFF6B7285)
val AcousticError = Color(0xFFFF6B6B)
val AcousticErrorContainer = Color(0xFF42171B)

enum class ProfileAccentPalette(
    val key: String,
    val label: String,
    val primary: Color,
    val secondary: Color
) {
    CORAL("CORAL", "Electric Coral", ElectricCoral, StudioViolet),
    VIOLET("VIOLET", "Studio Violet", StudioViolet, ElectricCoral),
    AMBER("AMBER", "Warm Vinyl", AmberVinyl, ElectricCoral),
    MINT("MINT", "Signal Mint", SignalMint, SignalCyan),
    CYAN("CYAN", "Neon Cyan", SignalCyan, StudioViolet);

    companion object {
        fun fromKey(key: String?): ProfileAccentPalette =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: CORAL
    }
}

enum class StudioBannerMood(
    val key: String,
    val label: String,
    val startColor: Color,
    val midColor: Color,
    val endColor: Color,
    val usesArtBackdrop: Boolean = false
) {
    OBSIDIAN_WAVE("OBSIDIAN_WAVE", "Silk Wave", Color(0xFF181124), Color(0xFF2D1221), Color(0xFF0B0C10), true),
    CORAL_NEBULA("CORAL_NEBULA", "Coral Glow", Color(0xFF44131E), Color(0xFF211229), Color(0xFF0B0C10)),
    VIOLET_PULSE("VIOLET_PULSE", "Violet Halo", Color(0xFF29184D), Color(0xFF14182D), Color(0xFF0B0C10)),
    MIDNIGHT_VINYL("MIDNIGHT_VINYL", "Amber Vinyl", Color(0xFF3D2411), Color(0xFF1D1521), Color(0xFF0B0C10));

    companion object {
        fun fromKey(key: String?): StudioBannerMood =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OBSIDIAN_WAVE
    }
}
