package net.corda.node.internal.serialization.testutils

import net.corda.core.serialization.AMQPSerializationContext
import net.corda.serialization.internal.AMQPSerializationContextImpl
import net.corda.serialization.internal.AllWhitelist

val serializationProperties: MutableMap<Any, Any> = mutableMapOf()

val serializationContext = AMQPSerializationContextImpl(
        deserializationClassLoader = ClassLoader.getSystemClassLoader(),
        whitelist = AllWhitelist,
        properties = serializationProperties,
        objectReferencesEnabled = false,
        useCase = AMQPSerializationContext.UseCase.Testing,
        encoding = null
)
