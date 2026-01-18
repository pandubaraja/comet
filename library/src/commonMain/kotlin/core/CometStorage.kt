package io.pandu.core

import io.pandu.config.CometConfig
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Storage element for Comet that uses a unique key.
 * This ensures Comet isn't replaced when dispatchers are added.
 * Also holds the shared span registry for Job hierarchy tracking.
 */
internal class CometStorage(
    val config: CometConfig,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CometStorage>

    // Shared span registry for Job hierarchy tracking across all TelemetryInterceptors
    val spanRegistry = SpanRegistry()

    /**
     * Thread-safe registry for tracking Job -> TraceContext mappings.
     * Shared across all TelemetryInterceptors to enable proper parent-child span linking.
     */
    internal class SpanRegistry {
        private val jobToSpan = mutableMapOf<Job, CoroutineTraceContext>()
        private val lock = SynchronizedObject()

        fun register(job: Job?, span: CoroutineTraceContext) {
            if (job == null) return
            synchronized(lock) {
                jobToSpan[job] = span
            }
        }

        fun unregister(job: Job?) {
            if (job == null) return
            synchronized(lock) {
                jobToSpan.remove(job)
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        fun findParentSpan(job: Job?): CoroutineTraceContext? {
            if (job == null) return null
            var parentJob = job.parent
            while (parentJob != null) {
                val span = synchronized(lock) { jobToSpan[parentJob] }
                if (span != null) return span
                parentJob = parentJob.parent
            }
            return null
        }
    }
}
