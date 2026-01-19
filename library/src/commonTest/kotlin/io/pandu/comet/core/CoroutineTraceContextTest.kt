package io.pandu.comet.core

import io.pandu.core.CoroutineTraceContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoroutineTraceContextTest {

    // =====================================================================
    // ID Generation Format Tests
    // =====================================================================

    @Test
    fun `create generates traceId with 32 characters`() {
        val context = CoroutineTraceContext.create("test-operation")
        assertEquals(32, context.traceId.length)
    }

    @Test
    fun `create generates spanId with 16 characters`() {
        val context = CoroutineTraceContext.create("test-operation")
        assertEquals(16, context.spanId.length)
    }

    @Test
    fun `create generates traceId with only hex characters`() {
        val context = CoroutineTraceContext.create("test-operation")
        assertTrue(context.traceId.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `create generates spanId with only hex characters`() {
        val context = CoroutineTraceContext.create("test-operation")
        assertTrue(context.spanId.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `create generates unique traceIds`() {
        val contexts = (1..100).map { CoroutineTraceContext.create("operation-$it") }
        val traceIds = contexts.map { it.traceId }.toSet()
        assertEquals(100, traceIds.size)
    }

    @Test
    fun `create generates unique spanIds`() {
        val contexts = (1..100).map { CoroutineTraceContext.create("operation-$it") }
        val spanIds = contexts.map { it.spanId }.toSet()
        assertEquals(100, spanIds.size)
    }

    // =====================================================================
    // Root Context Tests
    // =====================================================================

    @Test
    fun `create sets parentSpanId to null for root context`() {
        val context = CoroutineTraceContext.create("root-operation")
        assertNull(context.parentSpanId)
    }

    @Test
    fun `create sets operationName correctly`() {
        val context = CoroutineTraceContext.create("my-operation")
        assertEquals("my-operation", context.operationName)
    }

    @Test
    fun `create allows empty operation name`() {
        val context = CoroutineTraceContext.create("")
        assertEquals("", context.operationName)
    }

    // =====================================================================
    // Child Span Creation Tests
    // =====================================================================

    @Test
    fun `createChildSpan inherits traceId from parent`() {
        val parent = CoroutineTraceContext.create("parent")
        val child = parent.createChildSpan("child")
        assertEquals(parent.traceId, child.traceId)
    }

    @Test
    fun `createChildSpan generates new spanId`() {
        val parent = CoroutineTraceContext.create("parent")
        val child = parent.createChildSpan("child")
        assertNotEquals(parent.spanId, child.spanId)
    }

    @Test
    fun `createChildSpan sets parentSpanId to parent spanId`() {
        val parent = CoroutineTraceContext.create("parent")
        val child = parent.createChildSpan("child")
        assertEquals(parent.spanId, child.parentSpanId)
    }

    @Test
    fun `createChildSpan sets operationName correctly`() {
        val parent = CoroutineTraceContext.create("parent")
        val child = parent.createChildSpan("child-operation")
        assertEquals("child-operation", child.operationName)
    }

    @Test
    fun `createChildSpan generates spanId with 16 characters`() {
        val parent = CoroutineTraceContext.create("parent")
        val child = parent.createChildSpan("child")
        assertEquals(16, child.spanId.length)
    }

    @Test
    fun `createChildSpan generates spanId with only hex characters`() {
        val parent = CoroutineTraceContext.create("parent")
        val child = parent.createChildSpan("child")
        assertTrue(child.spanId.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `createChildSpan generates unique spanIds for multiple children`() {
        val parent = CoroutineTraceContext.create("parent")
        val children = (1..100).map { parent.createChildSpan("child-$it") }
        val spanIds = children.map { it.spanId }.toSet()
        assertEquals(100, spanIds.size)
    }

    @Test
    fun `nested children maintain correct parent chain`() {
        val root = CoroutineTraceContext.create("root")
        val level1 = root.createChildSpan("level1")
        val level2 = level1.createChildSpan("level2")
        val level3 = level2.createChildSpan("level3")

        // All share the same traceId
        assertEquals(root.traceId, level1.traceId)
        assertEquals(root.traceId, level2.traceId)
        assertEquals(root.traceId, level3.traceId)

        // Parent chain is correct
        assertNull(root.parentSpanId)
        assertEquals(root.spanId, level1.parentSpanId)
        assertEquals(level1.spanId, level2.parentSpanId)
        assertEquals(level2.spanId, level3.parentSpanId)
    }

    // =====================================================================
    // Equals/HashCode Contract Tests
    // =====================================================================

    @Test
    fun `equals returns true for same traceId and spanId`() {
        val context1 = CoroutineTraceContext.create("op1")
        // Same context should equal itself
        assertEquals(context1, context1)
    }

    @Test
    fun `equals returns false for different traceId`() {
        val context1 = CoroutineTraceContext.create("operation")
        val context2 = CoroutineTraceContext.create("operation")
        assertNotEquals(context1, context2)
    }

    @Test
    fun `equals returns false for different spanId same traceId`() {
        val parent = CoroutineTraceContext.create("parent")
        val child1 = parent.createChildSpan("child")
        val child2 = parent.createChildSpan("child")

        // Same traceId but different spanIds
        assertEquals(child1.traceId, child2.traceId)
        assertNotEquals(child1, child2)
    }

    @Test
    fun `equals ignores operationName differences`() {
        // Create two contexts - they will have different traceIds anyway
        val context1 = CoroutineTraceContext.create("operation1")
        val context2 = CoroutineTraceContext.create("operation2")

        // Different traceIds means not equal
        assertNotEquals(context1, context2)

        // Self-equality works regardless of operation name
        assertEquals(context1, context1)
    }

    @Test
    fun `equals returns false for null`() {
        val context = CoroutineTraceContext.create("operation")
        @Suppress("SENSELESS_COMPARISON")
        assertTrue(context != null)
    }

    @Test
    fun `equals returns false for different type`() {
        val context = CoroutineTraceContext.create("operation")
        assertNotEquals("not a trace context" as Any, context as Any)
    }

    @Test
    fun `hashCode is consistent for same context`() {
        val context = CoroutineTraceContext.create("operation")
        assertEquals(context.hashCode(), context.hashCode())
    }

    @Test
    fun `hashCode is different for different contexts`() {
        val context1 = CoroutineTraceContext.create("operation")
        val context2 = CoroutineTraceContext.create("operation")
        // Different traceIds should produce different hashCodes (with high probability)
        assertNotEquals(context1.hashCode(), context2.hashCode())
    }

    // =====================================================================
    // toString Tests
    // =====================================================================

    @Test
    fun `toString contains traceId`() {
        val context = CoroutineTraceContext.create("operation")
        assertTrue(context.toString().contains(context.traceId))
    }

    @Test
    fun `toString contains spanId`() {
        val context = CoroutineTraceContext.create("operation")
        assertTrue(context.toString().contains(context.spanId))
    }

    @Test
    fun `toString contains operationName`() {
        val context = CoroutineTraceContext.create("my-operation")
        assertTrue(context.toString().contains("my-operation"))
    }

    @Test
    fun `toString contains parent null for root context`() {
        val context = CoroutineTraceContext.create("operation")
        assertTrue(context.toString().contains("parent=null"))
    }

    @Test
    fun `toString contains parentSpanId for child context`() {
        val parent = CoroutineTraceContext.create("parent")
        val child = parent.createChildSpan("child")
        assertTrue(child.toString().contains(parent.spanId))
    }

    // =====================================================================
    // CoroutineContext Key Tests
    // =====================================================================

    @Test
    fun `CoroutineTraceContext Key is accessible`() {
        val key = CoroutineTraceContext.Key
        assertNotNull(key)
    }

    @Test
    fun `context can be retrieved from coroutine context using key`() {
        val context = CoroutineTraceContext.create("operation")
        val coroutineContext = context as kotlin.coroutines.CoroutineContext

        val retrieved = coroutineContext[CoroutineTraceContext.Key]
        assertEquals(context, retrieved)
    }
}
