package io.pandu.core.tools

import io.pandu.core.CoroutineTraceContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.currentCoroutineContext

/**
 * Create a child span within a suspend function.
 *
 * ```kotlin
 * suspend fun processUser(id: String) = withSpan("process-user") {
 *     val user = fetchUser(id)
 *     validateUser(user)
 * }
 * ```
 */
suspend inline fun <T> withSpan(
    operationName: String,
    crossinline block: suspend CoroutineScope.() -> T
): T {
    val parentTrace = currentCoroutineContext()[CoroutineTraceContext.Key]
    val childTrace = parentTrace?.createChildSpan(operationName)
        ?: CoroutineTraceContext.Key.create(operationName)

    return withContext(childTrace) {
        block()
    }
}

/**
 * Add baggage to the current trace context.
 *
 * ```kotlin
 * withBaggage("user-id", userId) {
 *     // All child operations will have this baggage
 * }
 * ```
 */
suspend inline fun <T> withBaggage(
    key: String,
    value: String,
    crossinline block: suspend CoroutineScope.() -> T
): T {
    val currentTrace = currentCoroutineContext()[CoroutineTraceContext.Key]
        ?: return coroutineScope { block() }

    val newTrace = currentTrace.withBaggage(key, value)
    return withContext(newTrace) {
        block()
    }
}

/**
 * Create an atomic array of nullable references.
 * Uses expect/actual for platform-specific implementation.
 */
internal expect fun <T> atomicArrayOfNulls(size: Int): AtomicArray<T>

/**
 * Platform-agnostic atomic array interface.
 */
internal interface AtomicArray<T> {
    val size: Int
    operator fun get(index: Int): AtomicRef<T?>
}

/**
 * Platform-agnostic atomic reference interface.
 */
internal interface AtomicRef<T> {
    var value: T
    fun compareAndSet(expected: T, new: T): Boolean
}
