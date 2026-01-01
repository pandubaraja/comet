package io.pandu.sampling.strategy

import io.pandu.sampling.SamplingContext
import io.pandu.sampling.SamplingResult

/**
 * Never sample anything.
 * Effectively disables telemetry.
 */
object NeverSamplingStrategy : SamplingStrategy {
    override fun shouldSample(context: SamplingContext): SamplingResult {
        return SamplingResult(sampled = false, reason = "NeverSample")
    }

    override val description: String = "NeverSample"
}