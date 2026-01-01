package io.pandu.sampling

import io.pandu.core.CoroutineTraceContext
import kotlin.coroutines.CoroutineContext

/**
 * Context provided to sampling strategies to make sampling decisions.
 */
data class SamplingContext(
    /**
     * Trace context if available.
     */
    val coroutineTraceContext: CoroutineTraceContext?,

    /**
     * Coroutine name if provided via CoroutineName context element.
     */
    val coroutineName: String?,

    /**
     * String representation of the dispatcher.
     */
    val dispatcherName: String,

    /**
     * Whether the parent coroutine was sampled.
     * Null if there's no parent or parent wasn't tracked.
     */
    val parentSampled: Boolean?,

    /**
     * Full coroutine context for advanced sampling decisions.
     */
    val coroutineContext: CoroutineContext
)