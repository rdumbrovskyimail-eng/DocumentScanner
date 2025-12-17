package com.docs.scanner.presentation.navigation

/**
 * ✅ Sealed class для всех экранов навигации
 */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Folders : Screen("folders")
    data object Camera : Screen("camera")
    data object Search : Screen("search")
    data object Terms : Screen("terms") // ✅ Экран условий использования
    data object Settings : Screen("settings")
    
    /**
     * Экран записей в папке
     */
    data object Records : Screen("records/{folderId}") {
        fun createRoute(folderId: Long?): String {
            return if (folderId != null && folderId > 0) {
                "records/$folderId"
            } else {
                "folders" // Fallback to folders screen
            }
        }
    }
    
    /**
     * Экран редактора документа
     */
    data object Editor : Screen("editor/{recordId}") {
        fun createRoute(recordId: Long?): String {
            return if (recordId != null && recordId > 0) {
                "editor/$recordId"
            } else {
                "folders" // Fallback to folders screen
            }
        }
    }
    
    /**
     * Экран просмотра изображения
     */
    data object ImageViewer : Screen("image_viewer/{documentId}") {
        fun createRoute(documentId: Long?): String {
            return if (documentId != null && documentId > 0) {
                "image_viewer/$documentId"
            } else {
                "folders" // Fallback to folders screen
            }
        }
    }
}
