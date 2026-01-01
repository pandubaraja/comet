package io.pandu.core.continuation

import io.pandu.config.CometConfig
import io.pandu.core.CoroutineSamplingDecision
import io.pandu.core.CoroutineTrackedMarker
import io.pandu.core.CoroutineTraceContext
import io.pandu.core.currentTimeNanos
import io.pandu.core.telemetry.types.CoroutineCancelled
import io.pandu.core.telemetry.types.CoroutineCompleted
import io.pandu.core.telemetry.types.CoroutineFailed
import io.pandu.core.telemetry.types.CoroutineResumed
import io.pandu.core.telemetry.types.CoroutineStarted
import io.pandu.core.telemetry.types.CoroutineSuspended
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
internal class CometContinuation<T>(
    val delegate: Continuation<T>,
    private val collector: CoroutineTelemetryCollector,
    private val config: CometConfig,
    private val coroutineTraceContext: CoroutineTraceContext?,
    private val coroutineName: String?,
    private val dispatcherName: String,
    private val currentJob: Job?,
    private val onSpanRegistered: ((Job?, CoroutineTraceContext) -> Unit)? = null,
    private val onSpanCompleted: ((Job?) -> Unit)? = null
) : Continuation<T> {

    // Unique ID for this coroutine (for event tracking)
    internal val coroutineId: Long = CoroutineIdGenerator.next()

    init {
        // Register this Job's span for child span lookup via Job hierarchy
        coroutineTraceContext?.let { onSpanRegistered?.invoke(currentJob, it) }
    }

    // Timing state
    private val startTime: Long = currentTimeNanos()
    private var lastResumeTime: Long = startTime
    private var totalRunningTime: Long = 0L
    private var totalSuspendedTime: Long = 0L
    private var suspensionCount: Int = 0
    private var isFirstResume: Boolean = true
    private var hasCompleted: Boolean = false

    // Add sampling decision, trace context, and marker to context for child coroutines
    // Note: Parent-child relationship is now tracked via Job hierarchy, not context elements
    override val context: CoroutineContext
        get() {
            var ctx = delegate.context +
                    CoroutineSamplingDecision(sampled = true) +
                    CoroutineTrackedMarker()
            // Propagate the trace context (possibly a child span created by interceptor)
            coroutineTraceContext?.let { ctx = ctx + it }
            return ctx
        }

    override fun resumeWith(result: Result<T>) {
        if (hasCompleted) {
            // Guard against double-completion
            delegate.resumeWith(result)
            return
        }

        val now = currentTimeNanos()

        if (isFirstResume) {
            isFirstResume = false
            emitStartEvent(now)
            lastResumeTime = now
        } else {
            // Resuming from suspension
            val suspendedDuration = now - lastResumeTime
            totalSuspendedTime += suspendedDuration

            if (config.trackSuspensions) {
                emitResumeEvent(now, suspendedDuration)
            }

            lastResumeTime = now
        }

        // Execute the actual continuation
        val completionResult = runCatching {
            delegate.resumeWith(result)
        }

        // Calculate running time for this execution slice
        val endTime = currentTimeNanos()
        totalRunningTime += (endTime - lastResumeTime)

        // Handle completion or exception from resumeWith itself
        completionResult.onFailure { error ->
            hasCompleted = true
            when (error) {
                is CancellationException -> emitCancelledEvent(endTime, error)
                else -> emitFailedEvent(endTime, error)
            }
            onSpanCompleted?.invoke(currentJob)
        }

        // Check if the original result was a failure
        result.onFailure { error ->
            if (!hasCompleted) {
                hasCompleted = true
                when (error) {
                    is CancellationException -> emitCancelledEvent(endTime, error)
                    else -> emitFailedEvent(endTime, error)
                }
                onSpanCompleted?.invoke(currentJob)
            }
        }
    }

    /**
     * Called when coroutine suspends.
     * This is invoked by the interceptor.
     */
    internal fun onSuspend(suspensionPoint: String?) {
        if (hasCompleted) return

        val now = currentTimeNanos()
        val runningDuration = now - lastResumeTime
        totalRunningTime += runningDuration
        suspensionCount++

        if (config.trackSuspensions) {
            emitSuspendEvent(now, runningDuration, suspensionPoint)
        }

        lastResumeTime = now
    }

    /**
     * Mark coroutine as successfully completed.
     * Called when we know the coroutine finished normally.
     */
    internal fun markCompleted() {
        if (hasCompleted) return
        hasCompleted = true

        val endTime = currentTimeNanos()
        emitCompletedEvent(endTime)

        // Clean up span registration from Job map
        onSpanCompleted?.invoke(currentJob)
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
                creationStackTrace = if (config.includeStackTrace) captureStackTrace() else null
            )
        )
    }

    private fun emitSuspendEvent(timestamp: Long, runningDuration: Long, suspensionPoint: String?) {
        collector.emit(
            CoroutineSuspended(
                timestamp = timestamp,
                coroutineTraceContext = coroutineTraceContext,
                coroutineId = coroutineId,
                coroutineName = coroutineName,
                dispatcher = dispatcherName,
                threadName = currentThreadName(),
                suspensionPoint = suspensionPoint,
                runningDurationNanos = runningDuration
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
