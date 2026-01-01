package io.pandu.core.telemetry.metrics

import io.pandu.core.currentTimeNanos
import io.pandu.core.telemetry.types.CoroutineTelemetry
import io.pandu.core.telemetry.types.CoroutineStarted
import io.pandu.core.telemetry.types.CoroutineCompleted
import io.pandu.core.telemetry.types.CoroutineFailed
import io.pandu.core.telemetry.types.CoroutineCancelled
import io.pandu.core.telemetry.types.CoroutineSuspended
import io.pandu.core.telemetry.types.CoroutineResumed
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Aggregates telemetry events into real-time metrics.
 *
 * Uses atomic operations for thread-safety with minimal locking.
 */
internal class CoroutineMetricsAggregator {

    // Global counters (atomic for thread-safety)
    private val activeCoroutines = atomic(0L)
    private val totalStarted = atomic(0L)
    private val totalCompleted = atomic(0L)
    private val totalFailed = atomic(0L)
    private val totalCancelled = atomic(0L)
    private val totalDropped = atomic(0L)

    // Duration tracking using streaming algorithm
    private val durationTracker = DurationTracker()

    // Per-dispatcher metrics
    private val dispatcherMetrics = ConcurrentMap<String, DispatcherMetricsAccumulator>()

    // Per-operation metrics
    private val operationMetrics = ConcurrentMap<String, OperationMetricsAccumulator>()

    /**
     * Record a telemetry event.
     * Called synchronously from the event emission path.
     */
    fun record(event: CoroutineTelemetry) {
        when (event) {
            is CoroutineStarted -> {
                activeCoroutines.incrementAndGet()
                totalStarted.incrementAndGet()
                getDispatcherAccumulator(event.dispatcher).recordStart()
                event.coroutineTraceContext?.operationName?.let { op ->
                    getOperationAccumulator(op).recordStart()
                }
            }

            is CoroutineCompleted -> {
                activeCoroutines.decrementAndGet()
                totalCompleted.incrementAndGet()
                durationTracker.record(event.totalDurationNanos)
                getDispatcherAccumulator(event.dispatcher).recordComplete(
                    event.totalDurationNanos
                )
                event.coroutineTraceContext?.operationName?.let { op ->
                    getOperationAccumulator(op).recordComplete(
                        event.totalDurationNanos,
                        event.suspensionCount
                    )
                }
            }

            is CoroutineFailed -> {
                activeCoroutines.decrementAndGet()
                totalFailed.incrementAndGet()
                durationTracker.record(event.totalDurationNanos)
                getDispatcherAccumulator(event.dispatcher).recordFailure(
                    event.totalDurationNanos
                )
                event.coroutineTraceContext?.operationName?.let { op ->
                    getOperationAccumulator(op).recordFailure(event.totalDurationNanos)
                }
            }

            is CoroutineCancelled -> {
                activeCoroutines.decrementAndGet()
                totalCancelled.incrementAndGet()
                getDispatcherAccumulator(event.dispatcher).recordCancelled()
                event.coroutineTraceContext?.operationName?.let { op ->
                    getOperationAccumulator(op).recordCancelled()
                }
            }

            is CoroutineSuspended, is CoroutineResumed -> {
                // These don't affect aggregate metrics currently
                // Could track suspension stats per operation if needed
            }
        }
    }

    /**
     * Record a dropped event.
     */
    fun recordDropped() {
        totalDropped.incrementAndGet()
    }

    /**
     * Create a snapshot of current metrics.
     */
    fun snapshot(): CoroutineMetricsSnapshot {
        return CoroutineMetricsSnapshot(
            activeCoroutines = activeCoroutines.value,
            totalStarted = totalStarted.value,
            totalCompleted = totalCompleted.value,
            totalFailed = totalFailed.value,
            totalCancelled = totalCancelled.value,
            totalDropped = totalDropped.value,
            durationStats = durationTracker.snapshot(),
            byDispatcher = dispatcherMetrics.snapshot().mapValues { (_, acc) ->
                acc.snapshot()
            },
            byOperation = operationMetrics.snapshot().mapValues { (_, acc) ->
                acc.snapshot()
            },
            snapshotTimestamp = currentTimeNanos()
        )
    }

    private fun getDispatcherAccumulator(name: String): DispatcherMetricsAccumulator {
        return dispatcherMetrics.getOrPut(name) { DispatcherMetricsAccumulator(name) }
    }

    private fun getOperationAccumulator(name: String): OperationMetricsAccumulator {
        return operationMetrics.getOrPut(name) { OperationMetricsAccumulator(name) }
    }

    /**
     * Thread-safe concurrent map for common usage.
     * Uses atomicfu synchronization for thread-safety.
     */
    internal class ConcurrentMap<K, V> {
        private val map = mutableMapOf<K, V>()
        private val lock = SynchronizedObject()

        fun getOrPut(key: K, defaultValue: () -> V): V {
            return synchronized(lock) {
                map.getOrPut(key, defaultValue)
            }
        }

        fun snapshot(): Map<K, V> {
            return synchronized(lock) {
                map.toMap()
            }
        }
    }
}



