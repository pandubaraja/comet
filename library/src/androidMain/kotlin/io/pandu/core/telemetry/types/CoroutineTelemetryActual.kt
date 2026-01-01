package io.pandu.core.telemetry.types

internal actual fun Throwable.stackTraceToList(): List<String> {
    return this.stackTrace.map { it.toString() }
}
