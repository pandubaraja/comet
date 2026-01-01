package io.pandu.sampling.strategy

import io.pandu.sampling.SamplingContext
import io.pandu.sampling.SamplingResult
import kotlin.random.Random

/**
 * Sample based on operation name patterns.
 *
 * @property rules List of rules mapping operation patterns to sample rates
 * @property defaultRate Rate for operations not matching any rule
 */
class OperationBasedSampling(
    private val rules: List<OperationRule>,
    private val defaultRate: Float = 0f
) : SamplingStrategy {

    data class OperationRule(
        val pattern: Regex,
        val sampleRate: Float
    )

    override fun shouldSample(context: SamplingContext): SamplingResult {
        // Respect parent decision
        context.parentSampled?.let { parentSampled ->
            return SamplingResult(
                sampled = parentSampled,
                reason = "InheritedFromParent($parentSampled)"
            )
        }

        val operationName = context.coroutineTraceContext?.operationName
            ?: context.coroutineName
            ?: return SamplingResult(
                sampled = Random.nextFloat() < defaultRate,
                reason = "DefaultRate($defaultRate)"
            )

        val matchingRule = rules.firstOrNull { it.pattern.matches(operationName) }

        return if (matchingRule != null) {
            val sampled = Random.nextFloat() < matchingRule.sampleRate
            SamplingResult(
                sampled = sampled,
                reason = "OperationRule(${matchingRule.pattern.pattern}=${matchingRule.sampleRate})"
            )
        } else {
            val sampled = Random.nextFloat() < defaultRate
            SamplingResult(
                sampled = sampled,
                reason = "DefaultRate($defaultRate)"
            )
        }
    }

    override val description: String = "OperationBasedSampling(rules=${rules.size}, default=$defaultRate)"
}