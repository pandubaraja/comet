package io.pandu.core.tools

import java.util.concurrent.atomic.AtomicReferenceArray

internal actual fun <T> atomicArrayOfNulls(size: Int): AtomicArray<T> {
    return JvmAtomicArray(size)
}

private class JvmAtomicArray<T>(override val size: Int) : AtomicArray<T> {
    private val array = AtomicReferenceArray<T?>(size)

    override fun get(index: Int): AtomicRef<T?> = JvmAtomicRef(array, index)
}

private class JvmAtomicRef<T>(
    private val array: AtomicReferenceArray<T?>,
    private val index: Int
) : AtomicRef<T?> {
    override var value: T?
        get() = array.get(index)
        set(value) { array.set(index, value) }

    override fun compareAndSet(expected: T?, new: T?): Boolean {
        return array.compareAndSet(index, expected, new)
    }
}
