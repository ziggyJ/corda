package net.corda.zz_progress_showcase.attempt2.domain

import rx.Observable

interface TrackableHandle<EVENT> : Timestamped {

    val events: Observable<EVENT>
}