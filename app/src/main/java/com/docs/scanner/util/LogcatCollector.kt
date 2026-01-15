/**
 * LogcatCollector - Smart Error & Warning Logger
 * ✅ Captures: ALL ERRORS (E), ALL WARNINGS (W), CRASHES
 * ✅ Filters out: Debug (D), Info (I), Verbose (V)
 * 
 * Version: 2.0.0 - ERROR-FOCUSED LOGGING
 */
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

class LogcatCollector private constructor(private val context: Context) {
    
    private var logcatProcess: Process? = null
    private var collectJob: Job? = null
    private var autoSaveJob: Job? = null
    private val logBuffer = StringBuilder()
    private val maxBufferSize = 3 * 1024 * 1024 // 3MB для ошибок
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
        
        private const val MAX_LOG_FILES = 10 // Больше файлов для ошибок
        private const val AUTO_SAVE_INTERVAL_MS = 30_000L // 30 секунд (реже для ошибок)
        
        // ✅ KEYWORDS для важных событий (не ошибок, но важных)
        private val IMPORTANT_KEYWORDS = setOf(
            "OCR", "ML Kit", "crash", "ANR", "memory", "permission",
            "database", "network", "timeout", "cancelled", "failed"
        )
    }
    
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
     * ✅ SMART FILTER: Только ERROR (E) и WARNING (W) для ВАШЕГО приложения
     * Плюс важные ключевые слова из всех логов
     */
    fun startCollecting() {
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
                val logsDir = getLogsDir()
                val testFile = File(logsDir, ".test_write")
                try {
                    testFile.writeText("test")
                    testFile.delete()
                    android.util.Log.i("LogcatCollector", "✅ Write test passed")
                } catch (e: Exception) {
                    android.util.Log.e("LogcatCollector", "❌ CRITICAL: Cannot write to logs dir!", e)
                }
                
                // Clear buffer
                Runtime.getRuntime().exec("logcat -c").waitFor()
                delay(50)
                
                val pid = android.os.Process.myPid()
                
                // ✅ CRITICAL FIX: Фильтр только ERROR и WARNING
                // *:W означает "все Warning и выше (Warning, Error, Fatal)"
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf(
                        "logcat",
                        "-v", "threadtime",
                        "--pid=$pid",      // Только наше приложение
                        "*:W",             // ✅ WARNING и выше (W, E, F)
                        "-b", "main,system,crash"
                    )
                )
                
                val reader = BufferedReader(
                    InputStreamReader(logcatProcess!!.inputStream), 
                    16384
                )
                
                Timber.d("✅ LogcatCollector started - ERRORS & WARNINGS ONLY (PID: $pid)")
                android.util.Log.i("LogcatCollector", "✅ Smart filter active: W/E/F only")
                android.util.Log.i("LogcatCollector", "📁 Logs: ${logsDir.absolutePath}")
                
                var lineCount = 0
                while (isActive) {
                    val line = reader.readLine() ?: break
                    
                    // ✅ Double-check: только W/E/F строки
                    if (isImportantLine(line)) {
                        synchronized(logBuffer) {
                            logBuffer.append(line).append("\n")
                            lineCount++
                            
                            // Prevent memory overflow
                            if (logBuffer.length > maxBufferSize) {
                                logBuffer.delete(0, logBuffer.length - maxBufferSize)
                            }
                        }
                        
                        // Log each error immediately to console for debugging
                        if (line.contains(" E ") || line.contains(" F ")) {
                            android.util.Log.e("LogcatCollector", "📝 Captured: ${line.take(100)}")
                        }
                    }
                }
                
                android.util.Log.i("LogcatCollector", "📊 Total lines captured: $lineCount")
                
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
     * ✅ SMART FILTER: Проверка важности строки
     * 
     * Пропускает:
     * - Все ERROR (E)
     * - Все WARNING (W)
     * - Все FATAL (F)
     * - Строки с важными ключевыми словами
     */
    private fun isImportantLine(line: String): Boolean {
        // Формат logcat threadtime: MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: message
        
        // ✅ Быстрая проверка уровня (W, E, F)
        if (line.contains(" W ") || line.contains(" E ") || line.contains(" F ")) {
            return true
        }
        
        // ✅ Проверка важных ключевых слов (case-insensitive)
        val lowerLine = line.lowercase()
        if (IMPORTANT_KEYWORDS.any { keyword -> lowerLine.contains(keyword.lowercase()) }) {
            return true
        }
        
        // ✅ Проверка на stack trace (обычно идут после ошибок)
        if (line.trimStart().startsWith("at ") || 
            line.trimStart().startsWith("Caused by:") ||
            line.contains("Exception") ||
            line.contains("Error:")) {
            return true
        }
        
        return false
    }
    
    /**
     * ✅ Auto-save every 30 seconds (реже, т.к. только ошибки)
     */
    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            delay(AUTO_SAVE_INTERVAL_MS)
            
            while (isActive) {
                try {
                    // Сохраняем только если есть новые данные
                    val bufferSize = synchronized(logBuffer) { logBuffer.length }
                    if (bufferSize > 1000) { // Минимум 1KB для сохранения
                        saveLogsToFileBlocking(isAutoSave = true)
                        android.util.Log.i("LogcatCollector", "💾 Auto-saved ($bufferSize bytes)")
                    }
                    delay(AUTO_SAVE_INTERVAL_MS)
                } catch (e: Exception) {
                    android.util.Log.e("LogcatCollector", "❌ Auto-save failed", e)
                    delay(AUTO_SAVE_INTERVAL_MS)
                }
            }
        }
        
        android.util.Log.i("LogcatCollector", "✅ Auto-save started (every 30s)")
    }
    
    /**
     * ✅ Crash handler - SYNCHRONOUS save
     */
    private fun setupCrashHandler() {
        if (!BuildConfig.DEBUG) return
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("LogcatCollector", "💥 CRASH DETECTED: ${throwable.message}", throwable)
                
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
                
                saveLogsToFileBlocking(isCrash = true)
                Thread.sleep(500)
                
            } catch (e: Exception) {
                android.util.Log.e("LogcatCollector", "❌ CRITICAL: Failed to save crash log", e)
                
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
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        
        Timber.d("✅ Crash handler installed")
        android.util.Log.i("LogcatCollector", "✅ Crash handler active")
    }
    
    fun stopCollecting() {
        if (!BuildConfig.DEBUG) return
        
        Timber.d("⏹️ Stopping LogcatCollector")
        android.util.Log.i("LogcatCollector", "⏹️ Stopping collection")
        
        autoSaveJob?.cancel()
        collectJob?.cancel()
        logcatProcess?.destroy()
        
        saveLogsToFileBlocking()
    }
    
    fun forceSave() {
        if (!BuildConfig.DEBUG) return
        
        Timber.d("💾 Force saving logs")
        android.util.Log.i("LogcatCollector", "💾 Force save triggered")
        
        CoroutineScope(Dispatchers.IO).launch {
            saveLogsToFileBlocking()
        }
    }
    
    /**
     * ✅ BLOCKING save with metadata
     */
    private fun saveLogsToFileBlocking(isCrash: Boolean = false, isAutoSave: Boolean = false) {
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
            val fileName = "errors_$timestamp$suffix.txt" // ✅ Renamed for clarity
            
            android.util.Log.i("LogcatCollector", "📄 Creating file: $fileName")
            
            val logContent = synchronized(logBuffer) {
                val bufferSize = logBuffer.length
                android.util.Log.i("LogcatCollector", "📊 Buffer size: $bufferSize bytes")
                
                buildString {
                    append("=".repeat(80)).append("\n")
                    append("DocumentScanner - ERROR & WARNING Log\n")
                    append("=".repeat(80)).append("\n")
                    append("Timestamp: $timestamp\n")
                    append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                    append("Package: ${context.packageName}\n")
                    append("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    append("App PID: ${android.os.Process.myPid()}\n")
                    append("Filter: WARNING (W) + ERROR (E) + FATAL (F) only\n")
                    if (isCrash) {
                        append("⚠️ TYPE: CRASH LOG\n")
                    } else if (isAutoSave) {
                        append("TYPE: Auto-save\n")
                    }
                    append("=".repeat(80)).append("\n\n")
                    
                    if (logBuffer.isEmpty()) {
                        append("✅ No errors or warnings captured!\n")
                        append("This is good news - your app is running smoothly.\n")
                    } else {
                        append(logBuffer.toString())
                    }
                }
            }
            
            android.util.Log.i("LogcatCollector", "📝 Content size: ${logContent.length} bytes")
            
            val logsDir = getLogsDir()
            android.util.Log.i("LogcatCollector", "📁 Target dir: ${logsDir.absolutePath}")
            
            val file = File(logsDir, fileName)
            android.util.Log.i("LogcatCollector", "💾 Writing to: ${file.absolutePath}")
            
            file.writeText(logContent)
            
            val fileExists = file.exists()
            val fileSize = if (fileExists) file.length() else 0
            android.util.Log.i("LogcatCollector", "✅ File written - exists: $fileExists, size: $fileSize bytes")
            
            val message = if (isCrash) {
                "💥 CRASH LOG SAVED: ${file.absolutePath}"
            } else if (isAutoSave) {
                "💾 Auto-save: ${file.name} ($fileSize bytes)"
            } else {
                "✅ Logs saved: ${file.absolutePath} ($fileSize bytes)"
            }
            
            Timber.d(message)
            android.util.Log.i("LogcatCollector", message)
            
            if (!isCrash) {
                cleanOldLogs()
            }
            
        } catch (e: Exception) {
            val errorMsg = "❌ Failed to save logs: ${e.message}"
            Timber.e(e, errorMsg)
            android.util.Log.e("LogcatCollector", errorMsg, e)
            
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
     * ✅ Keep crash logs, delete old regular logs
     */
    private fun cleanOldLogs() {
        try {
            val logsDir = getLogsDir()
            
            val (crashLogs, regularLogs) = logsDir.listFiles { file ->
                file.name.startsWith("errors_") && file.name.endsWith(".txt")
            }?.partition { it.name.contains("_CRASH") } ?: return
            
            // Keep ALL crash logs
            regularLogs
                .sortedByDescending { it.lastModified() }
                .drop(MAX_LOG_FILES)
                .forEach { file ->
                    if (file.delete()) {
                        Timber.d("🗑️ Deleted old log: ${file.name}")
                    }
                }
            
            // Delete old autosaves (keep only latest)
            regularLogs
                .filter { it.name.contains("_autosave") }
                .sortedByDescending { it.lastModified() }
                .drop(1)
                .forEach { it.delete() }
                
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Failed to clean old logs")
        }
    }
    
    fun getAllLogFiles(): List<File> {
        return try {
            getLogsDir().listFiles { file ->
                file.name.startsWith("errors_") && file.name.endsWith(".txt")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
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
                putExtra(Intent.EXTRA_SUBJECT, "DocumentScanner Error Logs")
                putExtra(Intent.EXTRA_TEXT, "Error logs from ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to create share intent")
            null
        }
    }
    
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
            android.util.Log.i("LogcatCollector", "🗑️ All logs cleared")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to clear logs")
        }
    }
    
    fun getBufferSize(): Int {
        return synchronized(logBuffer) { logBuffer.length }
    }
}