package io.pandu.core.telemetry.metrics

/**
 * Metrics for a specific operation (identified by TraceContext.operationName).
 */
data class OperationMetrics(
    /**
     * Operation name.
     */
    val operationName: String,

    /**
     * Total invocations of this operation.
     */
    val totalCount: Long,

    /**
     * Number of successful completions.
     */
    val successCount: Long,

    /**
     * Number of failures.
     */
    val failureCount: Long,

    /**
     * Failure rate (failureCount / totalCount).
     */
    val failureRate: Float,

    /**
     * Duration statistics for this operation.
     */
    val durationStats: DurationStats,

    /**
     * Average suspensions per invocation.
     */
    val avgSuspensionsPerCall: Float
)