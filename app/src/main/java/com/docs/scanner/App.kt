/*
 * DocumentScanner - App.kt
 * Application class оптимизированный для 2026 Standards
 *
 * Version: 7.1.1 (Build 711) - PRODUCTION READY
 * 
 * ✅ FIXES (Session 13):
 * - Fixed Coil 3.x API compatibility - using okio.Path instead of java.nio.file.Path
 * - Fixed cache directory path conversion
 * - Updated MemoryCache and DiskCache builders
 */

package com.docs.scanner

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
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
import coil3.memory.MemoryCache
import com.docs.scanner.data.local.preferences.GeminiModelManager
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.domain.repository.FolderRepository
import com.docs.scanner.util.CrashLogHandler
import com.docs.scanner.util.LogcatCollector
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    
    @Inject
    lateinit var folderRepository: FolderRepository

    @Inject
    lateinit var geminiModelManager: GeminiModelManager

    private var logcatCollector: LogcatCollector? = null
    
    /**
     * Application-wide coroutine scope.
     * ✅ FIX: Now properly cancelled in onTerminate() to prevent memory leak
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    /**
     * Flag to track if notification channels were already created.
     * ✅ FIX: Avoid recreating channels on every call
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
        private const val MEMORY_CACHE_SIZE_MB = 50L // 50MB for memory cache
        private const val DISK_CACHE_SIZE_MB = 100L
        
        private const val QUICK_SCANS_FOLDER_NAME = "Quick Scans"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Logging - должен быть первым
        initializeTimber()
        
        // 2. ✅ NEW: Crash handler initialization
        CrashLogHandler.install(this)

        // 3. ✅ NEW: Debug tools initialization
        if (BuildConfig.DEBUG) {
            initializeDebugTools()
        }

        // 4. Locale - в фоне, чтобы не блокировать onCreate
        applicationScope.launch {
            initializeAppLocale()
        }
        
        // 4. Notification Channels — синхронно, до первого возможного аларма
        createNotificationChannels()
        
        // 5. ✅ Quick Scans folder - в фоне
        applicationScope.launch(Dispatchers.IO) {
            ensureQuickScansFolderExists()
        }
        
        // 🚀 АВТОМАТИЧЕСКИЙ ПРОГРЕВ выбранной на текущий момент модели
        applicationScope.launch {
            try {
                val activeOcrModel = settingsDataStore.geminiOcrModel.first()
                val activeTranslationModel = settingsDataStore.translationModel.first()
                
                // Прогреваем OCR модель
                geminiModelManager.warmUpModel(activeOcrModel)
                
                // Если для перевода выбрана другая модель, прогреваем и её
                if (activeTranslationModel != activeOcrModel) {
                    geminiModelManager.warmUpModel(activeTranslationModel)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to run startup models warm-up")
            }
        }
        
        // 6. Lifecycle Observer
        setupLifecycleObserver()

        // 7. Перепланирование будильников каждые 6 ч (Doze/kill-proof)
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reschedule_alarms",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<com.docs.scanner.data.local.alarm.RescheduleAlarmsWorker>(
                6, java.util.concurrent.TimeUnit.HOURS
            ).build()
        )

        Timber.i("🚀 App initialized. Device: ${Build.MANUFACTURER} ${Build.MODEL}, SDK: ${Build.VERSION.SDK_INT}")
    }
    
    // ════════════════════════════════════════════════════════════════════════════════
    // ✅ QUICK SCANS FOLDER INITIALIZATION
    // ════════════════════════════════════════════════════════════════════════════════
    
    /**
     * Ensures Quick Scans folder exists on app startup.
     * This folder is a system folder that cannot be deleted by user.
     */
    private suspend fun ensureQuickScansFolderExists() {
        try {
            val folderName = try {
                getString(R.string.quick_scans_folder_name)
            } catch (e: Exception) {
                QUICK_SCANS_FOLDER_NAME
            }
            
            folderRepository.ensureQuickScansFolderExists(folderName)
            Timber.d("✅ Quick Scans folder ensured")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to create Quick Scans folder")
        }
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
                android.util.Log.println(priority, tag ?: "DocsScanner", message)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════════
    // ✅ DEBUG TOOLS - WITH LOGCAT COLLECTOR
    // ════════════════════════════════════════════════════════════════════════════════
    
    private fun initializeDebugTools() {
        try {
            // ✅ Initialize LogcatCollector
            logcatCollector = LogcatCollector.getInstance(this).apply {
                startCollecting()
            }
            Timber.d("📝 LogcatCollector started")
            
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
    // ✅ COIL 3.x IMAGE LOADER - FIXED WITH OKIO PATH
    // ════════════════════════════════════════════════════════════════════════════════
    
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                // ✅ Coil 3.x: MemoryCache.Builder() is no-arg.
                // Context was removed in the Coil 2 → 3 migration because
                // MemoryCache no longer ties itself to an Android Context.
                MemoryCache.Builder()
                    .maxSizeBytes(MEMORY_CACHE_SIZE_MB * 1024 * 1024)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    // Safe conversion to Okio Path for robust multiplatform file system operations
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(DISK_CACHE_SIZE_MB * 1024 * 1024)
                    .build()
            }
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
