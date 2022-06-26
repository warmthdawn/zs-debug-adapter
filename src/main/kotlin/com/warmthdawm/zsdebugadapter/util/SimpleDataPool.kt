package com.warmthdawm.zsdebugadapter.util;

class SimpleDataPool<T> {
    private val idMap = mutableMapOf<Int, T>()
    private var currentID = 0

    fun put(value: T): Int {
        val id = nextID()
        idMap[id] = value
        return id
    }


    fun containsId(id: Int) = idMap.contains(id)

    fun clear() = idMap.clear();
    private fun nextID(): Int {
        do {
            currentID++
        } while (containsId(currentID));

        return currentID
    }


    fun getById(id: Int) = idMap[id]
}
