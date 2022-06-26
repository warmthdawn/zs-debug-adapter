package com.warmthdawm.zsdebugadapter.jdi

import com.sun.jdi.Bootstrap
import com.sun.jdi.VirtualMachine
import com.sun.jdi.connect.AttachingConnector
import com.sun.jdi.connect.TransportTimeoutException
import com.warmthdawm.zsdebugadapter.util.ZDAException
import java.io.IOException
import java.nio.file.Path

object DebugLauncher {

    private val vmManager = Bootstrap.virtualMachineManager()

    private fun attachingConnector() = vmManager.attachingConnectors().firstOrNull {
        "dt_socket" == it.transport().name()
    }

    fun attachVM(hostname: String, port: Int, timeout: Int): VirtualMachine {
        val connector: AttachingConnector = attachingConnector()
            ?: throw ZDAException("Socket attaching connector not found!")
        val arguments = connector.defaultArguments().also {
            it["hostname"]!!.setValue(hostname)
            it["port"]!!.setValue(port.toString())
            it["timeout"]!!.setValue(timeout.toString())
        }

        return try {
            connector.attach(arguments)
        } catch (e: Exception) {
            throw ZDAException("Failed to attach to VM", e)
        } ?: throw ZDAException("Could not attach to VM")
    }


    fun scriptsPathOf(projectRoot: Path, scriptsPath: String?): Path = if (projectRoot.endsWith("scripts")) {
        projectRoot.toAbsolutePath()
    } else if (scriptsPath != null) {
        projectRoot.resolve(scriptsPath)
    } else {
        projectRoot.resolve("scripts")
    }


}