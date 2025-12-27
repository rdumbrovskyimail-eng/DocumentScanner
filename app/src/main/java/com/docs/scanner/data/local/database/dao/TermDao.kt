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
 * DAO for Term operations.
 * 
 * Session 2 & 3 fixes:
 * - ✅ Added getOverdueTerms()
 * - ✅ Added getTermsNeedingReminder()
 * - ✅ Added getUpcomingCount()
 * - ✅ Added getOverdueCount()
 * - ✅ Added indexes hint for queries
 * - ✅ Fixed naming consistency (insert, update, delete)
 * 
 * Index requirements (from TermEntity):
 * - dueDate [INDEX] - for sorting by date
 * - isCompleted [INDEX] - for filtering completed/upcoming
 */
@Dao
interface TermDao {
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // QUERY OPERATIONS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Get all upcoming (not completed) terms.
     * Only returns terms with due date in the future.
     * 
     * @param currentTime Current timestamp (default: now)
     * @return Flow of upcoming terms, sorted by due date (earliest first)
     */
    @Query("""
        SELECT * FROM terms 
        WHERE isCompleted = 0 
        AND dueDate >= :currentTime 
        ORDER BY dueDate ASC
    """)
    fun getUpcomingTerms(
        currentTime: Long = System.currentTimeMillis()
    ): Flow<List<TermEntity>>
    
    /**
     * Get all completed terms.
     * 
     * @return Flow of completed terms, sorted by due date (most recent first)
     */
    @Query("""
        SELECT * FROM terms 
        WHERE isCompleted = 1 
        ORDER BY dueDate DESC
    """)
    fun getCompletedTerms(): Flow<List<TermEntity>>
    
    /**
     * Get overdue terms (not completed and past due date).
     * 
     * ✅ NEW: Session 2 addition
     * 
     * @param currentTime Current timestamp (default: now)
     * @return Flow of overdue terms, sorted by due date (most overdue first)
     */
    @Query("""
        SELECT * FROM terms 
        WHERE isCompleted = 0 
        AND dueDate < :currentTime 
        ORDER BY dueDate ASC
    """)
    fun getOverdueTerms(
        currentTime: Long = System.currentTimeMillis()
    ): Flow<List<TermEntity>>
    
    /**
     * Get terms needing reminder notification.
     * 
     * ✅ NEW: Session 2 addition
     * 
     * Returns terms where:
     * - Not completed
     * - Has reminder set (reminderMinutesBefore > 0)
     * - Reminder time reached but term not yet due
     * 
     * @param currentTime Current timestamp (default: now)
     * @return Flow of terms needing reminder, sorted by due date
     */
    @Query("""
        SELECT * FROM terms 
        WHERE isCompleted = 0 
        AND reminderMinutesBefore > 0
        AND dueDate - (reminderMinutesBefore * 60000) <= :currentTime
        AND dueDate > :currentTime
        ORDER BY dueDate ASC
    """)
    fun getTermsNeedingReminder(
        currentTime: Long = System.currentTimeMillis()
    ): Flow<List<TermEntity>>
    
    /**
     * Get term by ID.
     * 
     * @param termId Term ID
     * @return Term or null if not found
     */
    @Query("SELECT * FROM terms WHERE id = :termId")
    suspend fun getTermById(termId: Long): TermEntity?
    
    /**
     * Get count of upcoming terms.
     * 
     * ✅ NEW: Session 2 addition
     * 
     * @param currentTime Current timestamp (default: now)
     * @return Number of upcoming (not completed) terms
     */
    @Query("""
        SELECT COUNT(*) FROM terms 
        WHERE isCompleted = 0 
        AND dueDate >= :currentTime
    """)
    suspend fun getUpcomingCount(
        currentTime: Long = System.currentTimeMillis()
    ): Int
    
    /**
     * Get count of overdue terms.
     * 
     * ✅ NEW: Session 2 addition
     * 
     * @param currentTime Current timestamp (default: now)
     * @return Number of overdue terms
     */
    @Query("""
        SELECT COUNT(*) FROM terms 
        WHERE isCompleted = 0 
        AND dueDate < :currentTime
    """)
    suspend fun getOverdueCount(
        currentTime: Long = System.currentTimeMillis()
    ): Int
    
    /**
     * Get all terms (for debugging).
     * 
     * @return Flow of all terms
     */
    @Query("SELECT * FROM terms ORDER BY dueDate ASC")
    fun getAllTerms(): Flow<List<TermEntity>>
    
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // WRITE OPERATIONS
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    
    /**
     * Insert new term.
     * 
     * @param term Term to insert
     * @return ID of inserted term
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(term: TermEntity): Long
    
    /**
     * Insert multiple terms (batch operation).
     * 
     * ✅ NEW: For bulk operations
     * 
     * @param terms Terms to insert
     * @return List of inserted term IDs
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(terms: List<TermEntity>): List<Long>
    
    /**
     * Update existing term.
     * 
     * @param term Term to update (must have valid ID)
     */
    @Update
    suspend fun update(term: TermEntity)
    
    /**
     * Update multiple terms (batch operation).
     * 
     * ✅ NEW: For bulk operations
     * 
     * @param terms Terms to update
     */
    @Update
    suspend fun updateAll(terms: List<TermEntity>)
    
    /**
     * Mark term as completed/uncompleted.
     * 
     * @param termId Term ID
     * @param completed true to mark completed, false to mark uncompleted
     */
    @Query("UPDATE terms SET isCompleted = :completed WHERE id = :termId")
    suspend fun markCompleted(termId: Long, completed: Boolean)
    
    /**
     * Mark multiple terms as completed.
     * 
     * ✅ NEW: For bulk operations
     * 
     * @param termIds List of term IDs
     * @param completed Completion status
     */
    @Query("UPDATE terms SET isCompleted = :completed WHERE id IN (:termIds)")
    suspend fun markCompletedBatch(termIds: List<Long>, completed: Boolean)
    
    /**
     * Delete term.
     * 
     * @param term Term to delete
     */
    @Delete
    suspend fun delete(term: TermEntity)
    
    /**
     * Delete term by ID.
     * 
     * @param termId Term ID to delete
     */
    @Query("DELETE FROM terms WHERE id = :termId")
    suspend fun deleteById(termId: Long)
    
    /**
     * Delete multiple terms by IDs (batch operation).
     * 
     * ✅ NEW: For bulk operations
     * 
     * @param termIds List of term IDs to delete
     */
    @Query("DELETE FROM terms WHERE id IN (:termIds)")
    suspend fun deleteByIds(termIds: List<Long>)
    
    /**
     * Delete all completed terms.
     * 
     * ✅ NEW: For cleanup
     * 
     * @return Number of deleted terms
     */
    @Query("DELETE FROM terms WHERE isCompleted = 1")
    suspend fun deleteAllCompleted(): Int
    
    /**
     * Delete all terms (use with caution!).
     * 
     * @return Number of deleted terms
     */
    @Query("DELETE FROM terms")
    suspend fun deleteAll(): Int
}