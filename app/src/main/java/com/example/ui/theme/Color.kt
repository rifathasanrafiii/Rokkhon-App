package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Monochrome palette after Uber's Base design system: black content and buttons on white,
 * light greys for surfaces, and colour only where it carries meaning — red for SOS/emergency,
 * green for "available / safe", blue for your own GPS position.
 * (The older token names are kept so every screen picks the new values up.)
 */
val IosSystemRed = Color(0xFF000000)       // Primary: buttons, selected states, key icons
val PinCrimsonRed = Color(0xFFE11900)      // SOS / emergency only
val LifelineBlush = Color(0xFFF3F3F3)      // Icon circles, chips

val IosSystemGreen = Color(0xFF05944F)     // Available / safe
val IosSystemBlue = Color(0xFF276EF1)      // Your GPS position
val IosSystemOrange = Color(0xFFB86E00)    // Warnings

// Dark surfaces (splash, camera preview, map overlays)
val RadarDarkMaroon = Color(0xFF141414)

// Light surfaces
val IosLightBackground = Color(0xFFFFFFFF)
val IosLightSurface = Color(0xFFFFFFFF)
val IosLightSurfaceElevated = Color(0xFFF6F6F6)
val IosLightTextPrimary = Color(0xFF000000)
val IosLightTextSecondary = Color(0xFF5E5E5E)
val IosLightOutline = Color(0xFFE2E2E2)

// Dark surfaces for the (unused) dark scheme
val IosDarkBackground = Color(0xFF000000)
val IosDarkSurface = Color(0xFF141414)
val IosDarkSurfaceElevated = Color(0xFF1F1F1F)
val IosDarkTextPrimary = Color(0xFFFFFFFF)
val IosDarkTextSecondary = Color(0xFFAFAFAF)
val IosDarkOutline = Color(0xFF333333)

/** Semantic colours for the screens; use these instead of literal hex values. */
object RokkhonPalette {
    val Primary = Color(0xFF000000)        // Buttons, selected chips, titles' icons
    val OnPrimary = Color(0xFFFFFFFF)
    val Sos = Color(0xFFE11900)            // SOS and emergency actions only
    val Chip = Color(0xFFEEEEEE)           // Icon circles, unselected chips
    val Subtle = Color(0xFFF6F6F6)         // Search bar, tiles, grouped rows
    val Background = Color(0xFFFFFFFF)
    val Border = Color(0xFFE8E8E8)
    val Track = Color(0xFFF3F3F3)          // Segmented control / progress track
    val Ink = Color(0xFF000000)
    val Muted = Color(0xFF5E5E5E)
    val Faint = Color(0xFFAFAFAF)
    val Divider = Color(0xFFEEEEEE)
    val Warning = Color(0xFFB86E00)
    val Positive = Color(0xFF05944F)
}
