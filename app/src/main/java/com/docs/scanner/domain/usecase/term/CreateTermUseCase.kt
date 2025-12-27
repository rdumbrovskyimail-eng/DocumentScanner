package com.docs.scanner.domain.usecase.term

import com.docs.scanner.domain.model.Term
import com.docs.scanner.domain.repository.TermRepository
import javax.inject.Inject

/**
 * Create new term with validation.
 * 
 * Session 6 addition: Required by TermsViewModel.
 * 
 * @throws IllegalArgumentException if validation fails
 */
class CreateTermUseCase @Inject constructor(
    private val termRepository: TermRepository
) {
    suspend operator fun invoke(
        title: String,
        description: String? = null,
        dueDate: Long,
        reminderMinutesBefore: Int = 0
    ): Long {
        val term = Term.create(
            title = title,
            description = description,
            dueDate = dueDate,
            reminderMinutesBefore = reminderMinutesBefore
        )
        
        return termRepository.insertTerm(term)
    }
}