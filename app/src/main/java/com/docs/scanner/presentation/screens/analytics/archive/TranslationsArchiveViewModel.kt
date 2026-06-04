package com.docs.scanner.presentation.screens.analytics.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.core.AnalyticsTranslation
import com.docs.scanner.domain.core.AnalyticsTranslationId
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.usecase.AllUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class TranslationsArchiveUiState(
    val translations: List<AnalyticsTranslation> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TranslationsArchiveViewModel @Inject constructor(
    private val useCases: AllUseCases
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    val uiState: StateFlow<TranslationsArchiveUiState> = _searchQuery
        .debounce { query -> if (query.isBlank()) 0L else 250L }
        .flatMapLatest { query ->
            if (query.isBlank()) {
                useCases.analyticsTranslations.observeAll()
            } else {
                useCases.analyticsTranslations.search(query)
            }
        }
        .map { list ->
            TranslationsArchiveUiState(translations = list, isLoading = false)
        }
        .catch { e ->
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to load translations archive")
            emit(TranslationsArchiveUiState(isLoading = false, errorMessage = e.message))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TranslationsArchiveUiState()
        )

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun updateTranslation(translation: AnalyticsTranslation, newText: String) {
        viewModelScope.launch {
            val trimmed = newText.trim()
            if (trimmed == translation.translatedText) return@launch
            when (val r = useCases.analyticsTranslations.update(
                translation.copy(translatedText = trimmed)
            )) {
                is DomainResult.Success -> _snackbarEvents.tryEmit("Saved")
                is DomainResult.Failure -> _snackbarEvents.tryEmit("Save failed: ${r.error.message}")
            }
        }
    }

    fun deleteTranslation(id: AnalyticsTranslationId) {
        viewModelScope.launch {
            when (val r = useCases.analyticsTranslations.delete(id)) {
                is DomainResult.Success -> _snackbarEvents.tryEmit("Deleted")
                is DomainResult.Failure -> _snackbarEvents.tryEmit("Delete failed: ${r.error.message}")
            }
        }
    }
}