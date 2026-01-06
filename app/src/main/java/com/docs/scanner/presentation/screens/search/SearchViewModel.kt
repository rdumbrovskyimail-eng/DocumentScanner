package com.docs.scanner.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.core.SearchHistoryItem
import com.docs.scanner.domain.usecase.AllUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search Screen ViewModel.
 * 
 * Session 8 Fixes:
 * - ✅ Removed direct DocumentRepository injection
 * - ✅ Uses SearchDocumentsUseCase
 * - ✅ Added SearchUiState for better state management
 * - ✅ Fixed timing bug with _isSearching
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val useCases: AllUseCases
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Suggestions)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val searchHistory: StateFlow<List<SearchHistoryItem>> =
        useCases.documents.observeSearchHistory(limit = 20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(400)  // Wait for user to stop typing
                .distinctUntilChanged()
                .collectLatest { query ->
                    performSearch(query.trim())
                }
        }
    }

    /**
     * Update search query.
     * Automatically triggers search after 400ms debounce.
     */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /**
     * Perform search with query validation.
     */
    private suspend fun performSearch(query: String) {
        if (query.length < 2) {
            _uiState.value = if (query.isEmpty()) {
                SearchUiState.Suggestions
            } else {
                SearchUiState.QueryTooShort
            }
            return
        }

        _uiState.value = SearchUiState.Searching

        try {
            useCases.documents.search(query)
                .catch { e ->
                    _uiState.value = SearchUiState.Error(
                        "Search failed: ${e.message}"
                    )
                }
                .collect { documents ->
                    // Persist query to history (best-effort).
                    useCases.documents.saveSearchQuery(query, documents.size)

                    _uiState.value = if (documents.isEmpty()) {
                        SearchUiState.NoResults(query)
                    } else {
                        // Map to SearchResult with highlighting info
                        val results = documents.take(50).map { doc ->
                            SearchResult(
                                documentId = doc.id.value,
                                recordId = doc.recordId.value,
                                recordName = doc.recordName ?: "Untitled",
                                folderName = doc.folderName ?: "Documents",
                                matchedText = doc.originalText ?: doc.translatedText ?: "",
                                isOriginal = doc.originalText?.contains(query, ignoreCase = true) == true,
                                highlightedText = highlightMatch(
                                    text = doc.originalText ?: doc.translatedText ?: "",
                                    query = query
                                )
                            )
                        }
                        SearchUiState.Success(results, query)
                    }
                }
        } catch (e: Exception) {
            _uiState.value = SearchUiState.Error(
                "Search failed: ${e.message}"
            )
        }
    }

    /**
     * Highlight matched text for UI display.
     * Returns text with match position for highlighting.
     */
    private fun highlightMatch(text: String, query: String): String {
        val index = text.indexOf(query, ignoreCase = true)
        if (index == -1) return text
        
        // Return context around match (50 chars before/after)
        val start = maxOf(0, index - 50)
        val end = minOf(text.length, index + query.length + 50)
        
        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < text.length) "..." else ""
        
        return prefix + text.substring(start, end) + suffix
    }

    /**
     * Clear search and reset state.
     */
    fun clearSearch() {
        _searchQuery.value = ""
        _uiState.value = SearchUiState.Suggestions
    }

    fun selectHistory(query: String) {
        _searchQuery.value = query
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            useCases.documents.deleteSearchHistoryItem(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            useCases.documents.clearSearchHistory()
        }
    }
}

/**
 * UI State for Search Screen.
 * 
 * Session 8: Added proper state management.
 */
sealed interface SearchUiState {
    object Suggestions : SearchUiState
    object QueryTooShort : SearchUiState
    object Searching : SearchUiState
    data class Success(val results: List<SearchResult>, val query: String) : SearchUiState
    data class NoResults(val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

/**
 * Search result item.
 * Contains all info needed for display.
 */
data class SearchResult(
    val documentId: Long,
    val recordId: Long,
    val recordName: String,
    val folderName: String,
    val matchedText: String,
    val isOriginal: Boolean,
    val highlightedText: String
)