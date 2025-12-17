package com.docs.scanner.presentation.navigation

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.docs.scanner.presentation.screens.camera.CameraScreen
import com.docs.scanner.presentation.screens.editor.EditorScreen
import com.docs.scanner.presentation.screens.folders.FoldersScreen
import com.docs.scanner.presentation.screens.imageviewer.ImageViewerScreen
import com.docs.scanner.presentation.screens.onboarding.OnboardingScreen
import com.docs.scanner.presentation.screens.records.RecordsScreen
import com.docs.scanner.presentation.screens.search.SearchScreen
import com.docs.scanner.presentation.screens.settings.SettingsScreen
import com.docs.scanner.presentation.screens.terms.TermsScreen

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Onboarding.route
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Folders.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Folders.route) {
            FoldersScreen(
                onFolderClick = { folderId ->
                    try {
                        navController.navigate(Screen.Records.createRoute(folderId))
                    } catch (e: Exception) {
                        println("❌ Navigation error: ${e.message}")
                    }
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                },
                onTermsClick = {
                    navController.navigate(Screen.Terms.route)
                },
                onCameraClick = {
                    navController.navigate(Screen.Camera.route)
                },
                onQuickScanComplete = { recordId ->
                    try {
                        navController.navigate(Screen.Editor.createRoute(recordId))
                    } catch (e: Exception) {
                        println("❌ Navigation error: ${e.message}")
                    }
                }
            )
        }
        
        composable(
            route = Screen.Records.route,
            arguments = listOf(
                navArgument("folderId") { 
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId") ?: -1L
            
            if (folderId == -1L) {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            } else {
                RecordsScreen(
                    folderId = folderId,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onRecordClick = { recordId ->
                        try {
                            navController.navigate(Screen.Editor.createRoute(recordId))
                        } catch (e: Exception) {
                            println("❌ Navigation error: ${e.message}")
                        }
                    }
                )
            }
        }
        
        composable(
            route = Screen.Editor.route,
            arguments = listOf(
                navArgument("recordId") { 
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: -1L
            
            if (recordId == -1L) {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            } else {
                EditorScreen(
                    recordId = recordId,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onImageClick = { documentId ->
                        try {
                            navController.navigate(Screen.ImageViewer.createRoute(documentId))
                        } catch (e: Exception) {
                            println("❌ Navigation error: ${e.message}")
                        }
                    }
                )
            }
        }
        
        composable(Screen.Camera.route) {
            CameraScreen(
                onScanComplete = { recordId ->
                    try {
                        navController.navigate(Screen.Editor.createRoute(recordId)) {
                            popUpTo(Screen.Folders.route) { inclusive = false }
                        }
                    } catch (e: Exception) {
                        println("❌ Navigation error: ${e.message}")
                        navController.popBackStack()
                    }
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Search.route) {
            SearchScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onDocumentClick = { recordId ->
                    try {
                        navController.navigate(Screen.Editor.createRoute(recordId))
                    } catch (e: Exception) {
                        println("❌ Navigation error: ${e.message}")
                    }
                }
            )
        }
        
        // ✅ ИСПРАВЛЕНО: Terms Screen - используем onNavigateBack вместо onBackClick
        composable(Screen.Terms.route) {
            TermsScreen(
                onNavigateBack = {  // ✅ Правильный параметр
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.ImageViewer.route,
            arguments = listOf(
                navArgument("documentId") { 
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId") ?: -1L
            
            if (documentId == -1L) {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            } else {
                ImageViewerScreen(
                    documentId = documentId,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
