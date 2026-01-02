/*
 * DocumentScanner - Domain Models
 * Clean Architecture Domain Layer - Pure Kotlin, Framework Independent
 *
 * Версия: 3.2.0 - Production Ready
 */

package com.docs.scanner.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                              CONSTANTS                                        ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

object DomainConstants {
    // Folder limits
    const val QUICK_SCANS_FOLDER_ID = -1L
    const val DEFAULT_FOLDER_ID = 1L
    const val FOLDER_NAME_MAX_LENGTH = 100
    const val FOLDER_DESC_MAX_LENGTH = 500

    // Record limits
    const val RECORD_NAME_MAX_LENGTH = 100
    const val RECORD_DESC_MAX_LENGTH = 1000
    
    // Document limits
    const val IMG_MAX_SIZE_BYTES = 10 * 1024 * 1024L // 10MB
    const val MAX_OCR_TEXT_LENGTH = 100_000
    
    // Tag limits
    const val MAX_TAGS_PER_RECORD = 10
    const val TAG_MAX_LENGTH = 30
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                           VALUE OBJECTS                                       ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Типобезопасная обертка для Folder ID
 */
@JvmInline
@Serializable
value class FolderId(val value: Long) {
    init {
        require(value != 0L) { "Folder ID cannot be 0" }
    }
    
    val isQuickScans: Boolean get() = value == DomainConstants.QUICK_SCANS_FOLDER_ID
    
    companion object {
        val QUICK_SCANS = FolderId(DomainConstants.QUICK_SCANS_FOLDER_ID)
        val DEFAULT = FolderId(DomainConstants.DEFAULT_FOLDER_ID)
    }
}

/**
 * Типобезопасная обертка для Record ID
 */
@JvmInline
@Serializable
value class RecordId(val value: Long) {
    init {
        require(value > 0) { "Record ID must be positive, got: $value" }
    }
}

/**
 * Типобезопасная обертка для Document ID
 */
@JvmInline
@Serializable
value class DocumentId(val value: Long) {
    init {
        require(value > 0) { "Document ID must be positive, got: $value" }
    }
}

/**
 * Валидированное имя папки
 */
@JvmInline
@Serializable
value class FolderName(val value: String) {
    init {
        require(value.isNotBlank()) { "Folder name cannot be blank" }
        require(value.length <= DomainConstants.FOLDER_NAME_MAX_LENGTH) {
            "Folder name too long: ${value.length} > ${DomainConstants.FOLDER_NAME_MAX_LENGTH}"
        }
    }
}

/**
 * Валидированное имя записи
 */
@JvmInline
@Serializable
value class RecordName(val value: String) {
    init {
        require(value.isNotBlank()) { "Record name cannot be blank" }
        require(value.length <= DomainConstants.RECORD_NAME_MAX_LENGTH) {
            "Record name too long: ${value.length} > ${DomainConstants.RECORD_NAME_MAX_LENGTH}"
        }
    }
}

/**
 * Валидированный тег
 */
@JvmInline
@Serializable
value class Tag(val value: String) {
    init {
        require(value.isNotBlank()) { "Tag cannot be blank" }
        require(value.length <= DomainConstants.TAG_MAX_LENGTH) {
            "Tag too long: ${value.length} > ${DomainConstants.TAG_MAX_LENGTH}"
        }
        require(!value.contains(",")) { "Tag cannot contain comma" }
    }
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                              LANGUAGE                                         ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Язык документа (ISO 639-1 codes)
 * Не содержит инфраструктурных деталей (ML Kit codes вынесены в адаптер)
 */
@Serializable
enum class Language(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val features: Set<LanguageFeature> = setOf(LanguageFeature.OCR, LanguageFeature.TRANSLATION)
) {
    ENGLISH("en", "English", "English"),
    RUSSIAN("ru", "Russian", "Русский"),
    UKRAINIAN("uk", "Ukrainian", "Українська"),
    GERMAN("de", "German", "Deutsch"),
    FRENCH("fr", "French", "Français"),
    SPANISH("es", "Spanish", "Español"),
    ITALIAN("it", "Italian", "Italiano"),
    PORTUGUESE("pt", "Portuguese", "Português"),
    POLISH("pl", "Polish", "Polski"),
    DUTCH("nl", "Dutch", "Nederlands"),
    TURKISH("tr", "Turkish", "Türkçe"),
    CHINESE_SIMPLIFIED("zh", "Chinese (Simplified)", "简体中文"),
    CHINESE_TRADITIONAL("zh-TW", "Chinese (Traditional)", "繁體中文"),
    JAPANESE("ja", "Japanese", "日本語"),
    KOREAN("ko", "Korean", "한국어"),
    ARABIC("ar", "Arabic", "العربية"),
    HINDI("hi", "Hindi", "हिन्दी"),
    THAI("th", "Thai", "ไทย"),
    VIETNAMESE("vi", "Vietnamese", "Tiếng Việt"),
    CZECH("cs", "Czech", "Čeština"),
    GREEK("el", "Greek", "Ελληνικά"),
    HEBREW("he", "Hebrew", "עברית"),
    HUNGARIAN("hu", "Hungarian", "Magyar"),
    ROMANIAN("ro", "Romanian", "Română"),
    SWEDISH("sv", "Swedish", "Svenska"),
    NORWEGIAN("no", "Norwegian", "Norsk"),
    DANISH("da", "Danish", "Dansk"),
    FINNISH("fi", "Finnish", "Suomi"),
    BULGARIAN("bg", "Bulgarian", "Български"),
    CROATIAN("hr", "Croatian", "Hrvatski"),
    SERBIAN("sr", "Serbian", "Српски"),
    SLOVAK("sk", "Slovak", "Slovenčina"),
    SLOVENIAN("sl", "Slovenian", "Slovenščina"),
    
    /**
     * Автоопределение языка.
     * Не поддерживает перевод (нельзя переводить "на авто")
     */
    AUTO(
        code = "auto",
        displayName = "Auto-detect",
        nativeName = "Auto",
        features = setOf(LanguageFeature.OCR)
    );

    val supportsOcr: Boolean get() = LanguageFeature.OCR in features
    val supportsTranslation: Boolean get() = LanguageFeature.TRANSLATION in features
    val isAutoDetect: Boolean get() = this == AUTO

    companion object {
        /**
         * Найти язык по ISO коду
         */
        fun fromCode(code: String): Language? =
            entries.find { it.code.equals(code, ignoreCase = true) }

        /**
         * Все языки с поддержкой OCR (кроме AUTO)
         */
        val ocrSupported: List<Language>
            get() = entries.filter { it.supportsOcr && !it.isAutoDetect }

        /**
         * Все языки с поддержкой перевода
         */
        val translationSupported: List<Language>
            get() = entries.filter { it.supportsTranslation }
    }
}

/**
 * Функции языка
 */
enum class LanguageFeature {
    /** Optical Character Recognition - распознавание текста */
    OCR,
    
    /** Translation - перевод текста */
    TRANSLATION,
    
    /** Text-to-Speech - озвучивание текста */
    TTS
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                              ENTITIES                                         ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Папка для организации записей
 * 
 * @property id Уникальный идентификатор. 0 для новых папок, -1 для Quick Scans
 * @property name Имя папки (валидировано, 1-100 символов)
 * @property description Опциональное описание (до 500 символов)
 * @property color Цвет папки в формате ARGB (nullable)
 * @property icon Имя иконки из ресурсов или emoji (nullable)
 * @property recordCount Количество записей в папке (денормализовано для производительности)
 * @property isPinned Закреплена ли папка в списке
 * @property isArchived Архивирована ли папка
 * @property createdAt Timestamp создания в миллисекундах (устанавливается Use Case)
 * @property updatedAt Timestamp последнего обновления
 */
@Serializable
data class Folder(
    val id: Long = 0L,
    val name: String,
    val description: String? = null,
    val color: Int? = null,
    val icon: String? = null,
    val recordCount: Int = 0,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    init {
        require(name.isNotBlank()) { "Folder name cannot be blank" }
        require(name.length <= DomainConstants.FOLDER_NAME_MAX_LENGTH) {
            "Folder name exceeds max length"
        }
        description?.let {
            require(it.length <= DomainConstants.FOLDER_DESC_MAX_LENGTH) {
                "Folder description exceeds max length"
            }
        }
        require(recordCount >= 0) { "Record count cannot be negative" }
    }

    val isQuickScansFolder: Boolean get() = id == DomainConstants.QUICK_SCANS_FOLDER_ID
    val isEmpty: Boolean get() = recordCount == 0
    val isNotEmpty: Boolean get() = recordCount > 0

    companion object {
        /**
         * Создать специальную папку Quick Scans
         * @param name Локализованное имя
         * @param timestamp Текущее время (из TimeProvider)
         */
        fun createQuickScans(name: String, timestamp: Long) = Folder(
            id = DomainConstants.QUICK_SCANS_FOLDER_ID,
            name = name,
            isPinned = true,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }
}

/**
 * Запись (документ с несколькими страницами)
 * 
 * @property id Уникальный идентификатор
 * @property folderId ID родительской папки
 * @property name Имя записи (валидировано)
 * @property description Опциональное описание
 * @property tags Список тегов для поиска
 * @property documentCount Количество страниц (денормализовано)
 * @property sourceLanguage Исходный язык документа
 * @property targetLanguage Целевой язык для перевода
 * @property isPinned Закреплена ли запись
 * @property isArchived Архивирована ли запись
 * @property createdAt Timestamp создания
 * @property updatedAt Timestamp последнего обновления
 */
@Serializable
data class Record(
    val id: Long = 0L,
    val folderId: Long,
    val name: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val documentCount: Int = 0,
    val sourceLanguage: Language = Language.AUTO,
    val targetLanguage: Language = Language.ENGLISH,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    init {
        require(name.isNotBlank()) { "Record name cannot be blank" }
        require(name.length <= DomainConstants.RECORD_NAME_MAX_LENGTH) {
            "Record name exceeds max length"
        }
        require(folderId != 0L) { "Record must belong to a folder" }
        description?.let {
            require(it.length <= DomainConstants.RECORD_DESC_MAX_LENGTH) {
                "Record description exceeds max length"
            }
        }
        require(tags.size <= DomainConstants.MAX_TAGS_PER_RECORD) {
            "Too many tags: ${tags.size} > ${DomainConstants.MAX_TAGS_PER_RECORD}"
        }
        require(documentCount >= 0) { "Document count cannot be negative" }
    }

    val isQuickScan: Boolean get() = folderId == DomainConstants.QUICK_SCANS_FOLDER_ID
    val isEmpty: Boolean get() = documentCount == 0
    val isNotEmpty: Boolean get() = documentCount > 0
    val hasTags: Boolean get() = tags.isNotEmpty()
}

/**
 * Документ (одна страница записи)
 * 
 * @property id Уникальный идентификатор
 * @property recordId ID родительской записи
 * @property imagePath Путь к изображению высокого качества
 * @property thumbnailPath Путь к миниатюре. Null если не создана
 * @property originalText OCR распознанный текст. Null до завершения OCR
 * @property translatedText Переведенный текст. Null если перевод не выполнен
 * @property detectedLanguage Язык, определенный OCR. Null если не определен
 * @property sourceLanguage Исходный язык (настройка пользователя)
 * @property targetLanguage Целевой язык для перевода
 * @property position Позиция в записи (для сортировки страниц)
 * @property processingStatus Статус обработки (OCR, перевод)
 * @property ocrConfidence Уверенность OCR (0.0-1.0). Null если OCR не выполнен
 * @property fileSize Размер файла изображения в байтах
 * @property width Ширина изображения в пикселях
 * @property height Высота изображения в пикселях
 * @property createdAt Timestamp создания
 * @property updatedAt Timestamp последнего обновления
 * @property recordName Денормализованное имя записи (для UI)
 * @property folderName Денормализованное имя папки (для UI)
 */
@Serializable
data class Document(
    val id: Long = 0L,
    val recordId: Long,
    val imagePath: String,
    val thumbnailPath: String? = null,
    val originalText: String? = null,
    val translatedText: String? = null,
    val detectedLanguage: Language? = null,
    val sourceLanguage: Language = Language.AUTO,
    val targetLanguage: Language = Language.ENGLISH,
    val position: Int = 0,
    val processingStatus: ProcessingStatus = ProcessingStatus.INITIAL,
    val ocrConfidence: Float? = null,
    val fileSize: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    // Денормализованные данные для UI
    val recordName: String? = null,
    val folderName: String? = null
) {
    init {
        require(recordId > 0) { "Document must belong to a record" }
        require(imagePath.isNotBlank()) { "Image path cannot be blank" }
        require(position >= 0) { "Position cannot be negative" }
        require(fileSize >= 0) { "File size cannot be negative" }
        require(width >= 0 && height >= 0) { "Dimensions cannot be negative" }
        ocrConfidence?.let {
            require(it in 0f..1f) { "OCR confidence must be in range 0.0-1.0, got: $it" }
        }
        originalText?.let {
            require(it.length <= DomainConstants.MAX_OCR_TEXT_LENGTH) {
                "OCR text exceeds max length"
            }
        }
    }

    val hasOcrText: Boolean get() = !originalText.isNullOrBlank()
    val hasTranslation: Boolean get() = !translatedText.isNullOrBlank()
    val hasThumbnail: Boolean get() = !thumbnailPath.isNullOrBlank()
    
    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height else 1f
    
    val isPortrait: Boolean get() = height > width
    val isLandscape: Boolean get() = width > height
    val isSquare: Boolean get() = width == height

    /**
     * Полный путь для UI (Folder > Record)
     * Возвращает null если денормализованные данные отсутствуют
     */
    val fullPath: String?
        get() = when {
            folderName != null && recordName != null -> "$folderName > $recordName"
            recordName != null -> recordName
            else -> null
        }
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                         PROCESSING STATUS                                     ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Статус обработки документа
 */
@Serializable
enum class ProcessingStatus {
    /** Начальное состояние, обработка не начата */
    INITIAL,
    
    /** OCR в процессе выполнения */
    OCR_IN_PROGRESS,
    
    /** OCR успешно завершен */
    OCR_COMPLETE,
    
    /** OCR завершился с ошибкой */
    OCR_FAILED,
    
    /** Перевод в процессе выполнения */
    TRANSLATION_IN_PROGRESS,
    
    /** Перевод завершился с ошибкой */
    TRANSLATION_FAILED,
    
    /** Вся обработка завершена успешно */
    COMPLETE,
    
    /** Обработка отменена пользователем */
    CANCELLED,
    
    /** Документ в очереди на обработку */
    QUEUED,
    
    /** Повторная попытка после ошибки */
    RETRYING,
    
    /** Критическая ошибка */
    ERROR;

    val isError: Boolean
        get() = this in listOf(ERROR, OCR_FAILED, TRANSLATION_FAILED)

    val isInProgress: Boolean
        get() = this in listOf(
            OCR_IN_PROGRESS, 
            TRANSLATION_IN_PROGRESS, 
            QUEUED, 
            RETRYING
        )
    
    val isComplete: Boolean get() = this == COMPLETE
    val canRetry: Boolean get() = isError || this == CANCELLED
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                          DOMAIN RESULT                                        ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Стандартный Result pattern для Domain Layer
 * Не содержит Loading состояние (это UI concern)
 */
sealed class DomainResult<out T> {
    data class Success<T>(val data: T) : DomainResult<T>()
    data class Error(val error: DomainError) : DomainResult<Nothing>()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    
    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): DomainError? = (this as? Error)?.error
    
    inline fun onSuccess(action: (T) -> Unit): DomainResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (DomainError) -> Unit): DomainResult<T> {
        if (this is Error) action(error)
        return this
    }
    
    inline fun <R> map(transform: (T) -> R): DomainResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }
    
    suspend inline fun <R> flatMap(
        transform: suspend (T) -> DomainResult<R>
    ): DomainResult<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
    }

    companion object {
        fun <T> success(data: T): DomainResult<T> = Success(data)
        fun <T> error(error: DomainError): DomainResult<T> = Error(error)
    }
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                          DOMAIN ERRORS                                        ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Domain-специфичные ошибки с детальной информацией
 */
sealed class DomainError : Throwable() {
    
    // ═══════════════════════ VALIDATION ERRORS ═══════════════════════
    
    data class InvalidFolderName(val name: String, val reason: String) : DomainError() {
        override val message: String = "Invalid folder name '$name': $reason"
    }
    
    data class InvalidRecordName(val name: String, val reason: String) : DomainError() {
        override val message: String = "Invalid record name '$name': $reason"
    }
    
    data class InvalidTag(val tag: String, val reason: String) : DomainError() {
        override val message: String = "Invalid tag '$tag': $reason"
    }
    
    data class TooManyTags(val count: Int, val max: Int) : DomainError() {
        override val message: String = "Too many tags: $count exceeds maximum of $max"
    }
    
    data class FileSizeTooLarge(val size: Long, val max: Long) : DomainError() {
        override val message: String = "File size $size exceeds maximum of $max bytes"
    }
    
    // ═══════════════════════ BUSINESS LOGIC ERRORS ═══════════════════════
    
    data class FolderNotFound(val id: Long) : DomainError() {
        override val message: String = "Folder with ID $id not found"
    }
    
    data class RecordNotFound(val id: Long) : DomainError() {
        override val message: String = "Record with ID $id not found"
    }
    
    data class DocumentNotFound(val id: Long) : DomainError() {
        override val message: String = "Document with ID $id not found"
    }
    
    data class CannotDeleteNonEmptyFolder(
        val folderId: Long, 
        val recordCount: Int
    ) : DomainError() {
        override val message: String = 
            "Cannot delete folder $folderId: contains $recordCount records"
    }
    
    data class CannotDeleteSystemFolder(val folderId: Long) : DomainError() {
        override val message: String = "Cannot delete system folder $folderId"
    }
    
    data class CannotModifyQuickScansFolder(val operation: String) : DomainError() {
        override val message: String = 
            "Cannot $operation Quick Scans folder: it's a system folder"
    }
    
    // ═══════════════════════ PROCESSING ERRORS ═══════════════════════
    
    data class OcrFailed(
        val documentId: Long, 
        override val cause: Throwable?
    ) : DomainError() {
        override val message: String = "OCR failed for document $documentId: ${cause?.message}"
    }
    
    data class TranslationFailed(
        val documentId: Long,
        val fromLang: Language,
        val toLang: Language,
        val reason: String
    ) : DomainError() {
        override val message: String = 
            "Translation failed for document $documentId from $fromLang to $toLang: $reason"
    }
    
    data class UnsupportedLanguagePair(
        val source: Language,
        val target: Language
    ) : DomainError() {
        override val message: String = 
            "Translation from $source to $target is not supported"
    }
    
    // ═══════════════════════ INFRASTRUCTURE ERRORS ═══════════════════════
    
    data class StorageError(override val cause: Throwable?) : DomainError() {
        override val message: String = "Storage operation failed: ${cause?.message}"
    }
    
    data class NetworkError(override val cause: Throwable?) : DomainError() {
        override val message: String = "Network operation failed: ${cause?.message}"
    }
    
    data class FileSystemError(val path: String, override val cause: Throwable?) : DomainError() {
        override val message: String = "File system error at '$path': ${cause?.message}"
    }
    
    data class PermissionDenied(val permission: String) : DomainError() {
        override val message: String = "Permission denied: $permission"
    }
    
    // ═══════════════════════ GENERIC ERRORS ═══════════════════════
    
    data class Unknown(override val cause: Throwable?) : DomainError() {
        override val message: String = "Unknown error: ${cause?.message}"
    }
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                          TIME PROVIDER                                        ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Абстракция для получения текущего времени
 * Позволяет тестировать код с фиксированным временем
 */
interface TimeProvider {
    /**
     * Получить текущее время в миллисекундах с эпохи Unix
     */
    fun currentMillis(): Long
    
    /**
     * Получить текущее время как Instant
     */
    fun currentInstant(): Instant = Instant.ofEpochMilli(currentMillis())
}