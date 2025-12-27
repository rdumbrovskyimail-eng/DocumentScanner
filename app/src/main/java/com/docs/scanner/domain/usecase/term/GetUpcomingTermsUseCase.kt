package com.docs.scanner.domain.usecase.term

import com.docs.scanner.domain.model.Term
import com.docs.scanner.domain.repository.TermRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Get all upcoming (not completed) terms.
 * 
 * Session 6 addition: Required by TermsViewModel.
 */
class GetUpcomingTermsUseCase @Inject constructor(
    private val termRepository: TermRepository
) {
    operator fun invoke(): Flow<List<Term>> {
        return termRepository.getUpcomingTerms()
    }
}