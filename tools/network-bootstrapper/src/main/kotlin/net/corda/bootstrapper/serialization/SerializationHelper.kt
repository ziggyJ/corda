package net.corda.bootstrapper.serialization

import net.corda.core.serialization.internal.AMQPSerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeAMQPSerializationEnv
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.serialization.internal.AMQPSerializationFactoryImpl
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_STORAGE_CONTEXT

class SerializationEngine {
    companion object {
        fun init() {
            synchronized(this) {
                val classloader = this::class.java.classLoader
                if (nodeAMQPSerializationEnv == null) {
                    nodeAMQPSerializationEnv = AMQPSerializationEnvironmentImpl(
                            AMQPSerializationFactoryImpl().apply {
                                registerScheme(AMQPServerSerializationScheme(emptyList()))
                            },
                            p2pContext = AMQP_P2P_CONTEXT.withClassLoader(classloader),
                            _rpcServerContext = AMQP_P2P_CONTEXT.withClassLoader(classloader),
                            _storageContext = AMQP_STORAGE_CONTEXT.withClassLoader(classloader)
                    )
                }
            }
        }
    }
}
