package io.pandu.sampling.strategy

import io.pandu.sampling.SamplingContext
import io.pandu.sampling.SamplingResult


/**
 * Composite sampling strategy that combines multiple strategies.
 */
class CompositeSampling(
    private val strategies: List<SamplingStrategy>,
    private val mode: Mode = Mode.FIRST_MATCH
) : SamplingStrategy {

    enum class Mode {
        /** Use the first strategy that returns a definitive decision */
        FIRST_MATCH,
        /** Sample only if ALL strategies agree to sample */
        ALL,
        /** Sample if ANY strategy agrees to sample */
        ANY
    }

    override fun shouldSample(context: SamplingContext): SamplingResult {
        return when (mode) {
            Mode.FIRST_MATCH -> {
                strategies.asSequence()
                    .map { it.shouldSample(context) }
                    .firstOrNull()
                    ?: SamplingResult(false, "NoStrategies")
            }
            Mode.ALL -> {
                val results = strategies.map { it.shouldSample(context) }
                val allSampled = results.all { it.sampled }
                SamplingResult(
                    sampled = allSampled,
                    reason = "ALL(${results.count { it.sampled }}/${results.size})"
                )
            }
            Mode.ANY -> {
                val results = strategies.map { it.shouldSample(context) }
                val anySampled = results.any { it.sampled }
                SamplingResult(
                    sampled = anySampled,
                    reason = "ANY(${results.count { it.sampled }}/${results.size})"
                )
            }
        }
    }

    override val description: String = "CompositeSampling($mode, strategies=${strategies.size})"
}