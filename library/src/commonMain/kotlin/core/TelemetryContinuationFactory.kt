package io.pandu.core

import io.pandu.config.CometConfig
import io.pandu.core.continuation.CoroutineTelemetryContinuation
import io.pandu.core.continuation.NotSampledContinuation
import io.pandu.core.telemetry.types.CoroutineTelemetryCollector
import io.pandu.sampling.SamplingContext
import io.pandu.sampling.strategy.SamplingStrategy
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlin.coroutines.Continuation

/**
 * Shared logic for creating telemetry-wrapped continuations.
 * Used by both [io.pandu.core.interceptor.CoroutineTelemetryInterceptor] and [CometDispatcher].
 */
internal fun <T> createTelemetryContinuation(
    continuation: Continuation<T>,
    delegateIntercepted: Continuation<T>,
    config: CometConfig,
    collector: CoroutineTelemetryCollector,
    sampler: SamplingStrategy,
    dispatcherNameProvider: () -> String,
    localJobToSpan: MutableMap<Job, CoroutineTraceContext>,
    localSpansLock: SynchronizedObject
): Continuation<T> {
    val context = continuation.context
    val contextCoroutineTraceContext = context[CoroutineTraceContext.Key]
    val coroutineName = if (config.includeCoroutineName) {
        context[CoroutineName]?.name
    } else null

    val parentSampled = context[CoroutineSamplingDecision.Key]?.sampled
    val currentJob = context[Job]
    val spanRegistry = context[CometStorage.Key]?.spanRegistry

    val dispatcherName = dispatcherNameProvider()

    // Find parent span by walking up the Job hierarchy
    val parentSpanFromJob = spanRegistry?.findParentSpan(currentJob)

    val isUnstructured = when {
        spanRegistry == null && (contextCoroutineTraceContext != null || parentSpanFromJob != null) -> true
        parentSpanFromJob == null && contextCoroutineTraceContext?.parentSpanId != null -> true
        else -> false
    }

    val effectiveTraceContext = when {
        contextCoroutineTraceContext == null && parentSpanFromJob == null -> null
        parentSpanFromJob != null -> {
            val spanName = if (coroutineName != null && coroutineName != parentSpanFromJob.operationName) {
                coroutineName
            } else {
                "coroutine"
            }
            parentSpanFromJob.createChildSpan(spanName)
        }
        contextCoroutineTraceContext != null -> contextCoroutineTraceContext
        else -> null
    }

    val samplingContext = SamplingContext(
        coroutineTraceContext = effectiveTraceContext,
        coroutineName = coroutineName,
        dispatcherName = dispatcherName,
        parentSampled = parentSampled,
        coroutineContext = context
    )

    val samplingResult = sampler.shouldSample(samplingContext)

    if (!samplingResult.sampled) {
        return NotSampledContinuation(delegateIntercepted, effectiveTraceContext)
    }

    return CoroutineTelemetryContinuation(
        delegate = delegateIntercepted,
        collector = collector,
        config = config,
        coroutineTraceContext = effectiveTraceContext,
        coroutineName = coroutineName,
        dispatcherName = dispatcherName,
        isUnstructured = isUnstructured,
        onSpanRegistered = { job, trace ->
            if (job != null) {
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
