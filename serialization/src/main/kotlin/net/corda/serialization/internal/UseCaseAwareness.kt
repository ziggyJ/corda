@file:KeepForDJVM
package net.corda.serialization.internal

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.AMQPSerializationContext
import net.corda.core.serialization.AMQPSerializationFactory
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory
import java.util.*

fun amqpCheckUseCase(allowedUseCases: EnumSet<AMQPSerializationContext.UseCase>) {
    val currentContext: AMQPSerializationContext = AMQPSerializationFactory.currentFactory?.currentContext
            ?: throw IllegalStateException("Current context is not set")
    if (!allowedUseCases.contains(currentContext.useCase)) {
        throw IllegalStateException("UseCase '${currentContext.useCase}' is not within '$allowedUseCases'")
    }
}

fun checkUseCase(allowedUseCases: EnumSet<SerializationContext.UseCase>) {
    val currentContext: SerializationContext = SerializationFactory.currentFactory?.currentContext
            ?: throw IllegalStateException("Current context is not set")
    if (!allowedUseCases.contains(currentContext.useCase)) {
        throw IllegalStateException("UseCase '${currentContext.useCase}' is not within '$allowedUseCases'")
    }
}
