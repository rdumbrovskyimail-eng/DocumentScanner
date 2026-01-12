/*
 * DocumentScanner - Drag & Drop Modifiers
 * Version: 1.0.0 (2026 Standards)
 * 
 * ✅ Modifier.dragHandle() - для иконки перетаскивания
 * ✅ Modifier.draggableItem() - для всего элемента
 * ✅ Smooth animations with spring physics
 * ✅ Visual feedback (elevation, scale, alpha)
 */

package com.docs.scanner.presentation.components.dragdrop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ══════════════════════════════════════════════════════════════════════════════
// DRAG HANDLE MODIFIER
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Modifier для иконки-handle, за которую можно начать перетаскивание.
 * 
 * @param state DragDropState
 * @param index Индекс элемента в списке
 * @param enabled Включен ли drag & drop
 * @param onDragStarted Дополнительный колбэк при начале drag
 * @param onDragEnded Дополнительный колбэк при завершении drag
 */
fun Modifier.dragHandle(
    state: DragDropState,
    index: Int,
    enabled: Boolean = true,
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {}
): Modifier = composed {
    if (!enabled) return@composed this
    
    var itemHeight by remember { mutableIntStateOf(0) }
    
    this
        .onGloballyPositioned { coordinates ->
            // Получаем высоту родительского элемента (карточки)
            coordinates.parentLayoutCoordinates?.let {
                itemHeight = it.size.height
            }
        }
        .pointerInput(index, state) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    state.startDrag(index, itemHeight)
                    onDragStarted()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    state.onDrag(Offset(0f, dragAmount.y))
                },
                onDragEnd = {
                    state.endDrag()
                    onDragEnded()
                },
                onDragCancel = {
                    state.cancelDrag()
                    onDragEnded()
                }
            )
        }
}

// ══════════════════════════════════════════════════════════════════════════════
// DRAGGABLE ITEM MODIFIER
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Modifier для элемента списка, который может быть перетащен.
 * Применяет визуальные эффекты при перетаскивании.
 * 
 * @param state DragDropState
 * @param index Индекс элемента в списке
 * @param enabled Включен ли drag & drop
 */
fun Modifier.draggableItem(
    state: DragDropState,
    index: Int,
    enabled: Boolean = true
): Modifier = composed {
    val isDragging = state.isDraggingItem(index)
    val scope = rememberCoroutineScope()
    
    // Анимированные значения для плавности
    val elevation = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    
    // Смещение для не-перетаскиваемых элементов (placeholder logic)
    val placeholderOffset = if (enabled && state.isDragging && !isDragging) {
        state.getPlaceholderOffset(index)
    } else {
        0
    }
    
    val animatedPlaceholderOffset = remember { Animatable(0f) }
    
    // Анимируем смещение placeholder
    LaunchedEffect(placeholderOffset) {
        animatedPlaceholderOffset.animateTo(
            placeholderOffset.toFloat(),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    
    // Анимируем визуальные эффекты при начале/конце drag
    LaunchedEffect(isDragging) {
        if (isDragging) {
            launch {
                elevation.animateTo(
                    16f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            launch {
                scale.animateTo(
                    1.03f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        } else {
            launch {
                elevation.animateTo(
                    0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                scale.animateTo(
                    1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }
    
    // Затемняем другие элементы во время drag
    LaunchedEffect(state.isDragging, isDragging) {
        val targetAlpha = when {
            !state.isDragging -> 1f
            isDragging -> 1f
            else -> 0.7f // Другие элементы слегка затемняются
        }
        alpha.animateTo(
            targetAlpha,
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        )
    }
    
    this
        .zIndex(if (isDragging) 1f else 0f)
        .graphicsLayer {
            // Применяем смещение для перетаскиваемого элемента
            if (isDragging) {
                translationY = state.animatedOffset.value.y
            } else if (enabled && state.isDragging) {
                // Смещение для placeholder effect
                translationY = animatedPlaceholderOffset.value
            }
            
            // Масштаб и прозрачность
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
            
            // Тень
            shadowElevation = elevation.value
        }
}

// ══════════════════════════════════════════════════════════════════════════════
// DROP TARGET INDICATOR MODIFIER
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Modifier для индикатора места вставки (drop target).
 * Показывает линию или подсветку там, куда будет вставлен элемент.
 * 
 * @param state DragDropState
 * @param index Индекс позиции
 * @param enabled Включен ли индикатор
 */
fun Modifier.dropTargetIndicator(
    state: DragDropState,
    index: Int,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this
    
    val isTarget = state.isDropTarget(index)
    val indicatorAlpha = remember { Animatable(0f) }
    
    LaunchedEffect(isTarget) {
        indicatorAlpha.animateTo(
            if (isTarget) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessHigh)
        )
    }
    
    this.alpha(indicatorAlpha.value)
}

// ══════════════════════════════════════════════════════════════════════════════
// DRAG HANDLE VISUAL MODIFIER
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Modifier для визуальных эффектов на самой иконке handle.
 * 
 * @param state DragDropState
 * @param index Индекс элемента
 */
fun Modifier.dragHandleVisual(
    state: DragDropState,
    index: Int
): Modifier = composed {
    val isDragging = state.isDraggingItem(index)
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(0.6f) }
    
    LaunchedEffect(isDragging) {
        if (isDragging) {
            launch { scale.animateTo(1.2f, spring(stiffness = Spring.StiffnessMedium)) }
            launch { alpha.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
        } else {
            launch { scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
            launch { alpha.animateTo(0.6f, spring(stiffness = Spring.StiffnessMedium)) }
        }
    }
    
    this
        .scale(scale.value)
        .alpha(alpha.value)
}

// ══════════════════════════════════════════════════════════════════════════════
// UTILITY EXTENSIONS
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Комбинированный modifier для элемента с drag & drop.
 * Объединяет draggableItem визуальные эффекты.
 * 
 * @param state DragDropState
 * @param index Индекс элемента
 * @param enabled Включен ли drag & drop для этого элемента
 */
fun Modifier.dragDropEnabled(
    state: DragDropState,
    index: Int,
    enabled: Boolean = true
): Modifier = this
    .draggableItem(state, index, enabled)

/**
 * Создаёт offset для анимации перемещения.
 */
fun Offset.toIntOffset(): IntOffset = IntOffset(x.roundToInt(), y.roundToInt())