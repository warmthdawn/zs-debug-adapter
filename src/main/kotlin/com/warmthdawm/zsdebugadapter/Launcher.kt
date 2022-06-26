package com.warmthdawm.zsdebugadapter

import com.warmthdawm.zsdebugadapter.adapter.ZenScriptDebugAdapter
import com.warmthdawm.zsdebugadapter.util.DAPLoggerImpl
import com.warmthdawm.zsdebugadapter.util.LogLevel
import com.warmthdawm.zsdebugadapter.util.StdoutLoggerImpl
import com.warmthdawm.zsdebugadapter.util.ZDAException
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.lang.reflect.InvocationTargetException
import java.net.ServerSocket
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import java.util.logging.Level


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

    val serverLauncher = DebugLauncher.Builder<IDebugProtocolClient>()
        .setLocalService(debugAdapter)
        .setRemoteInterface(IDebugProtocolClient::class.java)
        .setInput(input)
        .setOutput(output)
        .setExecutorService(threads)
        .setExceptionHandler(::handleException)
        .create()


    debugAdapter.connect(serverLauncher.remoteProxy)
    serverLauncher.startListening()
}

fun handleException(throwable: Throwable): ResponseError? {
    if(throwable is CompletionException || throwable is InvocationTargetException && throwable.cause != null) {
        return handleException(throwable.cause!!)
    }
    return when {
        throwable is ZDAException -> ResponseError().apply {
            code = 1
            message = throwable.message
        }

        throwable is ResponseErrorException -> {
            throwable.responseError
        }
        else -> {
            fallbackResponseError("Internal error", throwable)
        }
    }
}

private fun fallbackResponseError(header: String, throwable: Throwable): ResponseError? {
    logger.error { "$header : ${throwable.message}" }
    val error = ResponseError()
    error.message = "$header."
    error.setCode(ResponseErrorCode.InternalError)
    val stackTrace = ByteArrayOutputStream()
    val stackTraceWriter = PrintWriter(stackTrace)
    throwable.printStackTrace(stackTraceWriter)
    stackTraceWriter.flush()
    error.data = stackTrace.toString()
    return error
}
