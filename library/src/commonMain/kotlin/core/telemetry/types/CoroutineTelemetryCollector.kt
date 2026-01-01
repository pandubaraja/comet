package io.pandu.core.telemetry.types

import io.pandu.config.CometConfig
import io.pandu.core.telemetry.exporters.CompositeCoroutineTelemetryExporter
import io.pandu.core.telemetry.exporters.CoroutineTelemetryExporter
import io.pandu.core.telemetry.metrics.CoroutineMetricsAggregator
import io.pandu.core.telemetry.metrics.CoroutineMetrics
import io.pandu.core.tools.MpscRingBuffer
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Collects telemetry events and dispatches them to exporters asynchronously.
 *
 * Features:
 * - Lock-free event buffering using ring buffer
 * - Background processing to avoid blocking coroutines
 * - Metrics aggregation
 * - Periodic flushing to exporters
 */
internal class CoroutineTelemetryCollector(
    private val config: CometConfig
) {
    // Lock-free ring buffer for events
    private val buffer = MpscRingBuffer<CoroutineTelemetry>(config.bufferSize)

    // Metrics aggregator
    private val metricsAggregator = CoroutineMetricsAggregator()

    // Composite exporter for all configured exporters
    private val exporter: CoroutineTelemetryExporter = when (config.exporters.size) {
        0 -> throw IllegalArgumentException("At least one exporter is required")
        1 -> config.exporters.first()
        else -> CompositeCoroutineTelemetryExporter(config.exporters)
    }

    // Background processor scope - uses its own dispatcher to avoid interference
    private val processorScope = CoroutineScope(
        SupervisorJob() +
                Dispatchers.Default +
                CoroutineName("comet-processor")
    )

    // Processing jobs
    private var eventProcessingJob: Job? = null
    private var metricsExportJob: Job? = null

    @Volatile
    private var isRunning = false

    /**
     * Start the background event processor.
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        // Event processing loop
        eventProcessingJob = processorScope.launch {
            processEvents()
        }

        // Periodic metrics export
        metricsExportJob = processorScope.launch {
            exportMetricsPeriodically()
        }
    }

    /**
     * Emit a telemetry event.
     *
     * This is non-blocking and designed to have minimal overhead.
     * Events are dropped if the buffer is full.
     */
    fun emit(event: CoroutineTelemetry) {
        if (!isRunning) return

        // Update real-time metrics synchronously (fast path)
        metricsAggregator.record(event)

        // Queue for export (may drop if buffer full)
        if (!buffer.offer(event)) {
            metricsAggregator.recordDropped()
            config.errorHandler(BufferOverflowException("Telemetry buffer full, event dropped"))
        }
    }

    /**
     * Get current metrics snapshot.
     */
    fun getMetrics(): CoroutineMetrics {
        return metricsAggregator.snapshot()
    }

    /**
     * Flush all buffered events.
     */
    suspend fun flush() {
        // Process remaining events in buffer
        while (true) {
            val event = buffer.poll() ?: break
            try {
                exporter.export(event)
            } catch (e: Exception) {
                config.errorHandler(e)
            }
        }

        // Flush exporters
        try {
            exporter.flush()
        } catch (e: Exception) {
            config.errorHandler(e)
        }
    }

    /**
     * Shutdown the collector.
     */
    suspend fun shutdown() {
        isRunning = false

        // Cancel background jobs
        eventProcessingJob?.cancelAndJoin()
        metricsExportJob?.cancelAndJoin()

        // Drain remaining events
        flush()

        // Shutdown exporters
        try {
            exporter.shutdown()
        } catch (e: Exception) {
            config.errorHandler(e)
        }

        processorScope.cancel()
    }

    /**
     * Background event processing loop.
     */
    private suspend fun processEvents() {
        while (coroutineContext[Job]?.isActive == true && isRunning) {
            val event = buffer.poll()
            if (event != null) {
                try {
                    exporter.export(event)
                } catch (e: Exception) {
                    config.errorHandler(e)
                }
            } else {
                // No events available, yield briefly to avoid busy-waiting
                delay(1.milliseconds)
            }
        }
    }

    /**
     * Periodic metrics export loop.
     */
    private suspend fun exportMetricsPeriodically() {
        while (currentCoroutineContext()[Job]?.isActive == true && isRunning) {
            delay(config.flushInterval)

            try {
                val snapshot = metricsAggregator.snapshot()
                exporter.exportMetrics(snapshot)
                exporter.flush()
            } catch (e: Exception) {
                config.errorHandler(e)
            }
        }
    }
}

/**
 * Exception thrown when the event buffer overflows.
 */
class BufferOverflowException(message: String) : RuntimeException(message)
