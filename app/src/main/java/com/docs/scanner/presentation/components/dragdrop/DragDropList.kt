/*
 * DocumentScanner - Drag & Drop List Components
 * Version: 1.0.0 (2026 Standards)
 * 
 * ✅ DragDropLazyColumn - обёртка над LazyColumn с drag & drop
 * ✅ DragDropItem - обёртка для элементов списка
 * ✅ Auto-scroll support
 * ✅ Placeholder visualization
 */

package com.docs.scanner.presentation.components.dragdrop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
// DRAG DROP LAZY COLUMN
// ══════════════════════════════════════════════════════════════════════════════

/**
 * LazyColumn с поддержкой drag & drop.
 * 
 * @param modifier Modifier для LazyColumn
 * @param state LazyListState
 * @param dragDropState DragDropState для управления перетаскиванием
 * @param contentPadding Отступы содержимого
 * @param verticalArrangement Расположение элементов
 * @param enabled Включен ли drag & drop
 * @param content Содержимое списка
 */
@Composable
fun DragDropLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    dragDropState: DragDropState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(0.dp),
    enabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // Автоскролл при перетаскивании к краям
    LaunchedEffect(dragDropState, enabled) {
        if (!enabled) return@LaunchedEffect
        
        dragDropState.scrollEvents.collectLatest { scrollAmount ->
            if (scrollAmount != 0f) {
                scope.launch {
                    state.scrollBy(scrollAmount)
                }
            }
        }
    }
    
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        content = content
    )
}

// ══════════════════════════════════════════════════════════════════════════════
// DRAG DROP ITEM WRAPPER
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Обёртка для элемента списка с поддержкой drag & drop.
 * 
 * @param modifier Modifier
 * @param dragDropState DragDropState
 * @param index Индекс элемента в списке
 * @param enabled Включен ли drag & drop для этого элемента
 * @param content Содержимое элемента (получает isDragging)
 */
@Composable
fun DragDropItem(
    modifier: Modifier = Modifier,
    dragDropState: DragDropState,
    index: Int,
    enabled: Boolean = true,
    content: @Composable (isDragging: Boolean) -> Unit
) {
    val isDragging = dragDropState.isDraggingItem(index)
    
    Box(
        modifier = modifier
            .dragDropEnabled(
                state = dragDropState,
                index = index,
                enabled = enabled
            )
    ) {
        content(isDragging)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DROP INDICATOR
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Визуальный индикатор места вставки.
 * Показывается между элементами, когда drag-элемент находится над этой позицией.
 * 
 * @param visible Показывать ли индикатор
 * @param modifier Modifier
 */
@Composable
fun DropIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessHigh)) + 
                expandVertically(spring(stiffness = Spring.StiffnessHigh)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessHigh)) + 
               shrinkVertically(spring(stiffness = Spring.StiffnessHigh))
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DRAG HANDLE COMPONENT
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Готовый компонент иконки-handle для перетаскивания.
 * 
 * @param dragDropState DragDropState
 * @param index Индекс элемента
 * @param enabled Включен ли drag & drop
 * @param modifier Modifier
 * @param onDragStarted Колбэк при начале drag
 * @param onDragEnded Колбэк при завершении drag
 * @param content Содержимое (иконка)
 */
@Composable
fun DragHandle(
    dragDropState: DragDropState,
    index: Int,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onDragStarted: () -> Unit = {},
    onDragEnded: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .dragHandle(
                state = dragDropState,
                index = index,
                enabled = enabled,
                onDragStarted = onDragStarted,
                onDragEnded = onDragEnded
            )
            .dragHandleVisual(
                state = dragDropState,
                index = index
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// HELPER COMPOSABLES
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Spacer с поддержкой drop indicator.
 * Используется между элементами для показа места вставки.
 * 
 * @param dragDropState DragDropState
 * @param index Индекс позиции (перед каким элементом показывать)
 * @param height Высота spacer
 * @param showIndicator Показывать ли индикатор
 */
@Composable
fun DragDropSpacer(
    dragDropState: DragDropState,
    index: Int,
    height: Dp = 8.dp,
    showIndicator: Boolean = true
) {
    val isTarget = dragDropState.isDropTarget(index)
    
    Column {
        if (showIndicator) {
            DropIndicator(visible = isTarget)
        }
        Spacer(modifier = Modifier.height(height))
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// REMEMBER FUNCTIONS
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Создать и запомнить полную конфигурацию drag & drop для списка.
 * 
 * @param items Список элементов
 * @param key Функция получения ключа элемента
 * @param onReorder Колбэк при изменении порядка (fromIndex, toIndex)
 * @param onDragStart Колбэк при начале drag
 * @param onDragEnd Колбэк при завершении drag
 * @param enabled Включен ли drag & drop
 */
@Composable
fun <T> rememberDragDropListState(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    enabled: Boolean = true
): DragDropListState<T> {
    val lazyListState = rememberLazyListState()
    val hapticFeedback = LocalHapticFeedback.current
    
    val dragDropState = rememberDragDropState(
        lazyListState = lazyListState,
        onMove = onReorder,
        onDragStart = { onDragStart() },
        onDragEnd = { _, _ -> onDragEnd() },
        hapticFeedback = if (enabled) hapticFeedback else null
    )
    
    return remember(items, enabled) {
        DragDropListState(
            items = items,
            key = key,
            lazyListState = lazyListState,
            dragDropState = dragDropState,
            enabled = enabled
        )
    }
}

/**
 * Состояние списка с drag & drop.
 */
@Stable
class DragDropListState<T>(
    val items: List<T>,
    val key: (T) -> Any,
    val lazyListState: LazyListState,
    val dragDropState: DragDropState,
    val enabled: Boolean
)

// ══════════════════════════════════════════════════════════════════════════════
// EXTENSION FUNCTIONS
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Extension для LazyListState для скролла на указанное количество пикселей.
 */
suspend fun LazyListState.scrollBy(pixels: Float) {
    val currentOffset = firstVisibleItemScrollOffset
    val targetOffset = currentOffset + pixels.toInt()
    
    if (targetOffset < 0) {
        // Скроллим к предыдущему элементу
        val prevIndex = (firstVisibleItemIndex - 1).coerceAtLeast(0)
        scrollToItem(prevIndex)
    } else {
        // Скроллим внутри текущего элемента или к следующему
        animateScrollToItem(
            firstVisibleItemIndex,
            targetOffset.coerceAtLeast(0)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// PREVIEW HELPERS
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Пустой DragDropState для превью.
 */
@Composable
fun rememberPreviewDragDropState(): DragDropState {
    val lazyListState = rememberLazyListState()
    return rememberDragDropState(
        lazyListState = lazyListState,
        onMove = { _, _ -> },
        onDragStart = {},
        onDragEnd = { _, _ -> }
    )
}