package io.pandu.sampling.strategy

import io.pandu.sampling.SamplingContext
import io.pandu.sampling.SamplingResult

/**
 * Determines which coroutines should be traced.
 *
 * Sampling is crucial for production environments where tracing
 * every coroutine would be too expensive.
 */
interface SamplingStrategy {
    /**
     * Called when a coroutine starts. Returns sampling decision.
     */
    fun shouldSample(context: SamplingContext): SamplingResult

    /**
     * Description of this strategy for logging/debugging.
     */
    val description: String
}

/**
 * Platform-specific current time in milliseconds.
 */
internal expect fun currentTimeMillis(): Long
