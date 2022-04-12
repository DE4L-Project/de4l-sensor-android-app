package io.de4l.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class ObservableSet<E>(private val set: MutableSet<E>) : Iterable<E> {
    private val changed = MutableSharedFlow<Boolean>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun add(element: E) {
        set.add(element)
        coroutineScope.launch {
            changed.emit(true)
        }
    }

    fun remove(element: E) {
        set.remove(element)
        coroutineScope.launch {
            changed.emit(true)
        }
    }

    fun size(): Int {
        return set.size
    }

    override fun iterator(): Iterator<E> {
        return set.iterator()
    }

    fun changed(): SharedFlow<Boolean> {
        return changed;
    }

    fun contains(element: E): Boolean {
        return set.contains(element)
    }

}