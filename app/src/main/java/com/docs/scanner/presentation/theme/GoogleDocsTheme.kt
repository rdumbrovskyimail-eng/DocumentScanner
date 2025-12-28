package com.docs.scanner.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
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

// ✅ ДОБАВЛЕНО: Dark color scheme (Session 11 Problem #4)
private val GoogleDocsDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8B9BBF), // Lighter version of primary
    onPrimary = Color(0xFF1B1B1F),
    primaryContainer = Color(0xFF3B4B6F),
    onPrimaryContainer = Color(0xFFE4E2E6),
    
    secondary = Color(0xFF3B3F4A),
    onSecondary = Color(0xFFE4E2E6),
    secondaryContainer = Color(0xFF2B2F3A),
    onSecondaryContainer = Color(0xFFBDBDBD),
    
    tertiary = Color(0xFF8B9BBF),
    onTertiary = Color(0xFF1B1B1F),
    
    background = Color(0xFF1B1B1F),
    onBackground = Color(0xFFE4E2E6),
    
    surface = Color(0xFF1F1F23),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF2B2B2F),
    onSurfaceVariant = Color(0xFFBDBDBD),
    
    error = Color(0xFFEF5350),
    onError = Color(0xFF1B1B1F),
    
    outline = Color(0xFF45464E),
    outlineVariant = Color(0xFF2B2B2F)
)

@Composable
fun GoogleDocsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // ✅ ИСПРАВЛЕНО: Теперь поддерживает dark theme (Session 11 Problem #4)
    val colorScheme = if (darkTheme) {
        GoogleDocsDarkColorScheme
    } else {
        GoogleDocsLightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            
            WindowCompat.getInsetsController(window, view).apply {
                // ✅ ИСПРАВЛЕНО: Динамически меняем светлость (Session 11 Problem #4)
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
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