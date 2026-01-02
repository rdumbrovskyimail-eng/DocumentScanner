/*
 * DocumentScanner - Domain Repository Interfaces
 * Clean Architecture Domain Layer - Pure Kotlin, Framework Independent
 *
 * Версия: 3.2.0 - Production Ready
 *
 * Repository интерфейсы определяют контракты для Data Layer.
 * Они не содержат Android-зависимостей (Uri, Context и т.д.)
 * и используют только Domain модели.
 */

package com.docs.scanner.domain.repository

import com.docs.scanner.domain.model.*
import kotlinx.coroutines.flow.Flow

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                         FOLDER REPOSITORY                                     ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Repository для операций с папками.
 *
 * Управляет жизненным циклом папок:
 * - CRUD операции
 * - Reactive streams через Flow
 * - Подсчёт записей
 * - Архивирование и закрепление
 */
interface FolderRepository {

    // ═══════════════════════════ REACTIVE QUERIES ═══════════════════════════

    /**
     * Получить все папки как reactive Flow.
     * Отсортированы: закреплённые первыми, затем по дате обновления.
     * Архивированные папки НЕ включены.
     */
    fun observeAllFolders(): Flow<List<Folder>>

    /**
     * Получить все папки включая архивированные.
     */
    fun observeAllFoldersIncludingArchived(): Flow<List<Folder>>

    /**
     * Наблюдать за одной папкой.
     * Эмитит null если папка удалена.
     */
    fun observeFolder(folderId: Long): Flow<Folder?>

    /**
     * Получить архивированные папки.
     */
    fun observeArchivedFolders(): Flow<List<Folder>>

    // ═══════════════════════════ ONE-SHOT QUERIES ═══════════════════════════

    /**
     * Получить папку по ID.
     * @return DomainResult.Success с папкой или DomainResult.Error если не найдена
     */
    suspend fun getFolderById(folderId: Long): DomainResult<Folder>

    /**
     * Получить папку по имени.
     * Поиск без учёта регистра.
     */
    suspend fun getFolderByName(name: String): DomainResult<Folder>

    /**
     * Проверить существование папки.
     */
    suspend fun folderExists(folderId: Long): Boolean

    /**
     * Проверить существование папки с именем.
     */
    suspend fun folderNameExists(name: String, excludeFolderId: Long? = null): Boolean

    /**
     * Получить общее количество папок.
     */
    suspend fun getFolderCount(): Int

    // ═══════════════════════════ MUTATIONS ═══════════════════════════

    /**
     * Создать новую папку.
     *
     * @param folder Папка для создания (id должен быть 0)
     * @return DomainResult.Success с ID созданной папки
     */
    suspend fun createFolder(folder: Folder): DomainResult<Long>

    /**
     * Обновить существующую папку.
     *
     * @param folder Папка с обновлёнными данными
     * @return DomainResult.Success(Unit) при успехе
     */
    suspend fun updateFolder(folder: Folder): DomainResult<Unit>

    /**
     * Удалить папку.
     *
     * @param folderId ID папки для удаления
     * @param deleteContents true = удалить все записи и документы внутри
     *                       false = переместить содержимое в Quick Scans
     * @return DomainResult.Success(Unit) при успехе
     */
    suspend fun deleteFolder(folderId: Long, deleteContents: Boolean = false): DomainResult<Unit>

    /**
     * Архивировать папку.
     */
    suspend fun archiveFolder(folderId: Long): DomainResult<Unit>

    /**
     * Разархивировать папку.
     */
    suspend fun unarchiveFolder(folderId: Long): DomainResult<Unit>

    /**
     * Закрепить/открепить папку.
     */
    suspend fun setPinned(folderId: Long, pinned: Boolean): DomainResult<Unit>

    /**
     * Обновить количество записей в папке.
     * Вызывается после добавления/удаления записей.
     */
    suspend fun updateRecordCount(folderId: Long): DomainResult<Unit>

