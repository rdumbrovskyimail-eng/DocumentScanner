/*
 * MlkitSettingsState.kt
 * Version: 7.0.0 - PRODUCTION READY 2026 - COMPLETE STATE
 * 
 * ✅ ПОЛНАЯ СПЕЦИФИКАЦИЯ:
 * - 9 полей для UI и логики
 * - Синхронизация с DataStore
 * - Поддержка test режима
 * - Thread-safe копирование
 * 
 * ИСПОЛЬЗУЕТСЯ В:
 * - SettingsViewModel (управление)
 * - MlkitSettingsSection (UI)
 * - EditorViewModel (чтение настроек)
 */

package com.docs.scanner.presentation.screens.settings.components

import android.net.Uri
import com.docs.scanner.data.remote.mlkit.OcrScriptMode
import com.docs.scanner.data.remote.mlkit.OcrTestResult

/**
 * ПОЛНОЕ состояние MLKit OCR настроек.
 * 
 * Это единственный источник истины для всех OCR параметров в приложении.
 * Все изменения проходят через SettingsViewModel.
 * 
 * @property scriptMode Режим распознавания (AUTO, LATIN, CHINESE, etc.)
 * @property autoDetectLanguage Автоопределение языка из изображения
 * @property confidenceThreshold Порог уверенности (0.0-1.0)
 * @property highlightLowConfidence Подсветка слов с низкой уверенностью
 * @property showWordConfidences Показывать проценты уверенности
 * @property selectedImageUri URI изображения для теста
 * @property isTestRunning Флаг выполнения теста
 * @property testResult Результат последнего теста
 * @property testError Ошибка теста (если была)
 */
data class MlkitSettingsState(
    // ═══════════════════════════════════════════════════════════════════════════
    // CORE OCR SETTINGS (синхронизируются с DataStore)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Режим распознавания текста.
     * 
     * Значения:
     * - AUTO: Автоопределение (пробует все режимы)
     * - LATIN: Латиница (English, Spanish, French, German, etc.)
     * - CHINESE: Китайский (упрощённый + традиционный)
     * - JAPANESE: Японский (Hiragana, Katakana, Kanji)
     * - KOREAN: Корейский (Hangul)
     * - DEVANAGARI: Деванагари (Hindi, Marathi, Nepali)
     * 
     * ВАЖНО: Сохраняется в DataStore и применяется ко всем новым документам.
     */
    val scriptMode: OcrScriptMode = OcrScriptMode.AUTO,
    
    /**
     * Автоматическое определение языка из изображения.
     * 
     * Если true:
     * - MLKit сначала пытается определить скрипт из содержимого
     * - Затем применяет соответствующий recognizer
     * 
     * Если false:
     * - Использует только scriptMode
     * 
     * Рекомендуется: true для смешанных документов.
     */
    val autoDetectLanguage: Boolean = true,
    
    /**
     * Порог уверенности для отметки слов как "низкая уверенность".
     * 
     * Диапазон: 0.0 - 1.0
     * Рекомендуемые значения:
     * - 0.9 - строгий (только высокая уверенность)
     * - 0.7 - сбалансированный (по умолчанию)
     * - 0.5 - мягкий (допускает больше неточностей)
     * 
     * Слова с confidence < threshold помечаются для ручной проверки.
     */
    val confidenceThreshold: Float = 0.7f,
    
    /**
     * Подсвечивать красным слова с низкой уверенностью в preview.
     * 
     * Визуальная индикация для быстрого поиска проблемных мест.
     * Работает в паре с confidenceThreshold.
     */
    val highlightLowConfidence: Boolean = true,
    
    /**
     * Показывать процент уверенности для каждого слова в test результатах.
     * 
     * Полезно для анализа качества OCR и настройки confidenceThreshold.
     * Включайте только для диагностики - замедляет UI.
     */
    val showWordConfidences: Boolean = false,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TEST MODE STATE (для функции "Test OCR" в настройках)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * URI выбранного изображения для теста OCR.
     * 
     * Null = изображение не выбрано.
     * Пользователь выбирает через PickVisualMedia.
     */
    val selectedImageUri: Uri? = null,
    
    /**
     * Флаг выполнения OCR теста.
     * 
     * true = тест выполняется прямо сейчас (показываем ProgressIndicator)
     * false = тест завершён или не запущен
     */
    val isTestRunning: Boolean = false,
    
    /**
     * Результат последнего успешного теста OCR.
     * 
     * Содержит:
     * - Распознанный текст
     * - Статистику (words, confidence, quality)
     * - Детали для каждого слова
     * 
     * Null = тест ещё не запускался или был ошибка.
     */
    val testResult: OcrTestResult? = null,
    
    /**
     * Сообщение об ошибке последнего теста.
     * 
     * Null = ошибок не было.
     * Показываем в ErrorCard для пользователя.
     */
    val testError: String? = null
) {
    /**
     * Проверка готовности к тесту.
     */
    val canRunTest: Boolean
        get() = selectedImageUri != null && !isTestRunning
    
    /**
     * Есть ли результаты для отображения.
     */
    val hasResults: Boolean
        get() = testResult != null || testError != null
}
