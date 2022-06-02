package io.de4l.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class ObservableMap<K, V>(private val map: MutableMap<K, V>) {

    private val changed = MutableSharedFlow<Boolean>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    operator fun get(key: K): V? {
        return map[key]
    }

    operator fun set(key: K, value: V) {
        map[key] = value
        emitChanged()
    }

    fun clear() {
        map.clear()
        emitChanged()
    }

    fun remove(key: K) {
        map.remove(key)
        emitChanged()
    }

    fun toMap(): Map<K, V> {
        return map.toMap();
    }

    fun isEmpty(): Boolean {
        return map.isEmpty()
    }

    fun isNotEmpty(): Boolean {
        return map.isNotEmpty()
    }

    fun containsKey(key: K): Boolean {
        return map.containsKey(key)
    }

    fun size(): Int {
        return map.size
    }

    fun changed(): SharedFlow<Boolean> {
        return changed;
    }

    fun entries(): MutableSet<MutableMap.MutableEntry<K, V>> {
        return map.entries
    }

    fun values(): MutableCollection<V> {
        return map.values
    }

    fun removeAllKeys(keys: Set<K>) {
        if (keys.isNotEmpty()) {
            keys.forEach { key ->
                map.remove(key)
            }
            emitChanged()
        }
    }

    private fun emitChanged() {
        coroutineScope.launch {
            changed.emit(true)
        }
    }
}