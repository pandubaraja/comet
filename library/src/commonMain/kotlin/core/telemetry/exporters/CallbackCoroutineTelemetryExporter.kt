package io.pandu.core.telemetry.exporters

import io.pandu.core.telemetry.types.CoroutineTelemetry
import io.pandu.core.telemetry.metrics.CoroutineMetrics

/**
 * Exporter that calls back with each event.
 * Useful for custom integrations.
 */
class CallbackCoroutineTelemetryExporter(
    private val onEvent: suspend (CoroutineTelemetry) -> Unit,
    private val onMetrics: (suspend (CoroutineMetrics) -> Unit)? = null,
    override val name: String = "Callback"
) : CoroutineTelemetryExporter {

    override suspend fun export(event: CoroutineTelemetry) {
        onEvent(event)
    }

    override suspend fun exportMetrics(metrics: CoroutineMetrics) {
        onMetrics?.invoke(metrics)
    }

    override suspend fun flush() {}
    override suspend fun shutdown() {}
}
