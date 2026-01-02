/*
 * DocumentScanner - App.kt
 * Application class оптимизированный для Document Scanner
 *
 * Версия: 3.1.0 - Performance Optimized
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
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.size.Precision
import com.docs.scanner.data.local.preferences.SettingsDataStore
import com.docs.scanner.util.LogcatCollector
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    // Используем Lazy инициализацию для Debug инструментов
    private var logcatCollector: LogcatCollector? = null
    
    // ОПТИМИЗАЦИЯ: Default Dispatcher безопаснее для глобального скоупа
    // Default лучше для CPU-intensive задач, Main - для координации
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        // Notification Channel IDs
        const val CHANNEL_TERM_REMINDERS = "term_reminders"
        const val CHANNEL_SCAN_PROGRESS = "scan_progress"
        const val CHANNEL_BACKUP_RESTORE = "backup_restore"
        const val CHANNEL_GENERAL = "general"
        
        const val GROUP_REMINDERS = "group_reminders"
        const val GROUP_OPERATIONS = "group_operations"
        
        // Memory thresholds for image loading
        private const val MEMORY_CACHE_PERCENT = 0.20 // 20% для сканера (документы могут быть большими)
        private const val DISK_CACHE_SIZE_MB = 100L // 100 MB для кэша сканов
    }

    override fun onCreate() {
        super.onCreate()

        // ОПТИМИЗАЦИЯ: Порядок инициализации по приоритету
        // 1. Logging - должен быть первым для отлова всех событий
        initializeTimber()
        
        // 2. Debug Tools - только в DEBUG режиме
        if (BuildConfig.DEBUG) {
            initializeDebugTools()
        }

        // 3. Locale - критично для UI, инициализируем рано
        initializeAppLocale()
        
        // 4. Notification Channels - можно отложить, но делаем синхронно для надежности
        createNotificationChannels()
        
        // 5. Lifecycle Observer - настраиваем мониторинг жизненного цикла
        setupLifecycleObserver()

        Timber.i("🚀 App initialized. Device: ${Build.MANUFACTURER} ${Build.MODEL}, SDK: ${Build.VERSION.SDK_INT}")
    }

    // ============================================================================
    // LOGGING
    // ============================================================================
    
    private fun initializeTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    // Добавляем номер строки для быстрого дебага
                    return "DocsScanner:${super.createStackElementTag(element)}:${element.lineNumber}"
                }
                
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // В DEBUG режиме логируем все, включая VERBOSE
                    super.log(priority, tag, message, t)
                }
            })
            Timber.d("🌲 Timber initialized in DEBUG mode")
        } else {
            // Production: только ошибки и критичные события
            Timber.plant(ReleaseTree())
            Timber.i("🌲 Timber initialized in RELEASE mode")
        }
    }
    
    /**
     * Production Timber Tree для отправки логов в аналитику
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // Логируем только WARNING и выше
            if (priority >= android.util.Log.WARN) {
                // TODO: Интеграция с Firebase Crashlytics
                // FirebaseCrashlytics.getInstance().log("$tag: $message")
                // t?.let { FirebaseCrashlytics.getInstance().recordException(it) }
                
                // Пока просто логируем в систему
                android.util.Log.println(priority, tag ?: "DocsScanner", message)
            }
        }
    }

    // ============================================================================
    // DEBUG TOOLS
    // ============================================================================
    
    private fun initializeDebugTools() {
        try {
            logcatCollector = LogcatCollector.getInstance(this).apply {
                startCollecting()
            }
            
            enableStrictMode()
            
            // TODO: LeakCanary для отлова утечек памяти
            // if (!LeakCanary.isInAnalyzerProcess(this)) {
            //     LeakCanary.install(this)
            // }
            
            Timber.d("🔧 Debug tools initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize debug tools")
        }
    }

    private fun enableStrictMode() {
        // Thread Policy: отслеживание операций на главном потоке
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
                // .penaltyDeath() // Раскомментировать для строгого режима
                .build()
        )
        
        // VM Policy: отслеживание утечек
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

    // ============================================================================
    // NOTIFICATION CHANNELS
    // ============================================================================

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java) ?: return
            
            try {
                // Сначала создаем группы
                createChannelGroups(notificationManager)
                
                // Затем batch-создаем каналы
                val channels = listOf(
                    createTermRemindersChannel(),
                    createScanProgressChannel(),
                    createBackupRestoreChannel(),
                    createGeneralChannel()
                )
                
                notificationManager.createNotificationChannels(channels)
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
        // Для прогресс-баров звук и вибрация не нужны
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

    // ============================================================================
    // APP LOCALE
    // ============================================================================
    
    private fun initializeAppLocale() {
        // ОПТИМИЗАЦИЯ: Android 13+ хранит настройки локали сам через per-app language API
        // Для старых версий делаем асинхронную загрузку из DataStore
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: система сама управляет локалью, но синхронизируем с DataStore
            applicationScope.launch {
                try {
                    val systemLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                    val savedLocale = settingsDataStore.appLanguage.first()
                    
                    // Если пользователь менял локаль через системные настройки, обновляем DataStore
                    if (systemLocale.isNotEmpty() && systemLocale != savedLocale) {
                        settingsDataStore.setAppLanguage(systemLocale)
                        Timber.d("🌍 Synced system locale to DataStore: $systemLocale")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to sync locale")
                }
            }
        } else {
            // Android 12 и ниже: загружаем из DataStore и применяем вручную
            applicationScope.launch {
                try {
                    val savedLocale = settingsDataStore.appLanguage.first()
                    
                    if (savedLocale.isNotEmpty()) {
                        // Переключаемся на Main поток только для вызова UI API
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
    }

    /**
     * Установить язык приложения
     * @param languageTag BCP 47 language tag (например, "en", "ru", "uk") или пустая строка для сброса
     */
    fun setAppLocale(languageTag: String) {
        try {
            val localeList = if (languageTag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTag)
            }
            
            AppCompatDelegate.setApplicationLocales(localeList)
            
            // Сохраняем в DataStore асинхронно
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

    // ============================================================================
    // COIL IMAGE LOADER
    // ============================================================================
    
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    // ОПТИМИЗАЦИЯ: Для сканера документов используем 20% памяти
                    // (документы могут быть большими, но их обычно меньше, чем фото)
                    .maxSizePercent(context, percent = MEMORY_CACHE_PERCENT)
                    .strongReferencesEnabled(true) // Удерживаем активные изображения
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_SIZE_MB * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(enable = true)
            .crossfade(durationMillis = 200) // Короткий crossfade для быстрого отклика
            // КРИТИЧНО ДЛЯ СКАНЕРА: RGB_565 экономит 50% памяти
            // Документы обычно не требуют прозрачности (alpha channel)
            .components {
                add(coil3.decode.BitmapFactoryDecoder.Factory(
                    bitmapConfig = Bitmap.Config.RGB_565
                ))
            }
            .respectCacheHeaders(false) // Принудительное кэширование
            .allowHardware(enable = true) // Hardware Bitmap для экономии памяти
            .precision(Precision.AUTOMATIC) // Автоматический подбор размера
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(coil3.util.DebugLogger())
                }
            }
            .build()
    }

    // ============================================================================
    // LIFECYCLE
    // ============================================================================
    
    private fun setupLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            AppLifecycleObserver(
                onAppForegrounded = {
                    Timber.d("📱 App moved to FOREGROUND")
                    // Можно запустить синхронизацию, проверку обновлений и т.д.
                },
                onAppBackgrounded = {
                    Timber.d("🌙 App moved to BACKGROUND")
                    // Здесь можно сбросить кэши, сохранить состояние
                    performBackgroundCleanup()
                }
            )
        )
    }
    
    /**
     * Очистка при переходе в background
     */
    private fun performBackgroundCleanup() {
        applicationScope.launch {
            try {
                // Trim memory для освобождения ресурсов
                onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
                
                // Можно добавить очистку старых кэшей
                // cleanOldCacheFiles()
                
                Timber.d("🧹 Background cleanup completed")
            } catch (e: Exception) {
                Timber.e(e, "Error during background cleanup")
            }
        }
    }
    
    /**
     * Реакция на нехватку памяти
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // Критическая нехватка памяти - очищаем все кэши
                Timber.w("⚠️ Critical memory pressure - clearing caches")
                // Coil автоматически очистит свой кэш
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // UI скрыт - можно освободить UI-ресурсы
                Timber.d("UI hidden - trimming memory")
            }
        }
    }
    
    /**
     * Cleanup при завершении процесса
     * ВАЖНО: onTerminate() вызывается только в эмуляторе/тестах
     */
    override fun onTerminate() {
        super.onTerminate()
        cleanupResources()
    }
    
    private fun cleanupResources() {
        try {
            logcatCollector?.stopCollecting()
            logcatCollector = null
            Timber.d("🧹 Resources cleaned up")
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