package com.hr.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Design tokens.
 *
 * Neutral-first surfaces with a single brand accent, per docs/05-screens-ux.md §5. Status colours
 * are never the only signal — every status is also carried by an icon or a label, so the UI works
 * for colour-blind users.
 *
 * These are placeholder values pending the design system in `P0-DES-01`. The structure is what
 * matters here: one place to change, tenant-overridable at runtime.
 */
private val LightColorScheme =
    lightColorScheme(
        primary = LightColors.brandPrimary,
        onPrimary = LightColors.brandOnPrimary,
        primaryContainer = LightColors.brandPrimaryContainer,
        onPrimaryContainer = LightColors.brandOnPrimaryContainer,
        secondary = LightColors.secondary,
        background = LightColors.surface,
        onBackground = LightColors.onSurface,
        surface = LightColors.surface,
        onSurface = LightColors.onSurface,
        surfaceVariant = LightColors.surfaceVariant,
        onSurfaceVariant = LightColors.onSurfaceMuted,
        outline = LightColors.outline,
        outlineVariant = LightColors.outlineVariant,
        error = LightColors.danger,
        onError = LightColors.onDanger,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = DarkColors.brandPrimary,
        onPrimary = DarkColors.brandOnPrimary,
        primaryContainer = DarkColors.brandPrimaryContainer,
        onPrimaryContainer = DarkColors.brandOnPrimaryContainer,
        secondary = DarkColors.secondary,
        background = DarkColors.surface,
        onBackground = DarkColors.onSurface,
        surface = DarkColors.surface,
        onSurface = DarkColors.onSurface,
        surfaceVariant = DarkColors.surfaceVariant,
        onSurfaceVariant = DarkColors.onSurfaceMuted,
        outline = DarkColors.outline,
        outlineVariant = DarkColors.outlineVariant,
        error = DarkColors.danger,
        onError = DarkColors.onDanger,
    )

/**
 * Type scale from docs/05-screens-ux.md §5.
 *
 * Numeric columns must use tabular figures so money and hours align — that is applied at the call
 * site with `TextStyle(fontFeatureSettings = "tnum")` rather than globally, since proportional
 * figures read better in prose.
 */
private fun typeStyle(token: TypeToken) =
    TextStyle(fontSize = token.size, lineHeight = token.lineHeight, fontWeight = token.weight)

private val HrTypography =
    Typography(
        displaySmall = typeStyle(TypeScale.displaySmall),
        headlineMedium = typeStyle(TypeScale.headlineMedium),
        headlineSmall = typeStyle(TypeScale.headlineSmall),
        titleLarge = typeStyle(TypeScale.titleLarge),
        bodyLarge = typeStyle(TypeScale.bodyLarge),
        bodyMedium = typeStyle(TypeScale.bodyMedium),
        labelSmall = typeStyle(TypeScale.labelSmall),
    )


@Composable
fun HrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HrTypography,
        content = content,
    )
}
