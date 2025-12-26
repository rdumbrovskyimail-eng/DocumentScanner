package com.docs.scanner.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
    
    // ✅ НОВОЕ: Job для отмены предыдущего поиска
    private var searchJob: Job? = null
    
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
                .debounce(500) // ✅ Увеличен debounce с 300ms до 500ms
                .distinctUntilChanged()
                .collectLatest { query ->
                    performSearch(query)
                }
        }
    }
    
    fun onSearchQueryChange(query: String) {
        // ✅ НОВОЕ: Отменяем предыдущий поиск
        searchJob?.cancel()
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
            android.util.Log.d("SearchViewModel", "✅ Cache hit for: $query")
            _searchResults.value = cached
            return
        }
        
        // ✅ НОВОЕ: Создаём новый Job для этого поиска
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            
            try {
                android.util.Log.d("SearchViewModel", "🔍 Searching for: $query")
                
                documentRepository.searchEverywhereWithNames(query)
                    .catch { e ->
                        // ✅ ИСПРАВЛЕНО: Игнорируем CancellationException
                        if (e !is kotlinx.coroutines.CancellationException) {
                            android.util.Log.e("SearchViewModel", "❌ Search error: ${e.message}")
                        }
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
                        
                        android.util.Log.d("SearchViewModel", "✅ Found ${results.size} results")
                        _searchResults.value = results
                        
                        // Thread-safe запись в кэш
                        cacheMutex.withLock {
                            searchCache[query.lowercase()] = results
                        }
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // ✅ ИСПРАВЛЕНО: Нормальная отмена поиска
                android.util.Log.d("SearchViewModel", "🚫 Search cancelled for: $query")
            } catch (e: Exception) {
                android.util.Log.e("SearchViewModel", "❌ Search error: ${e.message}")
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    fun clearCache() {
        viewModelScope.launch {
            cacheMutex.withLock {
                searchCache.clear()
            }
            android.util.Log.d("SearchViewModel", "🧹 Cache cleared")
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
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