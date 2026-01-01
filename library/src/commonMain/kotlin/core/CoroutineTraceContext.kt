package io.pandu.core

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

/**
 * Trace context for distributed tracing.
 * Compatible with W3C Trace Context specification.
 *
 * Usage:
 * ```
 * launch(TraceContext.create("fetch-user")) {
 *     // Child coroutines need explicit child span creation
 *     launch(coroutineContext[TraceContext]?.createChildSpan("sub-task")) {
 *         // ...
 *     }
 *
 *     // Or use the helper extension
 *     launchTraced("sub-task") {
 *         // ...
 *     }
 * }
 * ```
 *
 * Note: Unlike some frameworks, child spans are NOT automatically created.
 * This is intentional - it gives you control over span granularity and naming.
 * Use [createChildSpan] or helper extensions like [io.pandu.tools.launchTraced] for child spans.
 *
 * @property traceId 128-bit trace identifier, hex encoded (32 chars)
 * @property spanId 64-bit span identifier, hex encoded (16 chars)
 * @property parentSpanId Parent span identifier, null for root spans
 * @property operationName Human-readable name for this span
 * @property baggage Key-value pairs that propagate to all child spans
 * @property attributes Span-specific attributes (do not propagate)
 */
class CoroutineTraceContext private constructor(
    val traceId: String,
    val spanId: String,
    val parentSpanId: String?,
    val operationName: String,
    val baggage: Map<String, String>,
    val attributes: Map<String, String>,
    val startTimeNanos: Long
) : AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<CoroutineTraceContext> {
        private const val TRACE_ID_LENGTH = 32
        private const val SPAN_ID_LENGTH = 16
        private const val HEX_CHARS = "0123456789abcdef"

        /**
         * Creates a new root trace context.
         *
         * @param operationName Human-readable name for this operation
         * @param baggage Key-value pairs that will propagate to all child spans
         * @param attributes Span-specific attributes
         */
        fun create(
            operationName: String,
            baggage: Map<String, String> = emptyMap(),
            attributes: Map<String, String> = emptyMap()
        ): CoroutineTraceContext {
            return CoroutineTraceContext(
                traceId = generateTraceId(),
                spanId = generateSpanId(),
                parentSpanId = null,
                operationName = operationName,
                baggage = baggage,
                attributes = attributes,
                startTimeNanos = currentTimeNanos()
            )
        }

        /**
         * Creates trace context from incoming W3C traceparent header.
         * Format: "00-{traceId}-{spanId}-{flags}"
         *
         * @param traceparent W3C traceparent header value
         * @param operationName Name for the new span
         * @param tracestate Optional tracestate header
         * @return TraceContext if parsing succeeds, null otherwise
         */
        fun fromTraceparent(
            traceparent: String,
            operationName: String,
            tracestate: String? = null
        ): CoroutineTraceContext? {
            val parts = traceparent.split("-")
            if (parts.size != 4) return null

            val version = parts[0]
            val traceId = parts[1]
            val parentSpanId = parts[2]
            // val flags = parts[3] // Not used currently

            if (version != "00") return null
            if (traceId.length != TRACE_ID_LENGTH) return null
            if (parentSpanId.length != SPAN_ID_LENGTH) return null

            // Parse tracestate into baggage if provided
            val baggage = tracestate?.split(",")
                ?.mapNotNull { entry ->
                    val kv = entry.split("=")
                    if (kv.size == 2) kv[0].trim() to kv[1].trim() else null
                }
                ?.toMap()
                ?: emptyMap()

            return CoroutineTraceContext(
                traceId = traceId,
                spanId = generateSpanId(),
                parentSpanId = parentSpanId,
                operationName = operationName,
                baggage = baggage,
                attributes = emptyMap(),
                startTimeNanos = currentTimeNanos()
            )
        }

        /**
         * Creates trace context from a map of headers.
         * Looks for "traceparent" and optionally "tracestate" headers.
         */
        fun fromHeaders(
            headers: Map<String, String>,
            operationName: String
        ): CoroutineTraceContext? {
            val traceparent = headers["traceparent"] ?: return null
            val tracestate = headers["tracestate"]
            return fromTraceparent(traceparent, operationName, tracestate)
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
     * @param childAttributes Additional attributes for the child span
     */
    fun createChildSpan(
        childOperationName: String,
        childAttributes: Map<String, String> = emptyMap()
    ): CoroutineTraceContext {
        return CoroutineTraceContext(
            traceId = traceId,
            spanId = generateSpanId(),
            parentSpanId = spanId,
            operationName = childOperationName,
            baggage = baggage, // Inherited
            attributes = childAttributes,
            startTimeNanos = currentTimeNanos()
        )
    }

    /**
     * Returns a copy with additional baggage.
     * Baggage propagates to all downstream operations.
     */
    fun withBaggage(key: String, value: String): CoroutineTraceContext {
        return CoroutineTraceContext(
            traceId = traceId,
            spanId = spanId,
            parentSpanId = parentSpanId,
            operationName = operationName,
            baggage = baggage + (key to value),
            attributes = attributes,
            startTimeNanos = startTimeNanos
        )
    }

    /**
     * Returns a copy with additional attributes.
     * Attributes are span-specific and don't propagate.
     */
    fun withAttribute(key: String, value: String): CoroutineTraceContext {
        return CoroutineTraceContext(
            traceId = traceId,
            spanId = spanId,
            parentSpanId = parentSpanId,
            operationName = operationName,
            baggage = baggage,
            attributes = attributes + (key to value),
            startTimeNanos = startTimeNanos
        )
    }

    /**
     * Exports to W3C traceparent header format.
     * Format: "00-{traceId}-{spanId}-01"
     */
    fun toTraceparentHeader(): String {
        return "00-$traceId-$spanId-01"
    }

    /**
     * Exports baggage to W3C tracestate header format.
     */
    fun toTracestateHeader(): String? {
        if (baggage.isEmpty()) return null
        return baggage.entries.joinToString(",") { "${it.key}=${it.value}" }
    }

    /**
     * Exports to a map of headers suitable for HTTP propagation.
     */
    fun toHeaders(): Map<String, String> = buildMap {
        put("traceparent", toTraceparentHeader())
        toTracestateHeader()?.let { put("tracestate", it) }
    }

    /**
     * Whether this is a root span (no parent).
     */
    val isRoot: Boolean get() = parentSpanId == null

    /**
     * Duration since span started, in nanoseconds.
     */
    val elapsedNanos: Long get() = currentTimeNanos() - startTimeNanos

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

/**
 * Platform-specific time provider.
 */
internal expect fun currentTimeNanos(): Long
