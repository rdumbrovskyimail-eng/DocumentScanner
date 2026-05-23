package com.docs.scanner.presentation.screens.analytics.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.usecase.AllUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Tiny hub VM — just exposes live counts for the two tiles on the hub screen.
 * Both counts are reactive: when a translation is mirrored from the editor or a
 * note is created/deleted, the badge updates without re-opening the screen.
 */
@HiltViewModel
class AnalyticsHubViewModel @Inject constructor(
    useCases: AllUseCases
) : ViewModel() {

    val translationsCount: StateFlow<Int> = useCases.analyticsTranslations
        .count()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val notesCount: StateFlow<Int> = useCases.analyticsNotes
        .activeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}