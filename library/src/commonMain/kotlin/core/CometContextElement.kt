package io.pandu.core

import io.pandu.config.CometConfig
import io.pandu.core.interceptor.CoroutineTelemetryInterceptor
import io.pandu.core.interceptor.CoroutineTelemetryInterceptorFactory
import io.pandu.core.telemetry.types.CoroutineTelemetryCollector
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * Comet context element that provides telemetry interception.
 *
 * This element intercepts the ContinuationInterceptor to wrap coroutines
 * with telemetry tracking. It also stores itself in CometStorage so that
 * child coroutines can still access Comet even when they use different dispatchers.
 */
internal class CometContextElement(
    private val config: CometConfig,
    private val collector: CoroutineTelemetryCollector,
    private val explicitDispatcher: CoroutineDispatcher?
) : AbstractCoroutineContextElement(ContinuationInterceptor.Key), ContinuationInterceptor {

    // Cache interceptors per dispatcher
    private val interceptorCache = mutableMapOf<CoroutineDispatcher, CoroutineTelemetryInterceptor>()
    private val cacheLock = SynchronizedObject()

    private fun getOrCreateInterceptor(dispatcher: CoroutineDispatcher): CoroutineTelemetryInterceptor {
        return synchronized(cacheLock) {
            interceptorCache.getOrPut(dispatcher) {
                CoroutineTelemetryInterceptorFactory.create(
                    dispatcher = dispatcher,
                    config = config,
                    collector = collector,
                    sampler = config.samplingStrategy
                )
            }
        }
    }

    override fun <T> interceptContinuation(
        continuation: Continuation<T>
    ): Continuation<T> {
        val dispatcher = explicitDispatcher ?: Dispatchers.Default
        return getOrCreateInterceptor(dispatcher).interceptContinuation(continuation)
    }

    override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
        synchronized(cacheLock) {
            interceptorCache.values.forEach { it.releaseInterceptedContinuation(continuation) }
        }
    }

    /**
     * Returns a context that includes both this element and CometStorage.
     * Child coroutines can look up CometStorage to create their own CometContextElement
     * with their own dispatcher.
     */
    fun asContext(): CoroutineContext {
        return this + CometStorage(config, collector, config.samplingStrategy)
    }
}