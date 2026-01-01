package io.pandu.core.telemetry.types

import io.pandu.core.CoroutineTraceContext

/**
 * Sealed hierarchy of all telemetry events captured by the library.
 *
 * Each event represents a significant point in a coroutine's lifecycle.
 * Events are designed to be:
 * - Immutable and thread-safe
 * - Lightweight for minimal allocation overhead
 * - Serializable for export to various backends
 */
sealed class CoroutineTelemetry {
    /**
     * High-precision timestamp when this event occurred.
     * Uses System.nanoTime() for accurate duration calculations.
     */
    abstract val timestamp: Long

    /**
     * Trace context associated with this coroutine, if any.
     */
    abstract val coroutineTraceContext: CoroutineTraceContext?

    /**
     * Unique identifier for this coroutine instance.
     */
    abstract val coroutineId: Long

    /**
     * Human-readable name of the coroutine, if provided via CoroutineName.
     */
    abstract val coroutineName: String?

    /**
     * String representation of the dispatcher executing this coroutine.
     */
    abstract val dispatcher: String

    /**
     * Thread name where this event occurred.
     */
    abstract val threadName: String?
}

/**
 * Helper extension to convert stack trace to list of strings.
 */
internal expect fun Throwable.stackTraceToList(): List<String>
