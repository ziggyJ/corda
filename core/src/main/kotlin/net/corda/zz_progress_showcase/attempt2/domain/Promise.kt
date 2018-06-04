package net.corda.zz_progress_showcase.attempt2.domain

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

interface Promise<RESULT> : Future<RESULT>, CompletionStage<RESULT>

class PromiseAdapter<RESULT>(private val future: CompletableFuture<RESULT>) : Future<RESULT> by future, CompletionStage<RESULT> by future, Promise<RESULT>