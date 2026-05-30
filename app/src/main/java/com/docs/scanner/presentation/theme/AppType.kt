package com.docs.scanner.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val F = FontFamily.Default

val AppTypography = Typography(
    headlineSmall = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge    = TextStyle(fontFamily = F, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp),
    titleMedium   = TextStyle(fontFamily = F, fontWeight = FontWeight.Medium,   fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge     = TextStyle(fontFamily = F, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontFamily = F, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge    = TextStyle(fontFamily = F, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontFamily = F, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
)