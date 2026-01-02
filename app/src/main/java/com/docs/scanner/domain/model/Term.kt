/*
 * DocumentScanner - Term Domain Model
 * Clean Architecture Domain Layer - Pure Kotlin, Framework Independent
 *
 * Версия: 3.2.0 - Production Ready
 * 
 * Term представляет срок/дедлайн с напоминанием.
 * Используется для отслеживания важных дат документов.
 */

package com.docs.scanner.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                              CONSTANTS                                        ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Константы для Term
 */
object TermConstants {
    /** Максимальная длина заголовка */
    const val TITLE_MAX_LENGTH = 200
    
    /** Максимальная длина описания */
    const val DESCRIPTION_MAX_LENGTH = 1000
    
    /** Максимальное время напоминания заранее (в минутах) - 30 дней */
    const val MAX_REMINDER_MINUTES = 43200
    
    /** Минимальное время до дедлайна для создания (в минутах) - 1 минута */
    const val MIN_DUE_DATE_OFFSET_MINUTES = 1
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                           VALUE OBJECTS                                       ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Типобезопасная обертка для Term ID
 */
@JvmInline
@Serializable
value class TermId(val value: Long) {
    init {
        require(value > 0) { "Term ID must be positive, got: $value" }
    }
}

/**
 * Валидированный заголовок Term
 */
@JvmInline
@Serializable
value class TermTitle(val value: String) {
    init {
        require(value.isNotBlank()) { "Term title cannot be blank" }
        require(value.length <= TermConstants.TITLE_MAX_LENGTH) {
            "Term title too long: ${value.length} > ${TermConstants.TITLE_MAX_LENGTH}"
        }
    }
}

/**
 * Время напоминания (за сколько минут до дедлайна)
 */
@JvmInline
@Serializable
value class ReminderOffset(val minutes: Int) {
    init {
        require(minutes >= 0) { "Reminder offset cannot be negative" }
        require(minutes <= TermConstants.MAX_REMINDER_MINUTES) {
            "Reminder offset too large: $minutes > ${TermConstants.MAX_REMINDER_MINUTES}"
        }
    }
    
    /** Напоминание отключено */
    val isDisabled: Boolean get() = minutes == 0
    
    /** Получить offset в миллисекундах */
    val asMillis: Long get() = minutes * 60 * 1000L
    
    companion object {
        /** Без напоминания */
        val NONE = ReminderOffset(0)
        
        /** За 15 минут */
        val MINUTES_15 = ReminderOffset(15)
        
        /** За 30 минут */
        val MINUTES_30 = ReminderOffset(30)
        
        /** За 1 час */
        val HOUR_1 = ReminderOffset(60)
        
        /** За 2 часа */
        val HOURS_2 = ReminderOffset(120)
        
        /** За 1 день */
        val DAY_1 = ReminderOffset(1440)
        
        /** За 2 дня */
        val DAYS_2 = ReminderOffset(2880)
        
        /** За 1 неделю */
        val WEEK_1 = ReminderOffset(10080)
        
        /** Все доступные пресеты */
        val PRESETS = listOf(
            NONE, MINUTES_15, MINUTES_30, HOUR_1, 
            HOURS_2, DAY_1, DAYS_2, WEEK_1
        )
    }
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                              TERM PRIORITY                                    ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Приоритет срока
 */
@Serializable
enum class TermPriority(val value: Int) {
    /** Низкий приоритет */
    LOW(0),
    
    /** Нормальный приоритет (по умолчанию) */
    NORMAL(1),
    
    /** Высокий приоритет */
    HIGH(2),
    
    /** Критический приоритет */
    CRITICAL(3);
    
    companion object {
        fun fromValue(value: Int): TermPriority =
            entries.find { it.value == value } ?: NORMAL
    }
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                              TERM STATUS                                      ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Статус срока (вычисляется динамически)
 */
@Serializable
enum class TermStatus {
    /** Активный, время ещё есть */
    PENDING,
    
    /** Скоро истекает (в пределах напоминания) */
    UPCOMING,
    
    /** Срок сегодня */
    DUE_TODAY,
    
    /** Срок истёк, не выполнено */
    OVERDUE,
    
    /** Выполнено */
    COMPLETED,
    
    /** Отменено */
    CANCELLED;
    
    /** Требует внимания (показывать в уведомлениях) */
    val requiresAttention: Boolean
        get() = this in listOf(UPCOMING, DUE_TODAY, OVERDUE)
    
    /** Активный (не завершён и не отменён) */
    val isActive: Boolean
        get() = this !in listOf(COMPLETED, CANCELLED)
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                              TERM ENTITY                                      ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Domain модель для срока/дедлайна.
 * 
 * Представляет напоминание о важной дате, связанной с документами.
 * Может быть привязан к конкретному документу или папке.
 *
 * @property id Уникальный идентификатор. 0 для новых записей
 * @property title Заголовок срока (валидировано, 1-200 символов)
 * @property description Опциональное описание (до 1000 символов)
 * @property dueDate Timestamp дедлайна в миллисекундах
 * @property reminderMinutesBefore За сколько минут напоминать (0 = отключено)
 * @property priority Приоритет срока
 * @property isCompleted Выполнен ли срок
 * @property isCancelled Отменён ли срок
 * @property completedAt Timestamp выполнения (null если не выполнен)
 * @property documentId Привязка к документу (опционально)
 * @property folderId Привязка к папке (опционально)
 * @property color Цвет для UI (опционально)
 * @property createdAt Timestamp создания
 * @property updatedAt Timestamp последнего обновления
 */
@Serializable
data class Term(
    val id: Long = 0L,
    val title: String,
    val description: String? = null,
    val dueDate: Long,
    val reminderMinutesBefore: Int = 0,
    val priority: TermPriority = TermPriority.NORMAL,
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val completedAt: Long? = null,
    val documentId: Long? = null,
    val folderId: Long? = null,
    val color: Int? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    init {
        require(title.isNotBlank()) { "Term title cannot be blank" }
        require(title.length <= TermConstants.TITLE_MAX_LENGTH) {
            "Term title too long: ${title.length} > ${TermConstants.TITLE_MAX_LENGTH}"
        }
        require(dueDate > 0) { "Due date must be positive timestamp" }
        require(reminderMinutesBefore >= 0) { "Reminder minutes cannot be negative" }
        require(reminderMinutesBefore <= TermConstants.MAX_REMINDER_MINUTES) {
            "Reminder minutes too large: $reminderMinutesBefore"
        }
        description?.let {
            require(it.length <= TermConstants.DESCRIPTION_MAX_LENGTH) {
                "Description too long: ${it.length} > ${TermConstants.DESCRIPTION_MAX_LENGTH}"
            }
        }
        // Если выполнен, должен быть timestamp завершения
        if (isCompleted) {
            require(completedAt != null) { "Completed term must have completedAt timestamp" }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COMPUTED PROPERTIES (используют текущее время)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Вычислить статус относительно текущего времени
     */
    fun computeStatus(currentTimeMillis: Long = System.currentTimeMillis()): TermStatus {
        return when {
            isCancelled -> TermStatus.CANCELLED
            isCompleted -> TermStatus.COMPLETED
            dueDate < currentTimeMillis -> TermStatus.OVERDUE
            isDueToday(currentTimeMillis) -> TermStatus.DUE_TODAY
            isInReminderWindow(currentTimeMillis) -> TermStatus.UPCOMING
            else -> TermStatus.PENDING
        }
    }

    /**
     * Просрочен ли срок
     */
    fun isOverdue(currentTimeMillis: Long = System.currentTimeMillis()): Boolean =
        !isCompleted && !isCancelled && dueDate < currentTimeMillis

    /**
     * Срок сегодня
     */
    fun isDueToday(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        if (isCompleted || isCancelled) return false
        
        val nowDate = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(currentTimeMillis),
            ZoneId.systemDefault()
        ).toLocalDate()
        
        val dueDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(dueDate),
            ZoneId.systemDefault()
        ).toLocalDate()
        
        return nowDate == dueDateTime
    }

    /**
     * Находится ли в окне напоминания
     */
    fun isInReminderWindow(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        if (isCompleted || isCancelled || reminderMinutesBefore <= 0) return false
        
        val reminderTime = dueDate - (reminderMinutesBefore * 60 * 1000L)
        return currentTimeMillis >= reminderTime && currentTimeMillis < dueDate
    }

    /**
     * Нужно ли показать напоминание
     */
    fun shouldShowReminder(currentTimeMillis: Long = System.currentTimeMillis()): Boolean =
        isInReminderWindow(currentTimeMillis)

    /**
     * Время напоминания (когда показать уведомление)
     */
    val reminderTime: Long
        get() = if (reminderMinutesBefore > 0) {
            dueDate - (reminderMinutesBefore * 60 * 1000L)
        } else {
            dueDate
        }

    /**
     * Напоминание включено
     */
    val hasReminder: Boolean
        get() = reminderMinutesBefore > 0

    /**
     * Привязан к документу
     */
    val hasDocumentLink: Boolean
        get() = documentId != null

    /**
     * Привязан к папке
     */
    val hasFolderLink: Boolean
        get() = folderId != null

    // ══════════════════════════════════════════════════════════════════════════
    // TIME CALCULATIONS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Дней до срока (отрицательное если просрочен)
     */
    fun daysUntilDue(currentTimeMillis: Long = System.currentTimeMillis()): Long {
        val now = Instant.ofEpochMilli(currentTimeMillis)
        val due = Instant.ofEpochMilli(dueDate)
        return ChronoUnit.DAYS.between(now, due)
    }

    /**
     * Часов до срока
     */
    fun hoursUntilDue(currentTimeMillis: Long = System.currentTimeMillis()): Long {
        val now = Instant.ofEpochMilli(currentTimeMillis)
        val due = Instant.ofEpochMilli(dueDate)
        return ChronoUnit.HOURS.between(now, due)
    }

    /**
     * Минут до срока
     */
    fun minutesUntilDue(currentTimeMillis: Long = System.currentTimeMillis()): Long {
        val now = Instant.ofEpochMilli(currentTimeMillis)
        val due = Instant.ofEpochMilli(dueDate)
        return ChronoUnit.MINUTES.between(now, due)
    }

    /**
     * Получить время до срока как Duration-like объект
     */
    fun timeUntilDue(currentTimeMillis: Long = System.currentTimeMillis()): TimeRemaining {
        val totalMinutes = minutesUntilDue(currentTimeMillis)
        return TimeRemaining.fromMinutes(totalMinutes)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // COPY HELPERS (используют TimeProvider для тестируемости)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Отметить как выполненный
     */
    fun markCompleted(timestamp: Long): Term = copy(
        isCompleted = true,
        isCancelled = false,
        completedAt = timestamp,
        updatedAt = timestamp
    )

    /**
     * Отметить как не выполненный
     */
    fun markNotCompleted(timestamp: Long): Term = copy(
        isCompleted = false,
        completedAt = null,
        updatedAt = timestamp
    )

    /**
     * Отменить срок
     */
    fun cancel(timestamp: Long): Term = copy(
        isCancelled = true,
        isCompleted = false,
        completedAt = null,
        updatedAt = timestamp
    )

    /**
     * Восстановить отменённый срок
     */
    fun restore(timestamp: Long): Term = copy(
        isCancelled = false,
        updatedAt = timestamp
    )

    /**
     * Обновить время напоминания
     */
    fun withReminder(minutes: Int, timestamp: Long): Term = copy(
        reminderMinutesBefore = minutes,
        updatedAt = timestamp
    )

    /**
     * Обновить приоритет
     */
    fun withPriority(priority: TermPriority, timestamp: Long): Term = copy(
        priority = priority,
        updatedAt = timestamp
    )

    /**
     * Перенести срок
     */
    fun reschedule(newDueDate: Long, timestamp: Long): Term = copy(
        dueDate = newDueDate,
        isCompleted = false,
        completedAt = null,
        updatedAt = timestamp
    )

    /**
     * Привязать к документу
     */
    fun linkToDocument(docId: Long, timestamp: Long): Term = copy(
        documentId = docId,
        updatedAt = timestamp
    )

    /**
     * Привязать к папке
     */
    fun linkToFolder(folderId: Long, timestamp: Long): Term = copy(
        folderId = folderId,
        updatedAt = timestamp
    )

    companion object {
        /**
         * Создать новый срок
         */
        fun create(
            title: String,
            dueDate: Long,
            description: String? = null,
            reminderMinutesBefore: Int = ReminderOffset.HOUR_1.minutes,
            priority: TermPriority = TermPriority.NORMAL,
            documentId: Long? = null,
            folderId: Long? = null,
            color: Int? = null,
            timestamp: Long
        ): Term = Term(
            title = title,
            description = description,
            dueDate = dueDate,
            reminderMinutesBefore = reminderMinutesBefore,
            priority = priority,
            documentId = documentId,
            folderId = folderId,
            color = color,
            createdAt = timestamp,
            updatedAt = timestamp
        )

        /**
         * Создать срок на завтра
         */
        fun createForTomorrow(
            title: String,
            hour: Int = 9,
            minute: Int = 0,
            description: String? = null,
            reminderMinutesBefore: Int = ReminderOffset.HOUR_1.minutes,
            timestamp: Long
        ): Term {
            val tomorrow = LocalDate.now().plusDays(1)
            val dueDateTime = tomorrow.atTime(hour, minute)
            val dueMillis = dueDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            return create(
                title = title,
                dueDate = dueMillis,
                description = description,
                reminderMinutesBefore = reminderMinutesBefore,
                timestamp = timestamp
            )
        }

        /**
         * Создать срок через N дней
         */
        fun createInDays(
            title: String,
            days: Int,
            hour: Int = 9,
            minute: Int = 0,
            description: String? = null,
            reminderMinutesBefore: Int = ReminderOffset.DAY_1.minutes,
            timestamp: Long
        ): Term {
            val targetDate = LocalDate.now().plusDays(days.toLong())
            val dueDateTime = targetDate.atTime(hour, minute)
            val dueMillis = dueDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            return create(
                title = title,
                dueDate = dueMillis,
                description = description,
                reminderMinutesBefore = reminderMinutesBefore,
                timestamp = timestamp
            )
        }
    }
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                           TIME REMAINING                                      ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Представление оставшегося времени
 */
@Serializable
data class TimeRemaining(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val totalMinutes: Long,
    val isOverdue: Boolean
) {
    /**
     * Полное количество часов (без учёта дней)
     */
    val totalHours: Long get() = totalMinutes / 60

    /**
     * Время истекло
     */
    val isExpired: Boolean get() = isOverdue

    /**
     * Меньше часа осталось
     */
    val isUrgent: Boolean get() = !isOverdue && totalMinutes in 1..59

    /**
     * Меньше дня осталось
     */
    val isDueSoon: Boolean get() = !isOverdue && totalMinutes in 1..1440

    companion object {
        /**
         * Создать из общего количества минут
         */
        fun fromMinutes(totalMinutes: Long): TimeRemaining {
            val isOverdue = totalMinutes < 0
            val absMinutes = kotlin.math.abs(totalMinutes)
            
            val days = absMinutes / (24 * 60)
            val remainingAfterDays = absMinutes % (24 * 60)
            val hours = remainingAfterDays / 60
            val minutes = remainingAfterDays % 60
            
            return TimeRemaining(
                days = days,
                hours = hours,
                minutes = minutes,
                totalMinutes = totalMinutes,
                isOverdue = isOverdue
            )
        }

        /**
         * Уже просрочено
         */
        val OVERDUE = TimeRemaining(0, 0, 0, -1, true)

        /**
         * Прямо сейчас
         */
        val NOW = TimeRemaining(0, 0, 0, 0, false)
    }
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                           TERM ERRORS                                         ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Ошибки специфичные для Term
 */
sealed class TermError : DomainError() {
    
    data class InvalidTitle(val title: String, val reason: String) : TermError() {
        override val message: String = "Invalid term title '$title': $reason"
    }
    
    data class InvalidDueDate(val dueDate: Long, val reason: String) : TermError() {
        override val message: String = "Invalid due date $dueDate: $reason"
    }
    
    data class DueDateInPast(val dueDate: Long) : TermError() {
        override val message: String = "Due date $dueDate is in the past"
    }
    
    data class TermNotFound(val id: Long) : TermError() {
        override val message: String = "Term with ID $id not found"
    }
    
    data class CannotModifyCompletedTerm(val id: Long) : TermError() {
        override val message: String = "Cannot modify completed term $id"
    }
    
    data class CannotModifyCancelledTerm(val id: Long) : TermError() {
        override val message: String = "Cannot modify cancelled term $id"
    }
    
    data class ReminderTooFarInAdvance(val minutes: Int, val max: Int) : TermError() {
        override val message: String = "Reminder $minutes minutes is too far in advance (max: $max)"
    }
}

// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║                         EXTENSION FUNCTIONS                                   ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

/**
 * Отсортировать список Term по срочности
 */
fun List<Term>.sortedByUrgency(): List<Term> = this.sortedWith(
    compareBy<Term> { it.isCompleted }
        .thenBy { it.isCancelled }
        .thenByDescending { it.priority.value }
        .thenBy { it.dueDate }
)

/**
 * Получить только активные (не выполненные и не отменённые)
 */
fun List<Term>.activeOnly(): List<Term> = this.filter { !it.isCompleted && !it.isCancelled }

/**
 * Получить просроченные
 */
fun List<Term>.overdueOnly(currentTimeMillis: Long = System.currentTimeMillis()): List<Term> =
    this.filter { it.isOverdue(currentTimeMillis) }

/**
 * Получить срочные (сегодня или в окне напоминания)
 */
fun List<Term>.urgentOnly(currentTimeMillis: Long = System.currentTimeMillis()): List<Term> =
    this.filter { 
        val status = it.computeStatus(currentTimeMillis)
        status in listOf(TermStatus.DUE_TODAY, TermStatus.UPCOMING, TermStatus.OVERDUE)
    }

/**
 * Сгруппировать по статусу
 */
fun List<Term>.groupByStatus(
    currentTimeMillis: Long = System.currentTimeMillis()
): Map<TermStatus, List<Term>> = this.groupBy { it.computeStatus(currentTimeMillis) }