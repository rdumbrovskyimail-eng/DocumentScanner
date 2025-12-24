package com.docs.scanner.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ============================================
// GOOGLE DOCS SHAPE SYSTEM
// ============================================

val GoogleDocsShapes = Shapes(
    // G-Container (16dp)
    extraLarge = RoundedCornerShape(16.dp),
    
    // Cards & Text Containers (12dp)
    large = RoundedCornerShape(12.dp),
    
    // Micro Buttons (20dp)
    medium = RoundedCornerShape(20.dp),
    
    // Translation Field (16dp)
    small = RoundedCornerShape(16.dp),
    
    // FAB (50% = Circle)
    extraSmall = RoundedCornerShape(50)
)
