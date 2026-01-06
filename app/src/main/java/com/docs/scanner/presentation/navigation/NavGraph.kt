package com.docs.scanner.presentation.navigation

import android.util.Log
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.docs.scanner.presentation.screens.camera.CameraScreen
import com.docs.scanner.presentation.screens.editor.EditorScreen
import com.docs.scanner.presentation.screens.folders.FoldersScreen
import com.docs.scanner.presentation.screens.imageviewer.ImageViewerScreen
import com.docs.scanner.presentation.screens.onboarding.OnboardingScreen
import com.docs.scanner.presentation.screens.records.RecordsScreen
import com.docs.scanner.presentation.screens.search.SearchScreen
import com.docs.scanner.presentation.screens.debug.DebugScreen
import com.docs.scanner.presentation.screens.settings.SettingsScreen
import com.docs.scanner.presentation.screens.terms.TermsScreen

private const val TAG = "NavGraph"

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    initialOpenTermId: Long? = null,
    onOpenTermConsumed: () -> Unit = {}
) {
    LaunchedEffect(initialOpenTermId) {
        val id = initialOpenTermId
        if (id != null && id > 0) {
            safeNavigate(navController) {
                navigate(Screen.Terms.createRoute(id))
            }
            onOpenTermConsumed()
        }
    }

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
                    safeNavigate(navController) {
                        navigate(Screen.Records.createRoute(folderId))
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
                    safeNavigate(navController) {
                        navigate(Screen.Editor.createRoute(recordId))
                    }
                }
            )
        }

        composable(
            route = Screen.Records.route,
            arguments = listOf(navArgument("folderId") { type = NavType.LongType })
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getLong("folderId") ?: run {
                Log.e(TAG, "Missing folderId parameter")
                navController.popBackStack()
                return@composable
            }
            
            if (folderId <= 0) {
                Log.e(TAG, "Invalid folderId: $folderId")
                navController.popBackStack()
                return@composable
            }
            
            RecordsScreen(
                folderId = folderId,
                onBackClick = { navController.popBackStack() },
                onRecordClick = { recordId ->
                    safeNavigate(navController) {
                        navigate(Screen.Editor.createRoute(recordId))
                    }
                }
            )
        }

        composable(
            route = Screen.Editor.route,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong("recordId") ?: run {
                Log.e(TAG, "Missing recordId parameter")
                navController.popBackStack()
                return@composable
            }
            
            if (recordId <= 0) {
                Log.e(TAG, "Invalid recordId: $recordId")
                navController.popBackStack()
                return@composable
            }
            
            EditorScreen(
                recordId = recordId,
                onBackClick = { navController.popBackStack() },
                onImageClick = { documentId ->
                    safeNavigate(navController) {
                        navigate(Screen.ImageViewer.createRoute(documentId))
                    }
                },
                onCameraClick = {
                    safeNavigate(navController) {
                        navigate(Screen.Camera.route)
                    }
                }
            )
        }

        composable(Screen.Camera.route) {
            CameraScreen(
                onScanComplete = { recordId ->
                    safeNavigate(navController) {
                        navigate(Screen.Editor.createRoute(recordId)) {
                            popUpTo(Screen.Folders.route) { inclusive = false }
                        }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onBackClick = { navController.popBackStack() },
                onDocumentClick = { recordId ->
                    safeNavigate(navController) {
                        navigate(Screen.Editor.createRoute(recordId))
                    }
                }
            )
        }

        composable(
            route = Screen.Terms.route,
            arguments = listOf(
                navArgument("openTermId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            // NOTE: openTermId can be passed via optional query param.
            val openTermId = backStackEntry.arguments?.getLong("openTermId")?.takeIf { v -> v > 0 }
            TermsScreen(
                onNavigateBack = { navController.popBackStack() },
                openTermId = openTermId
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onDebugClick = { navController.navigate(Screen.Debug.route) }
            )
        }

        composable(Screen.Debug.route) {
            DebugScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ImageViewer.route,
            arguments = listOf(navArgument("documentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId") ?: run {
                Log.e(TAG, "Missing documentId parameter")
                navController.popBackStack()
                return@composable
            }
            
            if (documentId <= 0) {
                Log.e(TAG, "Invalid documentId: $documentId")
                navController.popBackStack()
                return@composable
            }
            
            ImageViewerScreen(
                documentId = documentId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Safe navigation wrapper that catches IllegalArgumentException
 * from Screen.createRoute() validation.
 */
private fun safeNavigate(
    navController: NavHostController,
    block: NavHostController.() -> Unit
) {
    try {
        navController.block()
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "Navigation error: ${e.message}", e)
        // Stay on current screen instead of crashing
    }
}