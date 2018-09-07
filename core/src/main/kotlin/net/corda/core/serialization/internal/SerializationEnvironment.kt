@file:KeepForDJVM
package net.corda.core.serialization.internal

import net.corda.core.KeepForDJVM
import net.corda.core.internal.InheritableThreadLocalToggleField
import net.corda.core.internal.SimpleToggleField
import net.corda.core.internal.ThreadLocalToggleField
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.AMQPSerializationContext
import net.corda.core.serialization.AMQPSerializationFactory
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory

@KeepForDJVM
interface SerializationEnvironment {
    val serializationFactory: SerializationFactory
    val p2pContext: SerializationContext
    val rpcServerContext: SerializationContext
    val rpcClientContext: SerializationContext
    val storageContext: SerializationContext
    val checkpointContext: SerializationContext
}

@KeepForDJVM
open class SerializationEnvironmentImpl(
        override val serializationFactory: SerializationFactory,
        p2pContext: SerializationContext? = null,
        rpcServerContext: SerializationContext? = null,
        rpcClientContext: SerializationContext? = null,
        storageContext: SerializationContext? = null,
        checkpointContext: SerializationContext? = null) : SerializationEnvironment {
    // Those that are passed in as null are never inited:
    override lateinit var p2pContext: SerializationContext
    override lateinit var rpcServerContext: SerializationContext
    override lateinit var rpcClientContext: SerializationContext
    override lateinit var storageContext: SerializationContext
    override lateinit var checkpointContext: SerializationContext

    init {
        p2pContext?.let { this.p2pContext = it }
        rpcServerContext?.let { this.rpcServerContext = it }
        rpcClientContext?.let { this.rpcClientContext = it }
        storageContext?.let { this.storageContext = it }
        checkpointContext?.let { this.checkpointContext = it }
    }
}

private val _nodeSerializationEnv = SimpleToggleField<SerializationEnvironment>("nodeSerializationEnv", true)
@VisibleForTesting
val _globalSerializationEnv = SimpleToggleField<SerializationEnvironment>("globalSerializationEnv")
@VisibleForTesting
val _contextSerializationEnv = ThreadLocalToggleField<SerializationEnvironment>("contextSerializationEnv")
@VisibleForTesting
val _inheritableContextSerializationEnv = InheritableThreadLocalToggleField<SerializationEnvironment>("inheritableContextSerializationEnv") { stack ->
    stack.fold(false) { isAGlobalThreadBeingCreated, e ->
        isAGlobalThreadBeingCreated ||
                (e.className == "io.netty.util.concurrent.GlobalEventExecutor" && e.methodName == "startThread") ||
                (e.className == "java.util.concurrent.ForkJoinPool\$DefaultForkJoinWorkerThreadFactory" && e.methodName == "newThread")
    }
}
private val serializationEnvProperties = listOf(_nodeSerializationEnv, _globalSerializationEnv, _contextSerializationEnv, _inheritableContextSerializationEnv)
val effectiveSerializationEnv: SerializationEnvironment
    get() = serializationEnvProperties.map { Pair(it, it.get()) }.filter { it.second != null }.run {
        singleOrNull()?.run {
            second!!
        } ?: throw IllegalStateException("Expected exactly 1 of {${serializationEnvProperties.joinToString(", ") { it.name }}} but got: {${joinToString(", ") { it.first.name }}}")
    }

/** Should be set once in main. */
var nodeSerializationEnv by _nodeSerializationEnv

// AMQP-specific configuration

@KeepForDJVM
interface AMQPSerializationEnvironment {
    val serializationFactory: AMQPSerializationFactory
    val p2pContext: AMQPSerializationContext
    val rpcServerContext: AMQPSerializationContext
    val rpcClientContext: AMQPSerializationContext
    val storageContext: AMQPSerializationContext
}

@KeepForDJVM
class AMQPSerializationEnvironmentImpl(
        override val serializationFactory: AMQPSerializationFactory,
        override val p2pContext: AMQPSerializationContext,
        val _rpcServerContext: AMQPSerializationContext? = null,
        val _rpcClientContext: AMQPSerializationContext? = null,
        val _storageContext: AMQPSerializationContext? = null) : AMQPSerializationEnvironment {

    // TODO: not this
    override val rpcServerContext: AMQPSerializationContext get() = _rpcServerContext!!
    override val rpcClientContext: AMQPSerializationContext get() = _rpcClientContext!!
    override val storageContext: AMQPSerializationContext get() = _storageContext!!
}

private val _nodeAMQPSerializationEnv = SimpleToggleField<AMQPSerializationEnvironment>("nodeAMQPSerializationEnv", true)
@VisibleForTesting
val _globalAMQPSerializationEnv = SimpleToggleField<AMQPSerializationEnvironment>("globalAMQPSerializationEnv")
@VisibleForTesting
val _contextAMQPSerializationEnv = ThreadLocalToggleField<AMQPSerializationEnvironment>("contextAMQPSerializationEnv")
@VisibleForTesting
val _inheritableContextAMQPSerializationEnv = InheritableThreadLocalToggleField<AMQPSerializationEnvironment>("inheritableContextAMQPSerializationEnv") { stack ->
    stack.fold(false) { isAGlobalThreadBeingCreated, e ->
        isAGlobalThreadBeingCreated ||
                (e.className == "io.netty.util.concurrent.GlobalEventExecutor" && e.methodName == "startThread") ||
                (e.className == "java.util.concurrent.ForkJoinPool\$DefaultForkJoinWorkerThreadFactory" && e.methodName == "newThread")
    }
}
private val amqpSerializationEnvProperties = listOf(_nodeAMQPSerializationEnv, _globalAMQPSerializationEnv, _contextAMQPSerializationEnv, _inheritableContextAMQPSerializationEnv)
val effectiveAMQPSerializationEnv: AMQPSerializationEnvironment
    get() = amqpSerializationEnvProperties.map { Pair(it, it.get()) }.filter { it.second != null }.run {
        singleOrNull()?.run {
            second!!
        } ?: throw IllegalStateException("Expected exactly 1 of {${amqpSerializationEnvProperties.joinToString(", ") { it.name }}} but got: {${joinToString(", ") { it.first.name }}}")
    }

/** Should be set once in main. */
var nodeAMQPSerializationEnv by _nodeAMQPSerializationEnv
