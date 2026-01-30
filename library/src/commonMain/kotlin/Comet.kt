package io.pandu

import io.pandu.config.CometConfig
import io.pandu.config.CometConfigDsl
import io.pandu.core.CometContextElement
import io.pandu.core.CometDispatcher
import io.pandu.core.CoroutineTraceContext
import io.pandu.core.telemetry.types.CoroutineTelemetryCollector
import io.pandu.core.telemetry.metrics.CoroutineMetrics
import io.pandu.core.telemetry.metrics.CoroutineMetricsSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * Comet - Coroutine Telemetry
 *
 * Simply add Comet to any CoroutineScope or context to enable tracing.
 * No need to replace your existing scopes!
 *
 * ## How It Works
 *
 * Comet provides a [ContinuationInterceptor] that wraps your dispatcher.
 * When you add Comet to a context:
 * 1. It intercepts coroutine creation
 * 2. Automatically creates child spans for nested coroutines
 * 3. Collects timing and lifecycle events
 * 4. Exports to your configured backends
 */
class Comet private constructor(
    private val config: CometConfig,
    private val collector: CoroutineTelemetryCollector
) {

    companion object {

        /**
         * Create a new Comet instance with DSL configuration.
         *
         * ```kotlin
         * val comet = Comet.create {
         *     samplingStrategy(ProbabilisticSampling(0.1f))
         *     exporter(LoggingExporter())
         * }
         * ```
         */
        fun create(block: CometConfigDsl.() -> Unit): Comet {
            val config = CometConfigDsl().apply(block).build()
            return create(config)
        }

        /**
         * Create a new Comet instance with explicit configuration.
         */
        fun create(config: CometConfig): Comet {
            val collector = CoroutineTelemetryCollector(config)
            return Comet(config, collector)
        }
    }

    @kotlin.concurrent.Volatile
    private var isRunning = false

    /**
     * Start telemetry collection.
     * Must be called before any traced coroutines are launched.
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        collector.start()
    }

    /**
     * Get current metrics snapshot.
     */
    val metrics: CoroutineMetrics
        get() = if (isRunning) collector.getMetrics() else CoroutineMetricsSnapshot.EMPTY

    /**
     * Suspend until all buffered events are exported.
     */
    suspend fun flush() {
        collector.flush()
    }

    /**
     * Shutdown telemetry collection.
     * Drains remaining events and releases resources.
     */
    suspend fun shutdown() {
        if (!isRunning) return
        isRunning = false
        collector.shutdown()
    }


    // ============================================
    // Context Element Creation
    // ============================================

    /**
     * Create a traced context element with an operation name.
     * This is the primary API for adding tracing to coroutines.
     *
     * ```kotlin
     * // Simple and clean!
     * scope.launch(comet.traced("fetch-user")) {
     *     // This and all children are traced!
     * }
     * ```
     */
    fun traced(operationName: String): CoroutineContext {
        return CometContextElement(config, collector, null).asContext() +
                CoroutineTraceContext.create(operationName)
    }

    /**
     * Wrap a dispatcher with Comet tracing.
     * Use this with `withContext` to preserve tracing when switching dispatchers.
     *
     * ```kotlin
     * scope.launch(comet.traced("operation")) {
     *     withContext(comet.traced(Dispatchers.IO)) {
     *         async { ... } // still traced
     *     }
     * }
     * ```
     */
    fun traced(dispatcher: CoroutineDispatcher): CoroutineContext {
        return dispatcher + CometDispatcher(dispatcher, config, collector, config.samplingStrategy)
    }
}