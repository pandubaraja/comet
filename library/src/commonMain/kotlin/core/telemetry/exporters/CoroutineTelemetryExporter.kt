package io.pandu.core.telemetry.exporters

import io.pandu.core.telemetry.types.CoroutineTelemetry
import io.pandu.core.telemetry.metrics.CoroutineMetrics

/**
* Interface for exporting telemetry data to external systems.
*
* Exporters receive events asynchronously and should handle their own
* batching and error recovery.
*
* Implementations should be thread-safe.
*/
interface CoroutineTelemetryExporter {

    /**
     * Export a single telemetry event.
     *
     * This method is called from a dedicated processing coroutine,
     * so implementations can safely suspend.
     *
     * @param event The event to export
     */
    suspend fun export(event: CoroutineTelemetry)

    /**
     * Export aggregated metrics snapshot.
     *
     * Called periodically (configurable via TelemetryConfig.flushInterval).
     *
     * @param metrics Current metrics snapshot
     */
    suspend fun exportMetrics(metrics: CoroutineMetrics)

    /**
     * Flush any buffered data.
     *
     * Called periodically and during shutdown.
     */
    suspend fun flush()

    /**
     * Clean up resources.
     *
     * Called when telemetry is being shut down.
     */
    suspend fun shutdown()

    /**
     * Human-readable name of this exporter.
     */
    val name: String
}