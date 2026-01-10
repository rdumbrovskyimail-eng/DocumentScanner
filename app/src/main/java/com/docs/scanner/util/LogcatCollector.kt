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

/**
 * Collects logcat output for debugging purposes.
 * ⚠️ ONLY WORKS IN DEBUG MODE for security and battery preservation.
 * 
 * ✅ FIXED (Session 13):
 * - Saves to /storage/emulated/0/Android/DocumentScanner_Logs/
 * - Directly in Android folder, visible in file manager
 * - Proper crash handler integration
 * - Thread-safe log buffer
 * - Automatic old log cleanup
 */
class LogcatCollector private constructor(private val context: Context) {
    
    private var logcatProcess: Process? = null
    private var collectJob: Job? = null
    private val logBuffer = StringBuilder()
    private val maxBufferSize = 5 * 1024 * 1024 // 5MB
    
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
        
        private const val MAX_LOG_FILES = 5 // Keep only 5 most recent
    }
    
    /**
     * ✅ FIX: Returns /storage/emulated/0/Android/DocumentScanner_Logs/
     * 
     * Path: /storage/emulated/0/Android/DocumentScanner_Logs/
     * 
     * This is directly in the Android folder, visible in file managers.
     */
    private fun getLogsDir(): File {
        val androidDir = File(Environment.getExternalStorageDirectory(), "Android")
        val logsDir = File(androidDir, "DocumentScanner_Logs")
        
        if (!logsDir.exists()) {
            logsDir.mkdirs()
            Timber.d("📁 Created logs directory: ${logsDir.absolutePath}")
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
                Timber.d("📁 Logs will be saved to: ${getLogsDir().absolutePath}")
                
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
     * ✅ FIX: Properly chains to default handler
     */
    private fun setupCrashHandler() {
        if (!BuildConfig.DEBUG) return
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Timber.e(throwable, "💥 CRASH DETECTED")
                
                synchronized(logBuffer) {
                    logBuffer.append("\n\n=== 💥 CRASH ===\n")
                    logBuffer.append("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}\n")
                    logBuffer.append("Thread: ${thread.name}\n")
                    logBuffer.append("Exception: ${throwable.javaClass.simpleName}\n")
                    logBuffer.append("Message: ${throwable.message}\n\n")
                    logBuffer.append(throwable.stackTraceToString())
                }
                
                saveLogsToFileBlocking()
                
            } catch (e: Exception) {
                android.util.Log.e("LogcatCollector", "❌ Failed to save crash log", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        
        Timber.d("✅ Crash handler installed")
    }
    
    /**
     * Stop collecting logs and save to file.
     */
    fun stopCollecting() {
        if (!BuildConfig.DEBUG) return
        
        Timber.d("⏹️ Stopping LogcatCollector")
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
        CoroutineScope(Dispatchers.IO).launch {
            saveLogsToFileBlocking()
        }
    }
    
    /**
     * ✅ FIX: Save logs to /storage/emulated/0/Android/DocumentScanner_Logs/
     * 
     * Output: /storage/emulated/0/Android/DocumentScanner_Logs/logcat_2026-01-10_15-30-45.txt
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
                    append("Timestamp: $timestamp\n")
                    append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                    append("Package: ${context.packageName}\n")
                    append("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    append("App PID: ${android.os.Process.myPid()}\n")
                    append("\n")
                    append(logBuffer.toString())
                }
            }
            
            // ✅ Save directly to Android folder
            val logsDir = getLogsDir()
            val file = File(logsDir, fileName)
            file.writeText(logContent)
            
            Timber.d("✅ Logs saved: ${file.absolutePath}")
            android.util.Log.i("LogcatCollector", "✅ Logs saved: ${file.absolutePath}")
            
            // Cleanup old logs
            cleanOldLogs()
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to save logs")
            android.util.Log.e("LogcatCollector", "❌ Failed to save logs", e)
        }
    }
    
    /**
     * Delete old log files, keep only MAX_LOG_FILES most recent.
     */
    private fun cleanOldLogs() {
        try {
            val logsDir = getLogsDir()
            logsDir.listFiles { file ->
                file.name.startsWith("logcat_") && file.name.endsWith(".txt")
            }?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_LOG_FILES)
                ?.forEach { file ->
                    if (file.delete()) {
                        Timber.d("🗑️ Deleted old log: ${file.name}")
                    }
                }
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