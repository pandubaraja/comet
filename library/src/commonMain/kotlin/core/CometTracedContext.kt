package io.pandu.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Custom [CoroutineContext] wrapper that preserves the existing dispatcher when combined
 * with a parent context via the `+` operator.
 *
 * The problem: [CometContextElement] uses [ContinuationInterceptor.Key] as its key (same key
 * as all dispatchers). When `parentContext + tracedContext` is evaluated, the stdlib `plus`
 * implementation replaces the parent's dispatcher with [CometContextElement], causing the
 * coroutine to fall back to [kotlinx.coroutines.Dispatchers.Default].
 *
 * The fix: `plus` calls `rightOperand.fold(leftOperand, lambda)`. By overriding [fold],
 * we detect the existing [CoroutineDispatcher] from the left-hand context (the `initial`
 * parameter) before our [CometContextElement] replaces it, and create a new
 * [CometContextElement] that delegates to that dispatcher.
 */
internal class CometTracedContext(
    private val cometElement: CometContextElement,
    private val otherElements: CoroutineContext
) : CoroutineContext {

    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? {
        return cometElement[key] ?: otherElements[key]
    }

    override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R {
        // When called from CoroutineContext.plus(), initial is the left-hand context.
        // Capture any existing CoroutineDispatcher before our ContinuationInterceptor replaces it.
        val effectiveElement = if (initial is CoroutineContext) {
            val existingInterceptor = (initial as CoroutineContext)[ContinuationInterceptor]
            if (existingInterceptor is CoroutineDispatcher) {
                cometElement.withDispatcher(existingInterceptor)
            } else {
                cometElement
            }
        } else {
            cometElement
        }

        // Fold other elements first (CometStorage, CoroutineTraceContext), then the interceptor
        var result = otherElements.fold(initial, operation)
        result = operation(result, effectiveElement)
        return result
    }

    override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext {
        if (cometElement[key] != null) {
            return otherElements
        }
        val newOther = otherElements.minusKey(key)
        if (newOther === otherElements) return this
        if (newOther === EmptyCoroutineContext) return cometElement
        return CometTracedContext(cometElement, newOther)
    }
}
