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
 * LogcatCollector - OCR DIAGNOSTIC MODE (FIXED)
 * ✅ Captures: ALL app logs including MLKit/OCR errors
 * ⏱️ Behavior: Runs continuously, saves on demand via button
 * 🔧 Changes:
 *    - No auto-save timer
 *    - Manual save via saveLogsNow()
 *    - Captures ALL app logs (not just errors)
 *    - Better crash detection
 */
class LogcatCollector private constructor(private val context: Context) {

    private var logcatProcess: Process? = null
    private var collectJob: Job? = null
    private val logBuffer = StringBuilder()
    private val isSaving = AtomicBoolean(false)
    private var isCollecting = AtomicBoolean(false)

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

        // ✅ Увеличенный буфер для хранения всех логов
        private const val MAX_BUFFER_LINES = 10000

        // ✅ Ключевые слова для OCR/MLKit (расширенный список)
        private val OCR_KEYWORDS = setOf(
            // Tesseract
            "tess", "tesseract", "ocr", "leptonica", "pix", "rect",
            "blob", "recognition", "utf8", "unichar", "traineddata",
            
            // Google ML Kit / Vision
            "mlkit", "vision", "textrecognizer", "textrecognition",
            "barcod", "face", "text", "tensorflow", "tflite", 
            "nnapi", "model", "interpreter",
            
            // Native errors
            "unsatisfiedlink", "dlopen", "so file", "native",
            "signal 11", "sigsegv", "sigabrt", "tombstone",
            
            // Memory issues
            "outofmemory", "oom", "alloc", "bitmap", "large",
            "nativeallocationregistry",
            
            // Common crashes
            "nullpointerexception", "illegalstateexception",
            "illegalargumentexception", "runtimeexception"
        )
    }

    private fun getLogsDir(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val logsDir = File(downloadsDir, "DocumentScanner_OCR_Logs")
        if (!logsDir.exists()) logsDir.mkdirs()
        return logsDir
    }

    /**
     * Начать сбор логов (вызывается вручную из Settings)
     */
    fun startCollecting() {
        if (!BuildConfig.DEBUG) {
            Timber.w("⚠️ LogcatCollector disabled in RELEASE mode")
            return
        }

        if (isCollecting.get()) {
            Timber.i("⚠️ Already collecting logs")
            return
        }

        isCollecting.set(true)
        clearInternalBuffer()

        collectJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                // Очищаем системный буфер logcat
                Runtime.getRuntime().exec("logcat -c").waitFor()
                delay(500) // Даем время на очистку
                
                val pid = android.os.Process.myPid()
                Timber.i("🚀 OCR Log Collector STARTED (PID: $pid)")

                // ✅ КЛЮЧЕВОЕ ИЗМЕНЕНИЕ: Захватываем ВСЕ логи приложения (не фильтруем по уровню)
                // Это позволит поймать Warning и Info от Tesseract/MLKit
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf(
                        "logcat",
                        "-v", "threadtime",  // Timestamp + Thread ID
                        "--pid=$pid",        // Только наше приложение
                        "*:V"                // ALL log levels (Verbose and above)
                    )
                )

                val reader = BufferedReader(
                    InputStreamReader(logcatProcess!!.inputStream),
                    16384 // Увеличенный буфер
                )

                var lineCount = 0

                while (isActive && isCollecting.get()) {
                    val line = reader.readLine() ?: break
                    
                    // ✅ Сохраняем ВСЕ строки (фильтруем только anti-loop)
                    if (!line.contains("LogcatCollector")) {
                        synchronized(logBuffer) {
                            logBuffer.append(line).append("\n")
                            lineCount++

                            // Ограничиваем размер буфера
                            if (lineCount > MAX_BUFFER_LINES) {
                                val lines = logBuffer.lines()
                                logBuffer.setLength(0)
                                logBuffer.append(
                                    lines.takeLast(MAX_BUFFER_LINES / 2).joinToString("\n")
                                )
                                lineCount = MAX_BUFFER_LINES / 2
                            }
                        }

                        // Логируем критичные ошибки в реалтайме
                        if (isCriticalError(line)) {
                            Timber.e("🔥 CRITICAL: $line")
                        }
                    }
                }

                Timber.i("✅ Collected $lineCount log lines")
            } catch (e: Exception) {
                Timber.e(e, "❌ LogcatCollector crashed")
            }
        }
    }

    /**
     * Остановить сбор логов
     */
    fun stopCollecting() {
        if (!isCollecting.get()) return

        isCollecting.set(false)
        
        try {
            collectJob?.cancel()
            logcatProcess?.destroy()
            Timber.i("🛑 Collector Stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping collector")
        }
    }

    /**
     * Проверка на критичную ошибку (для real-time логирования)
     */
    private fun isCriticalError(line: String): Boolean {
        val lower = line.lowercase()
        return (line.contains(" E ") || line.contains(" F ")) &&
               (lower.contains("fatal") || 
                lower.contains("crash") || 
                lower.contains("exception") ||
                OCR_KEYWORDS.any { lower.contains(it) })
    }

    /**
     * НОВЫЙ МЕТОД: Сохранить логи ПРЯМО СЕЙЧАС (вызывается кнопкой)
     */
    fun saveLogsNow() {
        if (!isSaving.compareAndSet(false, true)) {
            Timber.w("⚠️ Already saving logs")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val content = synchronized(logBuffer) { logBuffer.toString() }
                
                if (content.isBlank()) {
                    Timber.w("⚠️ No logs to save")
                    isSaving.set(false)
                    return@launch
                }

                val timestamp = SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss",
                    Locale.getDefault()
                ).format(Date())
                
                val fileName = "OCR_DEBUG_$timestamp.txt"
                val file = File(getLogsDir(), fileName)

                val finalLog = buildString {
                    append("=" .repeat(60)).append("\n")
                    append("OCR DIAGNOSTIC LOG\n")
                    append("=" .repeat(60)).append("\n")
                    append("Timestamp: $timestamp\n")
                    append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                    append("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    append("Lines Captured: ${content.lines().size}\n")
                    append("=" .repeat(60)).append("\n\n")
                    
                    // Добавляем фильтрованные логи (OCR-related)
                    append("=== OCR/MLKIT RELATED LOGS ===\n")
                    val ocrLines = content.lines().filter { line ->
                        val lower = line.lowercase()
                        OCR_KEYWORDS.any { lower.contains(it) } || 
                        line.contains(" E ") || 
                        line.contains(" W ")
                    }
                    if (ocrLines.isEmpty()) {
                        append("(No OCR-specific logs found)\n")
                    } else {
                        append(ocrLines.joinToString("\n"))
                    }
                    append("\n\n")
                    
                    // Полный лог
                    append("=== FULL APPLICATION LOG ===\n")
                    append(content)
                }

                file.writeText(finalLog)
                
                Timber.i("✅ LOG SAVED: ${file.absolutePath} (${file.length() / 1024} KB)")

                // Опционально: Открыть файл через Intent
                if (BuildConfig.DEBUG) {
                    shareLogFile(file)
                }

            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to save logs")
            } finally {
                isSaving.set(false)
            }
        }
    }

    /**
     * Поделиться файлом логов
     */
    private fun shareLogFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "OCR Debug Log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Share log file").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Timber.e(e, "Failed to share log file")
        }
    }

    /**
     * Очистить внутренний буфер
     */
    private fun clearInternalBuffer() {
        synchronized(logBuffer) {
            logBuffer.setLength(0)
        }
    }

    /**
     * Получить количество собранных строк
     */
    fun getCollectedLinesCount(): Int {
        return synchronized(logBuffer) {
            logBuffer.lines().size
        }
    }

    /**
     * Проверка, идет ли сбор
     */
    fun isCollecting(): Boolean = isCollecting.get()
}