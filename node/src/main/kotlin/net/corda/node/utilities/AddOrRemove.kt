package net.corda.node.utilities

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
sealed class Change<out T> {
    abstract val item: T
    data class Add<out T>(override val item: T) : Change<T>()
    data class Remove<out T>(override val item: T) : Change<T>()
}
