package com.docs.scanner.presentation.screens.terms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Term
import com.docs.scanner.domain.usecase.AllUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Terms Screen ViewModel.
 * 
 * Session 8 Fixes:
 * - ✅ Removed direct TermRepository injection
 * - ✅ Uses Term Use Cases (GetUpcomingTerms, CreateTerm, etc.)
 * - ✅ Added TermsUiState for better state management
 * - ✅ Removed business logic (alarm scheduling moved to Use Case)
 * - ✅ Uses domain model Term instead of TermEntity
 */
@HiltViewModel
class TermsViewModel @Inject constructor(
    private val useCases: AllUseCases
) : ViewModel() {

    // ✅ FIX: Single UiState instead of 2 separate StateFlows
    private val _uiState = MutableStateFlow<TermsUiState>(TermsUiState.Loading)
    val uiState: StateFlow<TermsUiState> = _uiState.asStateFlow()

    init {
        loadTerms()
    }

    /**
     * Load upcoming and completed terms.
     */
    private fun loadTerms() {
        viewModelScope.launch {
            _uiState.value = TermsUiState.Loading

            try {
                combine(
                    useCases.getUpcomingTerms(),
                    useCases.getCompletedTerms()
                ) { upcoming, completed ->
                    TermsUiState.Success(
                        upcomingTerms = upcoming,
                        completedTerms = completed
                    )
                }.collect { state ->
                    _uiState.value = state
                }
            } catch (e: Exception) {
                _uiState.value = TermsUiState.Error(
                    "Failed to load terms: ${e.message}"
                )
            }
        }
    }

    /**
     * Create new term.
     * 
     * @param title Term title
     * @param description Optional description
     * @param dueDate Due date timestamp
     * @param reminderMinutesBefore Minutes before due date for reminder
     */
    fun createTerm(
        title: String,
        description: String? = null,
        dueDate: Long,
        reminderMinutesBefore: Int = 0
    ) {
        if (title.isBlank()) {
            updateErrorMessage("Title cannot be empty")
            return
        }

        if (dueDate <= System.currentTimeMillis()) {
            updateErrorMessage("Due date must be in the future")
            return
        }

        viewModelScope.launch {
            val term = Term(
                title = title.trim(),
                description = description?.takeIf { it.isNotBlank() },
                dueDate = dueDate,
                reminderMinutesBefore = reminderMinutesBefore,
                isCompleted = false
            )

            when (val result = useCases.createTerm(term)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    // Terms auto-refresh via Flow
                    // Alarms scheduled in Use Case
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to create term: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    /**
     * Update existing term.
     */
    fun updateTerm(term: Term) {
        viewModelScope.launch {
            when (val result = useCases.updateTerm(term)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    // Auto-refresh via Flow
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to update term: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    /**
     * Mark term as completed.
     * Uses dedicated Use Case for completion logic.
     */
    fun completeTerm(termId: Long) {
        viewModelScope.launch {
            when (val result = useCases.markTermCompleted(termId, completed = true)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    // Auto-refresh via Flow
                    // Alarms cancelled in Use Case
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to complete term: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    /**
     * Toggle term completion status.
     */
    fun toggleTermCompletion(term: Term) {
        viewModelScope.launch {
            when (val result = useCases.markTermCompleted(term.id, !term.isCompleted)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    // Auto-refresh via Flow
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to toggle term: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    /**
     * Delete term.
     */
    fun deleteTerm(term: Term) {
        viewModelScope.launch {
            when (val result = useCases.deleteTerm(term)) {
                is com.docs.scanner.domain.model.Result.Success -> {
                    // Auto-refresh via Flow
                    // Alarms cancelled in Use Case
                }
                is com.docs.scanner.domain.model.Result.Error -> {
                    updateErrorMessage("Failed to delete term: ${result.exception.message}")
                }
                else -> {}
            }
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        val currentState = _uiState.value
        if (currentState is TermsUiState.Success) {
            _uiState.value = currentState.copy(errorMessage = null)
        }
    }

    /**
     * Helper to update error message in Success state.
     */
    private fun updateErrorMessage(message: String) {
        val currentState = _uiState.value
        if (currentState is TermsUiState.Success) {
            _uiState.value = currentState.copy(errorMessage = message)
        } else {
            _uiState.value = TermsUiState.Error(message)
        }
    }
}

/**
 * UI State for Terms Screen.
 * 
 * Session 8: Consolidated from 2 separate StateFlows.
 */
sealed interface TermsUiState {
    object Loading : TermsUiState
    
    data class Success(
        val upcomingTerms: List<Term>,
        val completedTerms: List<Term>,
        val errorMessage: String? = null
    ) : TermsUiState
    
    data class Error(val message: String) : TermsUiState
}