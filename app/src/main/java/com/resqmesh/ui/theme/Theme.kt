package com.resqmesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EmergencyRed = Color(0xFFB3261E)
private val MeshGreen = Color(0xFF2E7D5B)
private val MeshGreenLight = Color(0xFF7DE2B8)

private val LightColors = lightColorScheme(
    primary = MeshGreen,
    onPrimary = Color.White,
    error = EmergencyRed,
    surfaceVariant = Color(0xFFE8F2EE),
)

private val DarkColors = darkColorScheme(
    primary = MeshGreenLight,
    onPrimary = Color(0xFF00382A),
    error = Color(0xFFF2B8B5),
    surfaceVariant = Color(0xFF1B3A30),
)

@Composable
fun ResQMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
