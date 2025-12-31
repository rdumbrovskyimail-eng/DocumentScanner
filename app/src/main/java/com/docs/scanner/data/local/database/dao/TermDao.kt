package com.docs.scanner.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.docs.scanner.data.local.database.entities.TermEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Term entity.
 * 
 * Provides all database operations for deadline reminders.
 * All methods that return Flow are reactive and will emit updates.
 */
@Dao
interface TermDao {

    // ══════════════════════════════════════════════════════════════
    // INSERT OPERATIONS
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Insert a new term.
     * @return ID of the inserted term
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(term: TermEntity): Long

    /**
     * Insert multiple terms.
     * @return List of inserted IDs
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(terms: List<TermEntity>): List<Long>

    // ══════════════════════════════════════════════════════════════
    // UPDATE OPERATIONS
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Update an existing term.
     */
    @Update
    suspend fun update(term: TermEntity)

    /**
     * Mark term as completed or uncompleted.
     * @param termId Term ID
     * @param completed true = completed, false = uncompleted
     * @param completedAt Timestamp when completed (null if uncompleted)
     */
    @Query("""
        UPDATE terms 
        SET is_completed = :completed, 
            completed_at = :completedAt 
        WHERE id = :termId
    """)
    suspend fun markCompleted(termId: Long, completed: Boolean, completedAt: Long?)

    // ══════════════════════════════════════════════════════════════
    // DELETE OPERATIONS
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Delete a term.
     */
    @Delete
    suspend fun delete(term: TermEntity)

    /**
     * Delete term by ID.
     */
    @Query("DELETE FROM terms WHERE id = :termId")
    suspend fun deleteById(termId: Long)

    /**
     * Delete all completed terms.
     */
    @Query("DELETE FROM terms WHERE is_completed = 1")
    suspend fun deleteAllCompleted()

    /**
     * Delete all terms.
     */
    @Query("DELETE FROM terms")
    suspend fun deleteAll()

    // ══════════════════════════════════════════════════════════════
    // SELECT - Single Item
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Get term by ID (one-shot).
     */
    @Query("SELECT * FROM terms WHERE id = :termId")
    suspend fun getById(termId: Long): TermEntity?

    /**
     * Get term by ID (reactive Flow).
     */
    @Query("SELECT * FROM terms WHERE id = :termId")
    fun getByIdFlow(termId: Long): Flow<TermEntity?>

    // ══════════════════════════════════════════════════════════════
    // SELECT - Lists (Reactive Flow)
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Get all terms ordered by due date.
     */
    @Query("SELECT * FROM terms ORDER BY due_date ASC")
    fun getAllTerms(): Flow<List<TermEntity>>

    /**
     * Get upcoming (not completed) terms.
     */
    @Query("""
        SELECT * FROM terms 
        WHERE is_completed = 0 
        ORDER BY due_date ASC
    """)
    fun getUpcomingTerms(): Flow<List<TermEntity>>

    /**
     * Get completed terms ordered by completion date.
     */
    @Query("""
        SELECT * FROM terms 
        WHERE is_completed = 1 
        ORDER BY completed_at DESC
    """)
    fun getCompletedTerms(): Flow<List<TermEntity>>

    /**
     * Get overdue terms (not completed and past due date).
     * 
     * @param currentTime Current timestamp in milliseconds
     */
    @Query("""
        SELECT * FROM terms 
        WHERE is_completed = 0 
        AND due_date < :currentTime
        ORDER BY due_date ASC
    """)
    fun getOverdueTerms(currentTime: Long): Flow<List<TermEntity>>

    /**
     * Get terms that need reminder notification.
     * 
     * Returns terms where:
     * - Not completed
     * - Has reminder set (reminder_minutes_before > 0)
     * - Reminder time has passed (due_date - reminder <= current)
     * - Due date not yet passed (due_date > current)
     * 
     * @param currentTime Current timestamp in milliseconds
     */
    @Query("""
        SELECT * FROM terms 
        WHERE is_completed = 0 
        AND reminder_minutes_before > 0
        AND (due_date - reminder_minutes_before * 60000) <= :currentTime
        AND due_date > :currentTime
        ORDER BY due_date ASC
    """)
    fun getTermsNeedingReminder(currentTime: Long): Flow<List<TermEntity>>

    /**
     * Get terms due within a date range.
     */
    @Query("""
        SELECT * FROM terms 
        WHERE due_date BETWEEN :startTime AND :endTime
        ORDER BY due_date ASC
    """)
    fun getTermsInDateRange(startTime: Long, endTime: Long): Flow<List<TermEntity>>

    /**
     * Get upcoming terms due within a date range.
     */
    @Query("""
        SELECT * FROM terms 
        WHERE is_completed = 0 
        AND due_date BETWEEN :startTime AND :endTime
        ORDER BY due_date ASC
    """)
    fun getUpcomingTermsInDateRange(startTime: Long, endTime: Long): Flow<List<TermEntity>>

    // ══════════════════════════════════════════════════════════════
    // COUNT OPERATIONS
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Get total count of all terms.
     */
    @Query("SELECT COUNT(*) FROM terms")
    suspend fun getTotalCount(): Int

    /**
     * Get count of upcoming (not completed) terms.
     */
    @Query("SELECT COUNT(*) FROM terms WHERE is_completed = 0")
    suspend fun getUpcomingCount(): Int

    /**
     * Get count of completed terms.
     */
    @Query("SELECT COUNT(*) FROM terms WHERE is_completed = 1")
    suspend fun getCompletedCount(): Int

    /**
     * Get count of overdue terms.
     * 
     * @param currentTime Current timestamp in milliseconds
     */
    @Query("SELECT COUNT(*) FROM terms WHERE is_completed = 0 AND due_date < :currentTime")
    suspend fun getOverdueCount(currentTime: Long): Int

    /**
     * Get count of terms due today.
     * 
     * @param startOfDay Start of today (00:00:00) in milliseconds
     * @param endOfDay End of today (23:59:59) in milliseconds
     */
    @Query("""
        SELECT COUNT(*) FROM terms 
        WHERE is_completed = 0 
        AND due_date BETWEEN :startOfDay AND :endOfDay
    """)
    suspend fun getDueTodayCount(startOfDay: Long, endOfDay: Long): Int
}