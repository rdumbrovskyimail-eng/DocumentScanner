package com.docs.scanner.presentation.screens.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.docs.scanner.presentation.screens.editor.components.DocumentCard
import com.docs.scanner.presentation.components.LoadingDialog
import com.docs.scanner.presentation.components.FullscreenTextEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    recordId: Long,
    onNavigateBack: () -> Unit, // Оставляем это имя, оно стандартное
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(recordId) {
        viewModel.loadRecord(recordId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактор") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onEvent(EditorEvent.AddDocument) }) {
                Icon(Icons.Default.AddAPhoto, null)
            }
        }
    ) { padding ->
        when (val state = uiState) {
            is EditorUiState.Loading -> LoadingDialog()
            is EditorUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(state.documents, key = { it.id }) { doc ->
                        DocumentCard(
                            document = doc,
                            onEvent = viewModel::onEvent
                        )
                    }
                }
            }
            else -> {}
        }
    }

    // Проверка состояний редактора из ViewModel
    if (viewModel.isTextEditorOpen) {
        FullscreenTextEditor(
            initialText = viewModel.editingText,
            onDismiss = { viewModel.onEvent(EditorEvent.CloseTextEditor) },
            onSave = { newText -> viewModel.onEvent(EditorEvent.SaveEditedText(newText)) }
        )
    }
}
