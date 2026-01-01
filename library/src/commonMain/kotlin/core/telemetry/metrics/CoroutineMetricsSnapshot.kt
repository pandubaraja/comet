package io.pandu.core.telemetry.metrics

/**
 * Immutable snapshot implementation of TelemetryMetrics.
 */
data class CoroutineMetricsSnapshot(
    override val activeCoroutines: Long,
    override val totalStarted: Long,
    override val totalCompleted: Long,
    override val totalFailed: Long,
    override val totalCancelled: Long,
    override val totalDropped: Long,
    override val durationStats: DurationStats,
    override val byDispatcher: Map<String, CoroutineDispatcherMetrics>,
    override val byOperation: Map<String, OperationMetrics>,
    override val snapshotTimestamp: Long
) : CoroutineMetrics {

    companion object {
        /**
         * Empty metrics snapshot.
         */
        val EMPTY: CoroutineMetricsSnapshot = CoroutineMetricsSnapshot(
            activeCoroutines = 0,
            totalStarted = 0,
            totalCompleted = 0,
            totalFailed = 0,
            totalCancelled = 0,
            totalDropped = 0,
            durationStats = DurationStats.EMPTY,
            byDispatcher = emptyMap(),
            byOperation = emptyMap(),
            snapshotTimestamp = 0
        )
    }
}
