package io.de4l.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ObservableSet<E>(private val set: MutableSet<E>) : Iterable<E> {
    private val changed = MutableSharedFlow<Boolean>()
    private val removed = MutableSharedFlow<E>()
    private val added = MutableSharedFlow<E>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun add(element: E) {
        set.add(element)
        coroutineScope.launch {
            changed.emit(true)
            added.emit(element)
        }
    }

    fun remove(element: E) {
        set.remove(element)
        coroutineScope.launch {
            changed.emit(true)
            removed.emit(element)
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

    fun addedElements(): SharedFlow<E> {
        return added.asSharedFlow();
    }

    fun removedElements(): SharedFlow<E> {
        return removed.asSharedFlow()
    }

    fun contains(element: E): Boolean {
        return set.contains(element)
    }

}