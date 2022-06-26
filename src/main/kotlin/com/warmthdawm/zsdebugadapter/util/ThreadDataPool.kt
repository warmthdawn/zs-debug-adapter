package com.warmthdawm.zsdebugadapter.util;

class ThreadDataPool<T> {
    private val idMap = mutableMapOf<Int, T>()
    private val threadsMap = mutableMapOf<Long, MutableSet<Int>>()
    private var currentID = 0;


    fun put(thread: Long, value: T): Int {
        val id = nextID()

        idMap[id] = value
        threadsMap.computeIfAbsent(thread) { mutableSetOf() }
            .add(id)
        return id
    }

    fun removeAllByThread(thread: Long) {
        threadsMap.remove(thread)?.let {
            it.forEach { id ->
                idMap.remove(id)
            }
        }
    }

    fun containsId(id: Int) = idMap.contains(id)

    private fun nextID(): Int {
        do {
            currentID++
        } while (containsId(currentID));

        return currentID
    }

    fun getById(id: Int) = idMap[id]

}
