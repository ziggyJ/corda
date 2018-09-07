@file:JvmName("AMQPSerializerFactories")

package net.corda.serialization.internal.amqp

import net.corda.core.serialization.AMQPSerializationContext

fun createSerializerFactoryFactory(): SerializerFactoryFactory = SerializerFactoryFactoryImpl()

open class SerializerFactoryFactoryImpl : SerializerFactoryFactory {
    override fun make(context: AMQPSerializationContext): SerializerFactory {
        return SerializerFactory(context.whitelist, context.deserializationClassLoader, context.lenientCarpenterEnabled)
    }
}
