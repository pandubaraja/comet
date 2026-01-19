package io.pandu.comet.sampling

import io.pandu.core.CoroutineTraceContext
import io.pandu.sampling.SamplingContext
import io.pandu.sampling.strategy.AlwaysSamplingStrategy
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlwaysSamplingStrategyTest {

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
    fun `shouldSample always returns sampled true`() {
        val context = createSamplingContext()
        val result = AlwaysSamplingStrategy.shouldSample(context)
        assertTrue(result.sampled)
    }

    @Test
    fun `shouldSample returns reason AlwaysSample`() {
        val context = createSamplingContext()
        val result = AlwaysSamplingStrategy.shouldSample(context)
        assertEquals("AlwaysSample", result.reason)
    }

    @Test
    fun `description is AlwaysSample`() {
        assertEquals("AlwaysSample", AlwaysSamplingStrategy.description)
    }

    @Test
    fun `shouldSample returns true even when parent was not sampled`() {
        val context = createSamplingContext(parentSampled = false)
        val result = AlwaysSamplingStrategy.shouldSample(context)
        assertTrue(result.sampled)
    }

    @Test
    fun `shouldSample returns true with trace context`() {
        val traceContext = CoroutineTraceContext.create("operation")
        val context = createSamplingContext(traceContext = traceContext)
        val result = AlwaysSamplingStrategy.shouldSample(context)
        assertTrue(result.sampled)
    }

    @Test
    fun `AlwaysSamplingStrategy is a singleton`() {
        val instance1 = AlwaysSamplingStrategy
        val instance2 = AlwaysSamplingStrategy
        assertTrue(instance1 === instance2)
    }
}
