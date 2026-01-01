package io.pandu.core.tools

import kotlinx.atomicfu.atomic

/**
 * Thread-safe coroutine ID generator.
 * Generates unique sequential IDs for tracking coroutines.
 */
internal object CoroutineIdGenerator {
    private val counter = atomic(0L)

    /**
     * Generate the next unique coroutine ID.
     */
    fun next(): Long = counter.incrementAndGet()

    /**
     * Reset the counter (for testing purposes only).
     */
    internal fun reset() {
        counter.value = 0L
    }
}