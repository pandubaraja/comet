package io.pandu.core.telemetry.metrics

/**
 * Metrics for a specific dispatcher.
 */
data class CoroutineDispatcherMetrics(
    /**
     * Dispatcher name/identifier.
     */
    val dispatcherName: String,

    /**
     * Currently active coroutines on this dispatcher.
     */
    val activeCoroutines: Long,

    /**
     * Total coroutines executed on this dispatcher.
     */
    val totalExecuted: Long,

    /**
     * Total failures on this dispatcher.
     */
    val totalFailed: Long,

    /**
     * Duration statistics for this dispatcher.
     */
    val durationStats: DurationStats,

    /**
     * Number of tasks queued (if available from platform).
     * Null if not inspectable.
     */
    val queuedTasks: Int?,

    /**
     * Number of active threads (if available).
     * Null if not inspectable.
     */
    val activeThreads: Int?,

    /**
     * Thread pool size (if available).
     * Null if not inspectable.
     */
    val poolSize: Int?,

    /**
     * Saturation percentage (activeThreads / poolSize).
     * Null if not calculable.
     */
    val saturationPercent: Float?
)