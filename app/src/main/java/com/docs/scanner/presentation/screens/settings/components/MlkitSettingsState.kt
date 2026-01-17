/*
 * MlkitSettingsState.kt
 * Version: 9.0.0 - GEMINI OCR FALLBACK + TEST CHECKBOX (2026 Standards)
 * 
 * ✅ NEW IN 9.0.0:
 * - testGeminiFallback: Boolean = false (для UI чекбокса)
 * - showGeminiFallbackTest computed property
 * 
 * ✅ ПОЛНАЯ СПЕЦИФИКАЦИЯ:
 * - 13 полей для UI и логики (12 старых + 1 новый)
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
 * @property geminiOcrEnabled Включен ли Gemini OCR fallback
 * @property geminiOcrThreshold Порог confidence для fallback 0-100
 * @property geminiOcrAlways Всегда использовать Gemini (пропустить ML Kit)
 * @property testGeminiFallback Флаг для принудительного теста Gemini fallback (NEW)
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
    val testError: String? = null,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ✅ GEMINI OCR FALLBACK SETTINGS (PHASE 2)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * ✅ Включен ли Gemini OCR fallback.
     * 
     * Когда true:
     * - Если ML Kit результаты плохие (низкий confidence, много ошибок)
     * - Автоматически запускается Gemini Vision OCR как fallback
     * 
     * Когда false:
     * - Используется только ML Kit (экономия API вызовов)
     * 
     * Default: true (рекомендуется для production)
     */
    val geminiOcrEnabled: Boolean = true,
    
    /**
     * ✅ Порог confidence (0-100) для triggering Gemini fallback.
     * 
     * Если overall confidence ML Kit < этого порога:
     * - Запускается Gemini OCR как fallback
     * 
     * Рекомендуемые значения:
     * - 70-80: Строгий (используем Gemini часто)
     * - 50: Сбалансированный (default)
     * - 30-40: Мягкий (ML Kit first, Gemini редко)
     * 
     * Default: 50 (50% confidence)
     */
    val geminiOcrThreshold: Int = 50,
    
    /**
     * ✅ Всегда использовать Gemini OCR (пропустить ML Kit).
     * 
     * Когда true:
     * - ML Kit полностью пропускается
     * - OCR делается сразу через Gemini Vision
     * - Полезно для рукописных документов
     * 
     * Когда false:
     * - Сначала ML Kit, затем Gemini только при необходимости
     * 
     * Default: false (рекомендуется для экономии API)
     * 
     * ⚠️ ВНИМАНИЕ: Расходует API quota быстрее!
     */
    val geminiOcrAlways: Boolean = false,
    
    /**
     * ✅ NEW: Флаг для принудительного тестирования Gemini OCR fallback.
     * 
     * Когда true (чекбокс в UI включен):
     * - OCR тест симулирует низкую уверенность ML Kit
     * - Принудительно запускает Gemini OCR
     * - Позволяет протестировать Gemini fallback вручную
     * 
     * Default: false (обычный режим теста)
     * 
     * ВАЖНО: Это только для диагностики, не сохраняется в DataStore.
     */
    val testGeminiFallback: Boolean = false
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
    
    /**
     * ✅ Gemini fallback активен и настроен корректно.
     */
    val isGeminiFallbackActive: Boolean
        get() = geminiOcrEnabled && !geminiOcrAlways && geminiOcrThreshold in 0..100
    
    /**
     * ✅ Режим "только Gemini" активен.
     */
    val isGeminiOnlyMode: Boolean
        get() = geminiOcrEnabled && geminiOcrAlways
    
    /**
     * ✅ NEW: Показывать ли чекбокс "Test Gemini fallback" в UI.
     * 
     * Показываем только если:
     * - Gemini OCR включен
     * - Изображение выбрано для теста
     */
    val showGeminiFallbackTest: Boolean
        get() = geminiOcrEnabled && selectedImageUri != null
}