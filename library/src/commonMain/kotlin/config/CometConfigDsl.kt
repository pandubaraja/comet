package io.pandu.config

import io.pandu.core.telemetry.exporters.CoroutineTelemetryExporter
import io.pandu.sampling.strategy.SamplingStrategy
import kotlin.time.Duration

/**
 * DSL builder for comet configuration.
 */
@CometDsl
class CometConfigDsl {
    private val builder = CometConfig.Builder()

    fun samplingStrategy(strategy: SamplingStrategy) {
        builder.samplingStrategy(strategy)
    }

    fun exporter(exporter: CoroutineTelemetryExporter) {
        builder.exporter(exporter)
    }

    fun trackSuspensions(enabled: Boolean) {
        builder.trackSuspensions(enabled)
    }

    fun trackJobHierarchy(enabled: Boolean) {
        builder.trackJobHierarchy(enabled)
    }

    fun includeStackTrace(enabled: Boolean) {
        builder.includeStackTrace(enabled)
    }

    fun includeCoroutineName(enabled: Boolean) {
        builder.includeCoroutineName(enabled)
    }

    fun maxStackTraceDepth(depth: Int) {
        builder.maxStackTraceDepth(depth)
    }

    fun bufferSize(size: Int) {
        builder.bufferSize(size)
    }

    fun flushInterval(interval: Duration) {
        builder.flushInterval(interval)
    }

    fun onError(handler: (Throwable) -> Unit) {
        builder.errorHandler(handler)
    }

    fun lowOverheadMode(enabled: Boolean) {
        builder.lowOverheadMode(enabled)
    }

    /**
     * Enable or disable automatic child span creation for nested coroutines.
     * Default is true (automatic).
     */
    fun autoCreateChildSpans(enabled: Boolean) {
        builder.autoCreateChildSpans(enabled)
    }

    internal fun build(): CometConfig = builder.build()
}