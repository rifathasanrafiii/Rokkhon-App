package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.R

// The old family pointed every weight at one small single-weight file without Bangla letters,
// so Bold/ExtraBold looked thin. Google Sans is the typeface of the design mockup and covers
// both Bangla and Latin, with one file per weight.
val GoogleSansFontFamily = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_bold, FontWeight.Bold),
    Font(R.font.google_sans_bold, FontWeight.ExtraBold)
)

val HindSiliguriFontFamily = GoogleSansFontFamily
val SpaceGroteskFontFamily = GoogleSansFontFamily
val PlusJakartaSansFontFamily = GoogleSansFontFamily

// Line heights scale with each text's own size (a fixed 22sp made small captions look loose).
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 1.15.em,
        letterSpacing = (-0.3).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 1.2.em,
        letterSpacing = (-0.2).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 1.25.em
    ),
    titleLarge = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 1.3.em
    ),
    titleMedium = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 1.3.em
    ),
    titleSmall = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 1.3.em
    ),
    bodyLarge = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 1.4.em
    ),
    bodyMedium = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 1.4.em
    ),
    bodySmall = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 1.4.em
    ),
    labelLarge = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 1.3.em
    ),
    labelMedium = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 1.3.em
    ),
    labelSmall = TextStyle(
        fontFamily = HindSiliguriFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 1.3.em
    )
)
