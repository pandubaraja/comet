package io.pandu.core.telemetry.types

import io.pandu.core.CoroutineTraceContext

/**
 * Emitted when a coroutine begins execution for the first time.
 *
 * @property parentCoroutineId ID of the parent coroutine, if this is a child coroutine
 * @property creationStackTrace Stack trace at coroutine creation (null in low-overhead mode)
 * @property isUnstructured True if this coroutine was launched outside the Comet-traced scope
 *                          (e.g., GlobalScope.launch or custom CoroutineScope)
 */
data class CoroutineStarted(
    override val timestamp: Long,
    override val coroutineTraceContext: CoroutineTraceContext?,
    override val coroutineId: Long,
    override val coroutineName: String?,
    override val dispatcher: String,
    override val threadName: String?,
    val parentCoroutineId: Long?,
    val creationStackTrace: List<String>?,
    val isUnstructured: Boolean = false
) : CoroutineTelemetry()