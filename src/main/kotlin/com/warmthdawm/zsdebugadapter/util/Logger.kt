package com.warmthdawm.zsdebugadapter.util;

import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient

interface Logger {
    fun info(msg: () -> Any)
    fun warn(msg: () -> Any)
    fun error(msg: () -> Any)
    fun debug(msg: () -> Any)
}

enum class LogLevel(val value: Int) {
    ERROR(2),
    WARN(1),
    INFO(0),
    DEBUG(-1),
}

class DAPLoggerImpl(private val logLevel: LogLevel) : Logger {
    var client: IDebugProtocolClient? = null
    override fun info(msg: () -> Any) {
        if (logLevel.value <= LogLevel.INFO.value) {
            client?.output(OutputEventArguments().apply {
                category = OutputEventArgumentsCategory.CONSOLE
                output = "[INFO] ${msg()} ${System.lineSeparator()}"
            })
        }
    }

    override fun warn(msg: () -> Any) {
        if (logLevel.value <= LogLevel.WARN.value) {
            client?.output(OutputEventArguments().apply {
                category = OutputEventArgumentsCategory.CONSOLE
                output = "[WARN] ${msg()} ${System.lineSeparator()}"
            })
        }
    }

    override fun error(msg: () -> Any) {
        if (logLevel.value <= LogLevel.ERROR.value) {
            client?.output(OutputEventArguments().apply {
                category = OutputEventArgumentsCategory.CONSOLE
                output = "[ERROR] ${msg()} ${System.lineSeparator()}"
            })
        }
    }

    override fun debug(msg: () -> Any) {
        if (logLevel.value <= LogLevel.DEBUG.value) {
            client?.output(OutputEventArguments().apply {
                category = OutputEventArgumentsCategory.CONSOLE
                output = "[DEBUG] ${msg()} ${System.lineSeparator()}"
            })
        }
    }
}

class StdoutLoggerImpl(private val level: LogLevel) : Logger {

    override fun info(msg: () -> Any) {
        if (level.value <= LogLevel.INFO.value) {
            println("[INFO] ${msg()}")
        }
    }

    override fun warn(msg: () -> Any) {
        if (level.value <= LogLevel.WARN.value) {
            println("[WARN] ${msg()}")
        }
    }

    override fun error(msg: () -> Any) {
        if (level.value <= LogLevel.ERROR.value) {
            println("[ERROR] ${msg()}")
        }
    }

    override fun debug(msg: () -> Any) {
        if (level.value <= LogLevel.DEBUG.value) {
            println("[DEBUG] ${msg()}")
        }
    }

}
