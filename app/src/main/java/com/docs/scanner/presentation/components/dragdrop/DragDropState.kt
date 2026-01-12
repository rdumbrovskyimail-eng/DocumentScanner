/*
 * DocumentScanner - Native Compose Drag & Drop System
 * Version: 1.0.0 (2026 Standards)
 * 
 * ✅ Pure Compose implementation - no external libraries
 * ✅ Haptic feedback support
 * ✅ Smooth animations
 * ✅ Auto-scroll near edges
 * ✅ Placeholder-based insertion (other items stay fixed)
 */

package com.docs.scanner.presentation.components.dragdrop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

// ══════════════════════════════════════════════════════════════════════════════
// DRAG DROP STATE
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Состояние drag & drop для LazyColumn.
 * 
 * @param lazyListState Состояние LazyColumn
 * @param onMove Колбэк при перемещении элемента (fromIndex, toIndex)
 * @param onDragStart Колбэк при начале перетаскивания (index)
 * @param onDragEnd Колбэк при завершении перетаскивания (fromIndex, toIndex)
 * @param scope CoroutineScope для анимаций
 * @param hapticFeedback HapticFeedback для тактильной отдачи
 */
@Stable
class DragDropState(
    val lazyListState: LazyListState,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDragStart: (index: Int) -> Unit,
    private val onDragEnd: (fromIndex: Int, toIndex: Int) -> Unit,
    private val scope: CoroutineScope,
    private val hapticFeedback: HapticFeedback?
) {
    // ═══════════════════════════════════════════════════════════════════════════
    // СОСТОЯНИЕ
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Индекс перетаскиваемого элемента (null если не тащим) */
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set
    
    /** Начальный индекс при старте drag */
    var initialDragIndex by mutableIntStateOf(-1)
        private set
    
    /** Текущий целевой индекс для drop */
    var targetIndex by mutableStateOf<Int?>(null)
        private set
    
    /** Смещение перетаскиваемого элемента относительно начальной позиции */
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    
    /** Анимированное смещение для плавности */
    val animatedOffset = Animatable(Offset.Zero, Offset.VectorConverter)
    
    /** Идёт ли сейчас перетаскивание */
    val isDragging: Boolean
        get() = draggingItemIndex != null
    
    /** Высота перетаскиваемого элемента */
    var draggingItemHeight by mutableIntStateOf(0)
        private set
    
    // События для UI
    private val _scrollChannel = Channel<Float>(Channel.CONFLATED)
    val scrollEvents = _scrollChannel.receiveAsFlow()
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ПУБЛИЧНЫЕ МЕТОДЫ
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Начать перетаскивание элемента.
     * 
     * @param index Индекс элемента в списке
     * @param itemHeight Высота элемента в пикселях
     */
    fun startDrag(index: Int, itemHeight: Int) {
        if (draggingItemIndex != null) return
        
        draggingItemIndex = index
        initialDragIndex = index
        targetIndex = index
        draggingItemHeight = itemHeight
        dragOffset = Offset.Zero
        
        scope.launch {
            animatedOffset.snapTo(Offset.Zero)
        }
        
        // Haptic feedback при начале drag
        hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
        
        onDragStart(index)
    }
    
    /**
     * Обновить позицию при перетаскивании.
     * 
     * @param delta Изменение позиции (дельта от предыдущего положения)
     */
    fun onDrag(delta: Offset) {
        if (draggingItemIndex == null) return
        
        dragOffset += delta
        
        scope.launch {
            animatedOffset.animateTo(
                dragOffset,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
        }
        
        // Определяем новый целевой индекс
        updateTargetIndex()
        
        // Автоскролл при приближении к краям
        checkAutoScroll()
    }
    
    /**
     * Завершить перетаскивание.
     */
    fun endDrag() {
        val fromIndex = initialDragIndex
        val toIndex = targetIndex ?: fromIndex
        
        if (fromIndex != -1 && fromIndex != toIndex) {
            // Выполняем перемещение
            onMove(fromIndex, toIndex)
            
            // Haptic feedback при drop
            hapticFeedback?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        
        onDragEnd(fromIndex, toIndex)
        
        // Сброс состояния
        resetState()
    }
    
    /**
     * Отменить перетаскивание.
     */
    fun cancelDrag() {
        if (draggingItemIndex == null) return
        
        val fromIndex = initialDragIndex
        onDragEnd(fromIndex, fromIndex)
        
        resetState()
    }
    
    /**
     * Проверить, является ли элемент перетаскиваемым.
     */
    fun isDraggingItem(index: Int): Boolean = draggingItemIndex == index
    
    /**
     * Проверить, является ли позиция целевой для drop.
     */
    fun isDropTarget(index: Int): Boolean = targetIndex == index && isDragging && draggingItemIndex != index
    
    /**
     * Получить смещение для placeholder.
     * Возвращает высоту элемента если нужно показать placeholder выше данного индекса.
     */
    fun getPlaceholderOffset(index: Int): Int {
        val dragIdx = draggingItemIndex ?: return 0
        val target = targetIndex ?: return 0
        
        if (!isDragging) return 0
        
        return when {
            // Тащим вниз: элементы между drag и target сдвигаются вверх
            dragIdx < target && index > dragIdx && index <= target -> -draggingItemHeight
            // Тащим вверх: элементы между target и drag сдвигаются вниз
            dragIdx > target && index >= target && index < dragIdx -> draggingItemHeight
            else -> 0
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ПРИВАТНЫЕ МЕТОДЫ
    // ═══════════════════════════════════════════════════════════════════════════
    
    private fun resetState() {
        draggingItemIndex = null
        initialDragIndex = -1
        targetIndex = null
        dragOffset = Offset.Zero
        draggingItemHeight = 0
        
        scope.launch {
            animatedOffset.snapTo(Offset.Zero)
        }
    }
    
    private fun updateTargetIndex() {
        val dragIdx = draggingItemIndex ?: return
        val layoutInfo = lazyListState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        
        if (visibleItems.isEmpty()) return
        
        // Находим элемент под текущей позицией drag
        val draggedItem = visibleItems.find { it.index == dragIdx } ?: return
        val draggedItemCenter = draggedItem.offset + draggedItem.size / 2 + dragOffset.y.toInt()
        
        // Ищем новую целевую позицию
        var newTarget = dragIdx
        
        for (item in visibleItems) {
            if (item.index == dragIdx) continue
            
            val itemCenter = item.offset + item.size / 2
            
            if (dragOffset.y > 0) {
                // Тащим вниз
                if (draggedItemCenter > itemCenter && item.index > newTarget) {
                    newTarget = item.index
                }
            } else {
                // Тащим вверх
                if (draggedItemCenter < itemCenter && item.index < newTarget) {
                    newTarget = item.index
                }
            }
        }
        
        // Обновляем target только если он изменился
        if (newTarget != targetIndex) {
            targetIndex = newTarget
            // Лёгкий haptic при смене позиции
            hapticFeedback?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    
    private fun checkAutoScroll() {
        val layoutInfo = lazyListState.layoutInfo
        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset
        val viewportHeight = viewportEnd - viewportStart
        
        val dragIdx = draggingItemIndex ?: return
        val draggedItem = layoutInfo.visibleItemsInfo.find { it.index == dragIdx } ?: return
        
        val draggedItemTop = draggedItem.offset + dragOffset.y.toInt()
        val draggedItemBottom = draggedItemTop + draggedItem.size
        
        // Зона автоскролла — 15% от высоты viewport
        val scrollThreshold = (viewportHeight * 0.15f).toInt()
        
        val scrollAmount = when {
            // Близко к верху — скроллим вверх
            draggedItemTop < viewportStart + scrollThreshold -> {
                -((scrollThreshold - (draggedItemTop - viewportStart)).coerceAtLeast(0) / 5f)
            }
            // Близко к низу — скроллим вниз
            draggedItemBottom > viewportEnd - scrollThreshold -> {
                ((scrollThreshold - (viewportEnd - draggedItemBottom)).coerceAtLeast(0) / 5f)
            }
            else -> 0f
        }
        
        if (scrollAmount != 0f) {
            scope.launch {
                _scrollChannel.send(scrollAmount)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// REMEMBER FUNCTION
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Создать и запомнить DragDropState.
 * 
 * @param lazyListState Состояние LazyColumn
 * @param onMove Колбэк при перемещении (fromIndex, toIndex)
 * @param onDragStart Колбэк при начале drag
 * @param onDragEnd Колбэк при завершении drag
 * @param hapticFeedback HapticFeedback (опционально)
 */
@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragStart: (index: Int) -> Unit = {},
    onDragEnd: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    hapticFeedback: HapticFeedback? = null
): DragDropState {
    val scope = rememberCoroutineScope()
    
    return remember(lazyListState) {
        DragDropState(
            lazyListState = lazyListState,
            onMove = onMove,
            onDragStart = onDragStart,
            onDragEnd = onDragEnd,
            scope = scope,
            hapticFeedback = hapticFeedback
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Информация о перетаскиваемом элементе для UI.
 */
data class DragInfo(
    val index: Int,
    val offset: Offset,
    val height: Int
)

/**
 * Конфигурация drag & drop.
 */
data class DragDropConfig(
    /** Включен ли drag & drop */
    val enabled: Boolean = true,
    /** Длительность long press для начала drag (ms) */
    val longPressTimeoutMs: Long = 400L,
    /** Включен ли автоскролл */
    val autoScrollEnabled: Boolean = true,
    /** Скорость автоскролла */
    val autoScrollSpeed: Float = 1f,
    /** Включен ли haptic feedback */
    val hapticEnabled: Boolean = true
)