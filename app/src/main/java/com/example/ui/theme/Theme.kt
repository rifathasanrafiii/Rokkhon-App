package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val IosDarkColorScheme = darkColorScheme(
    primary = IosSystemRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A101B),
    onPrimaryContainer = Color(0xFFDDDDDD),
    secondary = IosSystemBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF102A45),
    onSecondaryContainer = Color(0xFFB3D7FF),
    tertiary = IosSystemGreen,
    onTertiary = Color.White,
    background = IosDarkBackground,
    onBackground = IosDarkTextPrimary,
    surface = IosDarkSurface,
    onSurface = IosDarkTextPrimary,
    surfaceVariant = IosDarkSurfaceElevated,
    onSurfaceVariant = IosDarkTextSecondary,
    outline = IosDarkOutline
)

private val IosLightColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3F3F3),
    onPrimaryContainer = Color.Black,
    secondary = IosSystemBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEEEEE),
    onSecondaryContainer = Color.Black,
    tertiary = IosSystemGreen,
    onTertiary = Color.White,
    error = PinCrimsonRed,
    background = IosLightBackground,
    onBackground = IosLightTextPrimary,
    surface = IosLightSurface,
    onSurface = IosLightTextPrimary,
    surfaceVariant = IosLightSurfaceElevated,
    onSurfaceVariant = IosLightTextSecondary,
    surfaceContainerLow = Color(0xFFF6F6F6),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    outline = IosLightOutline,
    outlineVariant = Color(0xFFEEEEEE)
)

val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * The design mockup is drawn denser than default Android sizing: every card, pill and caption
 * is about 0.86x (like Android's "small" display size). Scaling once here gives every screen
 * the same proportions; the user's own font-size setting still applies on top.
 */
private const val ROKKHON_UI_SCALE = 0.86f

@Composable
fun MyApplicationTheme(
    // The screens are drawn with light colours; a dark scheme made text fields white on white.
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) IosDarkColorScheme else IosLightColorScheme
    val baseDensity = LocalDensity.current

    CompositionLocalProvider(
        LocalDensity provides Density(baseDensity.density * ROKKHON_UI_SCALE, baseDensity.fontScale)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = IosShapes,
            content = content
        )
    }
}
