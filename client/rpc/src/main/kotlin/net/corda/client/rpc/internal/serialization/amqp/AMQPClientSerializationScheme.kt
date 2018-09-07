package net.corda.client.rpc.internal.serialization.amqp

import net.corda.core.cordapp.Cordapp
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.AMQPSerializationContext
import net.corda.core.serialization.AMQPSerializationContext.*
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.internal.*
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.AccessOrderLinkedHashMap
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.RxNotificationSerializer

/**
 * When set as the serialization scheme for a process, sets it to be the Corda AMQP implementation.
 * This scheme is for use by the RPC Client calls.
 */
class AMQPClientSerializationScheme(
            cordappCustomSerializers: Set<SerializationCustomSerializer<*,*>>,
            serializerFactoriesForContexts: AccessOrderLinkedHashMap<Pair<ClassWhitelist, ClassLoader>, SerializerFactory>
    ) : AbstractAMQPSerializationScheme(cordappCustomSerializers, serializerFactoriesForContexts) {
    constructor(cordapps: List<Cordapp>) : this(cordapps.customSerializers, AccessOrderLinkedHashMap { 128 })

    @Suppress("UNUSED")
    constructor() : this(emptySet(), AccessOrderLinkedHashMap { 128 })

    companion object {
        /** Call from main only. */
        fun initialiseSerialization(classLoader: ClassLoader? = null) {
            nodeAMQPSerializationEnv = createSerializationEnv(classLoader)
        }

        fun createSerializationEnv(classLoader: ClassLoader? = null): AMQPSerializationEnvironment {
            return AMQPSerializationEnvironmentImpl(
                    AMQPSerializationFactoryImpl().apply {
                        registerScheme(AMQPClientSerializationScheme(emptyList()))
                    },
                    _storageContext = AMQP_STORAGE_CONTEXT,
                    p2pContext = if (classLoader != null) AMQP_P2P_CONTEXT.withClassLoader(classLoader) else AMQP_P2P_CONTEXT,
                    _rpcClientContext = AMQP_RPC_CLIENT_CONTEXT,
                    _rpcServerContext = AMQP_RPC_SERVER_CONTEXT
            )
        }
    }

    override fun canDeserializeVersion(target: AMQPSerializationContext.UseCase): Boolean {
        return target == UseCase.RPCClient || target == UseCase.P2P
    }

    override fun rpcClientSerializerFactory(context: AMQPSerializationContext): SerializerFactory {
        return SerializerFactory(context.whitelist, context.deserializationClassLoader, context.lenientCarpenterEnabled).apply {
            register(RpcClientObservableDeSerializer)
            register(RpcClientCordaFutureSerializer(this))
            register(RxNotificationSerializer(this))
        }
    }

    override fun rpcServerSerializerFactory(context: AMQPSerializationContext): SerializerFactory {
        throw UnsupportedOperationException()
    }
}
