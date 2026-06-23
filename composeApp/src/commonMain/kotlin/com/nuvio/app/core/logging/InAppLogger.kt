package com.nuvio.app.core.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val DEFAULT_MAX_LOG_LINES = 1_500

internal expect fun currentInAppLogTimestamp(): String

enum class InAppLogLevel(val label: String) {
    Debug("Debug"),
    Info("Info"),
    Warn("Warn"),
    Error("Error"),
}

/**
 * In-memory app log buffer shown from Settings -> Advanced -> Debugging.
 *
 * This is intentionally process-local and non-persistent so playback URLs,
 * headers, or account state are not written to disk.
 */
object InAppLogger {
    private val maxLines = DEFAULT_MAX_LOG_LINES
    private val _lines = MutableStateFlow<List<String>>(emptyList())

    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun debug(tag: String, message: String) = log(InAppLogLevel.Debug, tag, message)
    fun info(tag: String, message: String) = log(InAppLogLevel.Info, tag, message)
    fun warn(tag: String, message: String) = log(InAppLogLevel.Warn, tag, message)
    fun error(tag: String, message: String) = log(InAppLogLevel.Error, tag, message)

    fun log(level: InAppLogLevel, tag: String, message: String) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isEmpty()) return

        val safeTag = tag.trim().ifEmpty { "App" }
        val line = buildString {
            append(currentInAppLogTimestamp())
            append(" [")
            append(level.label)
            append("] [")
            append(safeTag)
            append("] ")
            append(trimmedMessage)
        }
        appendLine(line)
    }

    fun logMpv(platform: String, prefix: String, level: String, message: String) {
        val normalizedLevel = when (level.trim().lowercase()) {
            "fatal", "error" -> InAppLogLevel.Error
            "warn", "warning" -> InAppLogLevel.Warn
            "info", "status" -> InAppLogLevel.Info
            else -> InAppLogLevel.Debug
        }
        val tag = buildString {
            append("MPV")
            val safePlatform = platform.trim()
            if (safePlatform.isNotEmpty()) {
                append('/')
                append(safePlatform)
            }
            val safePrefix = prefix.trim()
            if (safePrefix.isNotEmpty()) {
                append('/')
                append(safePrefix)
            }
        }
        log(normalizedLevel, tag, message)
    }



    fun redactUrl(url: String?, maxLength: Int = 180): String {
        val raw = url?.trim().orEmpty()
        if (raw.isEmpty()) return "<empty>"

        val withoutQuery = raw.substringBefore('?').substringBefore('#')
        val schemeSeparator = withoutQuery.indexOf("://")
        val withoutCredentials = if (schemeSeparator >= 0) {
            val prefix = withoutQuery.take(schemeSeparator + 3)
            val rest = withoutQuery.drop(schemeSeparator + 3)
            val slashIndex = rest.indexOf('/')
            val authority = if (slashIndex >= 0) rest.take(slashIndex) else rest
            val path = if (slashIndex >= 0) rest.drop(slashIndex) else ""
            prefix + authority.substringAfter('@') + path
        } else {
            withoutQuery
        }
        return if (withoutCredentials.length <= maxLength) {
            withoutCredentials
        } else {
            withoutCredentials.take(maxLength) + "…"
        }
    }

    fun headerKeys(headers: Map<String, String>?): String {
        val keys = headers.orEmpty().keys
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .sortedBy { it.lowercase() }
        return keys.joinToString(separator = ",").ifBlank { "none" }
    }

    fun throwableSummary(error: Throwable): String {
        val type = error::class.simpleName ?: "Throwable"
        val message = error.message?.trim().orEmpty()
        return if (message.isBlank()) type else "$type: $message"
    }

    fun dump(): String = lines.value.joinToString(separator = "\n")

    fun clear() {
        _lines.value = emptyList()
    }

    private fun appendLine(line: String) {
        _lines.update { current ->
            if (current.size < maxLines) {
                current + line
            } else {
                current.drop(current.size - maxLines + 1) + line
            }
        }
    }
}

/**
 * Small Swift/Java friendly facade. Swift calls this as InAppLogBridge.shared.*
 * from MPVPlayerBridge.swift.
 */
object InAppLogBridge {
    fun debug(tag: String, message: String) = InAppLogger.debug(tag, message)
    fun info(tag: String, message: String) = InAppLogger.info(tag, message)
    fun warn(tag: String, message: String) = InAppLogger.warn(tag, message)
    fun error(tag: String, message: String) = InAppLogger.error(tag, message)

    fun mpv(platform: String, prefix: String, level: String, message: String) {
        InAppLogger.logMpv(platform = platform, prefix = prefix, level = level, message = message)
    }
}
