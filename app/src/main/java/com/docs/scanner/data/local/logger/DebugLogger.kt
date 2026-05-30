package com.docs.scanner.data.local.logger

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class DebugLog(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

enum class LogLevel {
    DEBUG, INFO, WARNING, ERROR
}

@Singleton
class DebugLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _logs = MutableStateFlow<List<DebugLog>>(emptyList())
    val logs: StateFlow<List<DebugLog>> = _logs.asStateFlow()

    // limitedParallelism(1) сериализует запись в файл — нет гонок на appendText / усечении.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))

    private val logFile = File(context.filesDir, "debug_logs.txt")

    // DateTimeFormatter потокобезопасен (в отличие от SimpleDateFormat).
    private val dateFormat: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)

    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)

    fun warning(tag: String, message: String) = log(LogLevel.WARNING, tag, message)

    fun error(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable?.stackTraceToString())

    private fun log(
        level: LogLevel,
        tag: String,
        message: String,
        stackTrace: String? = null
    ) {
        val logEntry = DebugLog(
            level = level,
            tag = tag,
            message = message,
            stackTrace = stackTrace
        )

        // Атомарное обновление — без потери записей при одновременных вызовах.
        _logs.update { (it + logEntry).takeLast(MAX_IN_MEMORY_LOGS) }

        // Запись в файл сериализована через scope (limitedParallelism = 1).
        scope.launch { writeToFile(logEntry) }

        val logMessage = "$tag: $message"
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, logMessage)
            LogLevel.INFO -> Log.i(tag, logMessage)
            LogLevel.WARNING -> Log.w(tag, logMessage)
            LogLevel.ERROR -> Log.e(tag, logMessage, stackTrace?.let { Throwable(it) })
        }
    }

    private fun writeToFile(log: DebugLog) {
        try {
            val timestamp = dateFormat.format(Instant.ofEpochMilli(log.timestamp))
            val line = buildString {
                append('[').append(timestamp).append("] [")
                append(log.level).append("] [").append(log.tag).append("] ")
                append(log.message).append('\n')
                if (log.stackTrace != null) {
                    append("  ").append(log.stackTrace).append('\n')
                }
            }

            logFile.appendText(line)

            // Ограничиваем размер файла (макс. 10MB), оставляя последние строки.
            if (logFile.length() > MAX_FILE_SIZE_BYTES) {
                val lines = logFile.readLines().takeLast(MAX_FILE_LINES)
                logFile.writeText(lines.joinToString("\n"))
            }
        } catch (e: Exception) {
            Log.e("DebugLogger", "Failed to write to file", e)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
        // Удаление файла — на том же сериализованном scope, чтобы не пересекаться с записью.
        scope.launch { runCatching { logFile.delete() } }
    }

    fun exportLogs(): File = logFile

    companion object {
        private const val MAX_IN_MEMORY_LOGS = 1000
        private const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
        private const val MAX_FILE_LINES = 10_000
    }
}
