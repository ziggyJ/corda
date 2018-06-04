package net.corda.zz_progress_showcase.attempt2.domain

import rx.Observable

// TODO not sure if we need this
interface StartedFlowExecutionHandle<RESULT> : StartedHandle<RESULT, FlowEvent<RESULT>> {

    val phases: Observable<Flow.Phase>
}