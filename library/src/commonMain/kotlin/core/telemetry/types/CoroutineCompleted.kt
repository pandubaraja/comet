package io.pandu.core.telemetry.types

import io.pandu.core.CoroutineTraceContext

/**
 * Emitted when a coroutine completes successfully.
 *
 * @property totalDurationNanos Total wall-clock time from start to completion
 * @property runningDurationNanos Actual CPU time (excludes suspension time)
 * @property suspendedDurationNanos Total time spent in suspended state
 * @property suspensionCount Number of times the coroutine suspended
 */
data class CoroutineCompleted(
    override val timestamp: Long,
    override val coroutineTraceContext: CoroutineTraceContext?,
    override val coroutineId: Long,
    override val coroutineName: String?,
    override val dispatcher: String,
    override val threadName: String?,
    val totalDurationNanos: Long,
    val runningDurationNanos: Long,
    val suspendedDurationNanos: Long,
    val suspensionCount: Int
) : CoroutineTelemetry()