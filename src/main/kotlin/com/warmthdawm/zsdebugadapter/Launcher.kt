package com.warmthdawm.zsdebugadapter

import com.warmthdawm.zsdebugadapter.adapter.ZenScriptDebugAdapter
import com.warmthdawm.zsdebugadapter.util.DAPLoggerImpl
import com.warmthdawm.zsdebugadapter.util.LogLevel
import com.warmthdawm.zsdebugadapter.util.StdoutLoggerImpl
import org.eclipse.lsp4j.debug.launch.DSPLauncher
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors


fun standardIOLaunch() {
    logger = DAPLoggerImpl(LogLevel.INFO)
    launch(System.`in`, System.out)
}

fun socketLaunch(port: Int = 9866) {
    logger = StdoutLoggerImpl(LogLevel.INFO)

    val socket = ServerSocket(port)
    logger.info { "Listening on port $port" }

    while (true) {
        val client = socket.accept()
        logger.info { "Accepted connection from ${client.inetAddress.hostAddress}" }
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            val input = client.getInputStream()
            val output = client.getOutputStream()
            launch(input, output)
        }
    }
}


private fun launch(input: InputStream, output: OutputStream) {

    val debugAdapter = ZenScriptDebugAdapter()
    val threads = Executors.newSingleThreadExecutor { Thread(it, "server") }
    val serverLauncher = DSPLauncher.createServerLauncher(debugAdapter, input, output, threads) { it }


    debugAdapter.connect(serverLauncher.remoteProxy)
    serverLauncher.startListening()
}