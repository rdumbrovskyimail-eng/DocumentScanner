package com.docs.scanner.presentation.screens.imageviewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.usecase.AllUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    private val useCases: AllUseCases,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val documentId: Long = savedStateHandle.get<Long>("documentId") ?: 0L

    private val _document = MutableStateFlow<Document?>(null)
    val document: StateFlow<Document?> = _document.asStateFlow()

    init {
        if (documentId > 0) loadDocument(documentId)
    }

    fun loadDocument(id: Long) {
        viewModelScope.launch {
            _document.value = useCases.getDocumentById(id)
        }
    }
}

