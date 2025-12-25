package com.docs.scanner.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.docs.scanner.presentation.theme.*

// ============================================
// ✅ SIMPLE DIVIDER (1px line)
// ============================================

@Composable
fun SimpleDivider(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GoogleDocsDivider) // #E5E7EB
    )
}

// ============================================
// ✅ SMART DIVIDER (Gradient + Icon)
// ============================================

@Composable
fun SmartDivider(
    modifier: Modifier = Modifier
) {
    // Центрируем через Box
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.8f) // 80% ширины
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ✅ LEFT GRADIENT LINE
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0x00E8EAED), // transparent
                                Color(0xFFE8EAED), // GoogleDocsBorder
                                Color(0xFF5B6B8F)  // GoogleDocsPrimary
                            )
                        )
                    )
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // ✅ CENTER ICON
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = GoogleDocsSurface, // #F8F9FA
                        shape = MaterialTheme.shapes.extraSmall
                    )
                    .border(
                        width = 2.dp,
                        color = GoogleDocsBorder,
                        shape = MaterialTheme.shapes.extraSmall
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Next page",
                    modifier = Modifier.size(16.dp),
                    tint = GoogleDocsPrimary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // ✅ RIGHT GRADIENT LINE
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF5B6B8F),  // GoogleDocsPrimary
                                Color(0xFFE8EAED),  // GoogleDocsBorder
                                Color(0x00E8EAED)   // transparent
                            )
                        )
                    )
            )
        }
    }
}
