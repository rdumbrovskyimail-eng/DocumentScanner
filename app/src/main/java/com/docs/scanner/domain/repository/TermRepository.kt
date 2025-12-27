package com.docs.scanner.domain.repository

import com.docs.scanner.domain.model.Term
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for Term operations.
 * 
 * 🔴 CRITICAL SESSION 5 FIX:
 * This interface was MISSING - violated Clean Architecture!
 * 
 * Problem:
 * - data/repository/TermRepository.kt existed but returned Entity directly
 * - No domain interface → ViewModels coupled to data layer
 * - TermEntity (Room annotation) leaked to presentation
 * 
 * Solution:
 * - Create domain/repository/TermRepository.kt (this file) ✅
 * - Create domain/model/Term.kt (domain model) ✅
 * - Rename data/repository/TermRepository → TermRepositoryImpl
 * - Add mappers: TermEntity ↔ Term
 * 
 * Architecture:
 * ```
 * ViewModel → TermRepository (interface) → TermRepositoryImpl → TermDao → TermEntity
 *    ↓              ↓                          ↓                   ↓          ↓
 * Domain         Domain                     Data                Data      Data
 * ```
 */
interface TermRepository {
    
    /**
     * Get all upcoming (not completed) terms.
     * Sorted by due date (earliest first).
     * 
     * @return Flow of upcoming terms
     */
    fun getUpcomingTerms(): Flow<List<Term>>
    
    /**
     * Get all completed terms.
     * Sorted by due date (most recent first).
     * 
     * @return Flow of completed terms
     */
    fun getCompletedTerms(): Flow<List<Term>>
    
    /**
     * Get term by ID.
     * 
     * @param id Term ID
     * @return Term or null if not found
     */
    suspend fun getTermById(id: Long): Term?
    
    /**
     * Get overdue terms (not completed and past due date).
     * 
     * @param currentTime Current timestamp (default: now)
     * @return Flow of overdue terms
     */
    fun getOverdueTerms(
        currentTime: Long = System.currentTimeMillis()
    ): Flow<List<Term>>
    
    /**
     * Get terms needing reminder notification.
     * 
     * Returns terms where:
     * - Not completed
     * - Has reminder set (reminderMinutesBefore > 0)
     * - Reminder time reached but term not yet due
     * 
     * @param currentTime Current timestamp (default: now)
     * @return Flow of terms needing reminder
     */
    fun getTermsNeedingReminder(
        currentTime: Long = System.currentTimeMillis()
    ): Flow<List<Term>>
    
    /**
     * Insert new term.
     * 
     * @param term Term to insert
     * @return ID of inserted term
     */
    suspend fun insertTerm(term: Term): Long
    
    /**
     * Update existing term.
     * 
     * @param term Term to update (must have valid ID)
     */
    suspend fun updateTerm(term: Term)
    
    /**
     * Delete term.
     * 
     * @param term Term to delete
     */
    suspend fun deleteTerm(term: Term)
    
    /**
     * Delete term by ID.
     * 
     * @param termId Term ID to delete
     */
    suspend fun deleteTermById(termId: Long)
    
    /**
     * Mark term as completed/uncompleted.
     * 
     * @param termId Term ID
     * @param completed true to mark completed, false to mark uncompleted
     */
    suspend fun markCompleted(termId: Long, completed: Boolean)
    
    /**
     * Get count of upcoming terms.
     * 
     * @return Number of upcoming (not completed) terms
     */
    suspend fun getUpcomingCount(): Int
    
    /**
     * Get count of overdue terms.
     * 
     * @param currentTime Current timestamp (default: now)
     * @return Number of overdue terms
     */
    suspend fun getOverdueCount(
        currentTime: Long = System.currentTimeMillis()
    ): Int
}