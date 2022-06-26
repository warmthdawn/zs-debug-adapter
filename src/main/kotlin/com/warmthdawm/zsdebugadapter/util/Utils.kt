package com.warmthdawm.zsdebugadapter.util

import com.sun.jdi.ThreadReference
import com.warmthdawm.zsdebugadapter.logger


fun waitFor(what: String, condition: () -> Boolean) {
    val delayUntilNotificationMs = 10_000
    val startTime = System.currentTimeMillis()
    var lastTime = startTime

    while (!condition()) {
        Thread.sleep(80)
        val now = System.currentTimeMillis()
        if ((now - lastTime) > delayUntilNotificationMs) {
            logger.info { "Waiting for $what" }
            lastTime = now
        }
    }
}


inline fun <R> catching(failReason: String, block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: Exception) {
        logger.error { "$failReason: ${e.message}" }
        Result.failure(e)
    }
}


fun isStackInZenScript(thread: ThreadReference) = runCatching {
    thread.frame(0)
        .location()
        ?.sourceName()
        ?.endsWith(".zs") == true
}.getOrElse { false }