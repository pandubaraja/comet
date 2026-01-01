package io.pandu.core.telemetry.types

internal actual fun Throwable.stackTraceToList(): List<String> {
    // Stack traces are not available in Kotlin/Native in the same way
    // Return the stack trace string split by lines if available
    return this.stackTraceToString().split("\n").filter { it.isNotBlank() }
}
