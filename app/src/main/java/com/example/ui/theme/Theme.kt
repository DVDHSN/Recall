package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AppleOrangeDark,
    onPrimary = Color.Black,
    primaryContainer = AppleOrangeMuted,
    onPrimaryContainer = AppleOrangeDark,
    secondary = DarkTextSecondary,
    onSecondary = DarkTextPrimary,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = AppleOrangeLight,
    onTertiary = Color.Black,
    tertiaryContainer = AppleOrangeMuted,
    onTertiaryContainer = AppleOrangeDark,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkCardBorder,
    error = StatusFailedColor,
    onError = Color.White,
    errorContainer = Color(0x33FF453A),
    onErrorContainer = StatusFailedColor
)

private val LightColorScheme = lightColorScheme(
    primary = AppleOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFECCC),
    onPrimaryContainer = AppleOrange,
    secondary = LightTextSecondary,
    onSecondary = LightTextPrimary,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightTextPrimary,
    tertiary = AppleOrangeDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF0DB),
    onTertiaryContainer = AppleOrange,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = LightCardBorder,
    error = StatusFailedColor,
    onError = Color.White,
    errorContainer = Color(0x22FF453A),
    onErrorContainer = StatusFailedColor
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
