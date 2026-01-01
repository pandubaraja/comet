package io.pandu.core.tools

import kotlinx.atomicfu.AtomicRef as AtomicFuRef
import kotlinx.atomicfu.atomic

internal actual fun <T> atomicArrayOfNulls(size: Int): AtomicArray<T> {
    return NativeAtomicArray(size)
}

private class NativeAtomicArray<T>(override val size: Int) : AtomicArray<T> {
    private val array = Array(size) { atomic<T?>(null) }

    override fun get(index: Int): AtomicRef<T?> = NativeAtomicRef(array[index])
}

private class NativeAtomicRef<T>(
    private val ref: AtomicFuRef<T?>
) : AtomicRef<T?> {
    override var value: T?
        get() = ref.value
        set(value) { ref.value = value }

    override fun compareAndSet(expected: T?, new: T?): Boolean {
        return ref.compareAndSet(expected, new)
    }
}
