package com.docs.scanner.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.docs.scanner.BuildConfig
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Collects logcat output for debugging purposes.
 * ⚠️ ONLY WORKS IN DEBUG MODE for security and battery preservation.
 * 
 * Session 11 fixes:
 * - ✅ BuildConfig.DEBUG check (CRITICAL SECURITY)
 * - ✅ Internal storage (no WRITE_EXTERNAL_STORAGE needed)
 * - ✅ shareLogs() function for DebugScreen
 * - ✅ cleanOldLogs() optimization
 * - ✅ Proper coroutine cleanup
 */
class LogcatCollector private constructor(private val context: Context) {
    
    private var logcatProcess: Process? = null
    private var collectJob: Job? = null
    private val logBuffer = StringBuilder()
    private val maxBufferSize = 5 * 1024 * 1024 // 5MB
    
    // ✅ Internal storage directory (no permission needed)
    private val logsDir: File
        get() = File(context.filesDir, "logs").apply { mkdirs() }
    
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
        
        private const val MAX_OLD_LOGS = 10
        private const val MAX_LOG_FILES = 5 // Keep only 5 most recent
    }
    
    /**
     * Start collecting logs.
     * ⚠️ ONLY WORKS IN DEBUG - Returns immediately in release builds.
     */
    fun startCollecting() {
        // 🔴 CRITICAL FIX: Don't collect in release!
        if (!BuildConfig.DEBUG) return
        
        if (collectJob?.isActive == true) return
        
        collectJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
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
                synchronized(logBuffer) {
                    logBuffer.append("\n=== COLLECTOR ERROR ===\n")
                    logBuffer.append(e.stackTraceToString())
                    logBuffer.append("\n")
                }
            }
        }
        
        setupCrashHandler()
    }
    
    /**
     * Setup crash handler to save logs on app crash.
     */
    private fun setupCrashHandler() {
        if (!BuildConfig.DEBUG) return
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                synchronized(logBuffer) {
                    logBuffer.append("\n\n=== CRASH ===\n")
                    logBuffer.append("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                    logBuffer.append("Thread: ${thread.name}\n")
                    logBuffer.append(throwable.stackTraceToString())
                }
                saveLogsToFileBlocking()
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    /**
     * Stop collecting logs and save to file.
     */
    fun stopCollecting() {
        if (!BuildConfig.DEBUG) return
        
        collectJob?.cancel()
        logcatProcess?.destroy()
        saveLogsToFileBlocking()
    }
    
    /**
     * Force immediate save without stopping collection.
     */
    fun forceSave() {
        if (!BuildConfig.DEBUG) return
        
        CoroutineScope(Dispatchers.IO).launch {
            saveLogsToFileBlocking()
        }
    }
    
    /**
     * Save logs to internal storage.
     * ✅ FIX: Uses internal storage (no permissions needed)
     */
    private fun saveLogsToFileBlocking() {
        try {
            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss", 
                Locale.getDefault()
            ).format(Date())
            val fileName = "logcat_$timestamp.txt"
            
            val logContent = synchronized(logBuffer) {
                buildString {
                    append("=== DocumentScanner Debug Log ===\n")
                    append("Time: $timestamp\n")
                    append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                    append("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})\n")
                    append("Package: ${context.packageName}\n")
                    append("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    append("\n")
                    append(logBuffer.toString())
                }
            }
            
            // ✅ Save to internal storage
            val file = File(logsDir, fileName)
            file.writeText(logContent)
            
            android.util.Log.d("LogcatCollector", "✅ Logs saved: ${file.absolutePath}")
            
            // Cleanup old logs
            cleanOldLogs()
            
        } catch (e: Exception) {
            android.util.Log.e("LogcatCollector", "❌ Failed to save logs", e)
        }
    }
    
    /**
     * Delete old log files, keep only MAX_LOG_FILES most recent.
     * ✅ FIX: Optimized cleanup logic
     */
    private fun cleanOldLogs() {
        try {
            logsDir.listFiles { file ->
                file.name.startsWith("logcat_") && file.name.endsWith(".txt")
            }?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_LOG_FILES)
                ?.forEach { file ->
                    if (file.delete()) {
                        android.util.Log.d("LogcatCollector", "🗑️ Deleted old log: ${file.name}")
                    }
                }
        } catch (e: Exception) {
            android.util.Log.w("LogcatCollector", "⚠️ Failed to clean old logs", e)
        }
    }
    
    /**
     * Get list of all saved log files.
     * ✅ NEW: For DebugScreen integration (Session 11)
     */
    fun getAllLogFiles(): List<File> {
        return try {
            logsDir.listFiles { file ->
                file.name.startsWith("logcat_") && file.name.endsWith(".txt")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Share log file via system share dialog.
     * ✅ NEW: For DebugScreen "Export Logs" button (Session 11)
     * 
     * @param file Log file to share
     * @return Intent to launch share dialog, or null if failed
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
            android.util.Log.e("LogcatCollector", "❌ Failed to create share intent", e)
            null
        }
    }
    
    /**
     * Delete all log files.
     * ✅ NEW: For DebugScreen "Clear Logs" button
     */
    fun clearAllLogs() {
        if (!BuildConfig.DEBUG) return
        
        try {
            logsDir.listFiles()?.forEach { file ->
                file.delete()
            }
            synchronized(logBuffer) {
                logBuffer.clear()
            }
            android.util.Log.d("LogcatCollector", "🗑️ All logs cleared")
        } catch (e: Exception) {
            android.util.Log.e("LogcatCollector", "❌ Failed to clear logs", e)
        }
    }
    
    /**
     * Get current buffer size for debugging.
     */
    fun getBufferSize(): Int {
        return synchronized(logBuffer) { logBuffer.length }
    }
}