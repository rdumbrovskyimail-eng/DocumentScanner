package com.docs.scanner.presentation.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Folders : Screen("folders")
    data object Records : Screen("records/{folderId}") {
        fun createRoute(folderId: Long) = "records/$folderId"
    }
    data object Editor : Screen("editor/{recordId}") {
        fun createRoute(recordId: Long) = "editor/$recordId"
    }
    data object Camera : Screen("camera")
    data object Search : Screen("search")
    data object Terms : Screen("terms")
    data object Settings : Screen("settings")
    data object Debug : Screen("debug")
    data object ImageViewer : Screen("image_viewer/{documentId}") {
        fun createRoute(documentId: Long) = "image_viewer/$documentId"
    }
}