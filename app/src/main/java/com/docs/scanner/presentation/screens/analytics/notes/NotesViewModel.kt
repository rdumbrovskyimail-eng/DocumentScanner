package com.docs.scanner.presentation.screens.analytics.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.core.AnalyticsNote
import com.docs.scanner.domain.core.AnalyticsNoteId
import com.docs.scanner.domain.core.DomainResult
import com.docs.scanner.domain.core.NewAnalyticsNote
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

data class NotesUiState(
    val notes: List<AnalyticsNote> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
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

    val uiState: StateFlow<NotesUiState> = _searchQuery
        .debounce { q -> if (q.isBlank()) 0L else 250L }
        .flatMapLatest { query ->
            if (query.isBlank()) {
                useCases.analyticsNotes.observeActive()
            } else {
                useCases.analyticsNotes.search(query)
            }
        }
        .map { list -> NotesUiState(notes = list, isLoading = false) }
        .catch { e ->
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to load notes")
            emit(NotesUiState(isLoading = false, errorMessage = e.message))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun createNote(title: String, content: String, tags: List<String>, color: String?) {
        viewModelScope.launch {
            val payload = NewAnalyticsNote(
                title = title.trim(),
                content = content,
                tags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
                color = color
            )
            when (val r = useCases.analyticsNotes.create(payload)) {
                is DomainResult.Success -> _snackbarEvents.tryEmit("Note saved")
                is DomainResult.Failure -> _snackbarEvents.tryEmit("Save failed: ${r.error.message}")
            }
        }
    }

    fun updateNote(note: AnalyticsNote) {
        viewModelScope.launch {
            when (val r = useCases.analyticsNotes.update(note)) {
                is DomainResult.Success -> _snackbarEvents.tryEmit("Note saved")
                is DomainResult.Failure -> _snackbarEvents.tryEmit("Save failed: ${r.error.message}")
            }
        }
    }

    fun togglePin(note: AnalyticsNote) {
        viewModelScope.launch {
            useCases.analyticsNotes.setPinned(note.id, !note.isPinned)
        }
    }

    fun archive(note: AnalyticsNote) {
        viewModelScope.launch {
            when (val r = useCases.analyticsNotes.setArchived(note.id, true)) {
                is DomainResult.Success -> _snackbarEvents.tryEmit("Archived")
                is DomainResult.Failure -> _snackbarEvents.tryEmit("Archive failed: ${r.error.message}")
            }
        }
    }

    fun delete(noteId: AnalyticsNoteId) {
        viewModelScope.launch {
            when (val r = useCases.analyticsNotes.delete(noteId)) {
                is DomainResult.Success -> _snackbarEvents.tryEmit("Deleted")
                is DomainResult.Failure -> _snackbarEvents.tryEmit("Delete failed: ${r.error.message}")
            }
        }
    }
}