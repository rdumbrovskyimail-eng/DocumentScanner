package com.docs.scanner.presentation.navigation

import android.util.Log
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.docs.scanner.domain.core.FolderId
import com.docs.scanner.presentation.screens.camera.CameraScreen
import com.docs.scanner.presentation.screens.editor.EditorScreen
import com.docs.scanner.presentation.screens.folders.FoldersScreen
import com.docs.scanner.presentation.screens.analytics.hub.AnalyticsHubScreen
import com.docs.scanner.presentation.screens.analytics.archive.TranslationsArchiveScreen
import com.docs.scanner.presentation.screens.analytics.notes.NotesScreen
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
                onAnalyticsClick = {
                    navController.navigate(Screen.AnalyticsHub.route)
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
            
            // ✅ FIX: Allow Quick Scans folder ID (-1L) 
            // Quick Scans uses QUICK_SCANS_ID = -1L to avoid conflict with autoGenerate
            // Valid folder IDs are: -1L (Quick Scans) or positive numbers (user folders)
            if (!isValidFolderId(folderId)) {
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
            arguments = listOf(
                navArgument("recordId") { type = NavType.LongType },
                navArgument("highlightDocId") {
                    type = NavType.LongType
                    defaultValue = Screen.Editor.HIGHLIGHT_NONE
                }
            )
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

            val rawHighlight = backStackEntry.arguments
                ?.getLong("highlightDocId")
                ?: Screen.Editor.HIGHLIGHT_NONE
            val highlightDocumentId = rawHighlight.takeIf { it > 0L }

            EditorScreen(
                recordId = recordId,
                highlightDocumentId = highlightDocumentId,
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
                onDocumentClick = { recordId, documentId ->
                    safeNavigate(navController) {
                        navigate(Screen.Editor.createRoute(recordId, documentId))
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

        // ─── Analytics Center ────────────────────────────────────────────

        composable(Screen.AnalyticsHub.route) {
            AnalyticsHubScreen(
                onBackClick = { navController.popBackStack() },
                onOpenArchive = {
                    safeNavigate(navController) {
                        navigate(Screen.TranslationsArchive.route)
                    }
                },
                onOpenNotes = {
                    safeNavigate(navController) {
                        navigate(Screen.AnalyticsNotes.route)
                    }
                }
            )
        }

        composable(Screen.TranslationsArchive.route) {
            TranslationsArchiveScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.AnalyticsNotes.route) {
            NotesScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Validates folder ID.
 * 
 * Valid folder IDs:
 * - FolderId.QUICK_SCANS_ID (-1L) - system folder for quick scans
 * - Any positive Long - user-created folders
 * 
 * Invalid:
 * - 0L (FolderId requires non-zero)
 * - Any other negative number (reserved, not used)
 */
private fun isValidFolderId(folderId: Long): Boolean {
    return folderId == FolderId.QUICK_SCANS_ID || folderId > 0
}

/**
 * Safe navigation wrapper that catches IllegalArgumentException
 * from Screen.createRoute() validation.
 *
 * On failure we don't try to show a Snackbar here (no Host accessible)
 * — instead caller is responsible for it. Returns true on success.
 */
private fun safeNavigate(
    navController: NavHostController,
    onError: ((String) -> Unit)? = null,
    block: NavHostController.() -> Unit
): Boolean {
    return try {
        navController.block()
        true
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "Navigation error: ${e.message}", e)
        onError?.invoke(e.message ?: "Cannot open destination: invalid arguments")
        false
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected navigation error", e)
        onError?.invoke("Navigation failed")
        false
    }
}
