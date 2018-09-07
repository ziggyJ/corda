package net.corda.serialization.internal.amqp.testutils

import net.corda.core.serialization.AMQPSerializationContext
import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.AMQPSerializationContextImpl
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.amqp.amqpMagic

val serializationProperties: MutableMap<Any, Any> = mutableMapOf()

val testSerializationContext = AMQPSerializationContextImpl(
        deserializationClassLoader = ClassLoader.getSystemClassLoader(),
        whitelist = AllWhitelist,
        properties = serializationProperties,
        objectReferencesEnabled = false,
        useCase = AMQPSerializationContext.UseCase.Testing,
        encoding = null)