    /**
     * Убедиться что Quick Scans папка существует.
     * Создаёт если не существует.
     *
     * @param localizedName Локализованное имя папки
     * @return ID папки Quick Scans
     */
    suspend fun ensureQuickScansFolderExists(localizedName: String): Long
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                         RECORD REPOSITORY                                     ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Repository для операций с записями.
 *
 * Запись - контейнер для документов внутри папки.
 * Поддерживает теги, языковые настройки, архивирование.
 */
interface RecordRepository {

    // ═══════════════════════════ REACTIVE QUERIES ═══════════════════════════

    /**
     * Наблюдать записи в папке.
     * Отсортированы: закреплённые первыми, затем по дате обновления.
     */
    fun observeRecordsByFolder(folderId: Long): Flow<List<Record>>

    /**
     * Наблюдать записи в папке включая архивированные.
     */
    fun observeRecordsByFolderIncludingArchived(folderId: Long): Flow<List<Record>>

    /**
     * Наблюдать за одной записью.
     */
    fun observeRecord(recordId: Long): Flow<Record?>

    /**
     * Наблюдать записи по тегу.
     */
    fun observeRecordsByTag(tag: String): Flow<List<Record>>

    /**
     * Наблюдать все записи (для глобального поиска).
     */
    fun observeAllRecords(): Flow<List<Record>>

    /**
     * Наблюдать недавние записи.
     *
     * @param limit Максимальное количество записей
     */
    fun observeRecentRecords(limit: Int = 10): Flow<List<Record>>

    // ═══════════════════════════ ONE-SHOT QUERIES ═══════════════════════════

    /**
     * Получить запись по ID.
     */
    suspend fun getRecordById(recordId: Long): DomainResult<Record>

    /**
     * Проверить существование записи.
     */
    suspend fun recordExists(recordId: Long): Boolean

    /**
     * Получить количество записей в папке.
     */
    suspend fun getRecordCountInFolder(folderId: Long): Int

    /**
     * Получить все теги из всех записей.
     */
    suspend fun getAllTags(): List<String>

    /**
     * Поиск записей по имени или описанию.
     */
    suspend fun searchRecords(query: String): List<Record>

    // ═══════════════════════════ MUTATIONS ═══════════════════════════

    /**
     * Создать новую запись.
     *
     * @param record Запись для создания
     * @return DomainResult.Success с ID созданной записи
     */
    suspend fun createRecord(record: Record): DomainResult<Long>

    /**
     * Обновить существующую запись.
     */
    suspend fun updateRecord(record: Record): DomainResult<Unit>

    /**
     * Удалить запись и все её документы.
     */
    suspend fun deleteRecord(recordId: Long): DomainResult<Unit>

    /**
     * Переместить запись в другую папку.
     */
    suspend fun moveRecord(recordId: Long, targetFolderId: Long): DomainResult<Unit>

    /**
     * Дублировать запись.
     *
     * @param recordId ID записи для дублирования
     * @param targetFolderId Папка для копии (null = та же папка)
     * @param copyDocuments true = копировать документы
     * @return ID новой записи
     */
    suspend fun duplicateRecord(
        recordId: Long,
        targetFolderId: Long? = null,
        copyDocuments: Boolean = true
    ): DomainResult<Long>

    /**
     * Архивировать запись.
     */
    suspend fun archiveRecord(recordId: Long): DomainResult<Unit>

    /**
     * Разархивировать запись.
     */
    suspend fun unarchiveRecord(recordId: Long): DomainResult<Unit>

    /**
     * Закрепить/открепить запись.
     */
    suspend fun setPinned(recordId: Long, pinned: Boolean): DomainResult<Unit>

    /**
     * Обновить языковые настройки записи.
     */
    suspend fun updateLanguageSettings(
        recordId: Long,
        sourceLanguage: Language,
        targetLanguage: Language
    ): DomainResult<Unit>

    /**
     * Добавить тег к записи.
     */
    suspend fun addTag(recordId: Long, tag: String): DomainResult<Unit>

