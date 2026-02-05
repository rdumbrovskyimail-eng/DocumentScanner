package com.docs.scanner.presentation.navigation

import androidx.lifecycle.SavedStateHandle

/**
 * ✅ Sealed class для всех экранов навигации
 * * FIX APPLIED:
 * - Changed get<String> to get<Long> in helper methods because NavGraph uses NavType.LongType.
 * - This fixes the issue where IDs were returned as 0L (null), blocking the Editor from opening.
 */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Folders : Screen("folders")
    data object Camera : Screen("camera")
    data object Search : Screen("search")
    data object Settings : Screen("settings")
    data object Debug : Screen("debug")
    
    data object Terms : Screen("terms?openTermId={openTermId}") {
        fun createRoute(openTermId: Long? = null): String {
            val id = openTermId ?: return "terms"
            return if (id > 0) "terms?openTermId=$id" else "terms"
        }
    }
    
    /**
     * Экран записей в папке
     */
    data object Records : Screen("records/{folderId}") {
        fun createRoute(folderId: Long): String {
            require(folderId != 0L) { "Invalid folderId: $folderId (0 is not allowed)" }
            return "records/$folderId"
        }
        
        fun getFolderIdFromRoute(savedStateHandle: SavedStateHandle): Long {
            // ✅ FIX: NavGraph saves this as Long, so we must retrieve it as Long
            return savedStateHandle.get<Long>("folderId") ?: 0L
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
            // ✅ FIX: NavGraph saves this as Long, so we must retrieve it as Long
            return savedStateHandle.get<Long>("recordId") ?: 0L
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
            // ✅ FIX: NavGraph saves this as Long, so we must retrieve it as Long
            return savedStateHandle.get<Long>("documentId") ?: 0L
        }
    }
}
