package io.pandu.comet.sampling

import io.pandu.core.CoroutineTraceContext
import io.pandu.sampling.SamplingContext
import io.pandu.sampling.strategy.RateLimitedSampling
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RateLimitedSamplingStrategyTest {

    private fun createSamplingContext(
        traceContext: CoroutineTraceContext? = null,
        coroutineName: String? = null,
        dispatcherName: String = "Default",
        parentSampled: Boolean? = null
    ) = SamplingContext(
        coroutineTraceContext = traceContext,
        coroutineName = coroutineName,
        dispatcherName = dispatcherName,
        parentSampled = parentSampled,
        coroutineContext = EmptyCoroutineContext
    )

    // =====================================================================
    // Parent Inheritance Tests
    // =====================================================================

    @Test
    fun `shouldSample respects parent sampled true`() {
        val strategy = RateLimitedSampling(1) // Very low rate
        // Exhaust the tokens first
        repeat(10) { strategy.shouldSample(createSamplingContext()) }

        val context = createSamplingContext(parentSampled = true)
        val result = strategy.shouldSample(context)
        assertTrue(result.sampled)
        assertEquals("InheritedFromParent(true)", result.reason)
    }

    @Test
    fun `shouldSample respects parent sampled false`() {
        val strategy = RateLimitedSampling(1000) // High rate
        val context = createSamplingContext(parentSampled = false)
        val result = strategy.shouldSample(context)
        assertFalse(result.sampled)
        assertEquals("InheritedFromParent(false)", result.reason)
    }

    // =====================================================================
    // Token Bucket Tests
    // =====================================================================

    @Test
    fun `shouldSample allows initial burst up to capacity`() {
        val burstCapacity = 5
        val strategy = RateLimitedSampling(maxPerSecond = 10, burstCapacity = burstCapacity)

        val results = (1..burstCapacity).map {
            strategy.shouldSample(createSamplingContext())
        }

        assertTrue(results.all { it.sampled }, "All initial burst requests should be sampled")
    }

    @Test
    fun `shouldSample rejects when tokens exhausted`() {
        val strategy = RateLimitedSampling(maxPerSecond = 1, burstCapacity = 2)

        // Exhaust the 2 tokens
        strategy.shouldSample(createSamplingContext())
        strategy.shouldSample(createSamplingContext())

        // Third request should be rejected
        val result = strategy.shouldSample(createSamplingContext())
        assertFalse(result.sampled)
        assertEquals("RateLimitExceeded", result.reason)
    }

    @Test
    fun `shouldSample returns RateLimitAllowed reason when allowed`() {
        val strategy = RateLimitedSampling(maxPerSecond = 100, burstCapacity = 100)
        val result = strategy.shouldSample(createSamplingContext())
        assertTrue(result.sampled)
        assertEquals("RateLimitAllowed", result.reason)
    }

    @Test
    fun `shouldSample returns RateLimitExceeded reason when rejected`() {
        val strategy = RateLimitedSampling(maxPerSecond = 1, burstCapacity = 1)
        strategy.shouldSample(createSamplingContext()) // Exhaust token
        val result = strategy.shouldSample(createSamplingContext())
        assertFalse(result.sampled)
        assertEquals("RateLimitExceeded", result.reason)
    }

    // =====================================================================
    // Description Tests
    // =====================================================================

    @Test
    fun `description contains maxPerSecond`() {
        val strategy = RateLimitedSampling(maxPerSecond = 100)
        assertEquals("RateLimitedSampling(max=100/s)", strategy.description)
    }

    @Test
    fun `description with different maxPerSecond`() {
        val strategy = RateLimitedSampling(maxPerSecond = 50)
        assertEquals("RateLimitedSampling(max=50/s)", strategy.description)
    }

    // =====================================================================
    // Default Burst Capacity Tests
    // =====================================================================

    @Test
    fun `default burst capacity equals maxPerSecond`() {
        val strategy = RateLimitedSampling(maxPerSecond = 5)
        // Should be able to burst 5 requests
        val results = (1..5).map {
            strategy.shouldSample(createSamplingContext())
        }
        assertTrue(results.all { it.sampled })

        // 6th should be rejected (without waiting for refill)
        val result = strategy.shouldSample(createSamplingContext())
        assertFalse(result.sampled)
    }
}