    /**
     * Удалить тег из записи.
     */
    suspend fun removeTag(recordId: Long, tag: String): DomainResult<Unit>

    /**
     * Обновить количество документов в записи.
     */
    suspend fun updateDocumentCount(recordId: Long): DomainResult<Unit>
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                        DOCUMENT REPOSITORY                                    ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Repository для операций с документами.
 *
 * Документ - одна страница/изображение внутри записи.
 * Содержит OCR текст, перевод, метаданные.
 */
interface DocumentRepository {

    // ═══════════════════════════ REACTIVE QUERIES ═══════════════════════════

    /**
     * Наблюдать документы в записи.
     * Отсортированы по позиции.
     */
    fun observeDocumentsByRecord(recordId: Long): Flow<List<Document>>

    /**
     * Наблюдать за одним документом.
     */
    fun observeDocument(documentId: Long): Flow<Document?>

    /**
     * Поиск документов по тексту (Full-Text Search).
     * Ищет в originalText и translatedText.
     */
    fun searchDocuments(query: String): Flow<List<Document>>

    /**
     * Поиск документов с именами папки и записи.
     * Для отображения полного пути в результатах поиска.
     */
    fun searchDocumentsWithPath(query: String): Flow<List<Document>>

    /**
     * Наблюдать документы требующие обработки.
     */
    fun observePendingDocuments(): Flow<List<Document>>

    /**
     * Наблюдать документы с ошибками.
     */
    fun observeFailedDocuments(): Flow<List<Document>>

    // ═══════════════════════════ ONE-SHOT QUERIES ═══════════════════════════

    /**
     * Получить документ по ID.
     */
    suspend fun getDocumentById(documentId: Long): DomainResult<Document>

    /**
     * Получить документы записи (не reactive).
     */
    suspend fun getDocumentsByRecord(recordId: Long): List<Document>

    /**
     * Получить количество документов в записи.
     */
    suspend fun getDocumentCountInRecord(recordId: Long): Int

    /**
     * Проверить существование документа.
     */
    suspend fun documentExists(documentId: Long): Boolean

    /**
     * Получить следующую позицию для нового документа.
     */
    suspend fun getNextPosition(recordId: Long): Int

    // ═══════════════════════════ MUTATIONS ═══════════════════════════

    /**
     * Создать новый документ.
     *
     * @param document Документ для создания
     * @return DomainResult.Success с ID созданного документа
     */
    suspend fun createDocument(document: Document): DomainResult<Long>

    /**
     * Создать несколько документов (batch).
     *
     * @param documents Список документов
     * @return Список ID созданных документов
     */
    suspend fun createDocuments(documents: List<Document>): DomainResult<List<Long>>

    /**
     * Обновить документ.
     */
    suspend fun updateDocument(document: Document): DomainResult<Unit>

    /**
     * Обновить OCR результат.
     */
    suspend fun updateOcrResult(
        documentId: Long,
        originalText: String,
        detectedLanguage: Language?,
        confidence: Float?,
        status: ProcessingStatus
    ): DomainResult<Unit>

    /**
     * Обновить результат перевода.
     */
    suspend fun updateTranslationResult(
        documentId: Long,
        translatedText: String,
        status: ProcessingStatus
    ): DomainResult<Unit>

    /**
     * Обновить статус обработки.
     */
    suspend fun updateProcessingStatus(
        documentId: Long,
        status: ProcessingStatus
    ): DomainResult<Unit>

    /**
     * Удалить документ.
     * Также удаляет файлы изображений.
     */
    suspend fun deleteDocument(documentId: Long): DomainResult<Unit>

    /**
     * Удалить несколько документов.
     */
    suspend fun deleteDocuments(documentIds: List<Long>): DomainResult<Unit>

    /**
     * Переместить документ в другую запись.
     */
    suspend fun moveDocument(documentId: Long, targetRecordId: Long): DomainResult<Unit>

    /**
     * Изменить позицию документа (для сортировки).
     */
    suspend fun updatePosition(documentId: Long, newPosition: Int): DomainResult<Unit>

