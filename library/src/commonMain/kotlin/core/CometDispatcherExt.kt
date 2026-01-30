package io.pandu.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Wraps this dispatcher with Comet telemetry tracing.
 * Auto-discovers [CometStorage] from the current coroutine context.
 *
 * Usage:
 * ```
 * scope.launch(comet.traced("operation")) {
 *     withContext(Dispatchers.IO.traced()) {
 *         // async/launch calls here are still traced
 *     }
 * }
 * ```
 *
 * If no Comet context is found, returns this dispatcher unchanged.
 */
suspend fun CoroutineDispatcher.traced(): CoroutineContext {
    val storage = coroutineContext[CometStorage.Key] ?: return this
    return this + CometDispatcher(this, storage.config, storage.collector, storage.sampler)
}
