package net.corda.zz_progress_showcase.attempt2.domain

import net.corda.core.flows.StateMachineRunId

interface EnqueuedFlowExecutionHandle<RESULT> : EnqueuedHandle<StateMachineRunId, StartedFlowExecutionHandle<RESULT>, FlowEvent<RESULT>>