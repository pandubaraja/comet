package io.pandu.core

import kotlin.coroutines.CoroutineContext

/**
 * Internal context element to track sampling decision.
 * Ensures child coroutines inherit parent's sampling decision.
 */
internal class CoroutineSamplingDecision(
    val sampled: Boolean
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<CoroutineSamplingDecision>
}