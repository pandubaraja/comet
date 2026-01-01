package io.pandu.sampling.strategy

import io.pandu.sampling.SamplingContext
import io.pandu.sampling.SamplingResult
import kotlin.random.Random

/**
 * Sample a fixed percentage of root traces.
 * Child coroutines inherit their parent's sampling decision to maintain trace continuity.
 *
 * @property sampleRate Probability of sampling (0.0 to 1.0)
 */
class ProbabilisticSamplingStrategy(
    private val sampleRate: Float
) : SamplingStrategy {

    init {
        require(sampleRate in 0f..1f) {
            "Sample rate must be between 0.0 and 1.0, got $sampleRate"
        }
    }

    override fun shouldSample(context: SamplingContext): SamplingResult {
        // Always respect parent decision for trace continuity
        context.parentSampled?.let { parentSampled ->
            return SamplingResult(
                sampled = parentSampled,
                reason = "InheritedFromParent($parentSampled)"
            )
        }

        // For root coroutines, apply probabilistic sampling
        val sampled = Random.nextFloat() < sampleRate
        return SamplingResult(
            sampled = sampled,
            reason = "Probabilistic($sampleRate)"
        )
    }

    override val description: String = "ProbabilisticSampling(rate=$sampleRate)"
}