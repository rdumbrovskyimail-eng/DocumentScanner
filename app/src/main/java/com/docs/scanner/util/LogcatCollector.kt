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

/**
 * ✅ СИСТЕМА ЛОГИРОВАНИЯ
 * - Автоматический сбор logcat при запуске приложения
 * - Сохранение при крашах
 * - Кнопка "Save Log" в Settings
 */
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
                instance ?: LogcatCollector(context.applicationContext).also { 
                    instance = it 
                }
            }
        }
    }
    
    fun startCollecting() {
        if (collectJob?.isActive == true) {
            android.util.Log.d("LogcatCollector", "Already collecting")
            return
        }
        
        android.util.Log.d("LogcatCollector", "🚀 Starting logcat collection...")
        
        collectJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Очищаем старые логи
                Runtime.getRuntime().exec("logcat -c").waitFor()
                delay(50)
                
                val pid = android.os.Process.myPid()
                
                // Запускаем logcat только для нашего процесса
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
                
                android.util.Log.d("LogcatCollector", "✅ Collector started for PID: $pid")
                
                while (isActive) {
                    val line = reader.readLine() ?: break
                    
                    synchronized(logBuffer) {
                        logBuffer.append(line).append("\n")
                        
                        // Ограничиваем размер буфера
                        if (logBuffer.length > maxBufferSize) {
                            val excess = logBuffer.length - maxBufferSize
                            logBuffer.delete(0, excess)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LogcatCollector", "❌ Error collecting logs", e)
                synchronized(logBuffer) {
                    logBuffer.append("\n=== ERROR IN COLLECTOR ===\n")
                    logBuffer.append(e.stackTraceToString())
                    logBuffer.append("\n")
                }
            }
        }
        
        // Обработчик крашей
        setupCrashHandler()
    }
    
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("LogcatCollector", "💥 APP CRASHED!", throwable)
                
                synchronized(logBuffer) {
                    logBuffer.append("\n\n")
                    logBuffer.append("=".repeat(60)).append("\n")
                    logBuffer.append("💥 FATAL EXCEPTION in thread: ${thread.name}\n")
                    logBuffer.append("=".repeat(60)).append("\n")
                    logBuffer.append(throwable.stackTraceToString())
                    logBuffer.append("\n")
                }
                
                // Сохраняем логи перед крашем
                saveLogsToFileBlocking()
                
            } catch (e: Exception) {
                android.util.Log.e("LogcatCollector", "Failed to save crash log", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    fun stopCollecting() {
        try {
            android.util.Log.d("LogcatCollector", "⏹️ Stopping collector...")
            collectJob?.cancel()
            logcatProcess?.destroy()
            saveLogsToFileBlocking()
        } catch (e: Exception) {
            android.util.Log.e("LogcatCollector", "Error stopping collector", e)
        }
    }
    
    private fun saveLogsToFileBlocking() {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                .format(Date())
            val fileName = "logcat_$timestamp.txt"
            
            val logContent = synchronized(logBuffer) {
                buildString {
                    append("=".repeat(60)).append("\n")
                    append("📱 Logcat dump: $timestamp\n")
                    append("=".repeat(60)).append("\n")
                    append("Package: ${context.packageName}\n")
                    append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                    append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("RAM: ${Runtime.getRuntime().totalMemory() / 1024 / 1024} MB\n")
                    append("=".repeat(60)).append("\n\n")
                    append(logBuffer.toString())
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - MediaStore
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                
                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, 
                    contentValues
                )
                
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(logContent.toByteArray())
                    }
                    android.util.Log.i("LogcatCollector", "✅ Log saved: $fileName")
                }
            } else {
                // Android 9 и ниже
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val logFile = File(downloadsDir, fileName)
                
                FileOutputStream(logFile).use { fos ->
                    fos.write(logContent.toByteArray())
                }
                
                android.util.Log.i("LogcatCollector", "✅ Log saved: ${logFile.absolutePath}")
            }
        } catch (e: Exception) {
            android.util.Log.e("LogcatCollector", "❌ Failed to save logs", e)
        }
    }
    
    /**
     * Принудительное сохранение логов
     * Вызывается из Settings -> "Save Debug Log"
     */
    fun forceSave() {
        android.util.Log.d("LogcatCollector", "💾 Force saving logs...")
        saveLogsToFileBlocking()
    }
}
