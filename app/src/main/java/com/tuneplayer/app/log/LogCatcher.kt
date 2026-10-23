package com.tuneplayer.app.log

import android.util.Log

enum class LogLevel { V, D, I, W, E }

data class LogEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
) {
    fun render(): String = buildString {
        append(timeOf(timestampMs))
        append(' ')
        append(level.name)
        append('/')
        append(tag)
        append(": ")
        append(message)
        throwable?.let {
            append(" | ")
            append(it::class.simpleName)
            append(": ")
            append(it.message)
        }
    }

    private fun timeOf(ms: Long): String {
        val total = ms / 1000
        return "%02d:%02d:%02d".format((total / 3600) % 24, (total / 60) % 60, total % 60)
    }
}

/**
 * In-process ring buffer of recent logs.
 *
 * Captures everything the app logs through [LogCatcher] plus any uncaught
 * exception, so the in-app log viewer and a bug report can share one source
 * of truth. Deliberately in-memory: a crash-and-restart loses the buffer,
 * which is acceptable for a 200-entry window and avoids writing to storage.
 */
object LogCatcher {

    private const val CAPACITY = 200

    private val buffer = ArrayDeque<LogEntry>(CAPACITY)
    private val lock = Any()

    @Volatile
    private var installed = false

    /** True once [install] has hooked the default uncaught exception handler. */
    val isInstalled: Boolean get() = installed

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message, throwable)
        synchronized(lock) {
            if (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(entry)
        }
    }

    fun v(tag: String, message: String) = log(LogLevel.V, tag, message)
    fun d(tag: String, message: String) = log(LogLevel.D, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.I, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.W, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.E, tag, message, throwable)

    /** Oldest first. */
    fun snapshot(): List<LogEntry> = synchronized(lock) { buffer.toList() }

    fun clear() = synchronized(lock) { buffer.clear() }

    fun export(): String = snapshot().joinToString("\n") { it.render() }

    /**
     * Routes uncaught exceptions into the buffer before delegating to the
     * previous handler, so a hard crash still leaves a trace to read after
     * restart is not possible — the buffer is in-memory. What this buys is
     * the exception showing up in a report if the app survives long enough
     * to send one, and a single funnel for all error capture.
     */
    fun install() {
        if (installed) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            log(
                LogLevel.E,
                "crash",
                "Uncaught exception on ${thread.name}",
                error,
            )
            previous?.uncaughtException(thread, error)
        }
        installed = true
    }
}

/** Mirrors [LogCatcher] calls to logcat at the same level. */
inline fun <reified T : Any> T.logD(message: String) {
    LogCatcher.d(T::class.java.simpleName, message)
    Log.d(T::class.java.simpleName, message)
}

inline fun <reified T : Any> T.logE(message: String, throwable: Throwable? = null) {
    LogCatcher.e(T::class.java.simpleName, message, throwable)
    Log.e(T::class.java.simpleName, message, throwable)
}
