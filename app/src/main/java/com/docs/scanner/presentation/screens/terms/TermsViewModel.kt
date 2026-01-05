package com.docs.scanner.presentation.screens.terms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.core.FolderId
import com.docs.scanner.domain.core.Term
import com.docs.scanner.domain.core.TermId
import com.docs.scanner.domain.core.TermPriority
import com.docs.scanner.domain.usecase.AllUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TermsViewModel @Inject constructor(
    private val useCases: AllUseCases
) : ViewModel() {

    private val _filter = MutableStateFlow(TermsFilter.ACTIVE)
    val filter: StateFlow<TermsFilter> = _filter.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val now = MutableStateFlow(System.currentTimeMillis())

    private val allTerms: StateFlow<List<Term>> =
        useCases.terms.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedTermId = MutableStateFlow<Long?>(null)
    val selectedTerm: StateFlow<Term?> =
        combine(allTerms, selectedTermId) { list, id ->
            id?.let { termId -> list.firstOrNull { it.id.value == termId } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<TermsUiState> =
        combine(allTerms, now, filter) { terms, nowMs, filter ->
            val active = terms.filter { !it.isCompleted && !it.isCancelled }
            val completed = terms.filter { it.isCompleted }
            val cancelled = terms.filter { it.isCancelled }

            val overdue = active.filter { it.dueDate < nowMs }
            val dueToday = active.filter { it.isDueToday(nowMs) }
            val upcoming = active.filter { it.isInReminderWindow(nowMs) && it.dueDate >= nowMs }

            val visible = when (filter) {
                TermsFilter.ALL -> terms.sortedBy { it.dueDate }
                TermsFilter.ACTIVE -> active.sortedBy { it.dueDate }
                TermsFilter.UPCOMING -> upcoming.sortedBy { it.dueDate }
                TermsFilter.DUE_TODAY -> dueToday.sortedBy { it.dueDate }
                TermsFilter.OVERDUE -> overdue.sortedBy { it.dueDate }
                TermsFilter.COMPLETED -> completed.sortedByDescending { it.completedAt ?: 0L }
                TermsFilter.CANCELLED -> cancelled.sortedByDescending { it.updatedAt }
            }

            TermsUiState.Success(
                now = nowMs,
                all = terms,
                active = active,
                upcoming = upcoming,
                dueToday = dueToday,
                overdue = overdue,
                completed = completed,
                cancelled = cancelled,
                visible = visible,
                filter = filter
            )
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TermsUiState.Loading)

    init {
        // keep status-based lists fresh (overdue/due-today/upcoming)
        viewModelScope.launch {
            while (true) {
                now.value = System.currentTimeMillis()
                delay(60_000)
            }
        }
    }

    fun setFilter(filter: TermsFilter) {
        _filter.value = filter
    }

    fun openTerm(termId: Long?) {
        selectedTermId.value = termId?.takeIf { it > 0 }
    }

    fun closeDialog() {
        selectedTermId.value = null
    }

    fun clearMessage() {
        _message.value = null
    }

    fun createTerm(
        title: String,
        description: String?,
        dueDate: Long,
        reminderMinutesBefore: Int,
        priority: TermPriority,
        folderId: Long?
    ) {
        if (title.isBlank()) {
            _message.value = "Title cannot be empty"
            return
        }
        if (dueDate <= System.currentTimeMillis()) {
            _message.value = "Due date must be in the future"
            return
        }

        viewModelScope.launch {
            when (
                val r = useCases.terms.create(
                    title = title.trim(),
                    dueDate = dueDate,
                    desc = description?.takeIf { it.isNotBlank() },
                    reminderMinutes = reminderMinutesBefore,
                    priority = priority,
                    folderId = folderId?.let { FolderId(it) }
                )
            ) {
                is com.docs.scanner.domain.core.DomainResult.Success -> _message.value = "✓ Term created"
                is com.docs.scanner.domain.core.DomainResult.Failure -> _message.value = "✗ Create failed: ${r.error.message}"
            }
        }
    }

    fun updateTerm(term: Term) {
        viewModelScope.launch {
            when (val r = useCases.terms.update(term)) {
                is com.docs.scanner.domain.core.DomainResult.Success -> _message.value = "✓ Saved"
                is com.docs.scanner.domain.core.DomainResult.Failure -> _message.value = "✗ Save failed: ${r.error.message}"
            }
        }
    }

    fun completeTerm(termId: Long) {
        viewModelScope.launch {
            when (val r = useCases.terms.complete(TermId(termId))) {
                is com.docs.scanner.domain.core.DomainResult.Success -> _message.value = "✓ Completed"
                is com.docs.scanner.domain.core.DomainResult.Failure -> _message.value = "✗ Complete failed: ${r.error.message}"
            }
        }
    }

    fun uncompleteTerm(termId: Long) {
        viewModelScope.launch {
            when (val r = useCases.terms.uncomplete(TermId(termId))) {
                is com.docs.scanner.domain.core.DomainResult.Success -> _message.value = "✓ Restored"
                is com.docs.scanner.domain.core.DomainResult.Failure -> _message.value = "✗ Restore failed: ${r.error.message}"
            }
        }
    }

    fun cancelTerm(termId: Long) {
        viewModelScope.launch {
            when (val r = useCases.terms.cancel(TermId(termId))) {
                is com.docs.scanner.domain.core.DomainResult.Success -> _message.value = "✓ Cancelled"
                is com.docs.scanner.domain.core.DomainResult.Failure -> _message.value = "✗ Cancel failed: ${r.error.message}"
            }
        }
    }

    fun restoreTerm(termId: Long) {
        viewModelScope.launch {
            when (val r = useCases.terms.restore(TermId(termId))) {
                is com.docs.scanner.domain.core.DomainResult.Success -> _message.value = "✓ Restored"
                is com.docs.scanner.domain.core.DomainResult.Failure -> _message.value = "✗ Restore failed: ${r.error.message}"
            }
        }
    }

    fun deleteTerm(termId: Long) {
        viewModelScope.launch {
            when (val r = useCases.terms.delete(TermId(termId))) {
                is com.docs.scanner.domain.core.DomainResult.Success -> _message.value = "✓ Deleted"
                is com.docs.scanner.domain.core.DomainResult.Failure -> _message.value = "✗ Delete failed: ${r.error.message}"
            }
        }
    }

    fun deleteAllCompleted() {
        viewModelScope.launch {
            when (val r = useCases.terms.deleteAllCompleted()) {
                is com.docs.scanner.domain.core.DomainResult.Success -> _message.value = "✓ Deleted ${r.data} completed terms"
                is com.docs.scanner.domain.core.DomainResult.Failure -> _message.value = "✗ Delete failed: ${r.error.message}"
            }
        }
    }

    fun deleteAllCancelled() {
        viewModelScope.launch {
            when (val r = useCases.terms.deleteAllCancelled()) {
                is com.docs.scanner.domain.core.DomainResult.Success -> _message.value = "✓ Deleted ${r.data} cancelled terms"
                is com.docs.scanner.domain.core.DomainResult.Failure -> _message.value = "✗ Delete failed: ${r.error.message}"
            }
        }
    }
}

enum class TermsFilter { ALL, ACTIVE, UPCOMING, DUE_TODAY, OVERDUE, COMPLETED, CANCELLED }

sealed interface TermsUiState {
    data object Loading : TermsUiState
    data class Error(val message: String) : TermsUiState
    data class Success(
        val now: Long,
        val all: List<Term>,
        val active: List<Term>,
        val upcoming: List<Term>,
        val dueToday: List<Term>,
        val overdue: List<Term>,
        val completed: List<Term>,
        val cancelled: List<Term>,
        val visible: List<Term>,
        val filter: TermsFilter
    ) : TermsUiState
}
