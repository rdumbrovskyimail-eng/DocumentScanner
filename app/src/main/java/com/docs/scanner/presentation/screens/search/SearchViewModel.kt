package com.docs.scanner.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val documentRepository: DocumentRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { query ->
                    performSearch(query.trim())
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        _searchQuery.value = query
    }

    private suspend fun performSearch(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        _isSearching.value = true
        searchJob = viewModelScope.launch {
            documentRepository.searchEverywhereWithNames("%$query%")
                .collect { docs ->
                    val results = docs.take(50).map { doc ->
                        SearchResult(
                            documentId = doc.id,
                            recordId = doc.recordId,
                            recordName = doc.recordName,
                            folderName = doc.folderName,
                            matchedText = doc.originalText ?: doc.translatedText ?: "",
                            isOriginal = doc.originalText?.contains(query, ignoreCase = true) == true
                        )
                    }
                    _searchResults.value = results
                }
        }
        _isSearching.value = false
    }
}