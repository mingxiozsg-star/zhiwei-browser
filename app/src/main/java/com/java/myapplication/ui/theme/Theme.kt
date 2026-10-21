package com.java.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Indigo300,
    onPrimary = Slate950,
    primaryContainer = Indigo700,
    onPrimaryContainer = Indigo100,
    secondary = Teal400,
    onSecondary = Slate950,
    secondaryContainer = Slate800,
    onSecondaryContainer = Teal400,
    tertiary = Pink400,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate200,
    outline = Slate700
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo500,
    onPrimary = Slate50,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo700,
    secondary = Teal400,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate800,
    tertiary = Pink400,
    background = Slate50,
    onBackground = Slate900,
    surface = Slate50,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    outline = Slate200
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}