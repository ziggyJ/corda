package net.corda.zz_progress_showcase.attempt2.domain

interface StartedHandle<RESULT, EVENT> : TrackableHandle<EVENT> {

    val result: Promise<RESULT>
}