package com.warmthdawm.zsdebugadapter.adapter

import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.StepEvent
import com.sun.jdi.event.ThreadDeathEvent
import com.sun.jdi.event.ThreadStartEvent
import com.warmthdawm.zsdebugadapter.jdi.*
import com.warmthdawm.zsdebugadapter.logger
import com.warmthdawm.zsdebugadapter.util.*
import java.util.concurrent.CompletableFuture
import java.nio.file.Paths
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import java.io.InputStream

/** The debug server interface conforming to the Debug Adapter Protocol */
class ZenScriptDebugAdapter() : IDebugProtocolServer {


    private val async = AsyncExecutor()
    private val launcherAsync = AsyncExecutor()
    private val stdoutAsync = AsyncExecutor()
    private val stderrAsync = AsyncExecutor()


    private val breakpointManager = BreakpointManager()

    private var debuggee: JDIDebuggee? = null
    private var client: IDebugProtocolClient? = null

    override fun initialize(args: InitializeRequestArguments?) = async.supply {
        Capabilities().apply {
            supportSuspendDebuggee = true
            supportTerminateDebuggee = true
            supportsFunctionBreakpoints = false
            supportsConfigurationDoneRequest = true

            exceptionBreakpointFilters = emptyArray()
        }
    }


    fun connect(client: IDebugProtocolClient) {
        this.client = client
        (logger as? DAPLoggerImpl)?.client = client
        breakpointManager.reset()

        client.initialized()
        logger.info { "Connected to client." }
    }

    private fun missingRequestArgument(requestName: String, argumentName: String) =
        ZDAException("Sent $requestName to debug adapter without the required argument'$argumentName'")


    override fun launch(args: MutableMap<String, Any>) = throw UnsupportedOperationException("Can only attach!")

    override fun attach(args: MutableMap<String, Any>) = launcherAsync.run {

        waitFor("Initialized") { this.initialized }

        val projectRoot = (args["projectRoot"] as? String)?.let { Paths.get(it) }
            ?: throw missingRequestArgument("attach", "projectRoot")

        val hostName = (args["hostName"] as? String)
            ?: throw missingRequestArgument("attach", "hostName")

        val port = (args["port"] as? Double)?.toInt()
            ?: throw missingRequestArgument("attach", "port")

        val timeout = (args["timeout"] as? Double)?.toInt()
            ?: throw missingRequestArgument("attach", "timeout")


        val scriptsPath = (args["scriptsPath"] as? String)

        val vm = DebugLauncher.attachVM(hostName, port, timeout)
        val sourceRoot = DebugLauncher.scriptsPathOf(projectRoot, scriptsPath)
        debuggee = JDIDebuggee(vm, sourceRoot).also {
            registerListeners(it, it.eventBus)
            breakpointManager.supplyDebuggee(it)
            for (thread in it.threads.keys) {
                notifyThread(thread, ThreadEventArgumentsReason.STARTED)
            }
        }
    }

    private fun registerListeners(debuggee: JDIDebuggee, bus: VMEventBus) {

        bus.onVMEvent<ThreadStartEvent> {
            notifyThread(it.jdiEvent.thread().uniqueID(), ThreadEventArgumentsReason.STARTED)
        }
        bus.onVMEvent<ThreadDeathEvent> {
            notifyThread(it.jdiEvent.thread().uniqueID(), ThreadEventArgumentsReason.EXITED)
        }

        bus.on(ExitEvent) {
            notifyExit(0)
        }
        bus.on(BreakpointChangeEvent) {
            notifyBreakpointReady(it)
        }

        bus.onVMEvent<BreakpointEvent> {
            notifyStop(it.jdiEvent.thread().uniqueID(), StoppedEventArgumentsReason.BREAKPOINT)
            it.resumeThreads = false
        }

        bus.onVMEvent<StepEvent> {
            notifyStop(it.jdiEvent.thread().uniqueID(), StoppedEventArgumentsReason.STEP)
            it.resumeThreads = false
        }


        stdoutAsync.run {
            debuggee.stdout?.let { pipeStreamToOutput(it, OutputEventArgumentsCategory.STDOUT) }
        }
        stderrAsync.run {
            debuggee.stderr?.let { pipeStreamToOutput(it, OutputEventArgumentsCategory.STDERR) }
        }

    }


