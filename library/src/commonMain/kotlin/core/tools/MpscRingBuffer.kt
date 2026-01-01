package io.pandu.core.tools

import kotlinx.atomicfu.atomic

/**
 * Lock-free Multi-Producer Single-Consumer Ring Buffer.
 *
 * This is optimized for the telemetry use case where:
 * - Multiple threads produce events (coroutines emitting telemetry)
 * - Single consumer thread processes events (background exporter)
 *
 * Uses atomic CAS operations for thread-safety without locks.
 *
 * @param capacity Must be a power of 2
 */
internal class MpscRingBuffer<T : Any>(capacity: Int) {

    init {
        require(capacity > 0) { "Capacity must be positive" }
        require(capacity and (capacity - 1) == 0) { "Capacity must be a power of 2" }
    }

    private val mask = capacity - 1
    private val buffer = atomicArrayOfNulls<T>(capacity)
    private val head = atomic(0L) // Consumer read position
    private val tail = atomic(0L) // Producer write position

    /**
     * Attempt to add an element to the buffer.
     *
     * @return true if successful, false if buffer is full
     */
    fun offer(element: T): Boolean {
        while (true) {
            val currentTail = tail.value
            val currentHead = head.value

            // Check if buffer is full
            if (currentTail - currentHead >= buffer.size) {
                return false
            }

            // Try to claim the slot
            if (tail.compareAndSet(currentTail, currentTail + 1)) {
                // We claimed the slot, write the element
                val index = (currentTail and mask.toLong()).toInt()
                buffer[index].value = element
                return true
            }
            // CAS failed, another producer got there first - retry
        }
    }

    /**
     * Remove and return the next element from the buffer.
     *
     * @return The element, or null if buffer is empty
     */
    fun poll(): T? {
        val currentHead = head.value
        val currentTail = tail.value

        // Check if buffer is empty
        if (currentHead >= currentTail) {
            return null
        }

        val index = (currentHead and mask.toLong()).toInt()

        // Wait for the producer to finish writing
        // This spin is rare and brief - only happens during race
        var element = buffer[index].value
        while (element == null) {
            element = buffer[index].value
        }

        // Clear the slot and advance head
        buffer[index].value = null
        head.incrementAndGet()

        return element
    }

    /**
     * Peek at the next element without removing it.
     */
    fun peek(): T? {
        val currentHead = head.value
        val currentTail = tail.value

        if (currentHead >= currentTail) {
            return null
        }

        val index = (currentHead and mask.toLong()).toInt()
        return buffer[index].value
    }

    /**
     * Check if buffer is empty.
     */
    fun isEmpty(): Boolean {
        return head.value >= tail.value
    }

    /**
     * Approximate size (may be stale).
     */
    val size: Int
        get() = (tail.value - head.value).coerceAtLeast(0).toInt()

    /**
     * Buffer capacity.
     */
    val capacity: Int = capacity
}