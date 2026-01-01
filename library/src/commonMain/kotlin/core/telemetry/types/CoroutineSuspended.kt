package io.pandu.core.telemetry.types

import io.pandu.core.CoroutineTraceContext

/**
 * Emitted when a coroutine suspends execution.
 *
 * @property suspensionPoint Function/method name where suspension occurred
 * @property runningDurationNanos Time spent running before this suspension
 */
data class CoroutineSuspended(
    override val timestamp: Long,
    override val coroutineTraceContext: CoroutineTraceContext?,
    override val coroutineId: Long,
    override val coroutineName: String?,
    override val dispatcher: String,
    override val threadName: String?,
    val suspensionPoint: String?,
    val runningDurationNanos: Long
) : CoroutineTelemetry()