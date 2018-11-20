package net.corda.node.reactive

interface Publisher<ELEMENT, PUBLISHER : Publisher<ELEMENT, PUBLISHER>> {

    fun addSubscriber(subscriber: Subscriber<ELEMENT>)

    operator fun plusAssign(subscriber: Subscriber<ELEMENT>) {
        addSubscriber(subscriber)
    }

    @Suppress("UNCHECKED_CAST")
    operator fun plus(subscriber: Subscriber<ELEMENT>): PUBLISHER {
        addSubscriber(subscriber)
        return (this as PUBLISHER)
    }
}