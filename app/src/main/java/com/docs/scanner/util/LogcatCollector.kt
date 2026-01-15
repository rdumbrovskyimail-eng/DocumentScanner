/**
 * LogcatCollector - ULTRA-STRICT: ERRORS ONLY
 * ✅ Captures: ONLY ERRORS (E) and FATAL (F)
 * ❌ Filters out: Debug, Info, Verbose, WARNING
 * 
 * Version: 3.0.0 - ERRORS ONLY
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
    private val maxBufferSize = 512 * 1024 // 512KB - только критичные ошибки
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
        
        private const val MAX_LOG_FILES = 15
        private const val AUTO_SAVE_INTERVAL_MS = 60_000L // 1 минута
        
        // ✅ ТОЛЬКО критичные ключевые слова
        private val CRITICAL_KEYWORDS = setOf(
            "crash", "fatal", "exception", "error", "failed", "cannot",
            "ANR", "OutOfMemory", "StackOverflow", "NullPointer"
        )
        
        // ✅ ИГНОРИРОВАТЬ эти теги (шум от системы)
        private val IGNORED_TAGS = setOf(
            "OpenGLRenderer", "Choreographer", "ViewRootImpl", "InputMethodManager",
            "WindowManager", "ActivityThread", "Surface", "BufferQueue",
            "EGL", "libEGL", "GraphicsEnvironment", "Gralloc", "mali",
            "InputTransport", "View", "Looper", "MessageQueue"
        )
    }
    
    private fun getLogsDir(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logsDir = File(downloadsDir, "DocumentScanner_Logs")
        
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        
        return logsDir
    }
    
    /**
     * ✅ ULTRA-STRICT: Только ERROR и FATAL
     */
    fun startCollecting() {
        if (!BuildConfig.DEBUG) {
            return
        }
        
        if (collectJob?.isActive == true) {
            return
        }
        
        collectJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                // Clear buffer
                Runtime.getRuntime().exec("logcat -c").waitFor()
                delay(100)
                
                val pid = android.os.Process.myPid()
                
                // ✅ CRITICAL: Только ERROR и FATAL (*:E)
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf(
                        "logcat",
                        "-v", "threadtime",
                        "--pid=$pid",
                        "*:E",  // ✅ ТОЛЬКО ERROR и FATAL
                        "-b", "main,crash"  // Только main и crash буферы
                    )
                )
                
                val reader = BufferedReader(
                    InputStreamReader(logcatProcess!!.inputStream), 
                    8192
                )
                
                android.util.Log.e("LogcatCollector", "✅ ULTRA-STRICT MODE: ERRORS ONLY (PID: $pid)")
                android.util.Log.e("LogcatCollector", "📁 Logs: ${getLogsDir().absolutePath}")
                
                var errorCount = 0
                var filteredCount = 0
                
                while (isActive) {
                    val line = reader.readLine() ?: break
                    
                    if (isCriticalError(line)) {
                        synchronized(logBuffer) {
                            logBuffer.append(line).append("\n")
                            errorCount++
                            
                            if (logBuffer.length > maxBufferSize) {
                                logBuffer.delete(0, logBuffer.length - maxBufferSize)
                            }
                        }
                        
                        // Print immediately for debugging
                        android.util.Log.e("LogcatCollector", "🔴 ERROR #$errorCount: ${line.take(120)}")
                    } else {
                        filteredCount++
                    }
                }
                
                android.util.Log.e("LogcatCollector", "📊 Stats: $errorCount errors, $filteredCount filtered")
                
            } catch (e: Exception) {
                android.util.Log.e("LogcatCollector", "❌ Collector crashed", e)
            }
        }
        
        setupCrashHandler()
        startAutoSave()
    }
    
    /**
     * ✅ ULTRA-STRICT FILTER: Только реальные критичные ошибки
     */
    private fun isCriticalError(line: String): Boolean {
        // ✅ 1. Должен быть ERROR или FATAL
        if (!line.contains(" E ") && !line.contains(" F ")) {
            return false
        }
        
        // ✅ 2. Извлечь TAG из строки (формат: MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: message)
        val parts = line.split(" ", limit = 7)
        if (parts.size < 6) return false
        
        val tag = parts[5].removeSuffix(":")
        
        // ✅ 3. ИГНОРИРОВАТЬ системный шум
        if (IGNORED_TAGS.any { tag.contains(it, ignoreCase = true) }) {
            return false
        }
        
        // ✅ 4. ИГНОРИРОВАТЬ "не критичные" ошибки
        val lowerLine = line.lowercase()
        
        // Список не критичных фраз
        val ignoredPhrases = listOf(
            "unable to get provider",
            "not found",
            "no such file",
            "permission denied",
            "timeout",
            "retry",
            "cancelled"
        )
        
        if (ignoredPhrases.any { lowerLine.contains(it) }) {
            return false
        }
        
        // ✅ 5. ОБЯЗАТЕЛЬНО должно содержать критичное слово
        val hasCriticalKeyword = CRITICAL_KEYWORDS.any { keyword ->
            lowerLine.contains(keyword.lowercase())
        }
        
        if (!hasCriticalKeyword) {
            // Разрешить только если это наш package
            if (!line.contains("com.docs.scanner")) {
                return false
            }
        }
        
        // ✅ 6. Это stack trace от предыдущей ошибки?
        if (line.trimStart().startsWith("at ") || 
            line.trimStart().startsWith("Caused by:")) {
            return true
        }
        
        return true
    }
    
    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            delay(AUTO_SAVE_INTERVAL_MS)
            
            while (isActive) {
                try {
                    val bufferSize = synchronized(logBuffer) { logBuffer.length }
                    if (bufferSize > 500) { // Минимум 500 байт
                        saveLogsToFileBlocking(isAutoSave = true)
                        android.util.Log.e("LogcatCollector", "💾 Auto-saved: $bufferSize bytes")
                    } else {
                        android.util.Log.e("LogcatCollector", "✅ No errors to save (buffer: $bufferSize bytes)")
                    }
                    delay(AUTO_SAVE_INTERVAL_MS)
                } catch (e: Exception) {
                    android.util.Log.e("LogcatCollector", "❌ Auto-save failed", e)
                    delay(AUTO_SAVE_INTERVAL_MS)
                }
            }
        }
    }
    
    private fun setupCrashHandler() {
        if (!BuildConfig.DEBUG) return
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("LogcatCollector", "💥 CRASH!", throwable)
                
                synchronized(logBuffer) {
                    logBuffer.append("\n")
                    logBuffer.append("=".repeat(80)).append("\n")
                    logBuffer.append("💥 CRASH: ${throwable.javaClass.simpleName}\n")
                    logBuffer.append("Message: ${throwable.message}\n")
                    logBuffer.append("Thread: ${thread.name}\n")
                    logBuffer.append("\n${throwable.stackTraceToString()}")
                    logBuffer.append("\n").append("=".repeat(80)).append("\n")
                }
                
                saveLogsToFileBlocking(isCrash = true)
                Thread.sleep(300)
                
            } catch (e: Exception) {
                android.util.Log.e("LogcatCollector", "❌ Crash save failed", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    fun stopCollecting() {
        if (!BuildConfig.DEBUG) return
        
        autoSaveJob?.cancel()
        collectJob?.cancel()
        logcatProcess?.destroy()
        
        saveLogsToFileBlocking()
    }
    
    fun forceSave() {
        if (!BuildConfig.DEBUG) return
        
        CoroutineScope(Dispatchers.IO).launch {
            saveLogsToFileBlocking()
        }
    }
    
    private fun saveLogsToFileBlocking(isCrash: Boolean = false, isAutoSave: Boolean = false) {
        if (!isSaving.compareAndSet(false, true)) {
            return
        }
        
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            
            val suffix = when {
                isCrash -> "_CRASH"
                isAutoSave -> "_auto"
                else -> ""
            }
            val fileName = "ERRORS_ONLY_$timestamp$suffix.txt"
            
            val logContent = synchronized(logBuffer) {
                buildString {
                    append("=".repeat(80)).append("\n")
                    append("DocumentScanner - CRITICAL ERRORS ONLY\n")
                    append("=".repeat(80)).append("\n")
                    append("Timestamp: $timestamp\n")
                    append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                    append("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    append("PID: ${android.os.Process.myPid()}\n")
                    append("Filter: ERROR (E) + FATAL (F) ONLY - System noise filtered\n")
                    if (isCrash) append("TYPE: CRASH LOG\n")
                    append("=".repeat(80)).append("\n\n")
                    
                    if (logBuffer.isEmpty()) {
                        append("✅ NO CRITICAL ERRORS!\n")
                        append("Your app is running without critical errors.\n")
                    } else {
                        append(logBuffer.toString())
                    }
                }
            }
            
            val logsDir = getLogsDir()
            val file = File(logsDir, fileName)
            file.writeText(logContent)
            
            android.util.Log.e("LogcatCollector", "✅ Saved: ${file.name} (${file.length()} bytes)")
            
            if (!isCrash) {
                cleanOldLogs()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("LogcatCollector", "❌ Save failed", e)
        } finally {
            isSaving.set(false)
        }
    }
    
    private fun cleanOldLogs() {
        try {
            val logsDir = getLogsDir()
            
            val (crashLogs, regularLogs) = logsDir.listFiles { file ->
                file.name.startsWith("ERRORS_ONLY_") && file.name.endsWith(".txt")
            }?.partition { it.name.contains("_CRASH") } ?: return
            
            regularLogs
                .sortedByDescending { it.lastModified() }
                .drop(MAX_LOG_FILES)
                .forEach { it.delete() }
            
            regularLogs
                .filter { it.name.contains("_auto") }
                .sortedByDescending { it.lastModified() }
                .drop(1)
                .forEach { it.delete() }
                
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    fun getAllLogFiles(): List<File> {
        return try {
            getLogsDir().listFiles { file ->
                file.name.startsWith("ERRORS_ONLY_") && file.name.endsWith(".txt")
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
                putExtra(Intent.EXTRA_SUBJECT, "DocumentScanner Critical Errors")
                putExtra(Intent.EXTRA_TEXT, "Critical error log from ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun clearAllLogs() {
        if (!BuildConfig.DEBUG) return
        
        try {
            getLogsDir().listFiles()?.forEach { it.delete() }
            synchronized(logBuffer) {
                logBuffer.clear()
            }
            android.util.Log.e("LogcatCollector", "🗑️ All logs cleared")
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    fun getBufferSize(): Int {
        return synchronized(logBuffer) { logBuffer.length }
    }
}