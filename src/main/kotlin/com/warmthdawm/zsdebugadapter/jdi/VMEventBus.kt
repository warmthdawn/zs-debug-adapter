package com.warmthdawm.zsdebugadapter.jdi


import com.sun.jdi.VirtualMachine
import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.event.VMDeathEvent
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.EventRequestManager
import com.warmthdawm.zsdebugadapter.event.DefaultEventEmitter
import com.warmthdawm.zsdebugadapter.event.EventEmitter
import com.warmthdawm.zsdebugadapter.logger
import kotlin.concurrent.thread
import com.sun.jdi.event.Event as JDIEvent
import com.sun.jdi.event.EventSet as JDIEventSet
import kotlin.reflect.KClass

/**
 * Asynchronously polls and publishes any events from
 * a debuggee virtual machine.
 */
class VMEventBus(private val vm: VirtualMachine) : EventEmitter by DefaultEventEmitter() {
    private var exited = false

    init {
        startAsyncPoller()
    }

    inline fun <reified T : JDIEvent> onVMEvent(crossinline handler: (VMEventArgs<T>) -> Unit) {
        on(vmEvent<T>()) {
            handler(it)
        }
    }

    fun <T : EventRequest> makeVMRequest(creator: (EventRequestManager) -> T, config: T.() -> Unit) {
        val req = creator(vm.eventRequestManager())
        config(req)
        req.enable()
    }

    private fun startAsyncPoller() {
        thread(name = "JDIEventBus") {
            val eventQueue = vm.eventQueue()
            try {
                while (!exited) {
                    val eventSet = eventQueue.remove()
                    var resumeThreads = true

                    for (event in eventSet) {
                        logger.debug { "Received VM event: $event" }
                        if (event is VMDeathEvent) {
                            exited = true
                            resumeThreads = false
                        } else {
                            val resume = dispatchEvent(event, eventSet)
                            resumeThreads = resumeThreads && resume
                        }
                    }

                    if (resumeThreads) {
                        eventSet.resume()
                    }
                }
            } catch (e: InterruptedException) {
                logger.warn { "VMEventBus event poller terminated by interrupt" }
            } catch (e: VMDisconnectedException) {
                logger.warn { "VMEventBus event poller terminated by disconnect: ${e.message}" }
            }
            emit(ExitEvent)
        }
    }


    private fun dispatchEvent(event: JDIEvent, eventSet: JDIEventSet): Boolean {
        val eventArgs = VMEventArgs(event, eventSet)
        val eventType: VMEvent<JDIEvent> = VMEvent.findEvent(event) ?: return true
        this.emit(eventType, eventArgs)
        return eventArgs.resumeThreads
    }
}
