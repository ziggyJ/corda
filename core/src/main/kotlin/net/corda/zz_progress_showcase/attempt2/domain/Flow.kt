package net.corda.zz_progress_showcase.attempt2.domain

import rx.Observable

interface Event : Timestamped {

    val id: String
}

interface CompletedEvent<RESULT> : Event {

    val result: RESULT
}

interface FailedEvent : Event {

    // This is to stay in line with the current API
    val cause: Throwable
}

interface CancelledEvent

interface StartedEvent

interface FlowEvent<RESULT> : Event {

    interface FlowStarted<RESULT> : FlowEvent<RESULT>, StartedEvent

    interface FlowCompleted<RESULT> : FlowEvent<RESULT>, CompletedEvent<RESULT>

    interface FlowFailed<RESULT> : FlowEvent<RESULT>, FailedEvent

    interface FlowCancelled<RESULT> : FlowEvent<RESULT>, CancelledEvent

    interface PhaseStarted<RESULT> : FlowEvent<RESULT>, StartedEvent {

        val phase: Flow.Phase
    }

    interface PhaseSkipped<RESULT> : FlowEvent<RESULT>

    interface PhaseCancelled<RESULT> : FlowEvent<RESULT>, CancelledEvent
}

interface Flow {

//        val events: Observable<Event<RESULT>>
//
//        val result: CompletionStage<RESULT>
//            get() {
//                val future = CompletableFuture<RESULT>()
//                events.observeOn(Schedulers.io()).filter { it is Event.Terminated }.subscribe({ event ->
//                    when (event) {
//                        is Event.Terminated.Successfully -> future.complete(event.result)
//                        is Event.Terminated.Unsuccessfully -> future.completeExceptionally(event.cause)
//                        is Event.Terminated.Cancelled -> future.cancel(false)
//                    }
//                }, { error ->
//                    future.completeExceptionally(error)
//                })
//                return future
//            }

    interface Phase {
        // TODO do we need the id in the API?
        val id: String
        val name: String
        // TODO do we need the id in the API?
        val parentId: String
        val events: Observable<FlowEvent<Unit>>

        val completed: Promise<Unit>
    }
}