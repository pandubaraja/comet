package io.pandu.core.continuation

import platform.Foundation.NSThread
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun currentThreadName(): String? {
    return NSThread.currentThread.name
}

internal actual fun captureCurrentStackTrace(maxDepth: Int): List<String>? {
    // Stack trace capture is limited in Kotlin/Native
    // Return null to indicate stack trace is not available
    return null
}

internal actual fun currentTimeNanos(): Long {
    // Use wall clock time in nanoseconds for Native
    return (NSDate().timeIntervalSince1970 * 1_000_000_000).toLong()
}