    /**
     * Переупорядочить документы в записи.
     *
     * @param recordId ID записи
     * @param documentIds Список ID документов в новом порядке
     */
    suspend fun reorderDocuments(recordId: Long, documentIds: List<Long>): DomainResult<Unit>
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                          TERM REPOSITORY                                      ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Repository для операций со сроками/дедлайнами.
 */
interface TermRepository {

    // ═══════════════════════════ REACTIVE QUERIES ═══════════════════════════

    /**
     * Наблюдать все сроки.
     * Отсортированы по дате.
     */
    fun observeAllTerms(): Flow<List<Term>>

    /**
     * Наблюдать активные (не выполненные и не отменённые) сроки.
     */
    fun observeActiveTerms(): Flow<List<Term>>

    /**
     * Наблюдать выполненные сроки.
     */
    fun observeCompletedTerms(): Flow<List<Term>>

    /**
     * Наблюдать просроченные сроки.
     *
     * @param currentTimeMillis Текущее время для определения просрочки
     */
    fun observeOverdueTerms(currentTimeMillis: Long): Flow<List<Term>>

    /**
     * Наблюдать сроки требующие напоминания.
     *
     * @param currentTimeMillis Текущее время
     */
    fun observeTermsNeedingReminder(currentTimeMillis: Long): Flow<List<Term>>

    /**
     * Наблюдать сроки в диапазоне дат.
     */
    fun observeTermsInDateRange(startMillis: Long, endMillis: Long): Flow<List<Term>>

    /**
     * Наблюдать сроки привязанные к документу.
     */
    fun observeTermsByDocument(documentId: Long): Flow<List<Term>>

    /**
     * Наблюдать сроки привязанные к папке.
     */
    fun observeTermsByFolder(folderId: Long): Flow<List<Term>>

    /**
     * Наблюдать за одним сроком.
     */
    fun observeTerm(termId: Long): Flow<Term?>

    // ═══════════════════════════ ONE-SHOT QUERIES ═══════════════════════════

    /**
     * Получить срок по ID.
     */
    suspend fun getTermById(termId: Long): DomainResult<Term>

    /**
     * Получить ближайший срок.
     */
    suspend fun getNextUpcomingTerm(currentTimeMillis: Long): Term?

    /**
     * Получить количество активных сроков.
     */
    suspend fun getActiveTermCount(): Int

    /**
     * Получить количество просроченных сроков.
     */
    suspend fun getOverdueTermCount(currentTimeMillis: Long): Int

    /**
     * Получить количество сроков на сегодня.
     */
    suspend fun getTermsDueTodayCount(currentTimeMillis: Long): Int

    // ═══════════════════════════ MUTATIONS ═══════════════════════════

    /**
     * Создать новый срок.
     *
     * @return ID созданного срока
     */
    suspend fun createTerm(term: Term): DomainResult<Long>

    /**
     * Обновить срок.
     */
    suspend fun updateTerm(term: Term): DomainResult<Unit>

    /**
     * Удалить срок.
     */
    suspend fun deleteTerm(termId: Long): DomainResult<Unit>

    /**
     * Отметить как выполненный.
     */
    suspend fun markCompleted(termId: Long, timestamp: Long): DomainResult<Unit>

    /**
     * Отметить как не выполненный.
     */
    suspend fun markNotCompleted(termId: Long, timestamp: Long): DomainResult<Unit>

    /**
     * Отменить срок.
     */
    suspend fun cancelTerm(termId: Long, timestamp: Long): DomainResult<Unit>

    /**
     * Восстановить отменённый срок.
     */
    suspend fun restoreTerm(termId: Long, timestamp: Long): DomainResult<Unit>

    /**
     * Удалить все выполненные сроки.
     */
    suspend fun deleteAllCompleted(): DomainResult<Int>

    /**
     * Удалить все отменённые сроки.
     */
    suspend fun deleteAllCancelled(): DomainResult<Int>
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                           OCR REPOSITORY                                      ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Repository для OCR операций.
 *
 * Абстрагирует ML Kit и другие OCR провайдеры.
 * Domain layer не знает о конкретной реализации (ML Kit, Tesseract и т.д.)
 */
interface OcrRepository {

