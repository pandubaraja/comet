package io.pandu.core.telemetry.metrics

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Statistical summary of durations.
 */
data class DurationStats(
    /**
     * 50th percentile (median) duration in nanoseconds.
     */
    val p50Nanos: Long,

    /**
     * 90th percentile duration in nanoseconds.
     */
    val p90Nanos: Long,

    /**
     * 99th percentile duration in nanoseconds.
     */
    val p99Nanos: Long,

    /**
     * Maximum observed duration in nanoseconds.
     */
    val maxNanos: Long,

    /**
     * Minimum observed duration in nanoseconds.
     */
    val minNanos: Long,

    /**
     * Mean (average) duration in nanoseconds.
     */
    val meanNanos: Long,

    /**
     * Total count of observations.
     */
    val count: Long
) {
    companion object {
        /**
         * Empty stats for when no data has been collected.
         */
        val EMPTY: DurationStats = DurationStats(
            p50Nanos = 0,
            p90Nanos = 0,
            p99Nanos = 0,
            maxNanos = 0,
            minNanos = 0,
            meanNanos = 0,
            count = 0
        )
    }

    /**
     * P50 as Duration.
     */
    val p50: Duration get() = p50Nanos.nanoseconds

    /**
     * P90 as Duration.
     */
    val p90: Duration get() = p90Nanos.nanoseconds

    /**
     * P99 as Duration.
     */
    val p99: Duration get() = p99Nanos.nanoseconds

    /**
     * Max as Duration.
     */
    val max: Duration get() = maxNanos.nanoseconds

    /**
     * Mean as Duration.
     */
    val mean: Duration get() = meanNanos.nanoseconds
}