    override fun next(args: NextArguments) = async.run {
        debuggee!!.threadById(args.threadId.toLong())?.stepOver()
    }

    override fun stepIn(args: StepInArguments) = async.run {
        debuggee!!.threadById(args.threadId.toLong())?.stepInto()
    }

    override fun stepOut(args: StepOutArguments) = async.run {
        debuggee!!.threadById(args.threadId.toLong())?.stepOut()
    }


    override fun pause(args: PauseArguments) = async.run {
        val threadId = args.threadId
        val success = debuggee!!.threadById(threadId.toLong())?.pause()
        if (success == true) {
            notifyStop(threadId.toLong(), StoppedEventArgumentsReason.PAUSE)
        }
    }

    override fun continue_(args: ContinueArguments) = async.supply {
        val threadId = args.threadId.toLong()

        val success = debuggee!!.threadById(threadId)?.resume() ?: false
        var allThreads = false

        if (!success) {
            debuggee!!.resume()
            allThreads = true
        }

        debuggee!!.converter.clearDebugCache(threadId)


        ContinueResponse().apply {
            allThreadsContinued = allThreads
        }
    }


    override fun disconnect(args: DisconnectArguments) = async.run {
        debuggee?.exit()
        debuggee = null
    }


    override fun stackTrace(args: StackTraceArguments) = async.supply {
        debuggee!!.threadById(args.threadId.toLong())?.stackTrace()
    }


    override fun scopes(args: ScopesArguments) = async.supply {
        val frame = debuggee!!.converter.getStackFrame(args.frameId) ?: throw ZDAException("Could not get local frame")
        debuggee!!.converter.convertScopes(frame)
    }

    override fun variables(args: VariablesArguments) = async.supply {
        debuggee!!.variables(args.variablesReference)
    }

    override fun setBreakpoints(args: SetBreakpointsArguments) = async.supply {
        SetBreakpointsResponse().apply {
            breakpoints = breakpointManager.setBreakpoints(args).toTypedArray()
        }
    }

    override fun setExceptionBreakpoints(args: SetExceptionBreakpointsArguments?) = async.supply {
        SetExceptionBreakpointsResponse()
    }

    private var initialized = false

    override fun configurationDone(args: ConfigurationDoneArguments?): CompletableFuture<Void> {
        initialized = true
        return CompletableFuture<Void>()
    }


    override fun threads() = async.supply {
        waitFor("debuggee") { debuggee != null }
        debuggee!!.updateThreads()
        ThreadsResponse().apply {
            threads = debuggee!!.threads
                .asSequence()
                .map { (id, thread) -> debuggee!!.converter.convertThread(id, thread) }
                .toList()
                .toTypedArray()
        }

    }


    private fun notifyThread(threadId: Long, reason: String) {
        client!!.thread(ThreadEventArguments().also {
            it.reason = reason
            it.threadId = threadId.toInt()
        })
    }

    private fun notifyStop(threadId: Long, reason: String) {
        client!!.stopped(StoppedEventArguments().also {
            it.reason = reason
            it.threadId = threadId.toInt()
        })
    }

    private fun notifyExit(exitCode: Long) {
        client!!.exited(ExitedEventArguments().also {
            it.exitCode = exitCode.toInt()
        })
        client!!.terminated(TerminatedEventArguments())

        logger.info { "Debuggee exited with code $exitCode" }
    }

    private fun notifyBreakpointReady(breakpoint: Breakpoint) {
        client!!.breakpoint(BreakpointEventArguments().also {
            it.reason = BreakpointEventArgumentsReason.CHANGED
            it.breakpoint = breakpoint
        })
    }


    private fun pipeStreamToOutput(stream: InputStream, outputCategory: String) {
        stream.bufferedReader().use {
            var line = it.readLine()
            while (line != null) {
                client?.output(OutputEventArguments().apply {
                    category = outputCategory
                    output = line + System.lineSeparator()
                })
                line = it.readLine()
            }
        }
    }
}