    /**
     * Распознать текст на изображении.
     *
     * @param imagePath Путь к файлу изображения
     * @param sourceLanguage Язык документа (AUTO для автоопределения)
     * @return Результат OCR
     */
    suspend fun recognizeText(
        imagePath: String,
        sourceLanguage: Language = Language.AUTO
    ): DomainResult<OcrResult>

    /**
     * Распознать текст с детальной информацией.
     *
     * @param imagePath Путь к файлу изображения
     * @param sourceLanguage Язык документа
     * @return Детальный результат OCR с блоками текста
     */
    suspend fun recognizeTextDetailed(
        imagePath: String,
        sourceLanguage: Language = Language.AUTO
    ): DomainResult<DetailedOcrResult>

    /**
     * Определить язык текста на изображении.
     *
     * @param imagePath Путь к файлу изображения
     * @return Определённый язык
     */
    suspend fun detectLanguage(imagePath: String): DomainResult<Language>

    /**
     * Улучшить OCR текст используя AI.
     *
     * Исправляет типичные OCR ошибки:
     * - Путаница символов (0/O, 1/l/I)
     * - Разбитые или слипшиеся слова
     * - Лишние символы и шум
     *
     * @param rawText Сырой OCR текст
     * @param language Язык текста
     * @return Улучшенный текст
     */
    suspend fun improveOcrText(
        rawText: String,
        language: Language = Language.AUTO
    ): DomainResult<String>

    /**
     * Проверить доступность OCR для языка.
     */
    suspend fun isLanguageSupported(language: Language): Boolean

    /**
     * Получить список поддерживаемых языков.
     */
    suspend fun getSupportedLanguages(): List<Language>
}

/**
 * Результат OCR распознавания.
 */
data class OcrResult(
    /** Распознанный текст */
    val text: String,
    
    /** Определённый язык */
    val detectedLanguage: Language?,
    
    /** Уверенность распознавания (0.0-1.0) */
    val confidence: Float?,
    
    /** Время обработки в миллисекундах */
    val processingTimeMs: Long
)

/**
 * Детальный результат OCR с блоками текста.
 */
data class DetailedOcrResult(
    /** Полный текст */
    val fullText: String,
    
    /** Блоки текста */
    val blocks: List<TextBlock>,
    
    /** Определённый язык */
    val detectedLanguage: Language?,
    
    /** Общая уверенность */
    val confidence: Float?,
    
    /** Время обработки */
    val processingTimeMs: Long
)

/**
 * Блок текста (параграф).
 */
data class TextBlock(
    /** Текст блока */
    val text: String,
    
    /** Строки внутри блока */
    val lines: List<TextLine>,
    
    /** Уверенность распознавания */
    val confidence: Float?,
    
    /** Bounding box (left, top, right, bottom) */
    val boundingBox: BoundingBox?
)

/**
 * Строка текста.
 */
data class TextLine(
    /** Текст строки */
    val text: String,
    
    /** Уверенность распознавания */
    val confidence: Float?,
    
    /** Bounding box */
    val boundingBox: BoundingBox?
)

/**
 * Ограничивающий прямоугольник.
 */
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                       TRANSLATION REPOSITORY                                  ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Repository для операций перевода.
 *
 * Абстрагирует Gemini API и другие переводчики.
 * Включает кэширование переводов.
 */
interface TranslationRepository {

    /**
     * Перевести текст.
     *
     * @param text Текст для перевода
     * @param sourceLanguage Исходный язык (AUTO для автоопределения)
     * @param targetLanguage Целевой язык
     * @param useCache Использовать кэш (по умолчанию true)
     * @return Результат перевода
     */
    suspend fun translate(
        text: String,
        sourceLanguage: Language = Language.AUTO,
        targetLanguage: Language,
        useCache: Boolean = true
    ): DomainResult<TranslationResult>

