package com.docs.scanner.presentation.screens.imageviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.docs.scanner.domain.model.Document
import com.docs.scanner.domain.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@Composable
fun ImageViewerScreen(
    documentId: Long,
    viewModel: ImageViewerViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val document by viewModel.document.collectAsState()
    
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        
        val maxX = (scale - 1f) * 500f
        val maxY = (scale - 1f) * 500f
        
        offset = Offset(
            x = (offset.x + offsetChange.x).coerceIn(-maxX, maxX),
            y = (offset.y + offsetChange.y).coerceIn(-maxY, maxY)
        )
    }
    
    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        document?.let { doc ->
            AsyncImage(
                model = File(doc.imagePath),
                contentDescription = "Document image",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = transformableState)
            )
        }
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = "Document Image",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                
                Box(modifier = Modifier.size(48.dp))
            }
        }
        
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = Color.Black.copy(alpha = 0.7f)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = {
                        scale = (scale * 0.8f).coerceAtLeast(0.5f)
                    }
                ) {
                    Icon(
                        Icons.Default.ZoomOut,
                        contentDescription = "Zoom Out",
                        tint = Color.White
                    )
                }
                
                IconButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    }
                ) {
                    Icon(
                        Icons.Default.FitScreen,
                        contentDescription = "Reset",
                        tint = Color.White
                    )
                }
                
                IconButton(
                    onClick = {
                        scale = (scale * 1.25f).coerceAtMost(5f)
                    }
                ) {
                    Icon(
                        Icons.Default.ZoomIn,
                        contentDescription = "Zoom In",
                        tint = Color.White
                    )
                }
            }
        }
        
        if (document == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
    }
}

@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val documentId: Long = savedStateHandle.get<Long>("documentId") ?: 0L
    
    private val _document = MutableStateFlow<Document?>(null)
    val document: StateFlow<Document?> = _document.asStateFlow()
    
    fun loadDocument(documentId: Long) {
        viewModelScope.launch {
            _document.value = documentRepository.getDocumentById(documentId)
        }
    }
}