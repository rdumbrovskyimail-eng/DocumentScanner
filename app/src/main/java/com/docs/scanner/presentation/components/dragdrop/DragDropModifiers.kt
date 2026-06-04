package com.docs.scanner.presentation.components.dragdrop

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Modifier для элемента который можно перетаскивать
 */
fun Modifier.draggableItem(
    state: DragDropState,
    index: Int,
    enabled: Boolean = true
): Modifier = this
    .zIndex(if (state.isDraggingItem(index)) 1f else 0f)
    .graphicsLayer {
        val isDragging = state.isDraggingItem(index)
        if (isDragging) {
            // ✅ FIXED: используем animatedOffsetY.value вместо animatedOffset.value.y
            translationY = state.animatedOffsetY.value
            scaleX = 1.02f
            scaleY = 1.02f
            shadowElevation = 8f
        }
    }
    .then(
        if (!state.isDraggingItem(index)) {
            Modifier.offset {
                val placeholderOffset = state.getPlaceholderOffset(index)
                IntOffset(0, placeholderOffset)
            }
        } else {
            Modifier
        }
    )
    .pointerInput(enabled, index) {
        if (!enabled) return@pointerInput
        
        detectDragGesturesAfterLongPress(
            onDragStart = {
                state.startDrag(index, size.height)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(Offset(dragAmount.x, dragAmount.y))
            },
            onDragEnd = {
                state.endDrag()
            },
            onDragCancel = {
                state.cancelDrag()
            }
        )
    }

/**
 * Modifier для контейнера списка с drag & drop
 */
fun Modifier.dragDropContainer(
    state: DragDropState
): Modifier = this

/**
 * Modifier с анимированным смещением для non-dragging элементов
 */
@Composable
fun Modifier.animatedPlaceholderOffset(
    state: DragDropState,
    index: Int
): Modifier {
    val targetOffset = state.getPlaceholderOffset(index)
    val animatedOffset by animateIntAsState(
        targetValue = targetOffset,
        label = "placeholderOffset"
    )
    
    return this.offset { IntOffset(0, animatedOffset) }
}

/**
 * Composable wrapper для draggable item с автоматическим измерением высоты
 */
@Composable
fun Modifier.draggableItemWithMeasurement(
    state: DragDropState,
    index: Int,
    enabled: Boolean = true,
    onHeightMeasured: (Int) -> Unit = {}
): Modifier {
    return this
        .onGloballyPositioned { coordinates ->
            onHeightMeasured(coordinates.size.height)
        }
        .draggableItem(state, index, enabled)
}