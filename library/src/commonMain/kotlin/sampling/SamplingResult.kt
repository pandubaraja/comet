package io.pandu.sampling

/**
 * Result of a sampling decision.
 */
data class SamplingResult(
    /**
     * Whether this coroutine should be sampled.
     */
    val sampled: Boolean,

    /**
     * Optional reason for the decision (useful for debugging).
     */
    val reason: String? = null
)
