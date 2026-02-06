package io.pandu.core.continuation

internal actual fun currentThreadName(): String? = Thread.currentThread().name

internal actual fun captureCurrentStackTrace(maxDepth: Int): List<String>? {
    return Thread.currentThread().stackTrace
        .map { it.toString() }
        .dropWhile { it.startsWith("java.lang.") || it.startsWith("dalvik.system.") || it.startsWith("io.pandu.core.continuation.") }
        .take(maxDepth)
}

internal actual fun currentTimeNanos(): Long = System.nanoTime()

internal actual fun isFromExcludedPackage(prefixes: Set<String>, continuation: kotlin.coroutines.Continuation<*>?): Boolean {
    if (continuation == null) return false
    val className = continuation.javaClass.name
    return prefixes.any { className.startsWith(it) }
}

