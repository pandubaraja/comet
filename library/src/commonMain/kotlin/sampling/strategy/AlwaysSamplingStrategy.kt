package io.pandu.sampling.strategy

import io.pandu.sampling.SamplingContext
import io.pandu.sampling.SamplingResult

/**
 * Always sample everything.
 * Use only in development or for specific high-value operations.
 */
object AlwaysSamplingStrategy : SamplingStrategy {
    override fun shouldSample(context: SamplingContext): SamplingResult {
        return SamplingResult(sampled = true, reason = "AlwaysSample")
    }

    override val description: String = "AlwaysSample"
}