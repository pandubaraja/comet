package io.pandu.core.continuation

internal actual fun currentThreadName(): String? = Thread.currentThread().name

internal actual fun captureCurrentStackTrace(maxDepth: Int): List<String>? {
    return Thread.currentThread().stackTrace
        .drop(2) // Skip getStackTrace and this function
        .take(maxDepth)
        .map { it.toString() }
}

internal actual fun currentTimeNanos(): Long = System.nanoTime()
