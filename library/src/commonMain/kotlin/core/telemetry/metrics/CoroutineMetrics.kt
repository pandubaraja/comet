package io.pandu.core.telemetry.metrics

/**
 * Real-time metrics snapshot from the telemetry system.
 *
 * Metrics are aggregated in-memory and can be exported periodically
 * to external monitoring systems.
 */
interface CoroutineMetrics {
    /**
     * Current number of active (running or suspended) coroutines being tracked.
     */
    val activeCoroutines: Long

    /**
     * Total coroutines started since telemetry was installed.
     */
    val totalStarted: Long

    /**
     * Total coroutines completed successfully.
     */
    val totalCompleted: Long

    /**
     * Total coroutines that failed with an exception.
     */
    val totalFailed: Long

    /**
     * Total coroutines that were cancelled.
     */
    val totalCancelled: Long

    /**
     * Total events dropped due to buffer overflow.
     */
    val totalDropped: Long

    /**
     * Duration statistics across all completed coroutines.
     */
    val durationStats: DurationStats

    /**
     * Metrics broken down by dispatcher.
     */
    val byDispatcher: Map<String, CoroutineDispatcherMetrics>

    /**
     * Metrics broken down by operation name (from TraceContext).
     */
    val byOperation: Map<String, OperationMetrics>

    /**
     * Timestamp when this snapshot was taken.
     */
    val snapshotTimestamp: Long
}