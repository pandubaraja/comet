package io.pandu

import io.pandu.config.CometConfig
import io.pandu.config.CometConfigDsl
import io.pandu.core.CometContextElement
import io.pandu.core.CoroutineTraceContext
import io.pandu.core.telemetry.types.CoroutineTelemetryCollector
import io.pandu.core.telemetry.metrics.CoroutineMetrics
import io.pandu.core.telemetry.metrics.CoroutineMetricsSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * ☄️ Comet - Coroutine Telemetry
 *
 * Simply add Comet to any CoroutineScope or context to enable tracing.
 * No need to replace your existing scopes!
 *
 * ## Basic Usage - Just Add to Context!
 *
 * ```kotlin
 * val comet = Comet.create {
 *     samplingStrategy(ProbabilisticSampling(0.1f))
 *     exporter(LoggingExporter())
 * }
 * comet.start()
 *
 * // Option 1: Add to existing scope's launches
 * existingScope.launch(comet + TraceContext.create("my-operation")) {
 *     // This and all child coroutines are traced!
 *     launch { /* auto traced */ }
 * }
 *
 * // Option 2: Create a new scope with Comet
 * val tracedScope = CoroutineScope(Dispatchers.IO + comet)
 * tracedScope.launch(TraceContext.create("operation")) {
 *     // Traced!
 * }
 *
 * // Option 3: Add to an existing scope permanently
 * val tracedScope = existingScope + comet
 * ```
 *
 * ## How It Works
 *
 * Comet provides a [ContinuationInterceptor] that wraps your dispatcher.
 * When you add Comet to a context:
 * 1. It intercepts coroutine creation
 * 2. Automatically creates child spans for nested coroutines
 * 3. Collects timing and lifecycle events
 * 4. Exports to your configured backends
 *
 * ## Access Metrics
 *
 * ```kotlin
 * val metrics = comet.metrics
 * println("Active: ${metrics.activeCoroutines}")
 * println("P99: ${metrics.durationStats.p99}")
 * ```
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
         *     lowOverheadMode(true)
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

    /**
     * Check if telemetry is currently running.
     */
    val isActive: Boolean
        get() = isRunning

    /**
     * Access the configuration.
     */
    val configuration: CometConfig
        get() = config

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
                CoroutineTraceContext.Key.create(operationName)
    }

    /**
     * Create a context element that can be added to any CoroutineScope.
     *
     * This wraps the specified dispatcher (or the context's existing dispatcher)
     * with telemetry interception.
     *
     * ```kotlin
     * // Use with specific dispatcher
     * scope.launch(comet.asContextElement(Dispatchers.IO)) { ... }
     *
     * // Or let it use the scope's dispatcher
     * scope.launch(comet.asContextElement()) { ... }
     * ```
     */
    fun asContextElement(
        dispatcher: CoroutineDispatcher? = null
    ): CoroutineContext {
        return CometContextElement(config, collector, dispatcher)
    }

    /**
     * Operator to easily add Comet to a CoroutineContext.
     *
     * ```kotlin
     * scope.launch(comet + TraceContext.create("op")) { ... }
     * ```
     */
    operator fun plus(context: CoroutineContext): CoroutineContext {
        return asContextElement() + context
    }
}