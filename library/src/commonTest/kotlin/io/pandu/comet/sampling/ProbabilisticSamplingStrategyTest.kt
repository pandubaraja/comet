package io.pandu.comet.sampling

import io.pandu.core.CoroutineTraceContext
import io.pandu.sampling.SamplingContext
import io.pandu.sampling.strategy.ProbabilisticSamplingStrategy
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProbabilisticSamplingStrategyTest {

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
    // Validation Tests
    // =====================================================================

    @Test
    fun `constructor rejects sample rate below 0`() {
        assertFailsWith<IllegalArgumentException> {
            ProbabilisticSamplingStrategy(-0.1f)
        }
    }

    @Test
    fun `constructor rejects sample rate above 1`() {
        assertFailsWith<IllegalArgumentException> {
            ProbabilisticSamplingStrategy(1.1f)
        }
    }

    @Test
    fun `constructor accepts sample rate of 0`() {
        val strategy = ProbabilisticSamplingStrategy(0f)
        assertEquals("ProbabilisticSampling(rate=0.0)", strategy.description)
    }

    @Test
    fun `constructor accepts sample rate of 1`() {
        val strategy = ProbabilisticSamplingStrategy(1f)
        assertEquals("ProbabilisticSampling(rate=1.0)", strategy.description)
    }

    @Test
    fun `constructor accepts sample rate of 0_5`() {
        val strategy = ProbabilisticSamplingStrategy(0.5f)
        assertEquals("ProbabilisticSampling(rate=0.5)", strategy.description)
    }

    // =====================================================================
    // Parent Inheritance Tests
    // =====================================================================

    @Test
    fun `shouldSample respects parent sampled true`() {
        val strategy = ProbabilisticSamplingStrategy(0f) // 0% rate
        val context = createSamplingContext(parentSampled = true)
        val result = strategy.shouldSample(context)
        assertTrue(result.sampled)
        assertEquals("InheritedFromParent(true)", result.reason)
    }

    @Test
    fun `shouldSample respects parent sampled false`() {
        val strategy = ProbabilisticSamplingStrategy(1f) // 100% rate
        val context = createSamplingContext(parentSampled = false)
        val result = strategy.shouldSample(context)
        assertFalse(result.sampled)
        assertEquals("InheritedFromParent(false)", result.reason)
    }

    // =====================================================================
    // Probabilistic Behavior Tests
    // =====================================================================

    @Test
    fun `shouldSample with rate 0 always returns false for root`() {
        val strategy = ProbabilisticSamplingStrategy(0f)
        val results = (1..100).map {
            strategy.shouldSample(createSamplingContext())
        }
        assertTrue(results.all { !it.sampled })
    }

    @Test
    fun `shouldSample with rate 1 always returns true for root`() {
        val strategy = ProbabilisticSamplingStrategy(1f)
        val results = (1..100).map {
            strategy.shouldSample(createSamplingContext())
        }
        assertTrue(results.all { it.sampled })
    }

    @Test
    fun `shouldSample with rate 0_5 returns approximately 50 percent sampled`() {
        val strategy = ProbabilisticSamplingStrategy(0.5f)
        val results = (1..1000).map {
            strategy.shouldSample(createSamplingContext())
        }
        val sampledCount = results.count { it.sampled }
        // Allow 10% tolerance (400-600 out of 1000)
        assertTrue(sampledCount in 350..650, "Expected ~50% sampled, got $sampledCount/1000")
    }

    @Test
    fun `shouldSample with rate 0_1 returns approximately 10 percent sampled`() {
        val strategy = ProbabilisticSamplingStrategy(0.1f)
        val results = (1..1000).map {
            strategy.shouldSample(createSamplingContext())
        }
        val sampledCount = results.count { it.sampled }
        // Allow tolerance (50-150 out of 1000)
        assertTrue(sampledCount in 50..200, "Expected ~10% sampled, got $sampledCount/1000")
    }

    // =====================================================================
    // Reason String Tests
    // =====================================================================

    @Test
    fun `shouldSample reason contains rate for root context`() {
        val strategy = ProbabilisticSamplingStrategy(0.5f)
        val result = strategy.shouldSample(createSamplingContext())
        assertEquals("Probabilistic(0.5)", result.reason)
    }

    @Test
    fun `description contains rate`() {
        val strategy = ProbabilisticSamplingStrategy(0.25f)
        assertEquals("ProbabilisticSampling(rate=0.25)", strategy.description)
    }
}
