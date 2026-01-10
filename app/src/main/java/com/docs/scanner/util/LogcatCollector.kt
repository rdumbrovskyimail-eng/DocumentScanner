package com.docs.scanner.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.docs.scanner.BuildConfig
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Collects logcat output for debugging purposes.
 * ⚠️ ONLY WORKS IN DEBUG MODE for security and battery preservation.
 * 
 * ✅ FIXED (Session 14 - CRASH SAFETY):
 * - Logs in DOWNLOADS: Download/DocumentScanner_Logs/
 * - Saves IMMEDIATELY on critical events
 * - Background auto-save every 10 seconds
 * - Synchronous crash handling
 */
class LogcatCollector private constructor(private val context: Context) {
    
    private var logcatProcess: Process? = null
    private var collectJob: Job? = null
    private var autoSaveJob: Job? = null
    private val logBuffer = StringBuilder()
    private val maxBufferSize = 5 * 1024 * 1024 // 5MB
    private val isSaving = AtomicBoolean(false)
    
    companion object {
        @Volatile
        private var instance: LogcatCollector? = null
        
        fun getInstance(context: Context): LogcatCollector {
            return instance ?: synchronized(this) {
                instance ?: LogcatCollector(context.applicationContext).also { 
                    instance = it 
                }
            }
        }
        
        private const val MAX_LOG_FILES = 5
        private const val AUTO_SAVE_INTERVAL_MS = 10_000L // 10 seconds
    }
    
    /**
     * ✅ DOWNLOADS FOLDER: /storage/emulated/0/Download/DocumentScanner_Logs/
     * 
     * Легко найти: Загрузки → DocumentScanner_Logs
     */
    private fun getLogsDir(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logsDir = File(downloadsDir, "DocumentScanner_Logs")
        
        if (!logsDir.exists()) {
            val created = logsDir.mkdirs()
            android.util.Log.i("LogcatCollector", "📁 Created logs dir: ${logsDir.absolutePath}, success=$created")
        }
        
        return logsDir
    }
    
    /**
     * Start collecting logs.
     * ⚠️ ONLY WORKS IN DEBUG - Returns immediately in release builds.
     */
    fun startCollecting() {
        // 🔴 CRITICAL: Don't collect in release!
        if (!BuildConfig.DEBUG) {
            Timber.d("⚠️ LogcatCollector disabled in release build")
            return
        }
        
        if (collectJob?.isActive == true) {
            Timber.d("⚠️ LogcatCollector already running")
            return
        }
        
        collectJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                // ✅ TEST: Verify we can actually write to logs directory
                val logsDir = getLogsDir()
                val testFile = File(logsDir, ".test_write")
                try {
                    testFile.writeText("test")
                    testFile.delete()
                    android.util.Log.i("LogcatCollector", "✅ Write test passed")
                } catch (e: Exception) {
                    android.util.Log.e("LogcatCollector", "❌ CRITICAL: Cannot write to logs dir!", e)
                }
                
                // Clear previous logcat buffer
                Runtime.getRuntime().exec("logcat -c").waitFor()
                delay(50)
                
                // Collect only THIS app's logs
                val pid = android.os.Process.myPid()
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf(
                        "logcat",
                        "-v", "threadtime",
                        "--pid=$pid",
                        "-b", "main,system,crash"
                    )
                )
                
                val reader = BufferedReader(
                    InputStreamReader(logcatProcess!!.inputStream), 
                    16384
                )
                
                Timber.d("✅ LogcatCollector started (PID: $pid)")
                Timber.d("📁 Logs directory: ${logsDir.absolutePath}")
                Timber.d("📝 Directory exists: ${logsDir.exists()}, canWrite: ${logsDir.canWrite()}")
                android.util.Log.i("LogcatCollector", "📁 Logs will be saved to: ${logsDir.absolutePath}")
                android.util.Log.i("LogcatCollector", "📝 Directory state - exists: ${logsDir.exists()}, canWrite: ${logsDir.canWrite()}")
                
                while (isActive) {
                    val line = reader.readLine() ?: break
                    synchronized(logBuffer) {
                        logBuffer.append(line).append("\n")
                        
                        // Prevent memory overflow
                        if (logBuffer.length > maxBufferSize) {
                            logBuffer.delete(0, logBuffer.length - maxBufferSize)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ LogcatCollector error")
                android.util.Log.e("LogcatCollector", "❌ Collection error", e)
                synchronized(logBuffer) {
                    logBuffer.append("\n=== COLLECTOR ERROR ===\n")
                    logBuffer.append(e.stackTraceToString())
                    logBuffer.append("\n")
                }
            }
        }
        
        setupCrashHandler()
        startAutoSave()
    }
    
    /**
     * ✅ NEW: Auto-save every 10 seconds to prevent data loss
     */
    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            delay(AUTO_SAVE_INTERVAL_MS) // Initial delay
            
