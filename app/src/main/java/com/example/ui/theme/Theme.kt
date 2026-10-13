package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = RecallPrimaryDark,
    onPrimary = RecallOnPrimaryDark,
    primaryContainer = RecallPrimaryContainerDark,
    onPrimaryContainer = RecallOnPrimaryContainerDark,
    secondary = RecallSecondaryDark,
    background = RecallBackgroundDark,
    surface = RecallSurfaceDark,
    surfaceVariant = RecallSurfaceVariantDark,
    onBackground = RecallOnSurfaceDark,
    onSurface = RecallOnSurfaceDark,
    onSurfaceVariant = RecallOnSurfaceVariantDark,
    outline = RecallOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = RecallPrimaryLight,
    onPrimary = RecallOnPrimaryLight,
    primaryContainer = RecallPrimaryContainerLight,
    onPrimaryContainer = RecallOnPrimaryContainerLight,
    secondary = RecallSecondaryLight,
    background = RecallBackgroundLight,
    surface = RecallSurfaceLight,
    surfaceVariant = RecallSurfaceVariantLight,
    onBackground = RecallOnSurfaceLight,
    onSurface = RecallOnSurfaceLight,
    onSurfaceVariant = RecallOnSurfaceVariantLight,
    outline = RecallOutlineLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our tuned Recall theme colors for brand consistency
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
