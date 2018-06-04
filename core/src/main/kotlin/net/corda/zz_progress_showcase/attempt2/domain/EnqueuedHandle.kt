package net.corda.zz_progress_showcase.attempt2.domain

interface EnqueuedHandle<ID, STARTED_HANDLE, EVENT> : TrackableHandle<EVENT> {

    val id: ID
    val started: Promise<STARTED_HANDLE>
}