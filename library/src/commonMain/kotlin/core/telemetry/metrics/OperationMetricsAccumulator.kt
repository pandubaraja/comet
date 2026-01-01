package io.pandu.core.telemetry.metrics

import kotlinx.atomicfu.atomic

/**
 * Accumulator for per-operation metrics.
 */
internal class OperationMetricsAccumulator(private val name: String) {
    private val total = atomic(0L)
    private val success = atomic(0L)
    private val failed = atomic(0L)
    private val cancelled = atomic(0L)
    private val totalSuspensions = atomic(0L)
    private val durationTracker = DurationTracker()

    fun recordStart() {
        total.incrementAndGet()
    }

    fun recordComplete(durationNanos: Long, suspensionCount: Int) {
        success.incrementAndGet()
        totalSuspensions.addAndGet(suspensionCount.toLong())
        durationTracker.record(durationNanos)
    }

    fun recordFailure(durationNanos: Long) {
        failed.incrementAndGet()
        durationTracker.record(durationNanos)
    }

    fun recordCancelled() {
        cancelled.incrementAndGet()
    }

    fun snapshot(): OperationMetrics {
        val totalVal = total.value
        val successVal = success.value
        val failedVal = failed.value
        val suspensionsVal = totalSuspensions.value
        val completedVal = successVal + failedVal

        return OperationMetrics(
            operationName = name,
            totalCount = totalVal,
            successCount = successVal,
            failureCount = failedVal,
            failureRate = if (completedVal > 0) failedVal.toFloat() / completedVal else 0f,
            durationStats = durationTracker.snapshot(),
            avgSuspensionsPerCall = if (completedVal > 0) {
                suspensionsVal.toFloat() / completedVal
            } else 0f
        )
    }
}