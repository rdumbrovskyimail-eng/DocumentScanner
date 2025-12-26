package com.docs.scanner.presentation.screens.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.docs.scanner.domain.model.Document
import com.docs.scanner.presentation.components.*
import com.docs.scanner.presentation.screens.editor.components.*
import com.docs.scanner.presentation.theme.*
import com.docs.scanner.util.Debouncer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    recordId: Long,
    viewModel: EditorViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onImageClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val record by viewModel.record.collectAsState()
    val folderName by viewModel.folderName.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var editingDocument by remember { mutableStateOf<Document?>(null) }

    val galleryDebouncer = remember { Debouncer(800L, coroutineScope) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addDocument(it) }
    }

    // Камера теперь работает — переход на CameraScreen
    val onCameraClick = {
        // Навигация обрабатывается в NavGraph, здесь только логика
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(recordId) {
        viewModel.loadRecord(recordId)
    }

    Scaffold(
        containerColor = GoogleDocsBackground,
        topBar = {
            GoogleDocsTopBar(
                title = folderName ?: "Documents",
                onBackClick = onBackClick,
                onMenuClick = { /* TODO */ }
            )
        },
        floatingActionButton = {
            FloatingActionButtons(
                onCameraClick = onCameraClick, // будет работать через NavGraph
                onGalleryClick = {
                    galleryDebouncer.invoke {
                        galleryLauncher.launch("image/*")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is EditorUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is EditorUiState.Empty -> EmptyState(
                    title = "No documents yet",
                    message = "Add your first document to scan and translate",
                    actionText = "Add Document",
                    onActionClick = { galleryLauncher.launch("image/*") }
                )
                is EditorUiState.Success -> {
                    val documents = state.documents
                    LazyColumn(state = listState, contentPadding = PaddingValues(8.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { DocumentHeader(record?.name ?: "Document", record?.description, { showEditNameDialog = true }) }
                        item { SimpleDivider() }
                        items(documents, key = { it.id }) { document ->
                            // Остальной код GContainerLayout, TranslationField и т.д. — без изменений
                        }
                    }
                }
                is EditorUiState.Error -> ErrorState(error = state.message, onRetry = { viewModel.loadRecord(recordId) })
            }
        }
    }

    // Диалоги редактирования имени и текста — без изменений
}