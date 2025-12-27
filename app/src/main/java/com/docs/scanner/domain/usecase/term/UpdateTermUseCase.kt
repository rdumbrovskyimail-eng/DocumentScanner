package com.docs.scanner.domain.usecase.term

import com.docs.scanner.domain.model.Term
import com.docs.scanner.domain.repository.TermRepository
import javax.inject.Inject

/**
 * Update existing term.
 * 
 * Session 6 addition: Required by TermsViewModel.
 * 
 * @throws IllegalArgumentException if validation fails
 */
class UpdateTermUseCase @Inject constructor(
    private val termRepository: TermRepository
) {
    suspend operator fun invoke(term: Term) {
        if (term.id <= 0) {
            throw IllegalArgumentException("Invalid term ID")
        }
        
        when (val result = term.validate()) {
            is Term.ValidationResult.Error -> 
                throw IllegalArgumentException(result.message)
            Term.ValidationResult.Valid -> 
                termRepository.updateTerm(term)
        }
    }
}