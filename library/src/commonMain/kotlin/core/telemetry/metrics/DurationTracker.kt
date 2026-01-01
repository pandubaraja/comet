package io.pandu.core.telemetry.metrics

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Tracks duration statistics using Welford's online algorithm
 * for numerically stable mean/variance calculation.
 */
internal class DurationTracker {
    private val lock = SynchronizedObject()
    private var count = 0L
    private var mean = 0.0
    private var m2 = 0.0 // For variance calculation
    private var min = Long.MAX_VALUE
    private var max = Long.MIN_VALUE

    // Approximate percentiles using reservoir sampling
    private val samples = mutableListOf<Long>()
    private val maxSamples = 1000 // Keep last N samples for percentile estimation

    fun record(durationNanos: Long) {
        synchronized(lock) {
            count++

            // Welford's algorithm for running mean
            val delta = durationNanos - mean
            mean += delta / count
            val delta2 = durationNanos - mean
            m2 += delta * delta2

            // Min/max
            if (durationNanos < min) min = durationNanos
            if (durationNanos > max) max = durationNanos

            // Store sample for percentile calculation
            samples.add(durationNanos)
            if (samples.size > maxSamples) {
                samples.removeAt(0)
            }
        }
    }

    fun snapshot(): DurationStats {
        return synchronized(lock) {
            if (count == 0L) {
                DurationStats.EMPTY
            } else {
                // Sort samples for percentile calculation
                val sorted = samples.sorted()
                DurationStats(
                    p50Nanos = percentile(sorted, 0.50),
                    p90Nanos = percentile(sorted, 0.90),
                    p99Nanos = percentile(sorted, 0.99),
                    maxNanos = max,
                    minNanos = min,
                    meanNanos = mean.toLong(),
                    count = count
                )
            }
        }
    }

    private fun percentile(sorted: List<Long>, p: Double): Long {
        if (sorted.isEmpty()) return 0
        val index = (p * (sorted.size - 1)).toInt()
        return sorted[index]
    }
}

/**
 * Accumulator for per-dispatcher metrics.
 */
internal class DispatcherMetricsAccumulator(private val name: String) {
    private val active = atomic(0L)
    private val total = atomic(0L)
    private val failed = atomic(0L)
    private val cancelled = atomic(0L)
    private val durationTracker = DurationTracker()

    fun recordStart() {
        active.incrementAndGet()
        total.incrementAndGet()
    }

    fun recordComplete(durationNanos: Long) {
        active.decrementAndGet()
        durationTracker.record(durationNanos)
    }

    fun recordFailure(durationNanos: Long) {
        active.decrementAndGet()
        failed.incrementAndGet()
        durationTracker.record(durationNanos)
    }

    fun recordCancelled() {
        active.decrementAndGet()
        cancelled.incrementAndGet()
    }

    fun snapshot(): CoroutineDispatcherMetrics {
        return CoroutineDispatcherMetrics(
            dispatcherName = name,
            activeCoroutines = active.value,
            totalExecuted = total.value,
            totalFailed = failed.value,
            durationStats = durationTracker.snapshot(),
            queuedTasks = null, // Requires platform-specific inspection
            activeThreads = null,
            poolSize = null,
            saturationPercent = null
        )
    }
}