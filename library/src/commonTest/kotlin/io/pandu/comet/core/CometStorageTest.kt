package io.pandu.comet.core

import io.pandu.config.CometConfig
import io.pandu.core.CometStorage
import io.pandu.core.CoroutineTraceContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class CometStorageTest {

    private fun createStorage(): CometStorage {
        val config = CometConfig.Builder().build()
        return CometStorage(config)
    }

    // =====================================================================
    // SpanRegistry Register Tests
    // =====================================================================

    @Test
    fun `register stores job to span mapping`() = runTest {
        val storage = createStorage()
        val job = Job()
        val span = CoroutineTraceContext.create("test-operation")

        storage.spanRegistry.register(job, span)

        // We can't directly access the mapping, but we can verify via findParentSpan
        val childJob = Job(parent = job)
        val foundSpan = storage.spanRegistry.findParentSpan(childJob)
        assertEquals(span, foundSpan)

        job.cancel()
        childJob.cancel()
    }

    @Test
    fun `register with null job does not throw`() {
        val storage = createStorage()
        val span = CoroutineTraceContext.create("test-operation")

        // Should not throw
        storage.spanRegistry.register(null, span)
    }

    @Test
    fun `register overwrites existing mapping for same job`() = runTest {
        val storage = createStorage()
        val job = Job()
        val span1 = CoroutineTraceContext.create("operation-1")
        val span2 = CoroutineTraceContext.create("operation-2")

        storage.spanRegistry.register(job, span1)
        storage.spanRegistry.register(job, span2)

        val childJob = Job(parent = job)
        val foundSpan = storage.spanRegistry.findParentSpan(childJob)
        assertEquals(span2, foundSpan)

        job.cancel()
        childJob.cancel()
    }

    // =====================================================================
    // SpanRegistry Unregister Tests
    // =====================================================================

    @Test
    fun `unregister removes job to span mapping`() = runTest {
        val storage = createStorage()
        val job = Job()
        val span = CoroutineTraceContext.create("test-operation")

        storage.spanRegistry.register(job, span)
        storage.spanRegistry.unregister(job)

        val childJob = Job(parent = job)
        val foundSpan = storage.spanRegistry.findParentSpan(childJob)
        assertNull(foundSpan)

        job.cancel()
        childJob.cancel()
    }

    @Test
    fun `unregister with null job does not throw`() {
        val storage = createStorage()

        // Should not throw
        storage.spanRegistry.unregister(null)
    }

    @Test
    fun `unregister non-existent job does not throw`() {
        val storage = createStorage()
        val job = Job()

        // Should not throw
        storage.spanRegistry.unregister(job)

        job.cancel()
    }

    // =====================================================================
    // SpanRegistry FindParentSpan Tests
    // =====================================================================

    @Test
    fun `findParentSpan returns null for null job`() {
        val storage = createStorage()
        val result = storage.spanRegistry.findParentSpan(null)
        assertNull(result)
    }

    @Test
    fun `findParentSpan returns null for job without parent`() {
        val storage = createStorage()
        val job = Job()
        val result = storage.spanRegistry.findParentSpan(job)
        assertNull(result)
        job.cancel()
    }

    @Test
    fun `findParentSpan finds span in direct parent`() = runTest {
        val storage = createStorage()
        val parentJob = Job()
        val childJob = Job(parent = parentJob)
        val span = CoroutineTraceContext.create("parent-operation")

        storage.spanRegistry.register(parentJob, span)

        val foundSpan = storage.spanRegistry.findParentSpan(childJob)
        assertEquals(span, foundSpan)

        parentJob.cancel()
        childJob.cancel()
    }

    @Test
    fun `findParentSpan traverses job hierarchy to find span`() = runTest {
        val storage = createStorage()
        val grandparentJob = Job()
        val parentJob = Job(parent = grandparentJob)
        val childJob = Job(parent = parentJob)
        val span = CoroutineTraceContext.create("grandparent-operation")

        storage.spanRegistry.register(grandparentJob, span)

        // Should find the span by traversing up the hierarchy
        val foundSpan = storage.spanRegistry.findParentSpan(childJob)
        assertEquals(span, foundSpan)

        grandparentJob.cancel()
        parentJob.cancel()
        childJob.cancel()
    }

    @Test
    fun `findParentSpan returns nearest parent span`() = runTest {
        val storage = createStorage()
        val grandparentJob = Job()
        val parentJob = Job(parent = grandparentJob)
        val childJob = Job(parent = parentJob)
        val grandparentSpan = CoroutineTraceContext.create("grandparent-operation")
        val parentSpan = CoroutineTraceContext.create("parent-operation")

        storage.spanRegistry.register(grandparentJob, grandparentSpan)
        storage.spanRegistry.register(parentJob, parentSpan)

        // Should find the nearest (parent) span, not the grandparent
        val foundSpan = storage.spanRegistry.findParentSpan(childJob)
        assertEquals(parentSpan, foundSpan)

        grandparentJob.cancel()
        parentJob.cancel()
        childJob.cancel()
    }

    @Test
    fun `findParentSpan returns null when no ancestors have spans`() = runTest {
        val storage = createStorage()
        val grandparentJob = Job()
        val parentJob = Job(parent = grandparentJob)
        val childJob = Job(parent = parentJob)

        // No spans registered
        val foundSpan = storage.spanRegistry.findParentSpan(childJob)
        assertNull(foundSpan)

        grandparentJob.cancel()
        parentJob.cancel()
        childJob.cancel()
    }

    // =====================================================================
    // CometStorage CoroutineContext Key Tests
    // =====================================================================

    @Test
    fun `CometStorage can be retrieved from CoroutineContext`() {
        val storage = createStorage()
        val context: CoroutineContext = storage

        val retrieved = context[CometStorage.Key]
        assertNotNull(retrieved)
        assertEquals(storage, retrieved)
    }

    @Test
    fun `CometStorage exposes config`() {
        val config = CometConfig.Builder()
            .bufferSize(1024)
            .build()
        val storage = CometStorage(config)

        assertEquals(1024, storage.config.bufferSize)
    }

    @Test
    fun `CometStorage exposes spanRegistry`() {
        val storage = createStorage()
        assertNotNull(storage.spanRegistry)
    }

    // =====================================================================
    // SpanRegistry with SupervisorJob Tests
    // =====================================================================

    @Test
    fun `findParentSpan works with SupervisorJob`() = runTest {
        val storage = createStorage()
        val supervisorJob = SupervisorJob()
        val childJob = Job(parent = supervisorJob)
        val span = CoroutineTraceContext.create("supervisor-operation")

        storage.spanRegistry.register(supervisorJob, span)

        val foundSpan = storage.spanRegistry.findParentSpan(childJob)
        assertEquals(span, foundSpan)

        supervisorJob.cancel()
        childJob.cancel()
    }
}
