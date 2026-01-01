package io.pandu.core.telemetry.types

import io.pandu.core.CoroutineTraceContext

/**
 * Emitted when a coroutine resumes from suspension.
 *
 * @property suspendedDurationNanos How long the coroutine was suspended
 */
data class CoroutineResumed(
    override val timestamp: Long,
    override val coroutineTraceContext: CoroutineTraceContext?,
    override val coroutineId: Long,
    override val coroutineName: String?,
    override val dispatcher: String,
    override val threadName: String?,
    val suspendedDurationNanos: Long
) : CoroutineTelemetry()