    /**
     * Перевести несколько текстов (batch).
     *
     * @param texts Список текстов
     * @param sourceLanguage Исходный язык
     * @param targetLanguage Целевой язык
     * @return Список результатов перевода
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: Language = Language.AUTO,
        targetLanguage: Language
    ): DomainResult<List<TranslationResult>>

    /**
     * Определить язык текста.
     */
    suspend fun detectLanguage(text: String): DomainResult<Language>

    /**
     * Проверить поддержку языковой пары.
     */
    suspend fun isLanguagePairSupported(
        sourceLanguage: Language,
        targetLanguage: Language
    ): Boolean

    /**
     * Получить поддерживаемые целевые языки для исходного.
     */
    suspend fun getSupportedTargetLanguages(sourceLanguage: Language): List<Language>

    // ═══════════════════════════ CACHE MANAGEMENT ═══════════════════════════

    /**
     * Очистить кэш переводов.
     */
    suspend fun clearCache(): DomainResult<Unit>

    /**
     * Очистить старые записи кэша.
     *
     * @param maxAgeDays Максимальный возраст записей в днях
     */
    suspend fun clearOldCache(maxAgeDays: Int): DomainResult<Int>

    /**
     * Получить статистику кэша.
     */
    suspend fun getCacheStats(): TranslationCacheStats
}

/**
 * Результат перевода.
 */
data class TranslationResult(
    /** Оригинальный текст */
    val originalText: String,
    
    /** Переведённый текст */
    val translatedText: String,
    
    /** Исходный язык (определённый или указанный) */
    val sourceLanguage: Language,
    
    /** Целевой язык */
    val targetLanguage: Language,
    
    /** Взят из кэша */
    val fromCache: Boolean,
    
    /** Время обработки в миллисекундах */
    val processingTimeMs: Long
)

/**
 * Статистика кэша переводов.
 */
data class TranslationCacheStats(
    /** Общее количество записей */
    val totalEntries: Int,
    
    /** Процент попаданий в кэш */
    val hitRate: Float,
    
    /** Общий размер в байтах */
    val totalSizeBytes: Long,
    
    /** Самая старая запись */
    val oldestEntryTimestamp: Long?,
    
    /** Количество запросов */
    val totalRequests: Long,
    
    /** Количество попаданий */
    val cacheHits: Long
)

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                        SETTINGS REPOSITORY                                    ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Repository для настроек приложения.
 */
interface SettingsRepository {

    // ═══════════════════════════ REACTIVE ═══════════════════════════

    /**
     * Наблюдать за языком приложения.
     */
    fun observeAppLanguage(): Flow<String>

    /**
     * Наблюдать за целевым языком перевода.
     */
    fun observeTargetLanguage(): Flow<Language>

    /**
     * Наблюдать за темой.
     */
    fun observeThemeMode(): Flow<ThemeMode>

    /**
     * Наблюдать за авто-переводом.
     */
    fun observeAutoTranslateEnabled(): Flow<Boolean>

    // ═══════════════════════════ API KEY ═══════════════════════════

    /**
     * Получить API ключ (зашифрованный).
     */
    suspend fun getApiKey(): String?

    /**
     * Сохранить API ключ (зашифрованный).
     */
    suspend fun setApiKey(key: String): DomainResult<Unit>

    /**
     * Удалить API ключ.
     */
    suspend fun clearApiKey(): DomainResult<Unit>

    /**
     * Проверить наличие API ключа.
     */
    suspend fun hasApiKey(): Boolean

    // ═══════════════════════════ LANGUAGE ═══════════════════════════

    /**
     * Получить язык приложения.
     */
    suspend fun getAppLanguage(): String

    /**
     * Установить язык приложения.
     *
     * @param languageCode BCP-47 код языка (например "uk", "en", "")
     *                     Пустая строка = системный язык
     */
    suspend fun setAppLanguage(languageCode: String): DomainResult<Unit>

