package io.pandu.comet.sampling

import io.pandu.sampling.SamplingContext
import io.pandu.sampling.strategy.AlwaysSamplingStrategy
import io.pandu.sampling.strategy.CompositeSampling
import io.pandu.sampling.strategy.NeverSamplingStrategy
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeSamplingStrategyTest {

    private fun createSamplingContext(
        parentSampled: Boolean? = null
    ) = SamplingContext(
        coroutineTraceContext = null,
        coroutineName = null,
        dispatcherName = "Default",
        parentSampled = parentSampled,
        coroutineContext = EmptyCoroutineContext
    )

    // =====================================================================
    // FIRST_MATCH Mode Tests
    // =====================================================================

    @Test
    fun `FIRST_MATCH returns first strategy result - sampled`() {
        val strategy = CompositeSampling(
            strategies = listOf(AlwaysSamplingStrategy, NeverSamplingStrategy),
            mode = CompositeSampling.Mode.FIRST_MATCH
        )
        val result = strategy.shouldSample(createSamplingContext())
        assertTrue(result.sampled)
    }

    @Test
    fun `FIRST_MATCH returns first strategy result - not sampled`() {
        val strategy = CompositeSampling(
            strategies = listOf(NeverSamplingStrategy, AlwaysSamplingStrategy),
            mode = CompositeSampling.Mode.FIRST_MATCH
        )
        val result = strategy.shouldSample(createSamplingContext())
        assertFalse(result.sampled)
    }

    @Test
    fun `FIRST_MATCH with empty strategies returns not sampled`() {
        val strategy = CompositeSampling(
            strategies = emptyList(),
            mode = CompositeSampling.Mode.FIRST_MATCH
        )
        val result = strategy.shouldSample(createSamplingContext())
        assertFalse(result.sampled)
        assertEquals("NoStrategies", result.reason)
    }

    // =====================================================================
    // ALL Mode Tests
    // =====================================================================

    @Test
    fun `ALL mode samples only when all strategies agree`() {
        val strategy = CompositeSampling(
            strategies = listOf(AlwaysSamplingStrategy, AlwaysSamplingStrategy),
            mode = CompositeSampling.Mode.ALL
        )
        val result = strategy.shouldSample(createSamplingContext())
        assertTrue(result.sampled)
    }

    @Test
    fun `ALL mode does not sample when one strategy disagrees`() {
        val strategy = CompositeSampling(
            strategies = listOf(AlwaysSamplingStrategy, NeverSamplingStrategy),
            mode = CompositeSampling.Mode.ALL
        )
        val result = strategy.shouldSample(createSamplingContext())
        assertFalse(result.sampled)
    }

    @Test
    fun `ALL mode does not sample when all disagree`() {
        val strategy = CompositeSampling(
            strategies = listOf(NeverSamplingStrategy, NeverSamplingStrategy),
            mode = CompositeSampling.Mode.ALL
        )
        val result = strategy.shouldSample(createSamplingContext())
        assertFalse(result.sampled)
    }

    @Test
    fun `ALL mode reason shows matched count`() {
        val strategy = CompositeSampling(
            strategies = listOf(AlwaysSamplingStrategy, NeverSamplingStrategy, AlwaysSamplingStrategy),
            mode = CompositeSampling.Mode.ALL
        )
        val result = strategy.shouldSample(createSamplingContext())
        assertEquals("ALL(2/3)", result.reason)
    }

    // =====================================================================
    // ANY Mode Tests
    // =====================================================================

    @Test
    fun `ANY mode samples when any strategy agrees`() {
        val strategy = CompositeSampling(
            strategies = listOf(NeverSamplingStrategy, AlwaysSamplingStrategy),
            mode = CompositeSampling.Mode.ANY
        )
        val result = strategy.shouldSample(createSamplingContext())
        assertTrue(result.sampled)
    }

    @Test
    fun `ANY mode does not sample when all disagree`() {
        val strategy = CompositeSampling(
            strategies = listOf(NeverSamplingStrategy, NeverSamplingStrategy),
            mode = CompositeSampling.Mode.ANY
        )
        val result = strategy.shouldSample(createSamplingContext())
        assertFalse(result.sampled)
    }

    @Test
    fun `ANY mode samples when all agree`() {
        val strategy = CompositeSampling(
            strategies = listOf(AlwaysSamplingStrategy, AlwaysSamplingStrategy),
            mode = CompositeSampling.Mode.ANY
        )
        val result = strategy.shouldSample(createSamplingContext())
        assertTrue(result.sampled)
    }

    @Test
    fun `ANY mode reason shows matched count`() {
        val strategy = CompositeSampling(
            strategies = listOf(AlwaysSamplingStrategy, NeverSamplingStrategy, AlwaysSamplingStrategy),
            mode = CompositeSampling.Mode.ANY
        )
        val result = strategy.shouldSample(createSamplingContext())
        assertEquals("ANY(2/3)", result.reason)
    }

    // =====================================================================
    // Description Tests
    // =====================================================================

    @Test
    fun `description contains mode and strategy count`() {
        val strategy = CompositeSampling(
            strategies = listOf(AlwaysSamplingStrategy, NeverSamplingStrategy),
            mode = CompositeSampling.Mode.ALL
        )
        assertEquals("CompositeSampling(ALL, strategies=2)", strategy.description)
    }

    @Test
    fun `default mode is FIRST_MATCH`() {
        val strategy = CompositeSampling(
            strategies = listOf(AlwaysSamplingStrategy)
        )
        assertEquals("CompositeSampling(FIRST_MATCH, strategies=1)", strategy.description)
    }
}
