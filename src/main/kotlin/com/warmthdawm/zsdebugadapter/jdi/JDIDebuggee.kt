package com.warmthdawm.zsdebugadapter.jdi

import com.sun.jdi.*
import com.sun.jdi.event.BreakpointEvent
import com.warmthdawm.zsdebugadapter.adapter.JDI2LSPConverter
import com.warmthdawm.zsdebugadapter.logger
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.VariablesResponse
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

class JDIDebuggee(
    val vm: VirtualMachine,
    val scriptsPath: Path,
) {
    var threads = emptyMap<Long, JDIThread>()

    val eventBus: VMEventBus = VMEventBus(vm)
    val stdin: OutputStream?
    val stdout: InputStream?
    val stderr: InputStream?

    val converter = JDI2LSPConverter(this)

    val pendingStepRequestThreadIds = mutableSetOf<Long>()

    init {

        val process = vm.process()
        stdin = process?.outputStream
        stdout = process?.inputStream
        stderr = process?.errorStream

        updateThreads()
    }

    fun updateThreads() {
        logger.debug { "Updating thread info" }
        threads = vm.allThreads().associateBy({ it.uniqueID() }, { JDIThread(it, this) })
    }


    fun threadById(id: Long) = threads[id]

    fun resume() {
        logger.info { "Resuming JDI session" }
        vm.resume()
    }

    fun exit() {
        logger.info { "Exiting JDI session" }
        try {
            if (vm.process()?.isAlive == true) {
                vm.exit(0)
            }
            vm.dispose()
        } catch (e: VMDisconnectedException) {
            // Ignore since we wanted to stop the VM anyway
        }
    }


    fun sourceOf(location: Location): Source? =
        try {
            val sourcePath = location.sourcePath()
            val sourceName = location.sourceName()
            val file = scriptsPath.resolve(sourcePath)
                .resolveSibling(sourceName)

            if (Files.exists(file)) {
                Source().apply {
                    name = sourceName ?: file.fileName.toString()
                    path = file.toAbsolutePath().toString()
                }
            } else null
        } catch (exception: AbsentInformationException) {
            null
        }

    fun variables(variablesReference: Int) = VariablesResponse().apply {
        variables = converter.getVariableTree(variablesReference)
            ?.children
            ?.map {
                converter.convertVariables(it)
            }?.toTypedArray() ?: emptyArray()
    }



}
