package io.pandu.comet.sampling

import io.pandu.core.CoroutineTraceContext
import io.pandu.sampling.SamplingContext
import io.pandu.sampling.strategy.OperationBasedSampling
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationBasedSamplingStrategyTest {

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
        val strategy = OperationBasedSampling(
            rules = listOf(OperationBasedSampling.OperationRule(".*".toRegex(), 0f)),
            defaultRate = 0f
        )
        val context = createSamplingContext(parentSampled = true)
        val result = strategy.shouldSample(context)
        assertTrue(result.sampled)
        assertEquals("InheritedFromParent(true)", result.reason)
    }

    @Test
    fun `shouldSample respects parent sampled false`() {
        val strategy = OperationBasedSampling(
            rules = listOf(OperationBasedSampling.OperationRule(".*".toRegex(), 1f)),
            defaultRate = 1f
        )
        val context = createSamplingContext(parentSampled = false)
        val result = strategy.shouldSample(context)
        assertFalse(result.sampled)
        assertEquals("InheritedFromParent(false)", result.reason)
    }

    // =====================================================================
    // Pattern Matching Tests
    // =====================================================================

    @Test
    fun `shouldSample matches operation name from trace context`() {
        val strategy = OperationBasedSampling(
            rules = listOf(OperationBasedSampling.OperationRule("api-.*".toRegex(), 1f)),
            defaultRate = 0f
        )
        val traceContext = CoroutineTraceContext.create("api-users")
        val context = createSamplingContext(traceContext = traceContext)
        val result = strategy.shouldSample(context)
        assertTrue(result.sampled)
    }

    @Test
    fun `shouldSample falls back to coroutine name when no trace context`() {
        val strategy = OperationBasedSampling(
            rules = listOf(OperationBasedSampling.OperationRule("worker-.*".toRegex(), 1f)),
            defaultRate = 0f
        )
        val context = createSamplingContext(coroutineName = "worker-background")
        val result = strategy.shouldSample(context)
        assertTrue(result.sampled)
    }

    @Test
    fun `shouldSample uses trace context operation name over coroutine name`() {
        val strategy = OperationBasedSampling(
            rules = listOf(
                OperationBasedSampling.OperationRule("trace-.*".toRegex(), 1f),
                OperationBasedSampling.OperationRule("coroutine-.*".toRegex(), 0f)
            ),
            defaultRate = 0f
        )
        val traceContext = CoroutineTraceContext.create("trace-operation")
        val context = createSamplingContext(traceContext = traceContext, coroutineName = "coroutine-name")
        val result = strategy.shouldSample(context)
        assertTrue(result.sampled)
    }

    @Test
    fun `shouldSample uses first matching rule`() {
        val strategy = OperationBasedSampling(
            rules = listOf(
                OperationBasedSampling.OperationRule("api-.*".toRegex(), 1f),
                OperationBasedSampling.OperationRule("api-critical".toRegex(), 0f) // Won't be used
            ),
            defaultRate = 0f
        )
        val traceContext = CoroutineTraceContext.create("api-critical")
        val context = createSamplingContext(traceContext = traceContext)
        val result = strategy.shouldSample(context)
        assertTrue(result.sampled)
    }

    // =====================================================================
    // Default Rate Tests
    // =====================================================================

    @Test
    fun `shouldSample uses default rate when no operation name available`() {
        val strategy = OperationBasedSampling(
            rules = listOf(OperationBasedSampling.OperationRule("api-.*".toRegex(), 1f)),
            defaultRate = 0f
        )
        val context = createSamplingContext() // No trace context or coroutine name
        val results = (1..100).map { strategy.shouldSample(context) }
        assertTrue(results.all { !it.sampled }, "Default rate of 0 should never sample")
    }

    @Test
    fun `shouldSample uses default rate when no rules match`() {
        val strategy = OperationBasedSampling(
            rules = listOf(OperationBasedSampling.OperationRule("api-.*".toRegex(), 1f)),
            defaultRate = 0f
        )
        val traceContext = CoroutineTraceContext.create("other-operation")
        val context = createSamplingContext(traceContext = traceContext)
        val results = (1..100).map { strategy.shouldSample(context) }
        assertTrue(results.all { !it.sampled }, "Default rate of 0 should never sample for non-matching operation")
    }

    @Test
    fun `shouldSample with default rate 1 always samples non-matching operations`() {
        val strategy = OperationBasedSampling(
            rules = listOf(OperationBasedSampling.OperationRule("api-.*".toRegex(), 0f)),
            defaultRate = 1f
        )
        val traceContext = CoroutineTraceContext.create("other-operation")
        val context = createSamplingContext(traceContext = traceContext)
        val results = (1..100).map { strategy.shouldSample(context) }
        assertTrue(results.all { it.sampled }, "Default rate of 1 should always sample for non-matching operation")
    }

    // =====================================================================
    // Reason String Tests
    // =====================================================================

    @Test
    fun `shouldSample reason contains matched rule pattern`() {
        val strategy = OperationBasedSampling(
            rules = listOf(OperationBasedSampling.OperationRule("api-.*".toRegex(), 1f)),
            defaultRate = 0f
        )
        val traceContext = CoroutineTraceContext.create("api-users")
        val context = createSamplingContext(traceContext = traceContext)
        val result = strategy.shouldSample(context)
        assertTrue(result.reason?.contains("api-.*") == true)
    }

    @Test
    fun `shouldSample reason contains DefaultRate for non-matching operations`() {
        val strategy = OperationBasedSampling(
            rules = listOf(OperationBasedSampling.OperationRule("api-.*".toRegex(), 1f)),
            defaultRate = 0.5f
        )
        val traceContext = CoroutineTraceContext.create("other-operation")
        val context = createSamplingContext(traceContext = traceContext)
        val result = strategy.shouldSample(context)
        assertTrue(result.reason?.contains("DefaultRate") == true)
    }

    // =====================================================================
    // Description Tests
    // =====================================================================

    @Test
    fun `description contains rules count and default rate`() {
        val strategy = OperationBasedSampling(
            rules = listOf(
                OperationBasedSampling.OperationRule("api-.*".toRegex(), 1f),
                OperationBasedSampling.OperationRule("worker-.*".toRegex(), 0.5f)
            ),
            defaultRate = 0.1f
        )
        assertEquals("OperationBasedSampling(rules=2, default=0.1)", strategy.description)
    }

    @Test
    fun `default defaultRate is 0`() {
        val strategy = OperationBasedSampling(
            rules = emptyList()
        )
        assertEquals("OperationBasedSampling(rules=0, default=0.0)", strategy.description)
    }
}
