package io.pandu.core

import io.pandu.core.interceptor.CoroutineTelemetryInterceptor
import io.pandu.core.interceptor.CoroutineTelemetryInterceptorFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * A ContinuationInterceptor that wraps a dispatcher with Comet telemetry.
 * This is created when a child coroutine specifies a different dispatcher.
 */
internal class CometWrappedDispatcher(
    private val delegate: CoroutineDispatcher,
    private val storage: CometStorage
) : ContinuationInterceptor {

    override val key: CoroutineContext.Key<*> = ContinuationInterceptor.Key

    private val interceptor: CoroutineTelemetryInterceptor by lazy {
        CoroutineTelemetryInterceptorFactory.create(
            dispatcher = delegate,
            config = storage.config,
            collector = storage.collector,
            sampler = storage.config.samplingStrategy
        )
    }

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        return interceptor.interceptContinuation(continuation)
    }

    override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
        interceptor.releaseInterceptedContinuation(continuation)
    }
}