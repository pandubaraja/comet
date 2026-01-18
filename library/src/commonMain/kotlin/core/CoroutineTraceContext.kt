package io.pandu.core

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

/**
 * Trace context for distributed tracing.
 *
 * @property traceId 128-bit trace identifier, hex encoded (32 chars)
 * @property spanId 64-bit span identifier, hex encoded (16 chars)
 * @property parentSpanId Parent span identifier, null for root spans
 * @property operationName Human-readable name for this span
 */
class CoroutineTraceContext private constructor(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String?,
    val operationName: String
) : AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<CoroutineTraceContext> {
        private const val TRACE_ID_LENGTH = 32
        private const val SPAN_ID_LENGTH = 16
        private const val HEX_CHARS = "0123456789abcdef"

        /**
         * Creates a new root trace context.
         *
         * @param operationName Human-readable name for this operation
         */
        fun create(
            operationName: String
        ): CoroutineTraceContext {
            return CoroutineTraceContext(
                traceId = generateTraceId(),
                spanId = generateSpanId(),
                parentSpanId = null,
                operationName = operationName
            )
        }

        private fun generateTraceId(): String = buildString(TRACE_ID_LENGTH) {
            repeat(TRACE_ID_LENGTH) {
                append(HEX_CHARS[Random.nextInt(16)])
            }
        }

        private fun generateSpanId(): String = buildString(SPAN_ID_LENGTH) {
            repeat(SPAN_ID_LENGTH) {
                append(HEX_CHARS[Random.nextInt(16)])
            }
        }
    }

    /**
     * Creates a child span for nested operations.
     * The child inherits traceId and baggage but gets a new spanId.
     *
     * @param childOperationName Name for the child span
     */
    fun createChildSpan(
        childOperationName: String
    ): CoroutineTraceContext {
        return CoroutineTraceContext(
            traceId = traceId,
            spanId = generateSpanId(),
            parentSpanId = spanId,
            operationName = childOperationName
        )
    }

    override fun toString(): String {
        return "TraceContext(traceId=$traceId, spanId=$spanId, parent=$parentSpanId, op=$operationName)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoroutineTraceContext) return false
        return traceId == other.traceId && spanId == other.spanId
    }

    override fun hashCode(): Int {
        var result = traceId.hashCode()
        result = 31 * result + spanId.hashCode()
        return result
    }
}
