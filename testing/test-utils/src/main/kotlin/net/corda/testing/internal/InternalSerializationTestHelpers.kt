package net.corda.testing.internal

import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.whenever
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.core.DoNotImplement
import net.corda.core.serialization.internal.*
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.node.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.node.serialization.kryo.KryoServerSerializationScheme
import net.corda.serialization.internal.*
import net.corda.testing.core.SerializationEnvironmentRule
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

val inVMExecutors = ConcurrentHashMap<Any, ExecutorService>()

/**
 * For example your test class uses [SerializationEnvironmentRule] but you want to turn it off for one method.
 * Use sparingly, ideally a test class shouldn't mix serializers init mechanisms.
 */
fun <T> withoutTestSerialization(callable: () -> T): T { // TODO: Delete this, see CORDA-858.
    val (property, env) = listOf(_contextSerializationEnv, _inheritableContextSerializationEnv).map { Pair(it, it.get()) }.single { it.second != null }
    property.set(null)
    try {
        return callable()
    } finally {
        property.set(env)
    }
}

internal fun createTestKryoSerializationEnv(label: String): CheckpointSerializationEnvironmentImpl {
    val factory = CheckpointSerializationFactory(KryoServerSerializationScheme())
    return object : CheckpointSerializationEnvironmentImpl(
            factory,
            KRYO_CHECKPOINT_CONTEXT
    ) {
        override fun toString() = "testSerializationEnv($label)"
    }
}

internal fun createTestAMQPSerializationEnv(label: String): AMQPSerializationEnvironmentImpl {
    val factory = AMQPSerializationFactoryImpl().apply {
        registerScheme(AMQPClientSerializationScheme(emptyList()))
        registerScheme(AMQPServerSerializationScheme(emptyList()))
    }
    return AMQPSerializationEnvironmentImpl(
            factory,
            AMQP_P2P_CONTEXT,
            AMQP_RPC_SERVER_CONTEXT,
            AMQP_RPC_CLIENT_CONTEXT,
            AMQP_STORAGE_CONTEXT
    )
}

/**
 * Should only be used by Driver and MockNode.
 * @param armed true to install, false to do nothing and return a dummy env.
 */
fun setGlobalCheckpointSerialization(armed: Boolean): GlobalCheckpointSerializationEnvironment {
    return if (armed) {
        object : GlobalCheckpointSerializationEnvironment,
                CheckpointSerializationEnvironment by createTestKryoSerializationEnv("<global>") {
            override fun unset() {
                _globalSerializationEnv.set(null)
                inVMExecutors.remove(this)
            }
        }.also {
            _globalSerializationEnv.set(it)
        }
    } else {
        rigorousMock<GlobalCheckpointSerializationEnvironment>().also {
            doNothing().whenever(it).unset()
        }
    }
}

/**
 * Should only be used by Driver and MockNode.
 * @param armed true to install, false to do nothing and return a dummy env.
 */
fun setGlobalAMQPSerialization(armed: Boolean): GlobalAMQPSerializationEnvironment {
    return if (armed) {
        object : GlobalAMQPSerializationEnvironment,
                AMQPSerializationEnvironment by createTestAMQPSerializationEnv("<global>") {
            override fun unset() {
                _globalAMQPSerializationEnv.set(null)
                inVMExecutors.remove(this)
            }
        }.also {
            _globalAMQPSerializationEnv.set(it)
        }
    } else {
        rigorousMock<GlobalAMQPSerializationEnvironment>().also {
            doNothing().whenever(it).unset()
        }
    }
}

@DoNotImplement
interface GlobalCheckpointSerializationEnvironment : CheckpointSerializationEnvironment {
    /** Unset this environment. */
    fun unset()
}

@DoNotImplement
interface GlobalAMQPSerializationEnvironment : AMQPSerializationEnvironment {
    /** Unset this environment. */
    fun unset()
}

