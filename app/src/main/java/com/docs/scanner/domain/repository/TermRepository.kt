package com.docs.scanner.domain.repository

import com.docs.scanner.domain.model.Term
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Term (deadline) operations.
 * 
 * Provides all operations for managing deadline reminders.
 * Implementations should handle database operations via TermDao.
 */
interface TermRepository {
    
    // ══════════════════════════════════════════════════════════════
    // FLOW OPERATIONS (Reactive)
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Get all terms ordered by due date.
     */
    fun getAllTerms(): Flow<List<Term>>
    
    /**
     * Get upcoming (not completed) terms.
     */
    fun getUpcomingTerms(): Flow<List<Term>>
    
    /**
     * Get completed terms ordered by completion date.
     */
    fun getCompletedTerms(): Flow<List<Term>>
    
    /**
     * Get overdue terms (not completed and past due date).
     */
    fun getOverdueTerms(): Flow<List<Term>>
    
    /**
     * Get terms that need reminder notification.
     */
    fun getTermsNeedingReminder(): Flow<List<Term>>
    
    /**
     * Get terms due within a date range.
     */
    fun getTermsInDateRange(startTime: Long, endTime: Long): Flow<List<Term>>
    
    // ══════════════════════════════════════════════════════════════
    // SUSPEND OPERATIONS (One-shot)
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Get term by ID.
     * @return Term or null if not found
     */
    suspend fun getTermById(termId: Long): Term?
    
    /**
     * Insert a new term.
     * @return ID of the inserted term
     */
    suspend fun insertTerm(term: Term): Long
    
    /**
     * Update an existing term.
     */
    suspend fun updateTerm(term: Term)
    
    /**
     * Delete a term.
     */
    suspend fun deleteTerm(term: Term)
    
    /**
     * Delete term by ID.
     */
    suspend fun deleteTermById(termId: Long)
    
    /**
     * Mark term as completed or uncompleted.
     * @param termId Term ID
     * @param completed true = completed, false = uncompleted
     */
    suspend fun markCompleted(termId: Long, completed: Boolean)
    
    /**
     * Delete all completed terms.
     */
    suspend fun deleteAllCompleted()
    
    // ══════════════════════════════════════════════════════════════
    // COUNT OPERATIONS
    // ══════════════════════════════════════════════════════════════
    
    /**
     * Get count of upcoming terms.
     */
    suspend fun getUpcomingCount(): Int
    
    /**
     * Get count of overdue terms.
     */
    suspend fun getOverdueCount(): Int
    
    /**
     * Get count of terms due today.
     */
    suspend fun getDueTodayCount(): Int
}