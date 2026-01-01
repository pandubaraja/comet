package io.pandu.core.telemetry.types

import io.pandu.core.CoroutineTraceContext

/**
 * Emitted when a coroutine fails with an exception.
 *
 * @property exception The exception that caused the failure
 * @property exceptionType Fully qualified class name of the exception
 * @property exceptionMessage Exception message
 * @property exceptionStackTrace Stack trace as list of strings
 * @property totalDurationNanos Total time from start to failure
 * @property wasHandled True if exception was caught within the coroutine
 */
data class CoroutineFailed(
    override val timestamp: Long,
    override val coroutineTraceContext: CoroutineTraceContext?,
    override val coroutineId: Long,
    override val coroutineName: String?,
    override val dispatcher: String,
    override val threadName: String?,
    val exceptionType: String,
    val exceptionMessage: String?,
    val exceptionStackTrace: List<String>?,
    val totalDurationNanos: Long,
    val wasHandled: Boolean
) : CoroutineTelemetry() {

    companion object {
        /**
         * Creates a CoroutineFailed event from an exception.
         */
        fun fromException(
            timestamp: Long,
            coroutineTraceContext: CoroutineTraceContext?,
            coroutineId: Long,
            coroutineName: String?,
            dispatcher: String,
            threadName: String?,
            exception: Throwable,
            totalDurationNanos: Long,
            wasHandled: Boolean,
            includeStackTrace: Boolean
        ): CoroutineFailed {
            return CoroutineFailed(
                timestamp = timestamp,
                coroutineTraceContext = coroutineTraceContext,
                coroutineId = coroutineId,
                coroutineName = coroutineName,
                dispatcher = dispatcher,
                threadName = threadName,
                exceptionType = exception::class.qualifiedName ?: exception::class.simpleName ?: "Unknown",
                exceptionMessage = exception.message,
                exceptionStackTrace = if (includeStackTrace) {
                    exception.stackTraceToList()
                } else null,
                totalDurationNanos = totalDurationNanos,
                wasHandled = wasHandled
            )
        }
    }
}
