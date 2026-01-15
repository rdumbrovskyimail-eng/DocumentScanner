package com.docs.scanner.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.docs.scanner.BuildConfig
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LogcatCollector - OCR DIAGNOSTIC MODE
 * ✅ Captures: OCR/MLKit Errors + System Crashes
 * ⏱️ Behavior: Runs for 40 seconds, saves ONCE, then dies.
 */
class LogcatCollector private constructor(private val context: Context) {

    private var logcatProcess: Process? = null
    private var collectJob: Job? = null
    private var timerJob: Job? = null
    private val logBuffer = StringBuilder()
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

        private const val CAPTURE_DURATION_MS = 40_000L // 40 секунд

        // ✅ Ключевые слова специально для OCR и ML Kit
        private val OCR_KEYWORDS = setOf(
            // Tesseract / OCR Specific
            "tess", "tesseract", "ocr", "leptonica", "pix", "rect",
            "blob", "recognition", "utf8", "unichar",
            
            // Google ML Kit / Vision
            "mlkit", "vision", "barcod", "face", "text", 
            "tensorflow", "tflite", "nnapi", "model",
            
            // Native & Memory (Частые причины падения OCR)
            "unsatisfiedlink", "dlopen", "so file", "native", 
            "signal 11", "sigsegv", "outofmemory", "alloc", "bitmap", "large"
        )

        // ✅ Общие слова для крашей (на случай если упадет не в OCR модуле)
        private val CRITICAL_KEYWORDS = setOf(
            "fatal", "exception", "crash", "died", "anr"
        )
    }

    private fun getLogsDir(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logsDir = File(downloadsDir, "DocumentScanner_OCR_Logs")
        if (!logsDir.exists()) logsDir.mkdirs()
        return logsDir
    }

    fun startCollecting() {
        if (!BuildConfig.DEBUG || collectJob?.isActive == true) return

        // 1. Очищаем старые логи перед запуском
        clearInternalBuffer() 

        collectJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                // Очищаем буфер logcat системы
                Runtime.getRuntime().exec("logcat -c").waitFor()
                
                val pid = android.os.Process.myPid()
                android.util.Log.i("LogcatCollector", "🚀 OCR Log Collector STARTED. Waiting 40s...")

                // Читаем всё (чтобы не пропустить warning от Tesseract), но фильтруем вручную
                logcatProcess = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "threadtime", "--pid=$pid")
                )

                val reader = BufferedReader(InputStreamReader(logcatProcess!!.inputStream), 8192)

                while (isActive) {
                    val line = reader.readLine() ?: break
                    
                    if (isRelevantLog(line)) {
                        synchronized(logBuffer) {
                            logBuffer.append(line).append("\n")
                        }
                    }
                }
            } catch (e: Exception) {
                // Silent fail to avoid loop
            }
        }

        // 2. Запускаем таймер на 40 секунд
        scheduleOneTimeSave()
    }

    private fun scheduleOneTimeSave() {
        timerJob?.cancel()
        timerJob = CoroutineScope(Dispatchers.IO).launch {
            delay(CAPTURE_DURATION_MS) // Ждем 40 секунд
            
            android.util.Log.i("LogcatCollector", "⏰ 40 seconds passed. Saving and Stopping...")
            saveLogsToFileBlocking() // Сохраняем
            stopCollecting() // Останавливаем всё
        }
    }

    private fun isRelevantLog(line: String): Boolean {
        // 🛑 ANTI-LOOP: Никогда не ловим логи самого коллектора
        if (line.contains("LogcatCollector")) return false

        val lowerLine = line.lowercase()

        // 1. Это ошибка OCR/MLKit?
        val isOcrRelated = OCR_KEYWORDS.any { lowerLine.contains(it) }
        
        // 2. Это жесткий краш?
        val isCrash = CRITICAL_KEYWORDS.any { lowerLine.contains(it) }
        
        // 3. Это ошибка уровня E (Error)?
        val isErrorLevel = line.contains(" E ") || line.contains(" F ")

        // Логируем, если:
        // (Это связано с OCR) ИЛИ (Это Ошибка и Краш) ИЛИ (Это StackTrace)
        return isOcrRelated || (isErrorLevel && isCrash) || line.trimStart().startsWith("at ")
    }

    fun stopCollecting() {
        try {
            collectJob?.cancel()
            timerJob?.cancel()
            logcatProcess?.destroy()
            android.util.Log.i("LogcatCollector", "🛑 Collector Stopped.")
        } catch (e: Exception) { }
    }

    private fun clearInternalBuffer() {
        synchronized(logBuffer) { logBuffer.setLength(0) }
    }

    private fun saveLogsToFileBlocking() {
        if (!isSaving.compareAndSet(false, true)) return

        try {
            val content = synchronized(logBuffer) { logBuffer.toString() }
            
            if (content.isBlank()) {
                android.util.Log.i("LogcatCollector", "⚠️ Nothing relevant found in 40s.")
                return
            }

            val timestamp = SimpleDateFormat("HH-mm-ss", Locale.getDefault()).format(Date())
            val fileName = "OCR_DEBUG_$timestamp.txt"
            val file = File(getLogsDir(), fileName)

            val finalLog = buildString {
                append("=== OCR DIAGNOSTIC LOG ===\n")
                append("Time: $timestamp\n")
                append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n")
                append("==========================\n\n")
                append(content)
            }

            file.writeText(finalLog)
            android.util.Log.e("LogcatCollector", "✅ FILE SAVED: ${file.absolutePath}")

        } catch (e: Exception) {
            android.util.Log.e("LogcatCollector", "❌ Save failed", e)
        } finally {
            isSaving.set(false)
        }
    }
}
