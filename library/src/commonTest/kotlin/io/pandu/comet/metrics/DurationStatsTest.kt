package io.pandu.comet.metrics

import io.pandu.core.telemetry.metrics.DurationStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds

class DurationStatsTest {

    // =====================================================================
    // EMPTY Constant Tests
    // =====================================================================

    @Test
    fun `EMPTY has p50Nanos of 0`() {
        assertEquals(0L, DurationStats.EMPTY.p50Nanos)
    }

    @Test
    fun `EMPTY has p90Nanos of 0`() {
        assertEquals(0L, DurationStats.EMPTY.p90Nanos)
    }

    @Test
    fun `EMPTY has p99Nanos of 0`() {
        assertEquals(0L, DurationStats.EMPTY.p99Nanos)
    }

    @Test
    fun `EMPTY has maxNanos of 0`() {
        assertEquals(0L, DurationStats.EMPTY.maxNanos)
    }

    @Test
    fun `EMPTY has minNanos of 0`() {
        assertEquals(0L, DurationStats.EMPTY.minNanos)
    }

    @Test
    fun `EMPTY has meanNanos of 0`() {
        assertEquals(0L, DurationStats.EMPTY.meanNanos)
    }

    @Test
    fun `EMPTY has count of 0`() {
        assertEquals(0L, DurationStats.EMPTY.count)
    }

    // =====================================================================
    // Duration Property Tests
    // =====================================================================

    @Test
    fun `p50 returns correct Duration`() {
        val stats = DurationStats(
            p50Nanos = 1_000_000L,
            p90Nanos = 0,
            p99Nanos = 0,
            maxNanos = 0,
            minNanos = 0,
            meanNanos = 0,
            count = 1
        )
        assertEquals(1_000_000L.nanoseconds, stats.p50)
    }

    @Test
    fun `p90 returns correct Duration`() {
        val stats = DurationStats(
            p50Nanos = 0,
            p90Nanos = 5_000_000L,
            p99Nanos = 0,
            maxNanos = 0,
            minNanos = 0,
            meanNanos = 0,
            count = 1
        )
        assertEquals(5_000_000L.nanoseconds, stats.p90)
    }

    @Test
    fun `p99 returns correct Duration`() {
        val stats = DurationStats(
            p50Nanos = 0,
            p90Nanos = 0,
            p99Nanos = 10_000_000L,
            maxNanos = 0,
            minNanos = 0,
            meanNanos = 0,
            count = 1
        )
        assertEquals(10_000_000L.nanoseconds, stats.p99)
    }

    @Test
    fun `max returns correct Duration`() {
        val stats = DurationStats(
            p50Nanos = 0,
            p90Nanos = 0,
            p99Nanos = 0,
            maxNanos = 100_000_000L,
            minNanos = 0,
            meanNanos = 0,
            count = 1
        )
        assertEquals(100_000_000L.nanoseconds, stats.max)
    }

    @Test
    fun `mean returns correct Duration`() {
        val stats = DurationStats(
            p50Nanos = 0,
            p90Nanos = 0,
            p99Nanos = 0,
            maxNanos = 0,
            minNanos = 0,
            meanNanos = 2_500_000L,
            count = 1
        )
        assertEquals(2_500_000L.nanoseconds, stats.mean)
    }

    // =====================================================================
    // Percentile Ordering Tests
    // =====================================================================

    @Test
    fun `percentile values should be logically ordered p50 less than or equal p90`() {
        val stats = DurationStats(
            p50Nanos = 100,
            p90Nanos = 200,
            p99Nanos = 300,
            maxNanos = 400,
            minNanos = 50,
            meanNanos = 150,
            count = 10
        )
        assertTrue(stats.p50Nanos <= stats.p90Nanos)
    }

    @Test
    fun `percentile values should be logically ordered p90 less than or equal p99`() {
        val stats = DurationStats(
            p50Nanos = 100,
            p90Nanos = 200,
            p99Nanos = 300,
            maxNanos = 400,
            minNanos = 50,
            meanNanos = 150,
            count = 10
        )
        assertTrue(stats.p90Nanos <= stats.p99Nanos)
    }

    @Test
    fun `percentile values should be logically ordered p99 less than or equal max`() {
        val stats = DurationStats(
            p50Nanos = 100,
            p90Nanos = 200,
            p99Nanos = 300,
            maxNanos = 400,
            minNanos = 50,
            meanNanos = 150,
            count = 10
        )
        assertTrue(stats.p99Nanos <= stats.maxNanos)
    }

    @Test
    fun `min should be less than or equal to p50`() {
        val stats = DurationStats(
            p50Nanos = 100,
            p90Nanos = 200,
            p99Nanos = 300,
            maxNanos = 400,
            minNanos = 50,
            meanNanos = 150,
            count = 10
        )
        assertTrue(stats.minNanos <= stats.p50Nanos)
    }

    // =====================================================================
    // Non-Negative Values Tests
    // =====================================================================

    @Test
    fun `stats can have all non-negative values`() {
        val stats = DurationStats(
            p50Nanos = 0,
            p90Nanos = 0,
            p99Nanos = 0,
            maxNanos = 0,
            minNanos = 0,
            meanNanos = 0,
            count = 0
        )
        assertTrue(stats.p50Nanos >= 0)
        assertTrue(stats.p90Nanos >= 0)
        assertTrue(stats.p99Nanos >= 0)
        assertTrue(stats.maxNanos >= 0)
        assertTrue(stats.minNanos >= 0)
        assertTrue(stats.meanNanos >= 0)
        assertTrue(stats.count >= 0)
    }

    // =====================================================================
    // Data Class Tests
    // =====================================================================

    @Test
    fun `equals works correctly`() {
        val stats1 = DurationStats(
            p50Nanos = 100,
            p90Nanos = 200,
            p99Nanos = 300,
            maxNanos = 400,
            minNanos = 50,
            meanNanos = 150,
            count = 10
        )
        val stats2 = DurationStats(
            p50Nanos = 100,
            p90Nanos = 200,
            p99Nanos = 300,
            maxNanos = 400,
            minNanos = 50,
            meanNanos = 150,
            count = 10
        )
        assertEquals(stats1, stats2)
    }

    @Test
    fun `copy works correctly`() {
        val original = DurationStats(
            p50Nanos = 100,
            p90Nanos = 200,
            p99Nanos = 300,
            maxNanos = 400,
            minNanos = 50,
            meanNanos = 150,
            count = 10
        )
        val copy = original.copy(p50Nanos = 150)
        assertEquals(150, copy.p50Nanos)
        assertEquals(200, copy.p90Nanos) // Unchanged
    }

    @Test
    fun `EMPTY is a singleton`() {
        val empty1 = DurationStats.EMPTY
        val empty2 = DurationStats.EMPTY
        assertTrue(empty1 === empty2)
    }
}
