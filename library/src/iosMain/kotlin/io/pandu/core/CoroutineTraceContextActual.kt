package io.pandu.core

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun currentTimeNanos(): Long {
    // Use wall clock time in nanoseconds for Native
    return (NSDate().timeIntervalSince1970 * 1_000_000_000).toLong()
}
