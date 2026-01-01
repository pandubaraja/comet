package io.pandu.core.interceptor

import io.pandu.config.CometConfig
import io.pandu.core.telemetry.types.CoroutineTelemetryCollector
import io.pandu.sampling.strategy.SamplingStrategy
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Factory to create TelemetryInterceptor with a specific dispatcher.
 */
internal object CoroutineTelemetryInterceptorFactory {

    /**
     * Create a telemetry interceptor wrapping the given dispatcher.
     */
    fun create(
        dispatcher: CoroutineDispatcher,
        config: CometConfig,
        collector: CoroutineTelemetryCollector,
        sampler: SamplingStrategy
    ): CoroutineTelemetryInterceptor {
        return CoroutineTelemetryInterceptor(
            delegate = dispatcher,
            config = config,
            collector = collector,
            sampler = sampler
        )
    }
}