package com.docs.scanner.domain.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Domain model for Term (deadline/reminder).
 * 
 * Used in:
 * - Use Cases (domain layer)
 * - ViewModels (presentation layer)
 * 
 * Mapped from/to TermEntity in data layer.
 */
data class Term(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val dueDate: Long,
    val reminderMinutesBefore: Int = 0,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(title.isNotBlank()) { "Term title cannot be blank" }
        require(title.length <= MAX_TITLE_LENGTH) { 
            "Term title too long (max $MAX_TITLE_LENGTH characters)" 
        }
        require(dueDate > 0) { "Invalid due date" }
        require(reminderMinutesBefore >= 0) { "Reminder minutes cannot be negative" }
    }

    // ══════════════════════════════════════════════════════════════
    // COMPUTED PROPERTIES
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Check if term is overdue.
     */
    val isOverdue: Boolean
        get() = !isCompleted && dueDate < System.currentTimeMillis()
    
    /**
     * Check if term is due today.
     */
    val isDueToday: Boolean
        get() {
            val now = LocalDateTime.now()
            val dueDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(dueDate),
                ZoneId.systemDefault()
            )
            return now.toLocalDate() == dueDateTime.toLocalDate()
        }
    
    /**
     * Check if reminder should be shown.
     */
    val shouldShowReminder: Boolean
        get() {
            if (isCompleted || reminderMinutesBefore <= 0) return false
            val reminderTime = dueDate - (reminderMinutesBefore * 60 * 1000L)
            val now = System.currentTimeMillis()
            return now >= reminderTime && now < dueDate
        }
    
    /**
     * Get days until due date.
     * Negative if overdue.
     */
    val daysUntilDue: Long
        get() {
            val now = Instant.now()
            val due = Instant.ofEpochMilli(dueDate)
            return ChronoUnit.DAYS.between(now, due)
        }
    
    /**
     * Get hours until due date.
     */
    val hoursUntilDue: Long
        get() {
            val now = Instant.now()
            val due = Instant.ofEpochMilli(dueDate)
            return ChronoUnit.HOURS.between(now, due)
        }
    
    /**
     * Get human-readable time until due.
     * Examples: "2 days", "5 hours", "30 minutes", "Overdue"
     */
    val timeUntilDueFormatted: String
        get() {
            if (isOverdue) return "Overdue"
            
            val days = daysUntilDue
            if (days > 0) return "$days day${if (days > 1) "s" else ""}"
            
            val hours = hoursUntilDue
            if (hours > 0) return "$hours hour${if (hours > 1) "s" else ""}"
            
            val minutes = ChronoUnit.MINUTES.between(
                Instant.now(),
                Instant.ofEpochMilli(dueDate)
            )
            return if (minutes > 0) {
                "$minutes minute${if (minutes > 1) "s" else ""}"
            } else {
                "Due now"
            }
        }

    // ══════════════════════════════════════════════════════════════
    // FORMATTING
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Format due date for display.
     */
    fun formatDueDate(pattern: String = "MMM dd, yyyy HH:mm"): String {
        val formatter = DateTimeFormatter.ofPattern(pattern)
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(dueDate),
            ZoneId.systemDefault()
        )
        return dateTime.format(formatter)
    }
    
    /**
     * Format completed date for display.
     */
    fun formatCompletedAt(pattern: String = "MMM dd, yyyy HH:mm"): String? {
        return completedAt?.let { timestamp ->
            val formatter = DateTimeFormatter.ofPattern(pattern)
            val dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
            )
            dateTime.format(formatter)
        }
    }

    // ══════════════════════════════════════════════════════════════
    // COPY HELPERS
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Mark as completed with current timestamp.
     */
    fun markCompleted(): Term = copy(
        isCompleted = true,
        completedAt = System.currentTimeMillis()
    )
    
    /**
     * Mark as not completed.
     */
    fun markNotCompleted(): Term = copy(
        isCompleted = false,
        completedAt = null
    )
    
    /**
     * Update reminder time.
     */
    fun withReminder(minutesBefore: Int): Term = copy(
        reminderMinutesBefore = minutesBefore
    )

    companion object {
        const val MAX_TITLE_LENGTH = 200
        const val MAX_DESCRIPTION_LENGTH = 1000
        
        // Common reminder presets (in minutes)
        val REMINDER_PRESETS = listOf(
            0,      // No reminder
            15,     // 15 minutes
            30,     // 30 minutes
            60,     // 1 hour
            120,    // 2 hours
            1440,   // 1 day
            2880,   // 2 days
            10080   // 1 week
        )
        
        /**
         * Create a new term with default values.
         */
        fun create(
            title: String,
            dueDate: Long,
            description: String? = null,
            reminderMinutesBefore: Int = 60
        ): Term = Term(
            title = title,
            description = description,
            dueDate = dueDate,
            reminderMinutesBefore = reminderMinutesBefore
        )
    }
}
