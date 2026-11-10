package com.proyecto_final.triage.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary         = ButtonPrimary,
    tertiary        = Color(0xFF277A56),
    onTertiary      = Color.White,
    secondary       = TextSecondary,
    background      = Background,
    surface         = InputBackground,
    error           = Warning,
    onPrimary       = InputBackground,
    onBackground    = TextPrimary,
    onSurface       = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun AppTheme ( content: @Composable () -> Unit) {
    MaterialTheme(  colorScheme = AppColorScheme,
                    typography  = appTypography(),
                    content     = content,
        )
}
