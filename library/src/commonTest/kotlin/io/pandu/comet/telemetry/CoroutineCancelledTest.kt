package io.pandu.comet.telemetry

import io.pandu.core.CoroutineTraceContext
import io.pandu.core.telemetry.types.CoroutineCancelled
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoroutineCancelledTest {

    // =====================================================================
    // Direct Construction Tests
    // =====================================================================

    @Test
    fun `direct construction sets all fields correctly`() {
        val traceContext = CoroutineTraceContext.create("test-op")
        val event = CoroutineCancelled(
            timestamp = 123456789L,
            coroutineTraceContext = traceContext,
            coroutineId = 42L,
            coroutineName = "test-coroutine",
            dispatcher = "Default",
            threadName = "main",
            cancellationReason = "User cancelled",
            totalDurationNanos = 1000000L
        )

        assertEquals(123456789L, event.timestamp)
        assertEquals(traceContext, event.coroutineTraceContext)
        assertEquals(42L, event.coroutineId)
        assertEquals("test-coroutine", event.coroutineName)
        assertEquals("Default", event.dispatcher)
        assertEquals("main", event.threadName)
        assertEquals("User cancelled", event.cancellationReason)
        assertEquals(1000000L, event.totalDurationNanos)
    }

    @Test
    fun `direct construction allows null optional fields`() {
        val event = CoroutineCancelled(
            timestamp = 123456789L,
            coroutineTraceContext = null,
            coroutineId = 42L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            cancellationReason = null,
            totalDurationNanos = 1000000L
        )

        assertNull(event.coroutineTraceContext)
        assertNull(event.coroutineName)
        assertNull(event.threadName)
        assertNull(event.cancellationReason)
    }

    // =====================================================================
    // fromCancellation Factory Tests
    // =====================================================================

    @Test
    fun `fromCancellation extracts message from CancellationException`() {
        val exception = CancellationException("Job was cancelled")
        val event = CoroutineCancelled.fromCancellation(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            cause = exception,
            totalDurationNanos = 500L
        )

        assertEquals("Job was cancelled", event.cancellationReason)
    }

    @Test
    fun `fromCancellation handles null cause`() {
        val event = CoroutineCancelled.fromCancellation(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            cause = null,
            totalDurationNanos = 500L
        )

        assertNull(event.cancellationReason)
    }

    @Test
    fun `fromCancellation handles CancellationException with null message`() {
        val exception = CancellationException()
        val event = CoroutineCancelled.fromCancellation(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            cause = exception,
            totalDurationNanos = 500L
        )

        assertNull(event.cancellationReason)
    }

    @Test
    fun `fromCancellation preserves trace context`() {
        val traceContext = CoroutineTraceContext.create("test-operation")
        val exception = CancellationException("Cancelled")

        val event = CoroutineCancelled.fromCancellation(
            timestamp = 100L,
            coroutineTraceContext = traceContext,
            coroutineId = 1L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            cause = exception,
            totalDurationNanos = 500L
        )

        assertEquals(traceContext, event.coroutineTraceContext)
    }

    @Test
    fun `fromCancellation preserves all metadata fields`() {
        val traceContext = CoroutineTraceContext.create("operation")
        val exception = CancellationException("Timeout")

        val event = CoroutineCancelled.fromCancellation(
            timestamp = 999L,
            coroutineTraceContext = traceContext,
            coroutineId = 123L,
            coroutineName = "my-coroutine",
            dispatcher = "IO",
            threadName = "worker-1",
            cause = exception,
            totalDurationNanos = 5000L
        )

        assertEquals(999L, event.timestamp)
        assertEquals(123L, event.coroutineId)
        assertEquals("my-coroutine", event.coroutineName)
        assertEquals("IO", event.dispatcher)
        assertEquals("worker-1", event.threadName)
        assertEquals(5000L, event.totalDurationNanos)
        assertEquals("Timeout", event.cancellationReason)
    }

    // =====================================================================
    // Data Class Behavior Tests
    // =====================================================================

    @Test
    fun `copy works correctly`() {
        val original = CoroutineCancelled(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = "original",
            dispatcher = "Default",
            threadName = null,
            cancellationReason = "Original reason",
            totalDurationNanos = 500L
        )

        val copy = original.copy(coroutineName = "modified", cancellationReason = "New reason")

        assertEquals("modified", copy.coroutineName)
        assertEquals("New reason", copy.cancellationReason)
        assertEquals(original.timestamp, copy.timestamp)
        assertEquals(original.totalDurationNanos, copy.totalDurationNanos)
    }

    @Test
    fun `equals works correctly`() {
        val event1 = CoroutineCancelled(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = "test",
            dispatcher = "Default",
            threadName = null,
            cancellationReason = "Cancelled",
            totalDurationNanos = 500L
        )

        val event2 = CoroutineCancelled(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = "test",
            dispatcher = "Default",
            threadName = null,
            cancellationReason = "Cancelled",
            totalDurationNanos = 500L
        )

        assertEquals(event1, event2)
    }

    @Test
    fun `hashCode is consistent`() {
        val event = CoroutineCancelled(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = "test",
            dispatcher = "Default",
            threadName = null,
            cancellationReason = "Cancelled",
            totalDurationNanos = 500L
        )

        assertEquals(event.hashCode(), event.hashCode())
    }
}
