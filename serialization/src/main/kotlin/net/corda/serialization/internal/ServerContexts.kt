@file:JvmName("ServerContexts")
@file:DeleteForDJVM
package net.corda.serialization.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.serialization.AMQPSerializationContext
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.serialization.internal.amqp.amqpMagic

/*
 * Serialisation contexts for the server.
 * These have been refactored into a separate file to prevent
 * clients from trying to instantiate any of them.
 *
 * NOTE: The [KRYO_STORAGE_CONTEXT] and [AMQP_STORAGE_CONTEXT]
 * CANNOT always be instantiated outside of the server and so
 * MUST be kept separate!
 */


val AMQP_STORAGE_CONTEXT = AMQPSerializationContextImpl(
        SerializationDefaults.javaClass.classLoader,
        AllButBlacklisted,
        emptyMap(),
        true,
        AMQPSerializationContext.UseCase.Storage,
        null,
        AlwaysAcceptEncodingWhitelist
)

val AMQP_RPC_SERVER_CONTEXT = AMQPSerializationContextImpl(
        SerializationDefaults.javaClass.classLoader,
        GlobalTransientClassWhiteList(BuiltInExceptionsWhitelist()),
        emptyMap(),
        true,
        AMQPSerializationContext.UseCase.RPCServer,
        null
)
