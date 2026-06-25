package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = LavenderPrimary,
    onPrimary = DeepPurpleOnPrimary,
    primaryContainer = LavenderPrimaryDim,
    onPrimaryContainer = LavenderSecondary,
    secondary = LavenderSecondary,
    onSecondary = DeepPurpleOnSecondary,
    tertiary = LavenderPrimary,
    background = ElegantDarkBg,
    onBackground = ElegantTextLight,
    surface = ElegantSurface,
    onSurface = Color.White,
    surfaceVariant = ElegantSurfaceVariant,
    onSurfaceVariant = ElegantTextMuted,
    error = ElegantError,
    onError = ElegantOnError,
    errorContainer = ElegantErrorContainer,
    onErrorContainer = ElegantOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = LavenderLightPrimary,
    onPrimary = Color.White,
    primaryContainer = LavenderLightSecondaryContainer,
    onPrimaryContainer = DeepPurpleOnSecondary,
    secondary = LavenderLightSecondary,
    onSecondary = Color.White,
    tertiary = LavenderLightPrimary,
    background = ElegantLightBg,
    onBackground = ElegantLightText,
    surface = ElegantLightSurface,
    onSurface = ElegantLightText,
    surfaceVariant = ElegantLightSurfaceVariant,
    onSurfaceVariant = ElegantLightTextVariant,
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun KirinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