    /**
     * Получить исходный язык OCR по умолчанию.
     */
    suspend fun getDefaultSourceLanguage(): Language

    /**
     * Установить исходный язык OCR по умолчанию.
     */
    suspend fun setDefaultSourceLanguage(language: Language): DomainResult<Unit>

    /**
     * Получить целевой язык перевода.
     */
    suspend fun getTargetLanguage(): Language

    /**
     * Установить целевой язык перевода.
     */
    suspend fun setTargetLanguage(language: Language): DomainResult<Unit>

    // ═══════════════════════════ APPEARANCE ═══════════════════════════

    /**
     * Получить режим темы.
     */
    suspend fun getThemeMode(): ThemeMode

    /**
     * Установить режим темы.
     */
    suspend fun setThemeMode(mode: ThemeMode): DomainResult<Unit>

    // ═══════════════════════════ FEATURES ═══════════════════════════

    /**
     * Авто-перевод включён?
     */
    suspend fun isAutoTranslateEnabled(): Boolean

    /**
     * Установить авто-перевод.
     */
    suspend fun setAutoTranslateEnabled(enabled: Boolean): DomainResult<Unit>

    /**
     * Onboarding завершён?
     */
    suspend fun isOnboardingCompleted(): Boolean

    /**
     * Установить статус onboarding.
     */
    suspend fun setOnboardingCompleted(completed: Boolean): DomainResult<Unit>

    /**
     * Биометрия включена?
     */
    suspend fun isBiometricEnabled(): Boolean

    /**
     * Установить биометрию.
     */
    suspend fun setBiometricEnabled(enabled: Boolean): DomainResult<Unit>

    // ═══════════════════════════ QUALITY ═══════════════════════════

    /**
     * Получить качество сохранения изображений.
     */
    suspend fun getImageQuality(): ImageQuality

    /**
     * Установить качество сохранения изображений.
     */
    suspend fun setImageQuality(quality: ImageQuality): DomainResult<Unit>

    // ═══════════════════════════ RESET ═══════════════════════════

    /**
     * Сбросить все настройки к значениям по умолчанию.
     */
    suspend fun resetToDefaults(): DomainResult<Unit>
}

/**
 * Режим темы приложения.
 */
enum class ThemeMode {
    /** Системная тема */
    SYSTEM,
    
    /** Светлая тема */
    LIGHT,
    
    /** Тёмная тема */
    DARK
}

/**
 * Качество сохранения изображений.
 */
enum class ImageQuality(val compressionPercent: Int) {
    /** Низкое (быстрее, меньше места) */
    LOW(60),
    
    /** Среднее (баланс) */
    MEDIUM(80),
    
    /** Высокое (лучшее качество) */
    HIGH(95),
    
    /** Оригинальное (без сжатия) */
    ORIGINAL(100)
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                        BACKUP REPOSITORY                                      ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Repository для backup/restore операций.
 */
interface BackupRepository {

    /**
     * Создать локальный backup.
     *
     * @param includImages Включить изображения
     * @return Путь к созданному backup файлу
     */
    suspend fun createLocalBackup(includeImages: Boolean = true): DomainResult<String>

    /**
     * Восстановить из локального backup.
     *
     * @param backupPath Путь к backup файлу
     * @param mergeWithExisting true = объединить, false = заменить
     */
    suspend fun restoreFromLocalBackup(
        backupPath: String,
        mergeWithExisting: Boolean = false
    ): DomainResult<BackupRestoreResult>

    /**
     * Загрузить backup в Google Drive.
     *
     * @param localBackupPath Путь к локальному backup
     * @return ID файла в Google Drive
     */
    suspend fun uploadToGoogleDrive(localBackupPath: String): DomainResult<String>

    /**
     * Скачать backup из Google Drive.
     *
     * @param driveFileId ID файла в Google Drive
     * @return Локальный путь к скачанному файлу
     */
    suspend fun downloadFromGoogleDrive(driveFileId: String): DomainResult<String>

    /**
     * Получить список backups в Google Drive.
     */
    suspend fun listGoogleDriveBackups(): DomainResult<List<CloudBackupInfo>>

