package com.warmthdawm.zsdebugadapter.util

//import mu.KotlinLogging
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

private var threadCount = 0

class AsyncExecutor {
//    private val log = KotlinLogging.logger { }
    private val workerThread = Executors.newSingleThreadExecutor { Thread(it, "async${threadCount++}") }

    fun run(task: () -> Unit): CompletableFuture<Void> =
        CompletableFuture.runAsync(Runnable(task), workerThread)

    fun <R> supply(task: () -> R): CompletableFuture<R> =
        CompletableFuture.supplyAsync(Supplier(task), workerThread)

    fun <R> supplyOr(defaultValue: R, task: () -> R?): CompletableFuture<R> =
        CompletableFuture.supplyAsync({
            try {
                task() ?: defaultValue
            } catch (e: Exception) {
                defaultValue
            }
        }, workerThread)

    fun shutdown(awaitTermination: Boolean) {
        workerThread.shutdown()
        if (awaitTermination) {
//            log.info("Awaiting async termination...")
            workerThread.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
        }
    }
}