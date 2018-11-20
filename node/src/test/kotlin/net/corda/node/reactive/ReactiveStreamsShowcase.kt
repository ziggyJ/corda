package net.corda.node.reactive

import org.junit.Test
import java.time.Instant
import java.util.*

class ReactiveStreamsShowcase {

    // TODO sollecitom find a push-looking alternative to Publisher & Subscriber (e.g., something that looks like a Flux or an Observable)
    @Test
    fun showcase() {

        val publisher = InMemoryAppendOnlyEventLogPublisher<EventStub>()
        (0 until 50).forEach { index ->
            publisher += EventStub("Event number $index")
        }

        val subscriber = FunctionalSubscriber<EventStub>(
                initialClientOffset = 35,
                persistClientOffset = { offset -> println("Persisted client offset $offset.") },
                shouldDiscardEvent = { event ->
                    val discard = event.message == "Event number 40"
                    if (discard) {
                        println("[WARN]: Discarded $event.")
                    }
                    discard
                },
                onError = { error -> throw error },
                onNoNext = {
                    // TODO sollecitom here you would typically schedule the next request e.g, `scheduler.submit({ subscriber.request(sameClientOffset, 1) }, 5, TimeUnit.SECOND)`
                    println("Nothing more to process, exiting.")
                },
                onComplete = { println("Completed!") },
                process = { event -> println("Got event $event.") }
        )

        publisher += subscriber
    }
}

private class FunctionalSubscriber<EVENT>(initialClientOffset: Int = 0, private val persistClientOffset: (Int) -> Unit = {}, private val shouldDiscardEvent: (EVENT) -> Boolean = { false }, private val onError: (Throwable) -> Unit = { error -> throw error }, private val onComplete: () -> Unit, private val onNoNext: (FunctionalSubscriber<EVENT>) -> Unit, private val process: (EVENT) -> Unit) : Subscriber<EVENT>, AutoCloseable {

    private var clientOffset = initialClientOffset
    private var subscription: Subscription? = null

    override fun onSubscribe(subscription: Subscription) {
        this.subscription = subscription
        subscription.request(clientOffset, 1)
    }

    override fun onNext(element: EVENT) {
        // TODO sollecitom here there should be 2 phases. 1) Validate event, and if incorrect, increment the offset and put this into an inspection queue. 2) If the event is valid, try catch the consumption along with increasing the offset; if it fails, do not increase the offset.
        if (shouldDiscardEvent(element)) {
            advance()
            return
        }
        try {
            process.invoke(element)
            // Successful!
            advance()
        } catch (e: Exception) {
            // Try again
            subscription!!.request(clientOffset, 1)
        }
    }

    private fun advance() {
        persistClientOffset.invoke(clientOffset)
        clientOffset++
        subscription!!.request(clientOffset, 1)
    }

    override fun onError(error: Throwable) {
        onError.invoke(error)
    }

    override fun onComplete() {
        // TODO sollecitom this should never happen.
        onComplete.invoke()
        close()
    }

    override fun onNoNext() {
        onNoNext.invoke(this)
    }

    override fun close() {
        subscription!!.cancel()
        subscription = null
    }
}

private class InMemoryAppendOnlyEventLogPublisher<EVENT> : AppendOnlyEventLogPublisher<EVENT> {

    private val subscriptions = mutableSetOf<Subscription>()

    private val eventLog = mutableListOf<EVENT>()

    override fun append(event: EVENT) {
        eventLog += event
    }

    override fun addSubscriber(subscriber: Subscriber<EVENT>) {

        val subscription = SubscriptionImpl(subscriber, ::slice, { s -> subscriptions.remove(s) })
        subscriptions += subscription
        subscriber.onSubscribe(subscription)
    }

    private fun slice(offset: Int, count: Int): List<EVENT> {

        if (offset < 0) {
            throw IllegalArgumentException("Offset cannot be less than 0.")
        }
        if (count < 1) {
            throw IllegalArgumentException("Count cannot be less than 1.")
        }
        val size = eventLog.size
        if (size == 0 || offset >= size) {
            return emptyList()
        }
        var end = offset + count
        if (end >= size) {
            end = size
        }
        return eventLog.slice(offset until end)
    }

    private class SubscriptionImpl<EVENT>(private val subscriber: Subscriber<EVENT>, private val requestEvents: (Int, Int) -> List<EVENT>, private val cancel: (SubscriptionImpl<EVENT>) -> Unit, private val id: String = UUID.randomUUID().toString()) : Subscription {

        override fun request(offset: Int, count: Int) {
            try {
                val events = requestEvents.invoke(offset, count)
                if (events.isNotEmpty()) {
                    events.forEach(subscriber::onNext)
                } else {
                    subscriber.onNoNext()
                }
            } catch (e: Exception) {
                // TODO sollecitom this is obviously grossly simplified for is PoC
                subscriber.onError(e)
            }
        }

        override fun cancel() {
            cancel.invoke(this)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SubscriptionImpl<*>

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }
}

private interface AppendOnlyEventLogPublisher<EVENT> : Publisher<EVENT, AppendOnlyEventLogPublisher<EVENT>> {

    fun append(event: EVENT)

    operator fun plusAssign(event: EVENT) {
        append(event)
    }
}

private class EventStub(val message: String, val createdAt: Instant = Instant.now(), val id: String = UUID.randomUUID().toString()) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EventStub

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "EventStub(message='$message', createdAt=$createdAt, id='$id')"
    }
}