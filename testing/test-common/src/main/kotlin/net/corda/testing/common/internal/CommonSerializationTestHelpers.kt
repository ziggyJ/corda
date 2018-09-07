package net.corda.testing.common.internal

import net.corda.core.serialization.internal.*

fun <T> AMQPSerializationEnvironment.asContextEnv(inheritable: Boolean = false, callable: (AMQPSerializationEnvironment) -> T): T {
    val property = if (inheritable) _inheritableContextAMQPSerializationEnv else _contextAMQPSerializationEnv
    property.set(this)
    try {
        return callable(this)
    } finally {
        property.set(null)
    }
}

// TODO: eliminate this if it's no longer needed
fun <T> CheckpointSerializationEnvironment.asContextEnv(inheritable: Boolean = false, callable: (CheckpointSerializationEnvironment) -> T): T {
    val property = if (inheritable) _inheritableContextSerializationEnv else _contextSerializationEnv
    property.set(this)
    try {
        return callable(this)
    } finally {
        property.set(null)
    }
}
