package com.docs.scanner.data.local.logger

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
    private val logFile = File(context.filesDir, "debug_logs.txt")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warning(tag: String, message: String) = log(LogLevel.WARNING, tag, message)
    fun error(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable?.stackTraceToString())

    private fun log(level: LogLevel, tag: String, message: String, stackTrace: String? = null) {
        val logEntry = DebugLog(level = level, tag = tag, message = message, stackTrace = stackTrace)
        _logs.update { (it + logEntry).takeLast(1000) }

        scope.launch {
            writeToFile(logEntry)
        }

        val logMessage = "$tag: $message"
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, logMessage)
            LogLevel.INFO -> Log.i(tag, logMessage)
            LogLevel.WARNING -> Log.w(tag, logMessage)
            LogLevel.ERROR -> Log.e(tag, logMessage, null)
        }
    }

    private fun writeToFile(log: DebugLog) {
        try {
            val timestamp = dateFormat.format(Instant.ofEpochMilli(log.timestamp))
            val line = "[$timestamp] [${log.level}] [${log.tag}] ${log.message}\n"
            logFile.appendText(line)
            log.stackTrace?.let { logFile.appendText("  $it\n") }

            if (logFile.length() > 10 * 1024 * 1024) {
                val lines = logFile.readLines().takeLast(10000)
                logFile.writeText(lines.joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            Log.e("DebugLogger", "Failed to write to file", e)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
        logFile.delete()
    }

    fun exportLogs(): File = logFile
}logFile.delete()
    }
    
    fun exportLogs(): File {
        return logFile
    }
}