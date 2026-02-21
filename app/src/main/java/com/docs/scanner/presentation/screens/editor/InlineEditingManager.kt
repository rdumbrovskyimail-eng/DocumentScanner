package com.docs.scanner.presentation.screens.editor

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * InlineEditingManager.kt
 * Version: 10.0.0 - FULLY FIXED (2026)
 *
 * ✅ FIX #9 APPLIED: Added saveMutex for thread-safety in saveAll()
 * ✅ FIX #12 APPLIED: saveAll() now uses activeEdits instead of _editingStates
 *
 * КРИТИЧЕСКИ ВАЖНО:
 * Использует ConcurrentHashMap для thread-safe операций
 * Каждый документ имеет свой Job для auto-save
 * Отмена одного не влияет на другие
 */
class InlineEditingManager(
    private val scope: CoroutineScope,
    private val onSave: suspend (Long, TextEditField, String) -> Unit,
    private val onHistoryAdd: (Long, TextEditField, String?, String?) -> Unit,
    private val autoSaveDelayMs: Long = 1500L
) {
    private val saveMutex = Mutex()
    
    // Thread-safe хранилище активных редактирований
    private val activeEdits = ConcurrentHashMap<String, InlineEditState>()
    
    // Job'ы для auto-save (один на документ+поле)
    private val autoSaveJobs = ConcurrentHashMap<String, Job>()
    
    // StateFlow для UI
    private val _editingStates = MutableStateFlow<Map<String, InlineEditState>>(emptyMap())
    val editingStates: StateFlow<Map<String, InlineEditState>> = _editingStates.asStateFlow()
    
    /**
     * Создаём уникальный ключ для документа+поля
     */
    private fun key(documentId: Long, field: TextEditField): String = "$documentId:${field.name}"
    
    /**
     * Начать редактирование
     */
    fun startEdit(documentId: Long, field: TextEditField, initialText: String) {
        val k = key(documentId, field)
        
        // Если уже редактируется - ничего не делаем
        if (activeEdits.containsKey(k)) {
            Timber.w("Already editing $k")
            return
        }
        
        val state = InlineEditState(
            documentId = documentId,
            field = field,
            currentText = initialText,
            originalText = initialText,
            isDirty = false,
            lastSaveTimestamp = 0L
        )
        
        activeEdits[k] = state
        updateStateFlow()
        
        Timber.d("Started inline edit: $k")
    }
    
    /**
     * Обновить текст (при каждом символе)
     */
    fun updateText(documentId: Long, field: TextEditField, text: String) {
        val k = key(documentId, field)
        val current = activeEdits[k] ?: run {
            Timber.w("No active edit for $k")
            return
        }
        
        // Обновляем состояние
        val updated = current.copy(
            currentText = text,
            isDirty = text != current.originalText
        )
        activeEdits[k] = updated
        updateStateFlow()
        
        // Отменяем предыдущий auto-save Job
        autoSaveJobs[k]?.cancel()
        
        // Запускаем новый auto-save Job
        if (updated.isDirty) {
            autoSaveJobs[k] = scope.launch {
                delay(autoSaveDelayMs)
                saveEdit(documentId, field)
            }
        }
        
        Timber.d("Updated text for $k (dirty=${updated.isDirty})")
    }
    
    /**
     * Сохранить изменения
     */
    suspend fun saveEdit(documentId: Long, field: TextEditField) {
        val k = key(documentId, field)
        val current = activeEdits[k] ?: run {
            Timber.w("No active edit for $k")
            return
        }
        
        if (!current.isDirty) {
            Timber.d("Nothing to save for $k")
            return
        }
        
        try {
            // Добавляем в историю
            onHistoryAdd(
                documentId,
                field,
                current.originalText,
                current.currentText
            )
            
            // Сохраняем в БД
            onSave(documentId, field, current.currentText)
            
            // Обновляем состояние
            val saved = current.copy(
                originalText = current.currentText,
                isDirty = false,
                lastSaveTimestamp = System.currentTimeMillis()
            )
            activeEdits[k] = saved
            updateStateFlow()
            
            Timber.d("Saved edit for $k")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save edit for $k")
            throw e
        }
    }
    
    /**
     * Завершить редактирование (с сохранением)
     */
    suspend fun finishEdit(documentId: Long, field: TextEditField) {
        val k = key(documentId, field)
        
        // Отменяем auto-save Job
        autoSaveJobs[k]?.cancel()
        autoSaveJobs.remove(k)
        
        // Сохраняем если есть изменения
        val current = activeEdits[k]
        if (current?.isDirty == true) {
            saveEdit(documentId, field)
        }
        
        // Удаляем из активных
        activeEdits.remove(k)
        updateStateFlow()
        
        Timber.d("Finished edit for $k")
    }
    
    /**
     * Отменить редактирование (БЕЗ сохранения)
     */
    fun cancelEdit(documentId: Long, field: TextEditField) {
        val k = key(documentId, field)
        
        // Отменяем auto-save Job
        autoSaveJobs[k]?.cancel()
        autoSaveJobs.remove(k)
        
        // Удаляем из активных
        activeEdits.remove(k)
        updateStateFlow()
        
        Timber.d("Cancelled edit for $k")
    }
    
    /**
     * Получить текущее состояние редактирования
     */
    fun getEditState(documentId: Long, field: TextEditField): InlineEditState? {
        val k = key(documentId, field)
        return activeEdits[k]
    }
    
    /**
     * Проверить, редактируется ли документ+поле
     */
    fun isEditing(documentId: Long, field: TextEditField): Boolean {
        val k = key(documentId, field)
        return activeEdits.containsKey(k)
    }
    
    /**
     * ✅ FIX #12: saveAll() now uses activeEdits instead of _editingStates
     * Сохранить все активные редактирования (при выходе из экрана)
     */
    suspend fun saveAll() {
        saveMutex.withLock {
            // ✅ FIX #12: Используем activeEdits вместо _editingStates
            activeEdits.values.toList().forEach { state ->
                if (state.isDirty) {
                    try {
                        onSave(state.documentId, state.field, state.currentText)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to save edit during saveAll")
                    }
                }
            }
            
            // Отменяем все Job'ы
            autoSaveJobs.values.forEach { it.cancel() }
            autoSaveJobs.clear()
            
            // Очищаем активные редактирования
            activeEdits.clear()
            updateStateFlow()
        }
    }
    
    /**
     * Отменить все активные редактирования
     */
    fun cancelAll() {
        // Отменяем все Job'ы
        autoSaveJobs.values.forEach { it.cancel() }
        autoSaveJobs.clear()
        
        // Очищаем состояния
        activeEdits.clear()
        updateStateFlow()
        
        Timber.d("Cancelled all inline edits")
    }
    
    /**
     * Обновить StateFlow для UI
     */
    private fun updateStateFlow() {
        _editingStates.value = activeEdits.toMap()
    }
}

/**
 * Extension для получения состояния редактирования в Composable
 */
@Composable
fun InlineEditingManager.rememberEditState(
    documentId: Long,
    field: TextEditField
): InlineEditState? {
    val states = editingStates.collectAsStateWithLifecycle()
    val key = "$documentId:${field.name}"
    return states.value[key]
}