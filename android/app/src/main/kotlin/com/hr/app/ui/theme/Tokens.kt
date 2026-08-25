package com.hr.app.ui.theme

// GENERATED from design/tokens.json — do not edit.
// Run `node design/generate.mjs` after changing a token.
// CI fails if this file is stale.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object LightColors {
    val brandPrimary = Color(0xFF1B5E9C)
    val brandOnPrimary = Color(0xFFFFFFFF)
    val brandPrimaryContainer = Color(0xFFD3E4F7)
    val brandOnPrimaryContainer = Color(0xFF001D34)
    val secondary = Color(0xFF50606F)
    val surface = Color(0xFFFCFCFF)
    val surfaceRaised = Color(0xFFFFFFFF)
    val surfaceVariant = Color(0xFFDEE3EB)
    val onSurface = Color(0xFF1A1C1E)
    val onSurfaceMuted = Color(0xFF42474E)
    val outline = Color(0xFF6F757C)
    val success = Color(0xFF1B7F4B)
    val warning = Color(0xFF8A5A00)
    val danger = Color(0xFFBA1A1A)
    val onDanger = Color(0xFFFFFFFF)
    val info = Color(0xFF1B5E9C)
    val outlineVariant = Color(0xFFC3C8CF)
}

object DarkColors {
    val brandPrimary = Color(0xFF8FBEEA)
    val brandOnPrimary = Color(0xFF003257)
    val brandPrimaryContainer = Color(0xFF00497C)
    val brandOnPrimaryContainer = Color(0xFFD3E4F7)
    val secondary = Color(0xFFB7C9D9)
    val surface = Color(0xFF1A1C1E)
    val surfaceRaised = Color(0xFF23262A)
    val surfaceVariant = Color(0xFF42474E)
    val onSurface = Color(0xFFE2E2E5)
    val onSurfaceMuted = Color(0xFFC2C7CF)
    val outline = Color(0xFF8C9199)
    val success = Color(0xFF6DD49A)
    val warning = Color(0xFFF0C26A)
    val danger = Color(0xFFFFB4AB)
    val onDanger = Color(0xFF690005)
    val info = Color(0xFF8FBEEA)
    val outlineVariant = Color(0xFF42474E)
}

/** 4pt base grid. The name states the value: s4 is 16.dp. */
object Spacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s6 = 24.dp
    val s8 = 32.dp
}

object Radius {
    val control = 8.dp
    val card = 12.dp
    val pill = 999.dp
}

data class TypeToken(val size: TextUnit, val lineHeight: TextUnit, val weight: FontWeight)

object TypeScale {
    val displaySmall = TypeToken(size = 32.sp, lineHeight = 40.sp, weight = FontWeight(600))
    val headlineMedium = TypeToken(size = 24.sp, lineHeight = 32.sp, weight = FontWeight(600))
    val headlineSmall = TypeToken(size = 20.sp, lineHeight = 28.sp, weight = FontWeight(600))
    val titleLarge = TypeToken(size = 17.sp, lineHeight = 24.sp, weight = FontWeight(500))
    val bodyLarge = TypeToken(size = 15.sp, lineHeight = 22.sp, weight = FontWeight(400))
    val bodyMedium = TypeToken(size = 13.sp, lineHeight = 20.sp, weight = FontWeight(400))
    val labelSmall = TypeToken(size = 11.sp, lineHeight = 16.sp, weight = FontWeight(500))
}
