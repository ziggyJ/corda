package net.corda.zz_progress_showcase.attempt1

import net.corda.core.concurrent.CordaFuture
import net.corda.core.utilities.getOrThrow
import rx.Observable
import rx.Subscriber
import rx.schedulers.Schedulers
import java.time.Instant

@Suppress("UNREACHABLE_CODE")
fun main(args: Array<String>) {

    val ops: Ops = TODO("not relevant")

    val execution = ops.submitFlow().getOrThrow()
    println("Submitted flow execution with id ${execution.id}.")

    execution.progress.phases.observeOn(Schedulers.io()).subscribe(FlowProgressPrinter())

    val result = execution.result.getOrThrow()
    when (result) {
        is Flow.Execution.Result.Successful -> println("Flow execution with id ${execution.id} was successful. Result was: ${result.value}.")
        is Flow.Execution.Result.Unsuccessful -> println("Flow execution with id ${execution.id} was unsuccessful. Cause was: ${result.cause}.")
        else -> throw IllegalStateException("Unknown flow execution result type: ${result::class.qualifiedName}.")
    }
}

class FlowProgressPrinter(private val parent: Flow.Execution.Phase? = null) : Subscriber<Flow.Execution.Phase>() {

    override fun onNext(currentPhase: Flow.Execution.Phase) {

        // TODO turn parent.name into a parent.path and join to string
        println("Current flow execution phase: \"${currentPhase.name}\".")
        currentPhase.children?.observeOn(Schedulers.io())?.subscribe(FlowProgressPrinter(currentPhase))
    }

    override fun onError(e: Throwable) {

        // just an example
        throw e
    }

    override fun onCompleted() {

        when {
        // TODO turn parent.name into a parent.path and join to string
            parent != null -> println("Flow execution phase \"${parent.name}\" completed.")
            else -> println("Flow execution completed.")
        }
    }
}

// this doesn't matter, just a stub
interface Ops {

    // String here is an example
    fun submitFlow(): CordaFuture<Flow.Execution<String>>
}

interface Flow {

    interface Execution<RESULT> {

        // for the sake of the example, but should be typed
        val id: String

        // TODO what's the reason for CordaFuture again (vs CompletableFuture, etc.)?
        val result: CordaFuture<Result<RESULT>>

        val progress: Progress

        // this doesn't matter, just a stub
        // TODO consider again whether to use an interface or an enum / sealed class. Hate Kotlin...
        interface Result<RESULT> {

            interface Successful<RESULT> : Result<RESULT> {

                val value: RESULT
            }

            interface Unsuccessful<RESULT> : Result<RESULT> {

                // could do better than String here, just an example
                val cause: String
            }
        }

        interface Progress {

            // TODO does it make sense as an Observable? Could a CordaFuture<List<Phase>> do instead?
            // TODO could this be optional?
            val phases: Observable<Phase>
        }

        interface Phase {

            // TODO should be typed
            // TODO do we need it in the interface?
            val id: String

            // TODO can it be a String?
            val name: String

            // TODO "children" sucks, find better
            // TODO does it make sense as an Observable? Could a CordaFuture<List<Phase>> do instead?
            // TODO should this be optional?
            val children: Observable<Phase>?

            // TODO do we need both "parent" and "children"? is it useful?
            val parent: Phase?

            // TODO what's the reason for CordaFuture again (vs CompletableFuture, etc.)?
            // TODO can we reuse Execution here?
            val outcome: CordaFuture<Outcome>

            // TODO consider again whether to use an interface or an enum / sealed class. Hate Kotlin...
            interface Outcome {

                val completedAt: Instant

                interface Successful : Outcome

                interface Unsuccessful : Outcome {

                    // TODO change this to strongly typed?
                    val cause: String
                }

                // TODO we might do without this (if we assume that we can figure out following steps independently from the result of sub-flows)
                interface Skipped : Outcome {

                    // TODO change this to strongly typed?
                    val cause: String?
                }

                interface Aborted : Outcome {

                    // TODO change this to strongly typed?
                    // TODO make "cause" a Phase (with Unsuccessful Outcome)?
                    val cause: String?
                }
            }
        }
    }
}