package io.pandu.core.continuation

import io.pandu.core.CoroutineSamplingDecision
import io.pandu.core.CoroutineTrackedMarker
import io.pandu.core.CoroutineTraceContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

/**
 * Continuation wrapper for non-sampled coroutines.
 * Propagates the "not sampled" decision and trace context to children.
 */
class NotSampledContinuation<T>(
    val delegate: Continuation<T>,
    private val coroutineTraceContext: CoroutineTraceContext?
) : Continuation<T> {

    override val context: CoroutineContext
        get() {
            var ctx = delegate.context + CoroutineSamplingDecision(sampled = false) + CoroutineTrackedMarker()
            coroutineTraceContext?.let { ctx += it }
            return ctx
        }

    override fun resumeWith(result: Result<T>) {
        delegate.resumeWith(result)
    }
}