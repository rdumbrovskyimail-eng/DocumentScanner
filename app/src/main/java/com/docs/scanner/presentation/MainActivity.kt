package com.docs.scanner.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docs.scanner.presentation.navigation.NavGraph
import com.docs.scanner.presentation.theme.DocumentScannerTheme
import com.docs.scanner.domain.core.ThemeMode
import com.docs.scanner.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private val pendingOpenTermId = mutableStateOf<Long?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Log results for debugging
        results.forEach { (permission, granted) ->
            Timber.d("Permission %s: %s", permission, if (granted) "✅ granted" else "❌ denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNecessaryPermissions()
        pendingOpenTermId.value = intent?.getLongExtra("open_term", -1L)?.takeIf { it > 0 }

        setContent {
            val themeMode = settingsRepository.observeThemeMode()
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
                .value
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            DocumentScannerTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(
                        initialOpenTermId = pendingOpenTermId.value,
                        onOpenTermConsumed = { pendingOpenTermId.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingOpenTermId.value = intent.getLongExtra("open_term", -1L).takeIf { it > 0 }
    }

    private fun requestNecessaryPermissions() {
        val prefs = getSharedPreferences("app_permissions_prefs", MODE_PRIVATE)
        val alreadyRequested = prefs.getBoolean("permissions_requested", false)
        if (alreadyRequested) return // Запрашиваем системно только один раз при первом старте

        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) !=
                PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
        prefs.edit().putBoolean("permissions_requested", true).apply()
    }
}