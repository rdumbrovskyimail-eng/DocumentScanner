package com.docs.scanner.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.docs.scanner.domain.model.Term

/**
 * Room entity for Term (deadline reminder).
 * 
 * ## Indices
 * - `due_date`: For efficient sorting by deadline
 * - `is_completed`: For filtering completed/upcoming
 * - `is_completed, due_date`: Composite index for common queries
 * 
 * ## Fields
 * - `completed_at`: Timestamp when term was marked complete (null if not completed)
 * 
 * ## Version History
 * - v1: Initial schema (title, description, dueDate, reminder, isCompleted)
 * - v2: Added completed_at field
 * - v3: Added indices for performance
 */
@Entity(
    tableName = "terms",
    indices = [
        Index(value = ["due_date"]),
        Index(value = ["is_completed"]),
        Index(value = ["is_completed", "due_date"])
    ]
)
data class TermEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "due_date")
    val dueDate: Long,
    
    @ColumnInfo(name = "reminder_minutes_before")
    val reminderMinutesBefore: Int = 0,
    
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Convert to domain model.
     */
    fun toDomain(): Term = Term(
        id = id,
        title = title,
        description = description,
        dueDate = dueDate,
        reminderMinutesBefore = reminderMinutesBefore,
        isCompleted = isCompleted,
        completedAt = completedAt,
        createdAt = createdAt
    )

    companion object {
        /**
         * Create entity from domain model.
         */
        fun fromDomain(term: Term): TermEntity = TermEntity(
            id = term.id,
            title = term.title,
            description = term.description,
            dueDate = term.dueDate,
            reminderMinutesBefore = term.reminderMinutesBefore,
            isCompleted = term.isCompleted,
            completedAt = term.completedAt,
            createdAt = term.createdAt
        )
    }
}