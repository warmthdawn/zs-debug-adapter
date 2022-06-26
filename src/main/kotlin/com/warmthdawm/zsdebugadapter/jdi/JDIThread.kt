package com.warmthdawm.zsdebugadapter.jdi

import com.sun.jdi.ThreadReference
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.StepEvent
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.StepRequest
import com.warmthdawm.zsdebugadapter.logger
import com.warmthdawm.zsdebugadapter.util.catching
import org.eclipse.lsp4j.debug.StackTraceResponse

class JDIThread(
    private val threadRef: ThreadReference,
    private val context: JDIDebuggee
) {
    val name: String = threadRef.name() ?: "Unnamed Thread"
    private val id: Long = threadRef.uniqueID()

    fun pause() =
        if (!threadRef.isSuspended) {
            threadRef.suspend()
            true
        } else false

    fun resume(): Boolean {
        val suspends = threadRef.suspendCount()
        (0 until suspends).forEach { _ ->
            threadRef.resume()
        }
        return suspends > 0
    }

    fun stackTrace(): StackTraceResponse {
        return catching("Failed to get stack frame of thread ${threadRef.name()}") {
            context.converter.convertStackTrace(threadRef.frames())
        }.getOrDefault(StackTraceResponse())
    }

    fun stepOver() = step(StepRequest.STEP_OVER)

    fun stepInto() = step(StepRequest.STEP_INTO)

    fun stepOut() = step(StepRequest.STEP_OUT)

    private fun step(depth: Int) {
        if (id in context.pendingStepRequestThreadIds) {
            return
        }
        val req = stepRequest(StepRequest.STEP_LINE, depth)
        performStep(req)
    }

    private fun performStep(request: StepRequest) {
        logger.info { "Performing step in thread ${threadRef.uniqueID()}" }
        request.enable()
        resume()
    }

    private fun stepRequest(size: Int, depth: Int): StepRequest {

        logger.info { "Stepping in thread ${threadRef.uniqueID()}" }
        val request = context.vm.eventRequestManager().createStepRequest(threadRef, size, depth)

        request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
        request.addCountFilter(1)

        val abortRequest = {
            context.vm.eventRequestManager().deleteEventRequest(request)
            context.pendingStepRequestThreadIds.remove(id)
        }

        context.eventBus.once(vmEvent<BreakpointEvent>()) { abortRequest() }
        context.eventBus.once(vmEvent<StepEvent>()) { abortRequest() }

        return request

    }
}
