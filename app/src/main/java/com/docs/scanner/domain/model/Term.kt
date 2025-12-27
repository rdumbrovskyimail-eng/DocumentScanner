package com.docs.scanner.domain.model

import java.text.SimpleDateFormat
import java.util.*

/**
 * Domain model for Term/Deadline.
 * 
 * 🔴 CRITICAL SESSION 5 FIX:
 * This model was MISSING - ViewModels used TermEntity directly!
 * 
 * Clean Architecture:
 * - Domain model (this) → Used in ViewModels, Use Cases
 * - Data model (TermEntity) → Used in DAOs, Room database
 * - Mapper converts between them
 * 
 * Benefits:
 * - No Room annotations in domain layer
 * - Can add business logic (validation, computed properties)
 * - Easy to unit test (no Android dependencies)
 * - Repository can change implementation without affecting domain
 */
data class Term(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val dueDate: Long,
    val reminderMinutesBefore: Int = 0,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    
    /**
     * Check if term is overdue.
     * 
     * @param currentTime Current timestamp (default: now)
     * @return true if past due date and not completed
     */
    fun isOverdue(currentTime: Long = System.currentTimeMillis()): Boolean {
        return !isCompleted && dueDate < currentTime
    }
    
    /**
     * Check if reminder should be shown.
     * 
     * @param currentTime Current timestamp (default: now)
     * @return true if reminder time reached but not yet due
     */
    fun needsReminder(currentTime: Long = System.currentTimeMillis()): Boolean {
        if (isCompleted || reminderMinutesBefore <= 0) return false
        
        val reminderTime = dueDate - (reminderMinutesBefore * 60 * 1000L)
        return currentTime >= reminderTime && currentTime < dueDate
    }
    
    /**
     * Get time until due (in milliseconds).
     * 
     * @param currentTime Current timestamp (default: now)
     * @return Milliseconds until due (negative if overdue)
     */
    fun timeUntilDue(currentTime: Long = System.currentTimeMillis()): Long {
        return dueDate - currentTime
    }
    
    /**
     * Get time until due in human-readable format.
     * 
     * Examples:
     * - "2 hours"
     * - "3 days"
     * - "Overdue by 1 day"
     */
    fun timeUntilDueFormatted(currentTime: Long = System.currentTimeMillis()): String {
        val diff = timeUntilDue(currentTime)
        val absDiff = kotlin.math.abs(diff)
        
        val prefix = if (diff < 0) "Overdue by " else ""
        
        return when {
            absDiff < 60 * 1000 -> "${prefix}Less than a minute"
            absDiff < 60 * 60 * 1000 -> {
                val minutes = (absDiff / (60 * 1000)).toInt()
                "$prefix$minutes ${if (minutes == 1) "minute" else "minutes"}"
            }
            absDiff < 24 * 60 * 60 * 1000 -> {
                val hours = (absDiff / (60 * 60 * 1000)).toInt()
                "$prefix$hours ${if (hours == 1) "hour" else "hours"}"
            }
            else -> {
                val days = (absDiff / (24 * 60 * 60 * 1000)).toInt()
                "$prefix$days ${if (days == 1) "day" else "days"}"
            }
        }
    }
    
    /**
     * Format due date for display.
     * 
     * @param pattern Date format pattern (default: "MMM dd, yyyy HH:mm")
     * @return Formatted date string
     */
    fun formatDueDate(pattern: String = "MMM dd, yyyy HH:mm"): String {
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(dueDate))
    }
    
    /**
     * Validate term data.
     * 
     * @return Validation result with error message if invalid
     */
    fun validate(): ValidationResult {
        return when {
            title.isBlank() -> ValidationResult.Error("Title cannot be empty")
            title.length > MAX_TITLE_LENGTH -> ValidationResult.Error("Title too long (max $MAX_TITLE_LENGTH)")
            description != null && description.length > MAX_DESCRIPTION_LENGTH -> 
                ValidationResult.Error("Description too long (max $MAX_DESCRIPTION_LENGTH)")
            dueDate <= 0 -> ValidationResult.Error("Invalid due date")
            reminderMinutesBefore < 0 -> ValidationResult.Error("Invalid reminder time")
            else -> ValidationResult.Valid
        }
    }
    
    companion object {
        const val MAX_TITLE_LENGTH = 100
        const val MAX_DESCRIPTION_LENGTH = 500
        
        /**
         * Create term with validation.
         * Throws IllegalArgumentException if invalid.
         */
        fun create(
            title: String,
            description: String? = null,
            dueDate: Long,
            reminderMinutesBefore: Int = 0
        ): Term {
            val term = Term(
                title = title.trim(),
                description = description?.trim(),
                dueDate = dueDate,
                reminderMinutesBefore = reminderMinutesBefore
            )
            
            when (val result = term.validate()) {
                is ValidationResult.Error -> throw IllegalArgumentException(result.message)
                ValidationResult.Valid -> return term
            }
        }
    }
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}