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
// SIMPLE DIVIDER
// ============================================

@Composable
fun SimpleDivider(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GoogleDocsDivider)
    )
}

// ============================================
// SMART DIVIDER (with gradient icon)
// ============================================

@Composable
fun SmartDivider(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth(0.8f)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left gradient line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            GoogleDocsSmartDividerGradientStart,
                            GoogleDocsBorder,
                            GoogleDocsSmartDividerGradientMid
                        )
                    )
                )
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Center icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = GoogleDocsSurface,
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
                contentDescription = "Scroll down",
                modifier = Modifier.size(16.dp),
                tint = GoogleDocsPrimary
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Right gradient line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            GoogleDocsSmartDividerGradientMid,
                            GoogleDocsBorder,
                            GoogleDocsSmartDividerGradientStart
                        )
                    )
                )
        )
    }
}
