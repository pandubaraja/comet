package io.pandu.sampling.strategy

import io.pandu.sampling.SamplingContext
import io.pandu.sampling.SamplingResult
import kotlinx.atomicfu.atomic

/**
 * Rate-limited sampling to cap telemetry overhead.
 * Uses a token bucket algorithm.
 *
 * @property maxPerSecond Maximum samples per second
 * @property burstCapacity Maximum burst capacity (defaults to maxPerSecond)
 */
class RateLimitedSampling(
    private val maxPerSecond: Int,
    private val burstCapacity: Int = maxPerSecond
) : SamplingStrategy {

    private val tokenBucket = TokenBucket(maxPerSecond.toDouble(), burstCapacity)

    override fun shouldSample(context: SamplingContext): SamplingResult {
        // Respect parent decision
        context.parentSampled?.let { parentSampled ->
            return SamplingResult(
                sampled = parentSampled,
                reason = "InheritedFromParent($parentSampled)"
            )
        }

        val sampled = tokenBucket.tryAcquire()
        return SamplingResult(
            sampled = sampled,
            reason = if (sampled) "RateLimitAllowed" else "RateLimitExceeded"
        )
    }

    override val description: String = "RateLimitedSampling(max=$maxPerSecond/s)"

    /**
     * Simple token bucket implementation for rate limiting.
     */
    internal class TokenBucket(
        private val refillRate: Double, // tokens per second
        private val capacity: Int
    ) {
        private data class State(
            val tokens: Double,
            val lastRefillTime: Long
        )

        private val state = atomic(State(capacity.toDouble(), currentTimeMillis()))

        fun tryAcquire(): Boolean {
            while (true) {
                val current = state.value
                val now = currentTimeMillis()
                val elapsed = (now - current.lastRefillTime) / 1000.0
                val newTokens = minOf(capacity.toDouble(), current.tokens + elapsed * refillRate)

                if (newTokens < 1.0) {
                    return false
                }

                val newState = State(newTokens - 1.0, now)
                if (state.compareAndSet(current, newState)) {
                    return true
                }
                // CAS failed, retry
            }
        }
    }
}
