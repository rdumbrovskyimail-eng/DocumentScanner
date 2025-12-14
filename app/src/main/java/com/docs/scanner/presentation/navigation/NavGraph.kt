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
import com.docs.scanner.presentation.screens.settings.SettingsScreen

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
                    navController.navigate(Screen.Records.createRoute(folderId))
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
                    navController.navigate(Screen.Editor.createRoute(recordId))
                }
            )
        }
        
        composable(
            route = Screen.Records.route,
            arguments = listOf(
                navArgument("folderId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId") ?: 0L
            
            RecordsScreen(
                folderId = folderId,
                onBackClick = {
                    navController.popBackStack()
                },
                onRecordClick = { recordId ->
                    navController.navigate(Screen.Editor.createRoute(recordId))
                }
            )
        }
        
        composable(
            route = Screen.Editor.route,
            arguments = listOf(
                navArgument("recordId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
            
            EditorScreen(
                recordId = recordId,
                onBackClick = {
                    navController.popBackStack()
                },
                onImageClick = { documentId ->
                    navController.navigate(Screen.ImageViewer.createRoute(documentId))
                }
            )
        }
        
        composable(Screen.Camera.route) {
            CameraScreen(
                onImageCaptured = { uris ->
                    // TODO: Handle captured images
                    navController.popBackStack()
                },
                onBackClick = {
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
                navArgument("documentId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId") ?: 0L
            
            ImageViewerScreen(
                documentId = documentId,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}