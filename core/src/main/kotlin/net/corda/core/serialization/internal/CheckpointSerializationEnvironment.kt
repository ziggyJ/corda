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
interface CheckpointSerializationEnvironment {
    val serializationFactory: SerializationFactory
    val checkpointContext: SerializationContext
}

@KeepForDJVM
open class CheckpointSerializationEnvironmentImpl(
        override val serializationFactory: SerializationFactory,
        override val checkpointContext: SerializationContext) : CheckpointSerializationEnvironment

private val _nodeSerializationEnv = SimpleToggleField<CheckpointSerializationEnvironment>("nodeSerializationEnv", true)
@VisibleForTesting
val _globalSerializationEnv = SimpleToggleField<CheckpointSerializationEnvironment>("globalSerializationEnv")
@VisibleForTesting
val _contextSerializationEnv = ThreadLocalToggleField<CheckpointSerializationEnvironment>("contextSerializationEnv")
@VisibleForTesting
val _inheritableContextSerializationEnv = InheritableThreadLocalToggleField<CheckpointSerializationEnvironment>("inheritableContextSerializationEnv") { stack ->
    stack.fold(false) { isAGlobalThreadBeingCreated, e ->
        isAGlobalThreadBeingCreated ||
                (e.className == "io.netty.util.concurrent.GlobalEventExecutor" && e.methodName == "startThread") ||
                (e.className == "java.util.concurrent.ForkJoinPool\$DefaultForkJoinWorkerThreadFactory" && e.methodName == "newThread")
    }
}
private val serializationEnvProperties = listOf(_nodeSerializationEnv, _globalSerializationEnv, _contextSerializationEnv, _inheritableContextSerializationEnv)
val effectiveCheckpointSerializationEnv: CheckpointSerializationEnvironment
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
