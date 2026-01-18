package io.pandu.config

import io.pandu.core.telemetry.exporters.CoroutineTelemetryExporter
import io.pandu.sampling.strategy.AlwaysSamplingStrategy
import io.pandu.sampling.strategy.SamplingStrategy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for coroutine telemetry.
 */
class CometConfig internal constructor(
    /**
     * Strategy for deciding which coroutines to trace.
     */
    val samplingStrategy: SamplingStrategy,

    /**
     * Exporters to send telemetry data to.
     */
    val exporters: List<CoroutineTelemetryExporter>,

    /**
     * Whether to include stack traces in events.
     * Disabling significantly reduces overhead.
     */
    val includeStackTrace: Boolean,

    /**
     * Whether to include coroutine names in events.
     */
    val includeCoroutineName: Boolean,

    /**
     * Maximum depth for captured stack traces.
     */
    val maxStackTraceDepth: Int,

    /**
     * Size of the internal event buffer.
     * Events are dropped when buffer is full.
     */
    val bufferSize: Int,

    /**
     * How often to flush buffered events and export metrics.
     */
    val flushInterval: Duration,

    /**
     * Handler for internal telemetry errors.
     */
    val errorHandler: (Throwable) -> Unit
) {
    /**
     * Builder for TelemetryConfig.
     */
    class Builder {
        private var samplingStrategy: SamplingStrategy = AlwaysSamplingStrategy
        private var exporters: MutableList<CoroutineTelemetryExporter> = mutableListOf()
        private var includeStackTrace: Boolean = false
        private var includeCoroutineName: Boolean = true
        private var maxStackTraceDepth: Int = 20
        private var bufferSize: Int = 10_000
        private var flushInterval: Duration = 10.seconds
        private var errorHandler: (Throwable) -> Unit = {}

        fun samplingStrategy(strategy: SamplingStrategy): Builder = apply {
            this.samplingStrategy = strategy
        }

        fun exporter(exporter: CoroutineTelemetryExporter): Builder = apply {
            this.exporters.add(exporter)
        }

        fun exporters(exporters: List<CoroutineTelemetryExporter>): Builder = apply {
            this.exporters.addAll(exporters)
        }

        fun includeStackTrace(enabled: Boolean): Builder = apply {
            this.includeStackTrace = enabled
        }

        fun includeCoroutineName(enabled: Boolean): Builder = apply {
            this.includeCoroutineName = enabled
        }

        fun maxStackTraceDepth(depth: Int): Builder = apply {
            require(depth > 0) { "Stack trace depth must be positive" }
            this.maxStackTraceDepth = depth
        }

        fun bufferSize(size: Int): Builder = apply {
            require(size > 0) { "Buffer size must be positive" }
            this.bufferSize = size
        }

        fun flushInterval(interval: Duration): Builder = apply {
            require(interval.isPositive()) { "Flush interval must be positive" }
            this.flushInterval = interval
        }

        fun errorHandler(handler: (Throwable) -> Unit): Builder = apply {
            this.errorHandler = handler
        }

        fun build(): CometConfig {
            return CometConfig(
                samplingStrategy = samplingStrategy,
                exporters = exporters.ifEmpty { listOf() },
                includeStackTrace = includeStackTrace,
                includeCoroutineName = includeCoroutineName,
                maxStackTraceDepth = maxStackTraceDepth,
                bufferSize = bufferSize,
                flushInterval = flushInterval,
                errorHandler = errorHandler,
            )
        }
    }
}