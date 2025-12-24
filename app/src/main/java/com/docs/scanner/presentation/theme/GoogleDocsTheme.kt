package com.docs.scanner.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================
// GOOGLE DOCS THEME
// ============================================

private val GoogleDocsLightColorScheme = lightColorScheme(
    primary = GoogleDocsPrimary,
    onPrimary = GoogleDocsBackground,
    primaryContainer = GoogleDocsSurfaceVariant,
    onPrimaryContainer = GoogleDocsTextPrimary,
    
    secondary = GoogleDocsButtonBackground,
    onSecondary = GoogleDocsButtonText,
    secondaryContainer = GoogleDocsButtonHover,
    onSecondaryContainer = GoogleDocsTextSecondary,
    
    tertiary = GoogleDocsAiLoadingDot,
    onTertiary = GoogleDocsBackground,
    
    background = GoogleDocsBackground,
    onBackground = GoogleDocsTextPrimary,
    
    surface = GoogleDocsSurface,
    onSurface = GoogleDocsTextPrimary,
    surfaceVariant = GoogleDocsSurfaceVariant,
    onSurfaceVariant = GoogleDocsTextSecondary,
    
    error = GoogleDocsError,
    onError = GoogleDocsBackground,
    
    outline = GoogleDocsBorder,
    outlineVariant = GoogleDocsBorderLight
)

@Composable
fun GoogleDocsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Note: Google Docs design is primarily light mode
    // For consistency, we use light scheme even in dark mode
    val colorScheme = GoogleDocsLightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GoogleDocsTypography,
        shapes = GoogleDocsShapes,
        content = content
    )
}
