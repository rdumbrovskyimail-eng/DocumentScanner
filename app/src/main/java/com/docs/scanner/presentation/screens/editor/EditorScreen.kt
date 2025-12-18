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
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(recordId) {
        viewModel.loadRecord(recordId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Документы записи", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Вызов камеры/галереи */ }) {
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
            is EditorUiState.Error -> { /* Компонент ошибки */ }
            else -> {}
        }
    }

    // Если открыт полноэкранный редактор (логика должна быть в EditorViewModel)
    if (viewModel.isTextEditorOpen) {
        FullscreenTextEditor(
            initialText = viewModel.editingText,
            onDismiss = { viewModel.closeTextEditor() },
            onSave = { newText -> viewModel.saveEditedText(newText) }
        )
    }
}
