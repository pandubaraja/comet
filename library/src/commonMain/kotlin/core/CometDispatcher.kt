package io.pandu.core

import io.pandu.config.CometConfig
import io.pandu.core.continuation.CoroutineTelemetryContinuation
import io.pandu.core.continuation.NotSampledContinuation
import io.pandu.core.telemetry.types.CoroutineTelemetryCollector
import io.pandu.sampling.strategy.SamplingStrategy
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * A [CoroutineDispatcher] that wraps another dispatcher while adding telemetry interception.
 *
 * Since [CoroutineDispatcher.interceptContinuation] is final, this class works by
 * composing the delegate dispatcher's dispatch behavior with a custom [ContinuationInterceptor]
 * implementation. It extends [CoroutineDispatcher] to provide dispatch/isDispatchNeeded,
 * and implements [ContinuationInterceptor] directly for continuation wrapping.
 *
 * Use this when switching dispatchers with `withContext` to preserve Comet tracing:
 * ```
 * withContext(Dispatchers.IO.traced()) {
 *     async { ... } // still traced
 * }
 * ```
 */
internal class CometDispatcher(
    private val delegate: CoroutineDispatcher,
    private val config: CometConfig,
    private val collector: CoroutineTelemetryCollector,
    private val sampler: SamplingStrategy
) : ContinuationInterceptor {

    override val key: CoroutineContext.Key<*> = ContinuationInterceptor.Key

    // Local fallback map if CometStorage is not available
    private val localJobToSpan = mutableMapOf<Job, CoroutineTraceContext>()
    private val localSpansLock = SynchronizedObject()

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        // Let the delegate intercept (handles actual dispatching)
        val delegateIntercepted = delegate.interceptContinuation(continuation)

        return createTelemetryContinuation(
            continuation = continuation,
            delegateIntercepted = delegateIntercepted,
            config = config,
            collector = collector,
            sampler = sampler,
            dispatcherNameProvider = { getDispatcherName(delegate) },
            localJobToSpan = localJobToSpan,
            localSpansLock = localSpansLock
        )
    }

    override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
        when (continuation) {
            is CoroutineTelemetryContinuation<*> -> {
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

    private fun getDispatcherName(dispatcher: CoroutineDispatcher): String {
        return when (dispatcher) {
            Dispatchers.Default -> "Dispatchers.Default"
            Dispatchers.Main -> "Dispatchers.Main"
            Dispatchers.Unconfined -> "Dispatchers.Unconfined"
            else -> dispatcher.toString()
        }
    }
}
