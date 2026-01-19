package io.pandu.comet.sampling

import io.pandu.core.CoroutineTraceContext
import io.pandu.sampling.SamplingContext
import io.pandu.sampling.strategy.NeverSamplingStrategy
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeverSamplingStrategyTest {

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

    @Test
    fun `shouldSample always returns sampled false`() {
        val context = createSamplingContext()
        val result = NeverSamplingStrategy.shouldSample(context)
        assertFalse(result.sampled)
    }

    @Test
    fun `shouldSample returns reason NeverSample`() {
        val context = createSamplingContext()
        val result = NeverSamplingStrategy.shouldSample(context)
        assertEquals("NeverSample", result.reason)
    }

    @Test
    fun `description is NeverSample`() {
        assertEquals("NeverSample", NeverSamplingStrategy.description)
    }

    @Test
    fun `shouldSample returns false even when parent was sampled`() {
        val context = createSamplingContext(parentSampled = true)
        val result = NeverSamplingStrategy.shouldSample(context)
        assertFalse(result.sampled)
    }

    @Test
    fun `shouldSample returns false with trace context`() {
        val traceContext = CoroutineTraceContext.create("operation")
        val context = createSamplingContext(traceContext = traceContext)
        val result = NeverSamplingStrategy.shouldSample(context)
        assertFalse(result.sampled)
    }

    @Test
    fun `NeverSamplingStrategy is a singleton`() {
        val instance1 = NeverSamplingStrategy
        val instance2 = NeverSamplingStrategy
        assertTrue(instance1 === instance2)
    }
}
