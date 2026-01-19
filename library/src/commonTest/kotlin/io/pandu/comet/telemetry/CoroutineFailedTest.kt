package io.pandu.comet.telemetry

import io.pandu.core.CoroutineTraceContext
import io.pandu.core.telemetry.types.CoroutineFailed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoroutineFailedTest {

    // =====================================================================
    // Direct Construction Tests
    // =====================================================================

    @Test
    fun `direct construction sets all fields correctly`() {
        val traceContext = CoroutineTraceContext.create("test-op")
        val event = CoroutineFailed(
            timestamp = 123456789L,
            coroutineTraceContext = traceContext,
            coroutineId = 42L,
            coroutineName = "test-coroutine",
            dispatcher = "Default",
            threadName = "main",
            exceptionType = "java.lang.RuntimeException",
            exceptionMessage = "Test error",
            exceptionStackTrace = listOf("at Test.method(Test.kt:10)"),
            totalDurationNanos = 1000000L,
            wasHandled = false
        )

        assertEquals(123456789L, event.timestamp)
        assertEquals(traceContext, event.coroutineTraceContext)
        assertEquals(42L, event.coroutineId)
        assertEquals("test-coroutine", event.coroutineName)
        assertEquals("Default", event.dispatcher)
        assertEquals("main", event.threadName)
        assertEquals("java.lang.RuntimeException", event.exceptionType)
        assertEquals("Test error", event.exceptionMessage)
        assertEquals(1, event.exceptionStackTrace?.size)
        assertEquals(1000000L, event.totalDurationNanos)
        assertEquals(false, event.wasHandled)
    }

    @Test
    fun `direct construction allows null optional fields`() {
        val event = CoroutineFailed(
            timestamp = 123456789L,
            coroutineTraceContext = null,
            coroutineId = 42L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            exceptionType = "RuntimeException",
            exceptionMessage = null,
            exceptionStackTrace = null,
            totalDurationNanos = 1000000L,
            wasHandled = true
        )

        assertNull(event.coroutineTraceContext)
        assertNull(event.coroutineName)
        assertNull(event.threadName)
        assertNull(event.exceptionMessage)
        assertNull(event.exceptionStackTrace)
        assertTrue(event.wasHandled)
    }

    // =====================================================================
    // fromException Factory Tests
    // =====================================================================

    @Test
    fun `fromException extracts exception type`() {
        val exception = RuntimeException("Test error")
        val event = CoroutineFailed.fromException(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            exception = exception,
            totalDurationNanos = 500L,
            wasHandled = false,
            includeStackTrace = false
        )

        assertTrue(event.exceptionType.contains("RuntimeException"))
    }

    @Test
    fun `fromException extracts exception message`() {
        val exception = RuntimeException("Test error message")
        val event = CoroutineFailed.fromException(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            exception = exception,
            totalDurationNanos = 500L,
            wasHandled = false,
            includeStackTrace = false
        )

        assertEquals("Test error message", event.exceptionMessage)
    }

    @Test
    fun `fromException handles null exception message`() {
        val exception = RuntimeException()
        val event = CoroutineFailed.fromException(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            exception = exception,
            totalDurationNanos = 500L,
            wasHandled = false,
            includeStackTrace = false
        )

        assertNull(event.exceptionMessage)
    }

    @Test
    fun `fromException does not include stack trace when disabled`() {
        val exception = RuntimeException("Test error")
        val event = CoroutineFailed.fromException(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            exception = exception,
            totalDurationNanos = 500L,
            wasHandled = false,
            includeStackTrace = false
        )

        assertNull(event.exceptionStackTrace)
    }

    @Test
    fun `fromException includes stack trace when enabled`() {
        val exception = RuntimeException("Test error")
        val event = CoroutineFailed.fromException(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            exception = exception,
            totalDurationNanos = 500L,
            wasHandled = false,
            includeStackTrace = true
        )

        assertNotNull(event.exceptionStackTrace)
        assertTrue(event.exceptionStackTrace!!.isNotEmpty())
    }

    @Test
    fun `fromException preserves wasHandled flag`() {
        val exception = RuntimeException("Test")

        val handledEvent = CoroutineFailed.fromException(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            exception = exception,
            totalDurationNanos = 500L,
            wasHandled = true,
            includeStackTrace = false
        )

        val unhandledEvent = CoroutineFailed.fromException(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            exception = exception,
            totalDurationNanos = 500L,
            wasHandled = false,
            includeStackTrace = false
        )

        assertTrue(handledEvent.wasHandled)
        assertEquals(false, unhandledEvent.wasHandled)
    }

    @Test
    fun `fromException preserves trace context`() {
        val traceContext = CoroutineTraceContext.create("test-operation")
        val exception = RuntimeException("Test")

        val event = CoroutineFailed.fromException(
            timestamp = 100L,
            coroutineTraceContext = traceContext,
            coroutineId = 1L,
            coroutineName = null,
            dispatcher = "Default",
            threadName = null,
            exception = exception,
            totalDurationNanos = 500L,
            wasHandled = false,
            includeStackTrace = false
        )

        assertEquals(traceContext, event.coroutineTraceContext)
    }

    @Test
    fun `fromException preserves all metadata fields`() {
        val traceContext = CoroutineTraceContext.create("operation")
        val exception = IllegalStateException("State error")

        val event = CoroutineFailed.fromException(
            timestamp = 999L,
            coroutineTraceContext = traceContext,
            coroutineId = 123L,
            coroutineName = "my-coroutine",
            dispatcher = "IO",
            threadName = "worker-1",
            exception = exception,
            totalDurationNanos = 5000L,
            wasHandled = true,
            includeStackTrace = false
        )

        assertEquals(999L, event.timestamp)
        assertEquals(123L, event.coroutineId)
        assertEquals("my-coroutine", event.coroutineName)
        assertEquals("IO", event.dispatcher)
        assertEquals("worker-1", event.threadName)
        assertEquals(5000L, event.totalDurationNanos)
    }

    // =====================================================================
    // Data Class Behavior Tests
    // =====================================================================

    @Test
    fun `copy works correctly`() {
        val original = CoroutineFailed(
            timestamp = 100L,
            coroutineTraceContext = null,
            coroutineId = 1L,
            coroutineName = "original",
            dispatcher = "Default",
            threadName = null,
            exceptionType = "RuntimeException",
            exceptionMessage = "Error",
            exceptionStackTrace = null,
            totalDurationNanos = 500L,
            wasHandled = false
        )

        val copy = original.copy(coroutineName = "modified", wasHandled = true)

        assertEquals("modified", copy.coroutineName)
        assertTrue(copy.wasHandled)
        assertEquals(original.timestamp, copy.timestamp)
        assertEquals(original.exceptionType, copy.exceptionType)
    }
}
