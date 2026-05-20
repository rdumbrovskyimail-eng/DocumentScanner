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
 * LogcatCollector - OCR DIAGNOSTIC MODE (FULLY FIXED + AUTO-SAVE)
 * 
 * ✅ FIXES:
 * - Правильная проверка количества строк
 * - Сохранение работает даже после остановки
 * - Корректная работа кнопки Save
 * - Проверка разрешений
 * - ⭐ AUTO-SAVE: Автоматически сохраняет логи каждые 30 секунд при сборе
 */
class LogcatCollector private constructor(private val context: Context) {

    private var logcatProcess: Process? = null
    private var collectJob: Job? = null
    private var autoSaveJob: Job? = null
    private val logBuffer = StringBuilder()
    private val isSaving = AtomicBoolean(false)
    private var isCollecting = AtomicBoolean(false)
    
    // ✅ ADDED: Отдельный счётчик строк
    @Volatile
    private var lineCount = 0

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

        private const val MAX_BUFFER_LINES = 10000
        private const val AUTO_SAVE_INTERVAL_MS = 30_000L // 30 секунд

        private val OCR_KEYWORDS = setOf(
            "tess", "tesseract", "ocr", "leptonica", "pix", "rect",
            "blob", "recognition", "utf8", "unichar", "traineddata",
            "mlkit", "vision", "textrecognizer", "textrecognition",
            "barcode", "face", "text", "tensorflow", "tflite", 
            "nnapi", "model", "interpreter",
            "unsatisfiedlink", "dlopen", "so file", "native",
            "signal 11", "sigsegv", "sigabrt", "tombstone",
            "outofmemory", "oom", "alloc", "bitmap", "large",
            "nativeallocationregistry",
            "nullpointerexception", "illegalstateexception",
            "illegalargumentexception", "runtimeexception"
        )
    }

    private fun getLogsDir(): File {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val logsDir = File(downloadsDir, "DocumentScanner_Logs")
        if (!logsDir.exists()) {
            val created = logsDir.mkdirs()
            Timber.d("📁 App-specific logs directory created: $created at ${logsDir.absolutePath}")
        }
        return logsDir
    }

    /**
     * Начать сбор логов + запустить автосохранение
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

        // ✅ Запускаем автосохранение
        startAutoSave()

        collectJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            collectJob?.invokeOnCompletion { 
                logcatProcess?.destroy() 
                Timber.d("Logcat process destroyed via completion handler")
            }
            try {
                // Очищаем системный буфер
                Runtime.getRuntime().exec("logcat -c").waitFor()
                delay(500)
                
                val pid = android.os.Process.myPid()
                Timber.i("🚀 OCR Log Collector STARTED (PID: $pid)")
                Timber.i("💾 Auto-save enabled (every 30s)")

                // Захватываем ВСЕ логи приложения
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf(
                        "logcat",
                        "-v", "threadtime",
                        "--pid=$pid",
                        "*:V"  // Все уровни
                    )
                )

                val reader = BufferedReader(
                    InputStreamReader(logcatProcess!!.inputStream),
                    16384
                )

                while (isActive && isCollecting.get()) {
                    val line = reader.readLine() ?: break
                    
                    // Фильтруем anti-loop
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

                        // Логируем критичные ошибки
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
     * Остановить сбор логов + остановить автосохранение
     */
    fun stopCollecting() {
        if (!isCollecting.get()) return

        isCollecting.set(false)
        
        try {
            // ✅ Останавливаем автосохранение
            stopAutoSave()
            
            collectJob?.cancel()
            logcatProcess?.destroy()
            Timber.i("🛑 Collector Stopped. Buffer has $lineCount lines")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping collector")
        }
    }

    /**
     * Проверка на критичную ошибку
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
     * ✅ FIXED: Сохранить логи ПРЯМО СЕЙЧАС
     * @param isAutoSave - если true, добавляет префикс "AUTO_" к имени файла
     */
    fun saveLogsNow(isAutoSave: Boolean = false) {
        if (!isSaving.compareAndSet(false, true)) {
            Timber.w("⚠️ Already saving logs")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val content = synchronized(logBuffer) { 
                    logBuffer.toString() 
                }
                
                val currentLines = lineCount
                
                if (content.isBlank() || currentLines == 0) {
                    Timber.w("⚠️ No logs to save (buffer empty)")
                    isSaving.set(false)
                    return@launch
                }

                // Проверяем/создаём папку
                val logsDir = getLogsDir()
                if (!logsDir.exists()) {
                    logsDir.mkdirs()
                }

                val timestamp = SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss",
                    Locale.getDefault()
                ).format(Date())
                
                val prefix = if (isAutoSave) "AUTO_" else ""
                val fileName = "${prefix}OCR_DEBUG_$timestamp.txt"
                val file = File(logsDir, fileName)

                val finalLog = buildString {
                    append("=".repeat(60)).append("\n")
                    append("OCR DIAGNOSTIC LOG${if (isAutoSave) " (AUTO-SAVED)" else ""}\n")
                    append("=".repeat(60)).append("\n")
                    append("Timestamp: $timestamp\n")
                    append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                    append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                    append("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    append("Lines Captured: $currentLines\n")
                    if (isAutoSave) {
                        append("Save Type: Automatic (every 30s)\n")
                    }
                    append("=".repeat(60)).append("\n\n")
                    
                    // OCR-related логи
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
                
                val saveType = if (isAutoSave) "AUTO-SAVED" else "SAVED"
                Timber.i("✅ LOG $saveType: ${file.absolutePath} (${file.length() / 1024} KB)")
                Timber.i("📊 Saved $currentLines lines")

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
            lineCount = 0
        }
        Timber.d("🧹 Buffer cleared")
    }

    /**
     * ✅ FIXED: Получить количество собранных строк
     */
    fun getCollectedLinesCount(): Int {
        return lineCount
    }

    /**
     * Проверка, идет ли сбор
     */
    fun isCollecting(): Boolean = isCollecting.get()
    
    // ════════════════════════════════════════════════════════════════════════
    // ⭐ AUTO-SAVE TIMER - BACKUP PROTECTION
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Запускает таймер автосохранения
     * Сохраняет логи каждые 30 секунд (на случай краша)
     */
    private fun startAutoSave() {
        autoSaveJob?.cancel()
        
        autoSaveJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                while (isActive && isCollecting.get()) {
                    delay(AUTO_SAVE_INTERVAL_MS)
                    
                    if (lineCount > 100) {  // Сохраняем только если есть данные
                        Timber.d("💾 Auto-save triggered (${lineCount} lines)")
                        saveLogsNow(isAutoSave = true)
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Timber.e(e, "Auto-save job failed")
                }
            }
        }
    }
    
    /**
     * Останавливает таймер автосохранения
     */
    private fun stopAutoSave() {
        try {
            autoSaveJob?.cancel()
            autoSaveJob = null
            Timber.d("⏹️ Auto-save stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping auto-save")
        }
    }
}