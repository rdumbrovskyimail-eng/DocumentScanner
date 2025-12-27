package com.docs.scanner.domain.usecase.term

import com.docs.scanner.domain.model.Term
import com.docs.scanner.domain.repository.TermRepository
import javax.inject.Inject

/**
 * Delete term.
 * 
 * Session 6 addition: Required by TermsViewModel.
 * ⚠️ Alarm cancellation should be handled in ViewModel.
 */
class DeleteTermUseCase @Inject constructor(
    private val termRepository: TermRepository
) {
    suspend operator fun invoke(term: Term) {
        termRepository.deleteTerm(term)
    }
}