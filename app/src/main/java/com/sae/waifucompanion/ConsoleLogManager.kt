package com.sae.waifucompanion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String,
    val source: String
)

enum class LogLevel { INFO, WARNING, ERROR }

object ConsoleLogManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun addLog(level: LogLevel, message: String, sourceId: String, lineNumber: Int) {
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            level = level,
            message = message,
            source = "$sourceId:$lineNumber"
        )
        _logs.value = listOf(entry) + _logs.value.take(200) // Keep the 200 most recent items
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}