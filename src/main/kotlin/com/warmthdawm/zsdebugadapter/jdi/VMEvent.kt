package com.warmthdawm.zsdebugadapter.jdi

import com.sun.jdi.event.Event as JDIEvent
import com.sun.jdi.event.EventSet
import kotlin.reflect.KClass
import com.warmthdawm.zsdebugadapter.event.Event
import org.eclipse.lsp4j.debug.Breakpoint


data class VMEventArgs<T : JDIEvent>(val jdiEvent: T, val eventSet: EventSet) {
    var resumeThreads = true
}


class VMEvent<T : JDIEvent>
private constructor(val jdiEvent: KClass<T>) : Event<VMEventArgs<T>> {

    override fun equals(other: Any?): Boolean {
        return other is VMEvent<*> && other.jdiEvent == jdiEvent
    }

    override fun hashCode(): Int {
        return jdiEvent.hashCode()
    }

    companion object {
        fun <T : JDIEvent> create(jdiEvent: KClass<T>): VMEvent<T> {
            return VMEvent(jdiEvent).also { events.add(it) }
        }

        private val events = mutableListOf<VMEvent<*>>()

        fun <T : JDIEvent> findEvent(obj: JDIEvent): VMEvent<T>? =
            events.firstOrNull() { it.jdiEvent.isInstance(obj) } as? VMEvent<T>

    }
}

inline fun <reified T : JDIEvent> vmEvent() = VMEvent.create(T::class)


object ExitEvent : Event<Unit>

object BreakpointChangeEvent : Event<Breakpoint>