package io.pandu.core

import kotlin.coroutines.CoroutineContext

/**
 * Marker to indicate a coroutine has already been tracked.
 * Prevents double-tracking when context is inherited.
 */
internal class CoroutineTrackedMarker : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key
    companion object Key : CoroutineContext.Key<CoroutineTrackedMarker>
}