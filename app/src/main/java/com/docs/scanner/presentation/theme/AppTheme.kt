package com.docs.scanner.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = md_primary, onPrimary = md_onPrimary,
    primaryContainer = md_primaryContainer, onPrimaryContainer = md_onPrimaryContainer,
    tertiary = md_tertiary,
    background = md_background, onBackground = md_onBackground,
    surface = md_surface, onSurface = md_onSurface,
    surfaceVariant = md_surfaceVariant, onSurfaceVariant = md_onSurfaceVariant,
    outline = md_outline, outlineVariant = md_outlineVariant,
    error = md_error, onError = md_onError, errorContainer = md_errorContainer,
)

private val DarkColors = darkColorScheme(
    primary = md_primary_d, onPrimary = md_onPrimary_d,
    primaryContainer = md_primaryContainer_d, onPrimaryContainer = md_onPrimaryContainer_d,
    tertiary = md_tertiary_d,
    background = md_background_d, onBackground = md_onBackground_d,
    surface = md_surface_d, onSurface = md_onSurface_d,
    surfaceVariant = md_surfaceVariant_d, onSurfaceVariant = md_onSurfaceVariant_d,
    outline = md_outline_d, outlineVariant = md_outlineVariant_d,
    error = md_error_d, onError = md_onError_d,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}