    /**
     * Удалить backup из Google Drive.
     */
    suspend fun deleteGoogleDriveBackup(driveFileId: String): DomainResult<Unit>

    /**
     * Получить информацию о последнем backup.
     */
    suspend fun getLastBackupInfo(): BackupInfo?

    /**
     * Наблюдать за прогрессом backup/restore.
     */
    fun observeProgress(): Flow<BackupProgress>
}

/**
 * Информация о backup.
 */
data class BackupInfo(
    val timestamp: Long,
    val sizeBytes: Long,
    val folderCount: Int,
    val recordCount: Int,
    val documentCount: Int,
    val includesImages: Boolean,
    val path: String
)

/**
 * Информация о cloud backup.
 */
data class CloudBackupInfo(
    val fileId: String,
    val fileName: String,
    val timestamp: Long,
    val sizeBytes: Long
)

/**
 * Результат восстановления.
 */
data class BackupRestoreResult(
    val foldersRestored: Int,
    val recordsRestored: Int,
    val documentsRestored: Int,
    val imagesRestored: Int,
    val errors: List<String>
)

/**
 * Прогресс backup/restore операции.
 */
sealed class BackupProgress {
    data object Idle : BackupProgress()
    data class InProgress(val percent: Int, val message: String) : BackupProgress()
    data class Completed(val result: BackupInfo) : BackupProgress()
    data class Failed(val error: DomainError) : BackupProgress()
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                         FILE REPOSITORY                                       ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Repository для файловых операций.
 *
 * Абстрагирует работу с файловой системой.
 */
interface FileRepository {

    /**
     * Сохранить изображение.
     *
     * @param sourceUri URI исходного изображения
     * @param quality Качество сжатия
     * @return Путь к сохранённому файлу
     */
    suspend fun saveImage(sourceUri: String, quality: ImageQuality): DomainResult<String>

    /**
     * Создать миниатюру.
     *
     * @param imagePath Путь к оригинальному изображению
     * @param maxSize Максимальный размер стороны
     * @return Путь к миниатюре
     */
    suspend fun createThumbnail(imagePath: String, maxSize: Int = 256): DomainResult<String>

    /**
     * Удалить файл.
     */
    suspend fun deleteFile(path: String): DomainResult<Unit>

    /**
     * Удалить несколько файлов.
     */
    suspend fun deleteFiles(paths: List<String>): DomainResult<Int>

    /**
     * Получить размер файла.
     */
    suspend fun getFileSize(path: String): Long

    /**
     * Проверить существование файла.
     */
    suspend fun fileExists(path: String): Boolean

    /**
     * Получить размеры изображения.
     *
     * @return Pair(width, height)
     */
    suspend fun getImageDimensions(path: String): DomainResult<Pair<Int, Int>>

    /**
     * Экспортировать документ в PDF.
     *
     * @param documentIds Список ID документов
     * @param outputPath Путь для сохранения PDF
     */
    suspend fun exportToPdf(documentIds: List<Long>, outputPath: String): DomainResult<String>

    /**
     * Поделиться файлом.
     *
     * @param path Путь к файлу
     * @return URI для sharing
     */
    suspend fun shareFile(path: String): DomainResult<String>

    /**
     * Очистить временные файлы.
     *
     * @return Количество удалённых файлов
     */
    suspend fun clearTempFiles(): Int

    /**
     * Получить использование места.
     */
    suspend fun getStorageUsage(): StorageUsage
}

/**
 * Информация об использовании места.
 */
data class StorageUsage(
    /** Размер изображений */
    val imagesBytes: Long,
    
    /** Размер миниатюр */
    val thumbnailsBytes: Long,
    
    /** Размер базы данных */
    val databaseBytes: Long,
    
    /** Размер кэша */
    val cacheBytes: Long,
    
    /** Общий размер */
    val totalBytes: Long
) {
    val formattedTotal: String
        get() = formatBytes(totalBytes)
    
    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}