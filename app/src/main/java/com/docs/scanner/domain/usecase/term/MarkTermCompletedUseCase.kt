package com.docs.scanner.domain.usecase.term

import com.docs.scanner.domain.model.Result
import com.docs.scanner.domain.repository.TermRepository
import javax.inject.Inject

/**
 * Mark term as completed/uncompleted.
 * 
 * Used by: TermsViewModel
 * 
 * Features:
 * - Toggle completion status
 * - Update completion timestamp
 * - Cancel scheduled alarms (if completed)
 * - Validation
 * 
 * Usage:
 * ```
 * // Mark as completed
 * markTermCompletedUseCase(termId, completed = true)
 * 
 * // Mark as uncompleted
 * markTermCompletedUseCase(termId, completed = false)
 * ```
 */
class MarkTermCompletedUseCase @Inject constructor(
    private val termRepository: TermRepository
) {
    /**
     * Toggle term completion status.
     * 
     * @param termId Term ID
     * @param completed true = mark completed, false = mark uncompleted
     * @return Result<Unit>
     */
    suspend operator fun invoke(
        termId: Long,
        completed: Boolean
    ): Result<Unit> {
        return try {
            // Validation
            if (termId <= 0) {
                return Result.Error(Exception("Invalid term ID"))
            }
            
            // Check term exists
            val term = termRepository.getTermById(termId)
            if (term == null) {
                return Result.Error(Exception("Term not found"))
            }
            
            // Update completion status
            termRepository.markCompleted(termId, completed)
            
            // TODO: Cancel alarms if completed (implement in Session 3 AlarmScheduler)
            // if (completed) {
            //     alarmScheduler.cancelTermAlarms(termId)
            // }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Failed to update term: ${e.message}", e))
        }
    }
    
    /**
     * Mark multiple terms as completed (batch operation).
     * 
     * @param termIds List of term IDs
     * @param completed Completion status
     * @return Result with count of successfully updated terms
     */
    suspend fun markMultiple(
        termIds: List<Long>,
        completed: Boolean
    ): Result<Int> {
        if (termIds.isEmpty()) {
            return Result.Success(0)
        }
        
        var successCount = 0
        val errors = mutableListOf<Exception>()
        
        termIds.forEach { termId ->
            when (invoke(termId, completed)) {
                is Result.Success -> successCount++
                is Result.Error -> errors.add(Exception("Failed to update term $termId"))
                else -> {}
            }
        }
        
        return when {
            errors.isEmpty() -> Result.Success(successCount)
            successCount == 0 -> Result.Error(Exception("All operations failed"))
            else -> {
                println("⚠️ Partial success: $successCount/${termIds.size}")
                Result.Success(successCount)
            }
        }
    }
    
    /**
     * Quick toggle: flip current completion status.
     * 
     * @param termId Term ID
     * @return Result<Boolean> - New completion status
     */
    suspend fun toggle(termId: Long): Result<Boolean> {
        return try {
            val term = termRepository.getTermById(termId)
                ?: return Result.Error(Exception("Term not found"))
            
            val newStatus = !term.isCompleted
            
            when (invoke(termId, newStatus)) {
                is Result.Success -> Result.Success(newStatus)
                is Result.Error -> Result.Error(Exception("Failed to toggle"))
                else -> Result.Error(Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Failed to toggle term: ${e.message}", e))
        }
    }
}