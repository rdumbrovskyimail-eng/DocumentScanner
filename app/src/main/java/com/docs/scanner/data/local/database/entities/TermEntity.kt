package com.docs.scanner.data.local.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "terms")
data class TermEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val dueDate: Long,  // ✅ ИЗМЕНЕНО: было dateTime, стало dueDate
    val reminderMinutesBefore: Int = 0,  // ✅ ИЗМЕНЕНО: было nullable, стало NOT NULL с default
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
