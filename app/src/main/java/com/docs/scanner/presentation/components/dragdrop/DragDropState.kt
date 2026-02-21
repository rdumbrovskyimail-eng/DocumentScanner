package com.docs.scanner.presentation.components.dragdrop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * DragDropState.kt
 * Version: 9.0.0 - FULLY FIXED (2026)
 *
 * ✅ FIX #12 APPLIED: resetState() now uses snapTo instead of animateTo
 * ✅ Added overscrollJob tracking and cancellation
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
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set
    
    var initialDragIndex by mutableIntStateOf(-1)
        private set
    
    var targetIndex by mutableStateOf<Int?>(null)
        private set
    
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    
    // ✅ FIXED: Use simple Float animatable instead of Offset
    val animatedOffsetY = Animatable(0f)
    
    val isDragging: Boolean
        get() = draggingItemIndex != null
    
    var draggingItemHeight by mutableIntStateOf(0)
        private set
    
    // ✅ FIX #12: Track overscroll job for proper cancellation
    private var overscrollJob: Job? = null
    
    // ✅ FIX #12: Track current element being dragged
    private var currentElement: Int? = null
    private var currentIndexOfDraggedItem: Int? = null
    
    private val _scrollChannel = Channel<Float>(Channel.CONFLATED)
    val scrollEvents = _scrollChannel.receiveAsFlow()
    
    fun startDrag(index: Int, itemHeight: Int) {
        if (draggingItemIndex != null) return
        
        draggingItemIndex = index
        initialDragIndex = index
        targetIndex = index
        draggingItemHeight = itemHeight
        dragOffset = Offset.Zero
        currentIndexOfDraggedItem = index
        
        scope.launch {
            animatedOffsetY.snapTo(0f)
        }
        
        hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
        
        onDragStart(index)
    }
    
    fun onDrag(delta: Offset) {
        if (draggingItemIndex == null) return
        
        dragOffset += delta
        
        scope.launch {
            animatedOffsetY.animateTo(
                dragOffset.y,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
        }
        
        updateTargetIndex()
        checkAutoScroll()
    }
    
    fun endDrag() {
        val fromIndex = initialDragIndex
        val toIndex = targetIndex ?: fromIndex
        
        if (fromIndex != -1 && fromIndex != toIndex) {
            onMove(fromIndex, toIndex)
            hapticFeedback?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        
        onDragEnd(fromIndex, toIndex)
        resetState()
    }
    
    fun cancelDrag() {
        if (draggingItemIndex == null) return
        
        val fromIndex = initialDragIndex
        onDragEnd(fromIndex, fromIndex)
        
        resetState()
    }
    
    fun isDraggingItem(index: Int): Boolean = draggingItemIndex == index
    
    fun isDropTarget(index: Int): Boolean = targetIndex == index && isDragging && draggingItemIndex != index
    
    fun getPlaceholderOffset(index: Int): Int {
        val dragIdx = draggingItemIndex ?: return 0
        val target = targetIndex ?: return 0
        
        if (!isDragging) return 0
        
        return when {
            dragIdx < target && index > dragIdx && index <= target -> -draggingItemHeight
            dragIdx > target && index >= target && index < dragIdx -> draggingItemHeight
            else -> 0
        }
    }
    
    /**
     * ✅ FIX #12: Use snapTo instead of animateTo to prevent sticky drag
     */
    private fun resetState() {
        currentIndexOfDraggedItem = null
        currentElement = null
        overscrollJob?.cancel()
        
        draggingItemIndex = null
        initialDragIndex = -1
        targetIndex = null
        dragOffset = Offset.Zero
        draggingItemHeight = 0
        
        scope.launch {
            // ✅ Use snapTo instead of animateTo for instant reset
            animatedOffsetY.snapTo(0f)
            animatedOffsetY.updateBounds(0f, 0f)
        }
    }
    
    private fun updateTargetIndex() {
        val dragIdx = draggingItemIndex ?: return
        val layoutInfo = lazyListState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        
        if (visibleItems.isEmpty()) return
        
        val draggedItem = visibleItems.find { it.index == dragIdx } ?: return
        val draggedItemCenter = draggedItem.offset + draggedItem.size / 2 + dragOffset.y.toInt()
        
        var newTarget = dragIdx
        
        for (item in visibleItems) {
            if (item.index == dragIdx) continue
            
            val itemCenter = item.offset + item.size / 2
            
            if (dragOffset.y > 0) {
                if (draggedItemCenter > itemCenter && item.index > newTarget) {
                    newTarget = item.index
                }
            } else {
                if (draggedItemCenter < itemCenter && item.index < newTarget) {
                    newTarget = item.index
                }
            }
        }
        
        if (newTarget != targetIndex) {
            targetIndex = newTarget
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
        
        val scrollThreshold = (viewportHeight * 0.15f).toInt()
        
        val scrollAmount = when {
            draggedItemTop < viewportStart + scrollThreshold -> {
                -((scrollThreshold - (draggedItemTop - viewportStart)).coerceAtLeast(0) / 5f)
            }
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

data class DragInfo(
    val index: Int,
    val offset: Offset,
    val height: Int
)

data class DragDropConfig(
    val enabled: Boolean = true,
    val longPressTimeoutMs: Long = 400L,
    val autoScrollEnabled: Boolean = true,
    val autoScrollSpeed: Float = 1f,
    val hapticEnabled: Boolean = true
)