package net.corda.zz_progress_showcase.attempt2

import net.corda.core.utilities.getOrThrow
import net.corda.zz_progress_showcase.attempt2.domain.EnqueuedFlowExecutionHandle
import net.corda.zz_progress_showcase.attempt2.domain.FlowEvent
import net.corda.zz_progress_showcase.attempt2.domain.Promise
import rx.schedulers.Schedulers
import java.util.concurrent.CountDownLatch

@Suppress("UNREACHABLE_CODE")
fun main(args: Array<String>) {

    val ops: Ops = TODO("not relevant")

    val enqueued = ops.submitFlow().getOrThrow()
    println("Flow execution with id ${enqueued.id} was enqueued at ${enqueued.localTimestamp()}")

    val started = enqueued.started.getOrThrow()
    println("Flow execution with id ${enqueued.id} started executing at ${started.localTimestamp()}")

    val latch = CountDownLatch(1)
    started.events.observeOn(Schedulers.io()).subscribe({ event -> handleEvent(event, started.result) }, { error -> throw error }, latch::countDown)
    latch.await()
}

private fun <RESULT> handleEvent(event: FlowEvent<RESULT>, result: Promise<RESULT>) {

    when (event) {
    // TODO figure out how to "attach" this phase to the parent
        is FlowEvent.PhaseStarted<*> -> println("Started phase \"${event.phase.name}\".")
        is FlowEvent.FlowCompleted<RESULT> -> println("Flow completed with result: \"${result.get()}\".")
        is FlowEvent.FlowFailed -> {
            println("Flow failed, reason was")
            event.cause.printStackTrace()
        }
    }
}

// this doesn't matter, just a stub
interface Ops {

    // String here is an example
    fun submitFlow(): Promise<EnqueuedFlowExecutionHandle<String>>
}