package io.pandu.core.continuation

import platform.Foundation.NSThread

internal actual fun currentThreadName(): String? {
    return NSThread.currentThread.name
}

internal actual fun captureCurrentStackTrace(maxDepth: Int): List<String>? {
    // Stack trace capture is limited in Kotlin/Native
    // Return null to indicate stack trace is not available
    return null
}
