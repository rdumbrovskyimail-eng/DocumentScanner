package com.docs.scanner.presentation.components.dragdrop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.collectLatest

/**
 * Универсальный LazyColumn с поддержкой drag & drop
 */
@Composable
fun <T> DragDropLazyColumn(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    enabled: Boolean = true,
    onDragStart: (index: Int) -> Unit = {},
    onDragEnd: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    key: ((index: Int, item: T) -> Any)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    itemContent: @Composable (index: Int, item: T, isDragging: Boolean, dragModifier: Modifier) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    
    val dragDropState = rememberDragDropState(
        lazyListState = state,
        onMove = onMove,
        onDragStart = onDragStart,
        onDragEnd = onDragEnd,
        hapticFeedback = if (enabled) hapticFeedback else null
    )
    
    // Auto-scroll во время перетаскивания
    LaunchedEffect(dragDropState) {
        dragDropState.scrollEvents.collectLatest { scrollAmount ->
            state.dispatchRawDelta(scrollAmount)
        }
    }
    
    LazyColumn(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement
    ) {
        itemsIndexed(
            items = items,
            key = key
        ) { index, item ->
            val isDragging = dragDropState.isDraggingItem(index)
            
            // ✅ Используем draggableItem из DragDropModifiers.kt
            val dragModifier = Modifier.draggableItem(
                state = dragDropState,
                index = index,
                enabled = enabled
            )
            
            itemContent(index, item, isDragging, dragModifier)
        }
    }
}

/**
 * Simplified версия без параметров для простых случаев
 */
@Composable
fun <T> SimpleDragDropList(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    key: ((index: Int, item: T) -> Any)? = null,
    itemContent: @Composable (item: T, isDragging: Boolean, dragModifier: Modifier) -> Unit
) {
    DragDropLazyColumn(
        items = items,
        onMove = onMove,
        modifier = modifier,
        enabled = enabled,
        key = key
    ) { index, item, isDragging, dragModifier ->
        itemContent(item, isDragging, dragModifier)
    }
}

/**
 * DragDropList с поддержкой отдельной drag handle иконки
 */
@Composable
fun <T> DragDropListWithHandle(
    items: List<T>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    enabled: Boolean = true,
    onDragStart: (index: Int) -> Unit = {},
    onDragEnd: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    key: ((index: Int, item: T) -> Any)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    itemContent: @Composable (
        index: Int,
        item: T,
        isDragging: Boolean,
        dragHandleModifier: Modifier,
        itemModifier: Modifier
    ) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    val dragDropState = rememberDragDropState(
        lazyListState = state,
        onMove = onMove,
        onDragStart = onDragStart,
        onDragEnd = onDragEnd,
        hapticFeedback = if (enabled) hapticFeedback else null
    )
    
    // Auto-scroll во время перетаскивания
    LaunchedEffect(dragDropState) {
        dragDropState.scrollEvents.collectLatest { scrollAmount ->
            state.dispatchRawDelta(scrollAmount)
        }
    }
    
    LazyColumn(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement
    ) {
        itemsIndexed(
            items = items,
            key = key
        ) { index, item ->
            val isDragging = dragDropState.isDraggingItem(index)
            
            // Modifier для всего item (визуальные эффекты при перетаскивании)
            val itemModifier = Modifier.draggableItem(
                state = dragDropState,
                index = index,
                enabled = false // Не ловим жесты на всём item
            )
            
            // Modifier для drag handle (ловим жесты только здесь)
            val dragHandleModifier = Modifier.draggableItem(
                state = dragDropState,
                index = index,
                enabled = enabled
            )
            
            itemContent(index, item, isDragging, dragHandleModifier, itemModifier)
        }
    }
}