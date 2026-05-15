package com.docs.scanner.util

import android.content.Context
import android.os.Build
import android.os.Environment
import com.docs.scanner.BuildConfig
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

/**
 * CrashLogHandler - AUTO-SAVE LOGCAT ON CRASH
 * 
 * ✅ FIXES:
 * - Автоматически сохраняет логи при ANY краше
 * - Работает даже если UI не доступен
 * - Сохраняет в Downloads БЕЗ разрешений (Android 10+)
 * - Перехватывает ЛЮБЫЕ исключения
 */
class CrashLogHandler private constructor(
    private val context: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private var isInstalled = false

    companion object {
        @Volatile
        private var instance: CrashLogHandler? = null

        fun install(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = CrashLogHandler(context.applicationContext)
                        instance?.installHandler()
                    }
                }
            }
        }

        fun getInstance(): CrashLogHandler? = instance
    }

    private fun installHandler() {
        if (!BuildConfig.DEBUG || isInstalled) return

        try {
            Thread.setDefaultUncaughtExceptionHandler(this)
            isInstalled = true
            Timber.i("💾 CrashLogHandler installed - auto-save on crash enabled")
        } catch (e: Exception) {
            Timber.e(e, "Failed to install crash handler")
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Timber.e(throwable, "🔥 UNCAUGHT EXCEPTION in thread: ${thread.name}")
            
            // Немедленно сохраняем логи
            saveEmergencyLog(throwable, thread)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to save emergency log")
        } finally {
            // Вызываем оригинальный обработчик
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Экстренное сохранение логов при краше
     */
    private fun saveEmergencyLog(throwable: Throwable, thread: Thread) {
        try {
            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss",
                Locale.getDefault()
            ).format(Date())

            // Получаем папку Downloads БЕЗ разрешений
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir // Fallback если внешнее хранилище недоступно
            val logsDir = File(downloadsDir, "DocumentScanner_OCR_Logs")
            
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }

            val fileName = "CRASH_$timestamp.txt"
            val file = File(logsDir, fileName)

            // Собираем логи НАПРЯМУЮ из logcat
            val logcatOutput = captureLogcatNow()

            // Формируем отчет о краше
            val crashReport = buildString {
                append("=".repeat(70)).append("\n")
                append("💥 CRASH REPORT - EMERGENCY AUTO-SAVE\n")
                append("=".repeat(70)).append("\n")
                append("Timestamp: $timestamp\n")
                append("Thread: ${thread.name} (ID: ${thread.id})\n")
                append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                append("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                append("\n")
                
                append("=".repeat(70)).append("\n")
                append("EXCEPTION DETAILS\n")
                append("=".repeat(70)).append("\n")
                append("Type: ${throwable.javaClass.simpleName}\n")
                append("Message: ${throwable.message}\n")
                append("\n")
                
                append("Stack Trace:\n")
                append(throwable.stackTraceToString())
                append("\n\n")
                
                // Если есть причина
                throwable.cause?.let { cause ->
                    append("=".repeat(70)).append("\n")
                    append("CAUSED BY\n")
                    append("=".repeat(70)).append("\n")
                    append("Type: ${cause.javaClass.simpleName}\n")
                    append("Message: ${cause.message}\n")
                    append("\nStack Trace:\n")
                    append(cause.stackTraceToString())
                    append("\n\n")
                }
                
                append("=".repeat(70)).append("\n")
                append("LOGCAT DUMP (Last 1000 lines)\n")
                append("=".repeat(70)).append("\n")
                append(logcatOutput)
            }

            // Записываем файл
            file.writeText(crashReport)

            Timber.e("💾 CRASH LOG SAVED: ${file.absolutePath} (${file.length() / 1024} KB)")
            
            // Пытаемся показать toast (может не сработать при краше)
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        context,
                        "💾 Crash log saved to Downloads/",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                // Игнорируем - UI может быть недоступен
            }

        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to save emergency crash log")
        }
    }

    /**
     * Захватывает последние 1000 строк logcat ПРЯМО СЕЙЧАС
     */
    private fun captureLogcatNow(): String {
        return try {
            val pid = android.os.Process.myPid()
            
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "logcat",
                    "-d",  // dump mode
                    "-t", "1000",  // последние 1000 строк
                    "-v", "threadtime",
                    "--pid=$pid",
                    "*:V"
                )
            )

            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            
            output.ifBlank { "(No logcat output captured)" }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to capture logcat")
            "(Failed to capture logcat: ${e.message})"
        }
    }
}
