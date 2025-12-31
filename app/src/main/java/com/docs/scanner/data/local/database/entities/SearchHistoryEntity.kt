package com.docs.scanner.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for search history.
 * 
 * Stores recent search queries for:
 * - Quick access to previous searches
 * - Search suggestions
 * - Analytics (optional)
 * 
 * ## Cleanup Strategy
 * - Keep max 50 entries
 * - Delete entries older than 30 days
 * - Deduplicate on insert (update timestamp if exists)
 */
@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["query"], unique = true)
    ]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "query")
    val query: String,
    
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "result_count")
    val resultCount: Int = 0
) {
    companion object {
        const val MAX_HISTORY_ENTRIES = 50
        const val MAX_AGE_DAYS = 30
        
        /**
         * Calculate expiry timestamp.
         */
        fun getExpiryTimestamp(): Long {
            return System.currentTimeMillis() - (MAX_AGE_DAYS * 24 * 60 * 60 * 1000L)
        }
    }
}
