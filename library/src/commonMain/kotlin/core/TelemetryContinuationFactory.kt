package io.pandu.core

import io.pandu.config.CometConfig
import io.pandu.core.continuation.CoroutineTelemetryContinuation
import io.pandu.core.continuation.NotSampledContinuation
import io.pandu.core.continuation.isFromExcludedPackage
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
    val actualCoroutineName = context[CoroutineName]?.name
    val coroutineName = if (config.includeCoroutineName) actualCoroutineName else null

    val parentSampled = context[CoroutineSamplingDecision.Key]?.sampled
    val currentJob = context[Job]
    val spanRegistry = context[CometStorage.Key]?.spanRegistry

    val dispatcherName = dispatcherNameProvider()

    // Find parent span by walking up the Job hierarchy
    val parentSpanFromJob = spanRegistry?.findParentSpan(currentJob)

    // Ghost detection: if context already has CoroutineTrackedMarker (inherited from a
    // tracked parent) but no parent span exists in the Job registry, this is a
    // framework-internal resumption continuation — not a real child coroutine.
    val alreadyTracked = context[CoroutineTrackedMarker.Key] != null
    // Ghost type 1: marker inherited, parent already completed/unregistered
    if (alreadyTracked && parentSpanFromJob == null && contextCoroutineTraceContext != null) {
        return delegateIntercepted
    }
    // Ghost type 2 (consolidated): unnamed framework scope or re-dispatch.
    // If a parent span exists in the Job registry, only named children with a
    // distinct name get their own span. Framework scopes (coroutineScope,
    // withContext) and re-dispatches never have a name.
    if (parentSpanFromJob != null) {
        val isNamedChild = actualCoroutineName != null
            && actualCoroutineName != parentSpanFromJob.operationName
        if (!isNamedChild) {
            return delegateIntercepted
        }
    }
    // Ghost type 3 (generalized): inherited context without marker, span already registered.
    // Detected by checking if a span with the same spanId is already registered —
    // meaning the real coroutine was already wrapped. Applies to both root and child contexts.
    if (!alreadyTracked && parentSpanFromJob == null
        && contextCoroutineTraceContext != null
        && spanRegistry != null
        && spanRegistry.isSpanRegistered(contextCoroutineTraceContext.spanId)) {
        return delegateIntercepted
    }

    val isUnstructured = when {
        spanRegistry == null && (contextCoroutineTraceContext != null || parentSpanFromJob != null) -> true
        parentSpanFromJob == null && contextCoroutineTraceContext?.parentSpanId != null -> true
        else -> false
    }

    // Check if this coroutine originated from an excluded package (e.g. Ktor internals)
    val isExcluded = parentSpanFromJob != null &&
        config.excludedCoroutinePackages.isNotEmpty() &&
        isFromExcludedPackage(config.excludedCoroutinePackages, continuation)

    val effectiveTraceContext = when {
        contextCoroutineTraceContext == null && parentSpanFromJob == null -> null
        isExcluded -> null
        contextCoroutineTraceContext != null && parentSpanFromJob != null -> {
            // Coroutine has explicit trace context and a traced parent — create child span
            val spanName = if (actualCoroutineName != null && actualCoroutineName != parentSpanFromJob.operationName) {
                actualCoroutineName
            } else {
                "coroutine"
            }
            parentSpanFromJob.createChildSpan(spanName)
        }
        parentSpanFromJob != null -> {
            val spanName = if (actualCoroutineName != null && actualCoroutineName != parentSpanFromJob.operationName) {
                actualCoroutineName
            } else {
                "coroutine"
            }
            parentSpanFromJob.createChildSpan(spanName)
        }
        contextCoroutineTraceContext != null -> contextCoroutineTraceContext
        else -> null
    }

    // Coroutines with no effective trace context don't belong to any trace — skip them
    if (effectiveTraceContext == null) {
        return delegateIntercepted
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
