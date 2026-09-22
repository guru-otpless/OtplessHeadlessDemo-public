package com.otpless.headlessdemo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// OTPless brand colors only (#000000 / #FFFFFF), not tied to system light/dark
// mode — this is a test harness, not a themed consumer app, so a single fixed
// look keeps every screenshot/bug-report consistent regardless of device
// settings. Error stays Material's default red since it needs to stand out;
// everything else in the scheme is black, white, or a neutral gray.
private val OtplessColorScheme = lightColorScheme(
    primary = OtplessBlack,
    onPrimary = OtplessWhite,
    secondary = OtplessBlack,
    onSecondary = OtplessWhite,
    tertiary = OtplessGrayMid,
    onTertiary = OtplessWhite,
    background = OtplessWhite,
    onBackground = OtplessBlack,
    surface = OtplessWhite,
    onSurface = OtplessBlack,
    surfaceVariant = OtplessGraySurface,
    onSurfaceVariant = OtplessGrayMid,
    outline = OtplessGrayOutline,
)

// Slightly rounded, not the fully-pill/stadium shape Material3 buttons default
// to. Applied via the theme's shape scale; Buttons in DemoScreen also pass this
// shape explicitly (see ButtonShape there) since Button's default shape token
// doesn't reliably follow this scale across Material3 versions.
private val OtplessShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

@Composable
fun OtplessTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OtplessColorScheme,
        shapes = OtplessShapes,
        content = content,
    )
}
