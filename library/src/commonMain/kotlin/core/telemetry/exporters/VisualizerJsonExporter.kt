package io.pandu.core.telemetry.exporters

import io.pandu.core.telemetry.metrics.CoroutineMetrics
import io.pandu.core.telemetry.types.CoroutineCancelled
import io.pandu.core.telemetry.types.CoroutineCompleted
import io.pandu.core.telemetry.types.CoroutineFailed
import io.pandu.core.telemetry.types.CoroutineResumed
import io.pandu.core.telemetry.types.CoroutineStarted
import io.pandu.core.telemetry.types.CoroutineTelemetry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Exporter that outputs JSON compatible with comet-visualizer's TraceEvent format.
 *
 * This exporter converts Comet telemetry events to JSON strings that can be consumed
 * by the comet-visualizer web UI via Server-Sent Events (SSE).
 *
 * Usage:
 * ```kotlin
 * val server = TraceServer(8080)
 * server.start()
 *
 * val comet = Comet.create {
 *     exporter(VisualizerJsonExporter(server::sendEvent))
 *     includeStackTrace(true)
 * }
 * comet.start()
 * ```
 *
 * @param onEvent Callback that receives JSON strings to send to the visualizer
 */
class VisualizerJsonExporter(
    private val onEvent: (String) -> Unit,
    override val name: String = "Visualizer"
) : CoroutineTelemetryExporter {

    private val json = Json { encodeDefaults = true }

    override suspend fun export(event: CoroutineTelemetry) {
        val traceEvent = when (event) {
            is CoroutineStarted -> event.toVisualizerEvent()
            is CoroutineCompleted -> event.toVisualizerEvent()
            is CoroutineFailed -> event.toVisualizerEvent()
            is CoroutineCancelled -> event.toVisualizerEvent()
            is CoroutineResumed -> null // Not needed for visualizer
        } ?: return

        onEvent(json.encodeToString(traceEvent))
    }

    override suspend fun exportMetrics(metrics: CoroutineMetrics) {
        // Visualizer doesn't use metrics export
    }

    override suspend fun flush() {}
    override suspend fun shutdown() {}

    private fun CoroutineStarted.toVisualizerEvent(): VisualizerEvent? {
        val ctx = coroutineTraceContext ?: return null
        val (sourceFile, lineNumber) = parseSourceLocation(creationStackTrace)
        return VisualizerEvent(
            type = "started",
            id = ctx.spanId,
            parentId = ctx.parentSpanId,
            operation = ctx.operationName,
            status = "running",
            durationMs = 0.0,
            dispatcher = dispatcher,
            timestamp = timestamp,
            sourceFile = sourceFile,
            lineNumber = lineNumber,
            isUnstructured = isUnstructured
        )
    }

    private fun CoroutineCompleted.toVisualizerEvent(): VisualizerEvent? {
        val ctx = coroutineTraceContext ?: return null
        return VisualizerEvent(
            type = "completed",
            id = ctx.spanId,
            parentId = ctx.parentSpanId,
            operation = ctx.operationName,
            status = "completed",
            durationMs = totalDurationNanos / 1_000_000.0,
            dispatcher = dispatcher,
            timestamp = timestamp
        )
    }

    private fun CoroutineFailed.toVisualizerEvent(): VisualizerEvent? {
        val ctx = coroutineTraceContext ?: return null
        return VisualizerEvent(
            type = "failed",
            id = ctx.spanId,
            parentId = ctx.parentSpanId,
            operation = ctx.operationName,
            status = "failed",
            durationMs = totalDurationNanos / 1_000_000.0,
            dispatcher = dispatcher,
            timestamp = timestamp
        )
    }

    private fun CoroutineCancelled.toVisualizerEvent(): VisualizerEvent? {
        val ctx = coroutineTraceContext ?: return null
        return VisualizerEvent(
            type = "cancelled",
            id = ctx.spanId,
            parentId = ctx.parentSpanId,
            operation = ctx.operationName,
            status = "cancelled",
            durationMs = totalDurationNanos / 1_000_000.0,
            dispatcher = dispatcher,
            timestamp = timestamp
        )
    }

    /**
     * Parse source location from stack trace.
     * Skips internal Comet/coroutine frames to find user code.
     */
    private fun parseSourceLocation(stackTrace: List<String>?): Pair<String, Int> {
        if (stackTrace.isNullOrEmpty()) return "" to 0

        val userFrame = stackTrace.firstOrNull { frame ->
            !frame.contains("io.pandu.core") &&
            !frame.contains("kotlin.coroutines") &&
            !frame.contains("kotlinx.coroutines") &&
            !frame.contains("java.lang")
        } ?: stackTrace.firstOrNull() ?: return "" to 0

        val match = Regex("""\((.+):(\d+)\)""").find(userFrame)
        return if (match != null) {
            val file = match.groupValues[1]
            val line = match.groupValues[2].toIntOrNull() ?: 0
            file to line
        } else {
            "" to 0
        }
    }
}

/**
 * Internal event format matching comet-visualizer's TraceEvent.
 * This is serialized to JSON and sent to the visualizer.
 */
@kotlinx.serialization.Serializable
private data class VisualizerEvent(
    val type: String,
    val id: String,
    val parentId: String?,
    val operation: String,
    val status: String,
    val durationMs: Double = 0.0,
    val dispatcher: String = "",
    val timestamp: Long,
    val sourceFile: String = "",
    val lineNumber: Int = 0,
    val isUnstructured: Boolean = false
)
