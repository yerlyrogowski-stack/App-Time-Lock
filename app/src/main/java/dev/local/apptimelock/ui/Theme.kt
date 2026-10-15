package dev.local.apptimelock.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF496B50), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EADC), onPrimaryContainer = Color(0xFF233C29),
    secondary = Color(0xFF716957), secondaryContainer = Color(0xFFEDE7D9),
    background = Color(0xFFF7F6F0), onBackground = Color(0xFF25312A),
    surface = Color(0xFFFCFBF6), onSurface = Color(0xFF25312A),
    surfaceVariant = Color(0xFFE9ECE2), onSurfaceVariant = Color(0xFF656E62),
    outline = Color(0xFFA5AD9F), error = Color(0xFFAA453A),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA8CBA9), onPrimary = Color(0xFF14381F),
    primaryContainer = Color(0xFF304D35), onPrimaryContainer = Color(0xFFD7EDD4),
    secondary = Color(0xFFD0C5AD), secondaryContainer = Color(0xFF484437),
    background = Color(0xFF161C18), onBackground = Color(0xFFE2E8DD),
    surface = Color(0xFF1E2620), onSurface = Color(0xFFE2E8DD),
    surfaceVariant = Color(0xFF303B31), onSurfaceVariant = Color(0xFFB8C2B3),
    outline = Color(0xFF667363),
)

@Composable fun TimeLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
