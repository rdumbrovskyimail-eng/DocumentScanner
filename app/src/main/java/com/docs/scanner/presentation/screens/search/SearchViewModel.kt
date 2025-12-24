package com.docs.scanner.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
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
    
    // Thread-safe кэш с LRU
    private val searchCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<SearchResult>>(
            20,
            0.75f,
            true
        ) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SearchResult>>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }
    )
    
    private val cacheMutex = Mutex()
    
    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    performSearch(query)
                }
        }
    }
    
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
    
    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        
        if (query.length < MIN_QUERY_LENGTH) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        
        // Thread-safe чтение из кэша
        val cached = cacheMutex.withLock {
            searchCache[query.lowercase()]
        }
        
        if (cached != null) {
            _searchResults.value = cached
            return
        }
        
        _isSearching.value = true
        
        try {
            documentRepository.searchEverywhereWithNames(query)
                .catch { e ->
                    println("❌ Search error: ${e.message}")
                    _searchResults.value = emptyList()
                }
                .collect { documents ->
                    val results = documents.take(MAX_RESULTS).map { doc ->
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
                    
                    // Thread-safe запись в кэш
                    cacheMutex.withLock {
                        searchCache[query.lowercase()] = results
                    }
                }
        } catch (e: Exception) {
            println("❌ Search error: ${e.message}")
            _searchResults.value = emptyList()
        } finally {
            _isSearching.value = false
        }
    }
    
    fun clearCache() {
        viewModelScope.launch {
            cacheMutex.withLock {
                searchCache.clear()
            }
        }
    }
    
    companion object {
        private const val MAX_CACHE_SIZE = 20
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_RESULTS = 50
    }
}

data class SearchResult(
    val documentId: Long,
    val recordId: Long,
    val recordName: String,
    val folderName: String,
    val matchedText: String,
    val isOriginal: Boolean
)
