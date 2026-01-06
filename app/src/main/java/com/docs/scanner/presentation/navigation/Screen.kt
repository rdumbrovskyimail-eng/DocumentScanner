package com.docs.scanner.presentation.navigation

import androidx.lifecycle.SavedStateHandle

/**
 * ✅ Sealed class для всех экранов навигации
 * 
 * Session 9 Fix:
 * - ✅ Allow negative folderId for Quick Scans folder (id = -1L)
 * - ✅ Only reject folderId == 0 (invalid for Room entities)
 * - ✅ Added validation via require()
 * - ✅ Added helper methods for SavedStateHandle extraction
 * - ✅ Fail-fast approach instead of silent fallbacks
 */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Folders : Screen("folders")
    data object Camera : Screen("camera")
    data object Search : Screen("search")
    data object Terms : Screen("terms?openTermId={openTermId}") {
        fun createRoute(openTermId: Long? = null): String {
            val id = openTermId ?: return "terms"
            return if (id > 0) "terms?openTermId=$id" else "terms"
        }
    }
    data object Settings : Screen("settings")
    data object Debug : Screen("debug")
    
    /**
     * Экран записей в папке
     * 
     * ✅ FIX: Allow negative folderId for system folders like Quick Scans (id = -1L)
     * Only 0 is invalid (Room autoGenerate never produces 0)
     */
    data object Records : Screen("records/{folderId}") {
        fun createRoute(folderId: Long): String {
            // ✅ FIX: Allow negative IDs (Quick Scans = -1L)
            // Only reject 0 which is never valid
            require(folderId != 0L) { "Invalid folderId: $folderId (0 is not allowed)" }
            return "records/$folderId"
        }
        
        fun getFolderIdFromRoute(savedStateHandle: SavedStateHandle): Long {
            return savedStateHandle.get<String>("folderId")?.toLongOrNull() ?: 0L
        }
    }
    
    /**
     * Экран редактора документа
     */
    data object Editor : Screen("editor/{recordId}") {
        fun createRoute(recordId: Long): String {
            require(recordId > 0) { "Invalid recordId: $recordId" }
            return "editor/$recordId"
        }
        
        fun getRecordIdFromRoute(savedStateHandle: SavedStateHandle): Long {
            return savedStateHandle.get<String>("recordId")?.toLongOrNull() ?: 0L
        }
    }
    
    /**
     * Экран просмотра изображения
     */
    data object ImageViewer : Screen("image_viewer/{documentId}") {
        fun createRoute(documentId: Long): String {
            require(documentId > 0) { "Invalid documentId: $documentId" }
            return "image_viewer/$documentId"
        }
        
        fun getDocumentIdFromRoute(savedStateHandle: SavedStateHandle): Long {
            return savedStateHandle.get<String>("documentId")?.toLongOrNull() ?: 0L
        }
    }
}
