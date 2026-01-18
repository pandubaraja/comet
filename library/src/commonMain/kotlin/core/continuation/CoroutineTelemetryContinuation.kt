package io.pandu.core.continuation

import io.pandu.config.CometConfig
import io.pandu.core.CoroutineSamplingDecision
import io.pandu.core.CoroutineTrackedMarker
import io.pandu.core.CoroutineTraceContext
import io.pandu.core.telemetry.types.CoroutineCancelled
import io.pandu.core.telemetry.types.CoroutineCompleted
import io.pandu.core.telemetry.types.CoroutineFailed
import io.pandu.core.telemetry.types.CoroutineResumed
import io.pandu.core.telemetry.types.CoroutineStarted
import io.pandu.core.telemetry.types.CoroutineTelemetryCollector
import io.pandu.core.tools.CoroutineIdGenerator
import kotlinx.coroutines.Job
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Wrapper continuation that tracks coroutine lifecycle events.
 *
 * This is the core mechanism for capturing telemetry:
 * - Intercepts resumeWith calls
 * - Tracks timing information
 * - Emits telemetry events to the collector
 */
internal class CoroutineTelemetryContinuation<T>(
    val delegate: Continuation<T>,
    private val collector: CoroutineTelemetryCollector,
    private val config: CometConfig,
    private val coroutineTraceContext: CoroutineTraceContext?,
    private val coroutineName: String?,
    private val dispatcherName: String,
    private val isUnstructured: Boolean = false,
    private val onSpanRegistered: ((Job?, CoroutineTraceContext) -> Unit)? = null,
    private val onSpanCompleted: ((Job?) -> Unit)? = null
) : Continuation<T> {

    // Unique ID for this coroutine (for event tracking)
    internal val coroutineId: Long = CoroutineIdGenerator.next()


    init {
        // Register this Job's span for child span lookup via Job hierarchy
        coroutineTraceContext?.let { onSpanRegistered?.invoke(context[Job], it) }
    }

    // Timing state
    private val startTime: Long = currentTimeNanos()
    private var lastResumeTime: Long = startTime
    private var totalRunningTime: Long = 0L
    private var totalSuspendedTime: Long = 0L
    private var suspensionCount: Int = 0
    private var hasStarted: Boolean = true
    private var hasCompleted: Boolean = false

    // Add sampling decision, trace context, and marker to context for child coroutines
    // Note: Parent-child relationship is now tracked via Job hierarchy, not context elements
    override val context: CoroutineContext
        get() {
            var ctx = delegate.context +
                    CoroutineSamplingDecision(sampled = true) +
                    CoroutineTrackedMarker()
            // Propagate the trace context (possibly a child span created by interceptor)
            coroutineTraceContext?.let { ctx += it }
            return ctx
        }

    override fun resumeWith(result: Result<T>) {
        if (hasCompleted) {
            // Guard against double-completion
            delegate.resumeWith(result)
            return
        }

        val now = currentTimeNanos()

        if (hasStarted) {
            hasStarted = false
            context[Job]?.let(::registerCompletionHandler)
            emitStartEvent(now)
        } else {
            // Resuming from suspension
            val suspendedDuration = now - lastResumeTime
            totalSuspendedTime += suspendedDuration

            emitResumeEvent(now, suspendedDuration)
        }

        lastResumeTime = now

        // Execute the actual continuation.
        // Note: Primary completion detection is via Job.invokeOnCompletion callback.
        // However, with supervisorScope, exceptions might be caught before the Job is marked failed.
        // We use try-catch as a backup to detect failures thrown during resumeWith.
        try {
            delegate.resumeWith(result)
        } catch (e: Throwable) {
            if (!hasCompleted) {
                hasCompleted = true
                val endTime = currentTimeNanos()
                totalRunningTime += (endTime - lastResumeTime)
                when (e) {
                    is CancellationException -> emitCancelledEvent(endTime, e)
                    else -> emitFailedEvent(endTime, e)
                }
                onSpanCompleted?.invoke(context[Job])
            }
            throw e // Re-throw to preserve original behavior
        }
    }

    /**
     * Register Job completion handler to detect completion/failure/cancellation.
     * This is called on first resume when the coroutine actually starts.
     * At this point, the Job hierarchy is fully established.
     */
    private fun registerCompletionHandler(job: Job) {
        // Use onCancelling = true to be notified in "Cancelling" state, not just final "Completed".
        // This is important because exceptions are available in the Cancelling state.
        job.invokeOnCompletion { cause ->
            if (hasCompleted) return@invokeOnCompletion
            hasCompleted = true

            val endTime = currentTimeNanos()
            totalRunningTime += (endTime - lastResumeTime)

            when (cause) {
                null -> emitCompletedEvent(endTime)
                is CancellationException -> emitCancelledEvent(endTime, cause)
                else -> emitFailedEvent(endTime, cause)
            }
            onSpanCompleted?.invoke(context[Job])
        }
    }

    private fun emitStartEvent(timestamp: Long) {
        collector.emit(
            CoroutineStarted(
                timestamp = timestamp,
                coroutineTraceContext = coroutineTraceContext,
                coroutineId = coroutineId,
                coroutineName = coroutineName,
                dispatcher = dispatcherName,
                threadName = currentThreadName(),
                parentCoroutineId = null, // Parent relationship now tracked via traceContext.parentSpanId
                creationStackTrace = if (config.includeStackTrace) captureStackTrace() else null,
                isUnstructured = isUnstructured
            )
        )
    }

    private fun emitResumeEvent(timestamp: Long, suspendedDuration: Long) {
        collector.emit(
            CoroutineResumed(
                timestamp = timestamp,
                coroutineTraceContext = coroutineTraceContext,
                coroutineId = coroutineId,
                coroutineName = coroutineName,
                dispatcher = dispatcherName,
                threadName = currentThreadName(),
                suspendedDurationNanos = suspendedDuration
            )
        )
    }

    private fun emitCompletedEvent(timestamp: Long) {
        collector.emit(
            CoroutineCompleted(
                timestamp = timestamp,
                coroutineTraceContext = coroutineTraceContext,
                coroutineId = coroutineId,
                coroutineName = coroutineName,
                dispatcher = dispatcherName,
                threadName = currentThreadName(),
                totalDurationNanos = timestamp - startTime,
                runningDurationNanos = totalRunningTime,
                suspendedDurationNanos = totalSuspendedTime,
                suspensionCount = suspensionCount
            )
        )
    }

    private fun emitFailedEvent(timestamp: Long, error: Throwable) {
        collector.emit(
            CoroutineFailed.fromException(
                timestamp = timestamp,
                coroutineTraceContext = coroutineTraceContext,
                coroutineId = coroutineId,
                coroutineName = coroutineName,
                dispatcher = dispatcherName,
                threadName = currentThreadName(),
                exception = error,
                totalDurationNanos = timestamp - startTime,
                wasHandled = false,
                includeStackTrace = config.includeStackTrace
            )
        )
    }

    private fun emitCancelledEvent(timestamp: Long, cause: CancellationException) {
        collector.emit(
            CoroutineCancelled.fromCancellation(
                timestamp = timestamp,
                coroutineTraceContext = coroutineTraceContext,
                coroutineId = coroutineId,
                coroutineName = coroutineName,
                dispatcher = dispatcherName,
                threadName = currentThreadName(),
                cause = cause,
                totalDurationNanos = timestamp - startTime
            )
        )
    }

    private fun captureStackTrace(): List<String>? {
        return captureCurrentStackTrace(config.maxStackTraceDepth)
    }
}

/**
 * Platform-specific: Get current thread name.
 */
internal expect fun currentThreadName(): String?

/**
 * Platform-specific: Capture current stack trace.
 */
internal expect fun captureCurrentStackTrace(maxDepth: Int): List<String>?

/**
 * Platform-specific time provider.
 */
internal expect fun currentTimeNanos(): Long
