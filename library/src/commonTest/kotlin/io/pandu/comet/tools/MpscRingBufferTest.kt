package io.pandu.comet.tools

import io.pandu.core.tools.MpscRingBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MpscRingBufferTest {

    // =====================================================================
    // Capacity Validation Tests
    // =====================================================================

    @Test
    fun `capacity must be positive - fails for 0`() {
        assertFailsWith<IllegalArgumentException> {
            MpscRingBuffer<String>(0)
        }
    }

    @Test
    fun `capacity must be positive - fails for negative`() {
        assertFailsWith<IllegalArgumentException> {
            MpscRingBuffer<String>(-1)
        }
    }

    @Test
    fun `capacity must be power of 2 - fails for 3`() {
        assertFailsWith<IllegalArgumentException> {
            MpscRingBuffer<String>(3)
        }
    }

    @Test
    fun `capacity must be power of 2 - fails for 5`() {
        assertFailsWith<IllegalArgumentException> {
            MpscRingBuffer<String>(5)
        }
    }

    @Test
    fun `capacity must be power of 2 - fails for 1023`() {
        assertFailsWith<IllegalArgumentException> {
            MpscRingBuffer<String>(1023)
        }
    }

    @Test
    fun `capacity accepts power of 2 - 1`() {
        val buffer = MpscRingBuffer<String>(1)
        assertEquals(1, buffer.capacity)
    }

    @Test
    fun `capacity accepts power of 2 - 2`() {
        val buffer = MpscRingBuffer<String>(2)
        assertEquals(2, buffer.capacity)
    }

    @Test
    fun `capacity accepts power of 2 - 4`() {
        val buffer = MpscRingBuffer<String>(4)
        assertEquals(4, buffer.capacity)
    }

    @Test
    fun `capacity accepts power of 2 - 1024`() {
        val buffer = MpscRingBuffer<String>(1024)
        assertEquals(1024, buffer.capacity)
    }

    // =====================================================================
    // Empty Buffer Tests
    // =====================================================================

    @Test
    fun `new buffer is empty`() {
        val buffer = MpscRingBuffer<String>(4)
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `new buffer has size 0`() {
        val buffer = MpscRingBuffer<String>(4)
        assertEquals(0, buffer.size)
    }

    @Test
    fun `poll returns null for empty buffer`() {
        val buffer = MpscRingBuffer<String>(4)
        assertNull(buffer.poll())
    }

    @Test
    fun `peek returns null for empty buffer`() {
        val buffer = MpscRingBuffer<String>(4)
        assertNull(buffer.peek())
    }

    // =====================================================================
    // Offer/Poll Tests
    // =====================================================================

    @Test
    fun `offer returns true when buffer has space`() {
        val buffer = MpscRingBuffer<String>(4)
        assertTrue(buffer.offer("element"))
    }

    @Test
    fun `offer followed by poll returns the element`() {
        val buffer = MpscRingBuffer<String>(4)
        buffer.offer("hello")
        assertEquals("hello", buffer.poll())
    }

    @Test
    fun `poll returns null after element is consumed`() {
        val buffer = MpscRingBuffer<String>(4)
        buffer.offer("hello")
        buffer.poll()
        assertNull(buffer.poll())
    }

    @Test
    fun `offer increases size`() {
        val buffer = MpscRingBuffer<String>(4)
        buffer.offer("a")
        assertEquals(1, buffer.size)
        buffer.offer("b")
        assertEquals(2, buffer.size)
    }

    @Test
    fun `poll decreases size`() {
        val buffer = MpscRingBuffer<String>(4)
        buffer.offer("a")
        buffer.offer("b")
        buffer.poll()
        assertEquals(1, buffer.size)
    }

    // =====================================================================
    // FIFO Ordering Tests
    // =====================================================================

    @Test
    fun `elements are returned in FIFO order`() {
        val buffer = MpscRingBuffer<String>(4)
        buffer.offer("first")
        buffer.offer("second")
        buffer.offer("third")

        assertEquals("first", buffer.poll())
        assertEquals("second", buffer.poll())
        assertEquals("third", buffer.poll())
    }

    @Test
    fun `interleaved offer and poll maintains FIFO order`() {
        val buffer = MpscRingBuffer<String>(4)
        buffer.offer("a")
        assertEquals("a", buffer.poll())
        buffer.offer("b")
        buffer.offer("c")
        assertEquals("b", buffer.poll())
        buffer.offer("d")
        assertEquals("c", buffer.poll())
        assertEquals("d", buffer.poll())
    }

    // =====================================================================
    // Full Buffer Tests
    // =====================================================================

    @Test
    fun `offer returns false when buffer is full`() {
        val buffer = MpscRingBuffer<String>(2)
        buffer.offer("a")
        buffer.offer("b")
        assertFalse(buffer.offer("c"))
    }

    @Test
    fun `full buffer reports correct size`() {
        val buffer = MpscRingBuffer<String>(4)
        repeat(4) { buffer.offer("item$it") }
        assertEquals(4, buffer.size)
    }

    @Test
    fun `full buffer is not empty`() {
        val buffer = MpscRingBuffer<String>(2)
        buffer.offer("a")
        buffer.offer("b")
        assertFalse(buffer.isEmpty())
    }

    @Test
    fun `can offer after poll on full buffer`() {
        val buffer = MpscRingBuffer<String>(2)
        buffer.offer("a")
        buffer.offer("b")
        assertFalse(buffer.offer("c")) // Full

        buffer.poll() // Remove one
        assertTrue(buffer.offer("c")) // Should work now
    }

    // =====================================================================
    // Peek Tests
    // =====================================================================

    @Test
    fun `peek returns element without removing`() {
        val buffer = MpscRingBuffer<String>(4)
        buffer.offer("hello")
        assertEquals("hello", buffer.peek())
        assertEquals("hello", buffer.peek()) // Still there
        assertEquals(1, buffer.size)
    }

    @Test
    fun `peek returns first element in FIFO order`() {
        val buffer = MpscRingBuffer<String>(4)
        buffer.offer("first")
        buffer.offer("second")
        assertEquals("first", buffer.peek())
    }

    // =====================================================================
    // Wrap-Around Tests
    // =====================================================================

    @Test
    fun `buffer handles wrap-around correctly`() {
        val buffer = MpscRingBuffer<String>(4)

        // Fill buffer
        buffer.offer("a")
        buffer.offer("b")
        buffer.offer("c")
        buffer.offer("d")

        // Consume some
        assertEquals("a", buffer.poll())
        assertEquals("b", buffer.poll())

        // Add more (causes wrap-around)
        buffer.offer("e")
        buffer.offer("f")

        // Verify FIFO still works
        assertEquals("c", buffer.poll())
        assertEquals("d", buffer.poll())
        assertEquals("e", buffer.poll())
        assertEquals("f", buffer.poll())
        assertNull(buffer.poll())
    }

    @Test
    fun `buffer handles multiple wrap-arounds`() {
        val buffer = MpscRingBuffer<Int>(4)
        var expected = 0

        repeat(20) { i ->
            buffer.offer(i)
            if (i >= 3) {
                assertEquals(expected, buffer.poll())
                expected++
            }
        }
    }

    // =====================================================================
    // Type Safety Tests
    // =====================================================================

    @Test
    fun `buffer works with different types - Int`() {
        val buffer = MpscRingBuffer<Int>(4)
        buffer.offer(42)
        assertEquals(42, buffer.poll())
    }

    @Test
    fun `buffer works with different types - data class`() {
        data class Event(val id: Int, val name: String)
        val buffer = MpscRingBuffer<Event>(4)
        buffer.offer(Event(1, "test"))
        assertEquals(Event(1, "test"), buffer.poll())
    }

    // =====================================================================
    // Capacity of 1 Edge Case
    // =====================================================================

    @Test
    fun `buffer with capacity 1 works correctly`() {
        val buffer = MpscRingBuffer<String>(1)
        assertTrue(buffer.offer("a"))
        assertFalse(buffer.offer("b")) // Full
        assertEquals("a", buffer.poll())
        assertTrue(buffer.offer("c")) // Space available
        assertEquals("c", buffer.poll())
    }
}
