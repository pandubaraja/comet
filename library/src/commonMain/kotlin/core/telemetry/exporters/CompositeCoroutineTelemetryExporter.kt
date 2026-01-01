package io.pandu.core.telemetry.exporters

import io.pandu.core.telemetry.types.CoroutineTelemetry
import io.pandu.core.telemetry.metrics.CoroutineMetrics

/**
 * Exporter that delegates to multiple other exporters.
 */
class CompositeCoroutineTelemetryExporter(
    private val exporters: List<CoroutineTelemetryExporter>
) : CoroutineTelemetryExporter {

    override suspend fun export(event: CoroutineTelemetry) {
        exporters.forEach { it.export(event) }
    }

    override suspend fun exportMetrics(metrics: CoroutineMetrics) {
        exporters.forEach { it.exportMetrics(metrics) }
    }

    override suspend fun flush() {
        exporters.forEach { it.flush() }
    }

    override suspend fun shutdown() {
        exporters.forEach { it.shutdown() }
    }

    override val name: String = "Composite(${exporters.joinToString { it.name }})"
}