            while (isActive) {
                try {
                    saveLogsToFileBlocking(isAutoSave = true)
                    delay(AUTO_SAVE_INTERVAL_MS)
                } catch (e: Exception) {
                    android.util.Log.e("LogcatCollector", "❌ Auto-save failed", e)
                    delay(AUTO_SAVE_INTERVAL_MS)
                }
            }
        }
        
        android.util.Log.i("LogcatCollector", "✅ Auto-save started (every 10s)")
    }
    
    /**
     * Setup crash handler to save logs on app crash.
     * ✅ FIX: SYNCHRONOUS save, no coroutines in crash handler
     */
    private fun setupCrashHandler() {
        if (!BuildConfig.DEBUG) return
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("LogcatCollector", "💥 CRASH DETECTED: ${throwable.message}", throwable)
                
                // ✅ CRITICAL: Append crash info BEFORE saving
                synchronized(logBuffer) {
                    logBuffer.append("\n\n")
                    logBuffer.append("=".repeat(80)).append("\n")
                    logBuffer.append("💥 APPLICATION CRASHED\n")
                    logBuffer.append("=".repeat(80)).append("\n")
                    logBuffer.append("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}\n")
                    logBuffer.append("Thread: ${thread.name}\n")
                    logBuffer.append("Exception: ${throwable.javaClass.name}\n")
                    logBuffer.append("Message: ${throwable.message}\n")
                    logBuffer.append("\nStack Trace:\n")
                    logBuffer.append(throwable.stackTraceToString())
                    logBuffer.append("\n")
                    logBuffer.append("=".repeat(80)).append("\n")
                }
                
                // ✅ CRITICAL: SYNCHRONOUS save (blocking)
                saveLogsToFileBlocking(isCrash = true)
                
                // ✅ Give time for file system to flush
                Thread.sleep(500)
                
            } catch (e: Exception) {
                android.util.Log.e("LogcatCollector", "❌ CRITICAL: Failed to save crash log", e)
                
                // ✅ EMERGENCY: Try to save to cache dir as last resort
                try {
                    val emergencyFile = File(context.cacheDir, "CRASH_${System.currentTimeMillis()}.txt")
                    synchronized(logBuffer) {
                        emergencyFile.writeText(logBuffer.toString())
                    }
                    android.util.Log.e("LogcatCollector", "✅ Emergency save to: ${emergencyFile.absolutePath}")
                } catch (e2: Exception) {
                    android.util.Log.e("LogcatCollector", "❌ CRITICAL: Emergency save also failed", e2)
                }
            } finally {
                // ✅ Call original handler to finish the crash
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        
        Timber.d("✅ Crash handler installed")
        android.util.Log.i("LogcatCollector", "✅ Crash handler active")
    }
    
    /**
     * Stop collecting logs and save to file.
     */
    fun stopCollecting() {
        if (!BuildConfig.DEBUG) return
        
        Timber.d("⏹️ Stopping LogcatCollector")
        android.util.Log.i("LogcatCollector", "⏹️ Stopping collection")
        
        autoSaveJob?.cancel()
        collectJob?.cancel()
        logcatProcess?.destroy()
        
        saveLogsToFileBlocking()
    }
    
    /**
     * Force immediate save without stopping collection.
     */
    fun forceSave() {
        if (!BuildConfig.DEBUG) return
        
        Timber.d("💾 Force saving logs")
        android.util.Log.i("LogcatCollector", "💾 Force save triggered")
        
        CoroutineScope(Dispatchers.IO).launch {
            saveLogsToFileBlocking()
        }
    }
    
    /**
     * ✅ CRITICAL: BLOCKING save - works even during crash
     * 
     * @param isCrash If true, adds "_CRASH" to filename for easy identification
     * @param isAutoSave If true, uses "_autosave" suffix (will be overwritten)
     */
    private fun saveLogsToFileBlocking(isCrash: Boolean = false, isAutoSave: Boolean = false) {
        // ✅ Prevent concurrent saves
        if (!isSaving.compareAndSet(false, true)) {
            android.util.Log.w("LogcatCollector", "⚠️ Save already in progress, skipping")
            return
        }
        
        android.util.Log.i("LogcatCollector", "💾 Starting save (crash=$isCrash, auto=$isAutoSave)")
        
        try {
            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss", 
                Locale.getDefault()
            ).format(Date())
            
            val suffix = when {
                isCrash -> "_CRASH"
                isAutoSave -> "_autosave"
                else -> ""
            }
            val fileName = "logcat_$timestamp$suffix.txt"
            
            android.util.Log.i("LogcatCollector", "📄 Creating file: $fileName")
            
            val logContent = synchronized(logBuffer) {
                val bufferSize = logBuffer.length
                android.util.Log.i("LogcatCollector", "📊 Buffer size: $bufferSize bytes")
                
                buildString {
                    append("=".repeat(80)).append("\n")
                    append("DocumentScanner Debug Log\n")
                    append("=".repeat(80)).append("\n")
                    append("Timestamp: $timestamp\n")
                    append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                    append("Package: ${context.packageName}\n")
                    append("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    append("App PID: ${android.os.Process.myPid()}\n")
                    if (isCrash) {
                        append("⚠️ TYPE: CRASH LOG\n")
                    } else if (isAutoSave) {
                        append("TYPE: Auto-save\n")
                    }
                    append("=".repeat(80)).append("\n\n")
                    append(logBuffer.toString())
                }
            }
            
            android.util.Log.i("LogcatCollector", "📝 Content size: ${logContent.length} bytes")
            
            // ✅ ИСПРАВЛЕНИЕ: Объявляем logsDir только ОДИН раз
            val logsDir = getLogsDir()
            android.util.Log.i("LogcatCollector", "📁 Target dir: ${logsDir.absolutePath}")
            android.util.Log.i("LogcatCollector", "📂 Dir exists: ${logsDir.exists()}, canWrite: ${logsDir.canWrite()}")
            
            val file = File(logsDir, fileName)
            android.util.Log.i("LogcatCollector", "💾 Writing to: ${file.absolutePath}")
            
            file.writeText(logContent)
            
            val fileExists = file.exists()
            val fileSize = if (fileExists) file.length() else 0
            android.util.Log.i("LogcatCollector", "✅ File written - exists: $fileExists, size: $fileSize bytes")
            
            val message = if (isCrash) {
                "💥 CRASH LOG SAVED: ${file.absolutePath}"
            } else if (isAutoSave) {
                "💾 Auto-save: ${file.name}"
            } else {
                "✅ Logs saved: ${file.absolutePath}"
            }
            
            Timber.d(message)
            android.util.Log.i("LogcatCollector", message)
            
            // ✅ Cleanup old logs (but not during crash to save time)
            if (!isCrash) {
                cleanOldLogs()
            }
            
        } catch (e: Exception) {
            val errorMsg = "❌ Failed to save logs: ${e.message}"
            Timber.e(e, errorMsg)
            android.util.Log.e("LogcatCollector", errorMsg, e)
            android.util.Log.e("LogcatCollector", "Stack trace:", e)
            
            // ✅ EMERGENCY: Try cache dir
            if (isCrash) {
                try {
                    val emergencyFile = File(context.cacheDir, "CRASH_EMERGENCY_${System.currentTimeMillis()}.txt")
                    android.util.Log.i("LogcatCollector", "🚨 Trying emergency save to: ${emergencyFile.absolutePath}")
                    synchronized(logBuffer) {
                        emergencyFile.writeText(logBuffer.toString())
                    }
                    android.util.Log.e("LogcatCollector", "✅ Emergency save: ${emergencyFile.absolutePath}")
                } catch (e2: Exception) {
                    android.util.Log.e("LogcatCollector", "❌ Emergency save failed", e2)
                }
            }
        } finally {
            isSaving.set(false)
        }
    }
    
    /**
     * Delete old log files, keep only MAX_LOG_FILES most recent.
     * ✅ FIX: Don't delete crash logs
     */
    private fun cleanOldLogs() {
        try {
            val logsDir = getLogsDir()
            
            // Separate crash logs from regular logs
            val (crashLogs, regularLogs) = logsDir.listFiles { file ->
                file.name.startsWith("logcat_") && file.name.endsWith(".txt")
            }?.partition { it.name.contains("_CRASH") } ?: return
            
            // Keep ALL crash logs, delete old regular logs
            regularLogs
                .sortedByDescending { it.lastModified() }
                .drop(MAX_LOG_FILES)
                .forEach { file ->
                    if (file.delete()) {
                        Timber.d("🗑️ Deleted old log: ${file.name}")
                    }
                }
            
            // Also delete old autosave files (keep only latest)
            regularLogs
                .filter { it.name.contains("_autosave") }
                .sortedByDescending { it.lastModified() }
                .drop(1)
                .forEach { it.delete() }
                
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Failed to clean old logs")
        }
    }
    
    /**
     * Get list of all saved log files.
     */
    fun getAllLogFiles(): List<File> {
        return try {
            getLogsDir().listFiles { file ->
                file.name.startsWith("logcat_") && file.name.endsWith(".txt")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Share log file via system share dialog.
     */
    fun shareLogs(file: File): Intent? {
        if (!BuildConfig.DEBUG) return null
        
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "DocumentScanner Debug Logs")
                putExtra(Intent.EXTRA_TEXT, "Debug logs from ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to create share intent")
            null
        }
    }
    
    /**
     * Delete all log files.
     */
    fun clearAllLogs() {
        if (!BuildConfig.DEBUG) return
        
        try {
            getLogsDir().listFiles()?.forEach { file ->
                file.delete()
            }
            synchronized(logBuffer) {
                logBuffer.clear()
            }
            Timber.d("🗑️ All logs cleared")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to clear logs")
        }
    }
    
    /**
     * Get current buffer size for debugging.
     */
    fun getBufferSize(): Int {
        return synchronized(logBuffer) { logBuffer.length }
    }
}