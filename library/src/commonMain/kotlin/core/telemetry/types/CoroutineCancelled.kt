package io.pandu.core.telemetry.types

import io.pandu.core.CoroutineTraceContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Emitted when a coroutine is cancelled.
 *
 * @property cancellationReason The cancellation exception message, if any
 * @property totalDurationNanos Total time from start to cancellation
 */
data class CoroutineCancelled(
    override val timestamp: Long,
    override val coroutineTraceContext: CoroutineTraceContext?,
    override val coroutineId: Long,
    override val coroutineName: String?,
    override val dispatcher: String,
    override val threadName: String?,
    val cancellationReason: String?,
    val totalDurationNanos: Long
) : CoroutineTelemetry() {

    companion object {
        /**
         * Creates a CoroutineCancelled event from a CancellationException.
         */
        fun fromCancellation(
            timestamp: Long,
            coroutineTraceContext: CoroutineTraceContext?,
            coroutineId: Long,
            coroutineName: String?,
            dispatcher: String,
            threadName: String?,
            cause: CancellationException?,
            totalDurationNanos: Long
        ): CoroutineCancelled {
            return CoroutineCancelled(
                timestamp = timestamp,
                coroutineTraceContext = coroutineTraceContext,
                coroutineId = coroutineId,
                coroutineName = coroutineName,
                dispatcher = dispatcher,
                threadName = threadName,
                cancellationReason = cause?.message,
                totalDurationNanos = totalDurationNanos
            )
        }
    }
}