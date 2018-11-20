package net.corda.node.reactive

interface Subscription {

    fun request(offset: Int, count: Int)

    fun cancel()
}