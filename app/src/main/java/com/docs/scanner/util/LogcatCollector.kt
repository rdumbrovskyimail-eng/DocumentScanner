package com.docs.scanner.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class LogcatCollector(private val context: Context) {
    
    private var logcatProcess: Process? = null
    private var collectJob: Job? = null
    private val logBuffer = StringBuilder()
    private val maxBufferSize = 5 * 1024 * 1024 // 5MB
    
    companion object {
        @Volatile
        private var instance: LogcatCollector? = null
        
        fun getInstance(context: Context): LogcatCollector {
            return instance ?: synchronized(this) {
                instance ?: LogcatCollector(context.applicationContext).also { instance = it }
            }
        }
        
        private const val MAX_OLD_LOGS = 10 // Оставляем только последние 10 логов
    }
    
    fun startCollecting() {
        if (collectJob?.isActive == true) return
        
        collectJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Runtime.getRuntime().exec("logcat -c").waitFor()
                delay(50)
                
                val pid = android.os.Process.myPid()
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "threadtime", "--pid=$pid", "-b", "main,system,crash")
                )
                
                val reader = BufferedReader(InputStreamReader(logcatProcess!!.inputStream), 16384)
                
                while (isActive) {
                    val line = reader.readLine() ?: break
                    synchronized(logBuffer) {
                        logBuffer.append(line).append("\n")
                        if (logBuffer.length > maxBufferSize) {
                            logBuffer.delete(0, logBuffer.length - maxBufferSize)
                        }
                    }
                }
            } catch (e: Exception) {
                synchronized(logBuffer) {
                    logBuffer.append("\n=== COLLECTOR ERROR ===\n${e.stackTraceToString()}\n")
                }
            }
        }
        
        setupCrashHandler()
    }
    
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                synchronized(logBuffer) {
                    logBuffer.append("\n\n=== CRASH ===\n")
                    logBuffer.append("Thread: ${thread.name}\n")
                    logBuffer.append(throwable.stackTraceToString())
                }
                saveLogsToFileBlocking()
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    fun stopCollecting() {
        collectJob?.cancel()
        logcatProcess?.destroy()
        saveLogsToFileBlocking()
    }
    
    fun forceSave() {
        CoroutineScope(Dispatchers.IO).launch {
            saveLogsToFileBlocking()
        }
    }
    
    private fun saveLogsToFileBlocking() {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val fileName = "logcat_$timestamp.txt"
            
            val logContent = synchronized(logBuffer) {
                buildString {
                    append("=== DocumentScanner Log ===\n")
                    append("Time: $timestamp\n")
                    append("Device: \( {Build.MANUFACTURER} \){Build.MODEL} (Android ${Build.VERSION.RELEASE})\n")
                    append("Package: ${context.packageName}\n\n")
                    append(logBuffer.toString())
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let { outputUri ->
                    resolver.openOutputStream(outputUri)?.use { stream ->
                        stream.write(logContent.toByteArray())
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { it.write(logContent.toByteArray()) }
            }
            
            cleanOldLogs()
            
        } catch (e: Exception) {
            android.util.Log.e("LogcatCollector", "Failed to save logs", e)
        }
    }
    
    private fun cleanOldLogs() {
        try {
            val downloadsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            
            downloadsDir.listFiles { file ->
                file.name.startsWith("logcat_") && file.name.endsWith(".txt")
            }?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_OLD_LOGS)
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            // Игнорируем ошибки очистки
        }
    }
}