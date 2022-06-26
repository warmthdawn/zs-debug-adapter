package com.warmthdawm.zsdebugadapter.jdi

import com.sun.jdi.AbsentInformationException
import com.sun.jdi.ReferenceType
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.request.EventRequest
import com.sun.jdi.request.EventRequestManager
import com.warmthdawm.zsdebugadapter.logger
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.SourceBreakpoint
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.concurrent.withLock

class BreakpointManager {
    private val lock = ReentrantLock()

    private var debuggee: JDIDebuggee? = null


    private val breakpoints = mutableMapOf<Source, List<Breakpoint>>()
    private val pendingBreakpoints = mutableMapOf<Source, MutableList<Breakpoint>>()


    fun reset() {
        lock.lock()
        debuggee = null
        breakpoints.clear()
        pendingBreakpoints.clear()

        logger.info { "Reset breakpoint manager" }
        lock.unlock()
    }

    fun supplyDebuggee(debuggee: JDIDebuggee) {
        this.debuggee = debuggee
        val bus = debuggee.eventBus
        bus.makeVMRequest(EventRequestManager::createClassPrepareRequest) {
            addSourceNameFilter("*.zs")
        }
        bus.onVMEvent<ClassPrepareEvent> { args ->
            val refType = args.jdiEvent.referenceType()
            val name = refType.name()
            val sourceName = refType.sourceName()

            lock.withLock {
                val possible = pendingBreakpoints.keys.firstOrNull { source ->
                    sourceName == source.relativePath()
                }

                val pendingList = pendingBreakpoints[possible]

                var loadedNumber = 0

                pendingList?.removeIf {
                    if (loadBreakpoint(it, refType)) {
                        loadedNumber++
                        bus.emit(BreakpointChangeEvent, it)
                        true
                    } else false
                }

                logger.info {
                    if (loadedNumber == 0)
                        "Loading class $name without breakpoints "
                    else "Loading class $name with $loadedNumber breakpoints "
                }
            }
        }
        if (breakpoints.isNotEmpty()) {
            refreshBreakpointRequests()
        }
    }

    private fun loadBreakpoint(breakpoint: Breakpoint, refType: ReferenceType): Boolean {
        if (debuggee == null) {
            breakpoint.isVerified = false
            return false
        }
        try {

            val location = refType
                .locationsOfLine(breakpoint.line.toInt())
                ?.firstOrNull() ?: return false

            val req = debuggee?.vm?.eventRequestManager()?.createBreakpointRequest(location)
                ?.apply {
                    setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
                    enable()
                }

            return if (req != null) {
                breakpoint.isVerified = true
                true
            } else false
        } catch (e: AbsentInformationException) {
            return false
        }
    }


    private fun findClasses(source: Source): List<ReferenceType> {
        val debuggee = debuggee ?: return emptyList()
        val sourceName = source.relativePath()
        return debuggee.vm.allClasses().filter {
            kotlin.runCatching { it.sourceName() == sourceName }.getOrElse { false }
        }
    }

    private fun refreshBreakpointRequests(sendChange: Boolean = true) = lock.withLock {
        if (debuggee == null) {
            return
        }

        debuggee!!.vm.eventRequestManager().deleteAllBreakpoints()


        var loaded = 0
        var pending = 0

        pendingBreakpoints.clear()

        for ((source, bpList) in breakpoints) {
            val i = validateBreakpoints(source, bpList, sendChange)
            loaded += i
            pending += bpList.size - i
        }

        logger.info { "Breakpoints refreshed, Loaded $loaded breakpoints, pending $pending breakpoints" }

    }

    private fun validateBreakpoints(source: Source, bpList: List<Breakpoint>, sendChange: Boolean): Int {
        var loaded = 0
        val classes = findClasses(source)

        val pendingList = ArrayList<Breakpoint>()

        if (classes.isNotEmpty()) {
            bpFor@ for (breakpoint in bpList) {
                for (clazz in classes) {
                    val succeed = loadBreakpoint(breakpoint, clazz)
                    if (succeed) {
                        loaded++
                        if (sendChange)
                            debuggee?.eventBus?.emit(BreakpointChangeEvent, breakpoint)
                        continue@bpFor
                    }
                }
                pendingList.add(breakpoint)
            }

            pendingBreakpoints[source] = pendingList
        } else {
            pendingBreakpoints[source] = bpList.toMutableList()
        }
        return loaded
    }


    fun setBreakpoints(args: SetBreakpointsArguments): List<Breakpoint> {
        logger.info { "Adding ${args.breakpoints.size} breakpoints in ${args.source.name}" }
        val source = args.source
        val bpList = lock.withLock {
            val bpList = buildList {
                for (sb in args.breakpoints) {
                    add(convertBreakpoint(args.source, sb))
                }
            }
            if (bpList.isEmpty()) {
                breakpoints.remove(source)
            } else {
                breakpoints[source] = bpList
            }
            bpList
        }
        refreshBreakpointRequests(false)

        return bpList
    }


    private fun Source.getClassName(): String? {
        val fullPath = Paths.get(path)
        val relativePath = debuggee?.scriptsPath?.relativize(fullPath) ?: return null;
        return extractClassName(relativePath.toString())
    }

    private fun Source.relativePath(): String? {
        val fullPath = Paths.get(path)
        return debuggee?.scriptsPath?.relativize(fullPath)?.toString()
    }


    private var currentID = 0
    private fun nextId() = currentID++

    private fun convertBreakpoint(source: Source, sourceBreakpoint: SourceBreakpoint) = Breakpoint().apply {
        this.id = nextId()
        this.source = source
        isVerified = false
        line = sourceBreakpoint.line
        column = sourceBreakpoint.column
    }


    companion object {


        fun extractClassName(filename: String): String {
            var filename = filename
            filename = filename.replace('\\', '/')
            if (filename.startsWith("/")) filename = filename.substring(1)

            // trim extension
            val lastDot = filename.lastIndexOf('.')
            if (lastDot > 0) filename = filename.substring(0, lastDot)
            filename = filename.replace(".", "_")
            filename = filename.replace(" ", "_")
            val dir: String
            val name: String

            // get file name vs folder path
            val lastSlash = filename.lastIndexOf('/')
            if (lastSlash > 0) {
                dir = filename.substring(0, lastSlash)
                name = filename.substring(lastSlash + 1)
            } else {
                name = filename
                dir = ""
            }
            return (if (dir.isNotEmpty()) dir.replace('/', '\\') + "\\" else "") + name.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.getDefault()
                ) else it.toString()
            }
        }
    }

}