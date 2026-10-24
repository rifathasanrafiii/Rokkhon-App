package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val IosDarkColorScheme = darkColorScheme(
    primary = IosSystemRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A101B),
    onPrimaryContainer = Color(0xFFFFB3C0),
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
    primary = Rk.Crimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5EA),
    onPrimaryContainer = IosDeepCrimson,
    secondary = IosSystemBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5F1FF),
    onSecondaryContainer = Color(0xFF004C99),
    tertiary = IosSystemGreen,
    onTertiary = Color.White,
    background = Rk.Bg,
    onBackground = IosLightTextPrimary,
    surface = IosLightSurface,
    onSurface = IosLightTextPrimary,
    surfaceVariant = IosLightSurfaceElevated,
    onSurfaceVariant = IosLightTextSecondary,
    outline = IosLightOutline
)

val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MyApplicationTheme(
    // The screens are designed for light surfaces only. Following the system dark theme made
    // text fields and dialogs render light text on white cards, so light is used everywhere.
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) IosDarkColorScheme else IosLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = IosShapes,
        content = content
    )
}
