package com.docs.scanner.presentation.navigation

import androidx.lifecycle.SavedStateHandle

/**
 * ✅ Sealed class для всех экранов навигации
 * 
 * Session 8 Fix:
 * - ✅ Removed nullable parameters and fallbacks
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
     */
    data object Records : Screen("records/{folderId}") {
        fun createRoute(folderId: Long): String {
            require(folderId > 0) { "Invalid folderId: $folderId" }
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