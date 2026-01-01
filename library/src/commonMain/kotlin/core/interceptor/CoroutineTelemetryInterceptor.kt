package io.pandu.core.interceptor

import io.pandu.config.CometConfig
import io.pandu.core.CometStorage
import io.pandu.core.CoroutineSamplingDecision
import io.pandu.core.CoroutineTraceContext
import io.pandu.core.continuation.CometContinuation
import io.pandu.core.continuation.NotSampledContinuation
import io.pandu.core.telemetry.types.CoroutineTelemetryCollector
import io.pandu.sampling.SamplingContext
import io.pandu.sampling.strategy.SamplingStrategy
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * ContinuationInterceptor that wraps coroutines with telemetry tracking.
 *
 * This interceptor delegates to an underlying dispatcher while adding
 * telemetry collection around coroutine execution.
 *
 * **Automatic Child Span Creation:**
 * When a coroutine is launched within a traced context, a child span is
 * automatically created. This means users don't need to manually create
 * child spans for every `launch` or `async`.
 *
 * Usage:
 * ```
 * val comet = Comet.create(config)
 * val scope = CoroutineScope(Dispatchers.IO + comet)
 *
 * // Root trace - user creates this
 * scope.launch(TraceContext.create("parent-operation")) {
 *     // Child coroutines automatically get child spans!
 *     launch { /* auto child span */ }
 *     async { /* auto child span */ }
 * }
 * ```
 */
internal class CoroutineTelemetryInterceptor(
    private val delegate: ContinuationInterceptor,
    private val config: CometConfig,
    private val collector: CoroutineTelemetryCollector,
    private val sampler: SamplingStrategy
) : ContinuationInterceptor {

    override val key: CoroutineContext.Key<*> = ContinuationInterceptor.Key

    // Local fallback map if CometStorage is not available
    private val localJobToSpan = mutableMapOf<Job, CoroutineTraceContext>()
    private val localSpansLock = SynchronizedObject()

    override fun <T> interceptContinuation(
        continuation: Continuation<T>
    ): Continuation<T> {
        // First, let the delegate intercept (handles actual dispatching)
        val delegateIntercepted = delegate.interceptContinuation(continuation)

        // Extract context info
        val context = continuation.context
        val contextCoroutineTraceContext = context[CoroutineTraceContext.Key]
        val coroutineName = if (config.includeCoroutineName) {
            context[CoroutineName]?.name
        } else null

        // Check if parent was sampled
        val parentSampled = context[CoroutineSamplingDecision.Key]?.sampled

        // Get the current Job for hierarchy tracking
        val currentJob = context[Job]

        // Get shared span registry from CometStorage (if available)
        val spanRegistry = context[CometStorage.Key]?.spanRegistry

        // Determine dispatcher name - use the delegate dispatcher
        val dispatcherName = getDispatcherName(delegate)

        // === AUTO CHILD SPAN CREATION USING JOB HIERARCHY ===
        // Find parent span by walking up the Job hierarchy (using shared registry)
        val parentSpanFromJob = spanRegistry?.findParentSpan(currentJob) ?: findParentSpanLocal(currentJob)

        val effectiveTraceContext = when {
            // Case 1: No trace context at all - no tracing
            contextCoroutineTraceContext == null && parentSpanFromJob == null -> null

            // Case 2: Job hierarchy found a parent - create child span from it
            // This takes priority because it's the actual coroutine parent
            config.autoCreateChildSpans && parentSpanFromJob != null -> {
                val spanName = coroutineName ?: "coroutine"
                parentSpanFromJob.createChildSpan(spanName)
            }

            // Case 3: No Job parent, but context has TraceContext (root span from comet.traced())
            // Use it directly for the first coroutine in the trace
            contextCoroutineTraceContext != null -> {
                contextCoroutineTraceContext
            }

            // Case 4: No tracing
            else -> null
        }

        // Make sampling decision
        val samplingContext = SamplingContext(
            coroutineTraceContext = effectiveTraceContext,
            coroutineName = coroutineName,
            dispatcherName = dispatcherName,
            parentSampled = parentSampled,
            coroutineContext = context
        )

        val samplingResult = sampler.shouldSample(samplingContext)

        if (!samplingResult.sampled) {
            // Not sampled - return delegate without wrapping
            return NotSampledContinuation(delegateIntercepted, effectiveTraceContext)
        }

        // Wrap with telemetry
        return CometContinuation(
            delegate = delegateIntercepted,
            collector = collector,
            config = config,
            coroutineTraceContext = effectiveTraceContext,
            coroutineName = coroutineName,
            dispatcherName = dispatcherName,
            currentJob = currentJob,
            onSpanRegistered = { job, trace ->
                if (job != null) {
                    // Use shared registry if available, otherwise local
                    if (spanRegistry != null) {
                        spanRegistry.register(job, trace)
                    } else {
                        synchronized(localSpansLock) { localJobToSpan[job] = trace }
                    }
                }
            },
            onSpanCompleted = { job ->
                if (job != null) {
                    if (spanRegistry != null) {
                        spanRegistry.unregister(job)
                    } else {
                        synchronized(localSpansLock) { localJobToSpan.remove(job) }
                    }
                }
            }
        )
    }

    /**
     * Find the parent span by walking up the Job hierarchy (local fallback).
     * Returns the TraceContext of the nearest ancestor Job that has a span, or null.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun findParentSpanLocal(currentJob: Job?): CoroutineTraceContext? {
        if (currentJob == null) return null

        // Walk up the Job hierarchy to find parent with span
        var parentJob = currentJob.parent
        while (parentJob != null) {
            val parentSpan = synchronized(localSpansLock) { localJobToSpan[parentJob] }
            if (parentSpan != null) {
                return parentSpan
            }
            parentJob = parentJob.parent
        }

        return null
    }

    override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
        when (continuation) {
            is CometContinuation<*> -> {
                continuation.markCompleted()
                delegate.releaseInterceptedContinuation(continuation.delegate)
            }
            is NotSampledContinuation<*> -> {
                delegate.releaseInterceptedContinuation(continuation.delegate)
            }
            else -> {
                delegate.releaseInterceptedContinuation(continuation)
            }
        }
    }

    /**
     * Get a readable name for the dispatcher.
     */
    private fun getDispatcherName(interceptor: ContinuationInterceptor): String {
        return when (interceptor) {
            Dispatchers.Default -> "Dispatchers.Default"
            Dispatchers.Main -> "Dispatchers.Main"
            Dispatchers.Unconfined -> "Dispatchers.Unconfined"
            is CoroutineDispatcher -> interceptor.toString()
            else -> interceptor::class.simpleName ?: "Unknown"
        }
    }
}
