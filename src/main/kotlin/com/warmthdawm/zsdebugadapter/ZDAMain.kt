package com.warmthdawm.zsdebugadapter

import com.warmthdawm.zsdebugadapter.util.Logger

lateinit var logger: Logger

fun main(args: Array<String>) {
    if ("--standard-io" in args) {
        standardIOLaunch()
    } else {
        val port = args.firstOrNull { it.startsWith("--port=") }?.substringAfter("--port=")?.toInt() ?: 9866
        socketLaunch(port)
    }
}
