package com.docs.scanner.domain.usecase.term

import com.docs.scanner.domain.model.Term
import com.docs.scanner.domain.repository.TermRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Get completed terms.
 * 
 * Used by: TermsViewModel (for "Completed" tab)
 * 
 * Features:
 * - Reactive Flow (auto-updates UI)
 * - Sorted by completion date (newest first)
 * - Includes overdue completed terms
 * 
 * Usage:
 * ```
 * getCompletedTermsUseCase().collect { terms ->
 *     _completedTerms.value = terms
 * }
 * ```
 */
class GetCompletedTermsUseCase @Inject constructor(
    private val termRepository: TermRepository
) {
    /**
     * Get all completed terms as Flow.
     * 
     * @return Flow<List<Term>> - Reactive stream of completed terms
     */
    operator fun invoke(): Flow<List<Term>> {
        return termRepository.getCompletedTerms()
    }
    
    /**
     * Get completed terms with date range filter.
     * 
     * @param startDate Start timestamp (inclusive)
     * @param endDate End timestamp (inclusive)
     * @return Flow<List<Term>>
     */
    fun getCompletedInRange(
        startDate: Long,
        endDate: Long
    ): Flow<List<Term>> {
        // TODO: Add to TermRepository if needed
        // For now, filter in ViewModel:
        // terms.filter { it.completedAt in startDate..endDate }
        return termRepository.getCompletedTerms()
    }
}