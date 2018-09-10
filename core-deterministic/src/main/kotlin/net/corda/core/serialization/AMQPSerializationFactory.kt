package net.corda.core.serialization

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.internal.effectiveAMQPSerializationEnv
import net.corda.core.utilities.ByteSequence

/**
 * An abstraction for serializing and deserializing objects to the AMQP wire protocol.
 */
@KeepForDJVM
abstract class AMQPSerializationFactory {

    companion object {
        private var _currentFactory: AMQPSerializationFactory? = null

        /**
         * A default factory for serialization/deserialization, taking into account the [currentFactory] if set.
         */
        val defaultFactory: AMQPSerializationFactory get() = currentFactory ?: effectiveAMQPSerializationEnv.serializationFactory

        /**
         * If there is a need to nest serialization/deserialization with a modified context during serialization or deserialization,
         * this will return the current factory used to start serialization/deserialization.
         */
        val currentFactory: AMQPSerializationFactory? get() = _currentFactory
    }

    /**
     * Deserialize the bytes in to an object, using the prefixed bytes to determine the format.
     *
     * @param byteSequence The bytes to deserialize, including a format header prefix.
     * @param clazz The class or superclass or the object to be deserialized, or [Any] or [Object] if unknown.
     * @param context A context that configures various parameters to deserialization.
     */
    abstract fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: AMQPSerializationContext): T

    /**
     * Deserialize the bytes in to an object, using the prefixed bytes to determine the format.
     *
     * @param byteSequence The bytes to deserialize, including a format header prefix.
     * @param clazz The class or superclass or the object to be deserialized, or [Any] or [Object] if unknown.
     * @param context A context that configures various parameters to deserialization.
     * @return deserialized object along with [SerializationContext] to identify encoding used.
     */
    fun <T : Any> deserializeWithCompatibleContext(byteSequence: ByteSequence, clazz: Class<T>, context: AMQPSerializationContext) =
            ObjectWithCompatibleAMQPContext(deserialize(byteSequence, clazz, context), context)

    /**
     * Serialize an object to bytes using the preferred serialization format version from the context.
     *
     * @param obj The object to be serialized.
     * @param context A context that configures various parameters to serialization, including the serialization format version.
     */
    abstract fun <T : Any> serialize(obj: T, context: AMQPSerializationContext): SerializedBytes<T>

    /**
     * Allow subclasses to temporarily mark themselves as the current factory for the current thread during serialization/deserialization.
     * Will restore the prior context on exiting the block.
     */
    fun <T> asCurrent(block: AMQPSerializationFactory.() -> T): T {
        val priorContext = _currentFactory
        _currentFactory = this
        try {
            return this.block()
        } finally {
            _currentFactory = priorContext
        }
    }

    /**
     * If there is a need to nest serialization/deserialization with a modified context during serialization or deserialization,
     * this will return the current context used to start serialization/deserialization.
     */
    val currentContext: AMQPSerializationContext? get() = _currentContext

    /**
     * A context to use as a default if you do not require a specially configured context.  It will be the current context
     * if the use is somehow nested (see [currentContext]).
     */
    val defaultContext: AMQPSerializationContext get() = currentContext ?: effectiveAMQPSerializationEnv.p2pContext

    private var _currentContext :AMQPSerializationContext? = null

    /**
     * Change the current context inside the block to that supplied.
     */
    fun <T> withCurrentContext(context: AMQPSerializationContext?, block: () -> T): T {
        val priorContext = _currentContext
        if (context != null) _currentContext = context
        try {
            return block()
        } finally {
            if (context != null) _currentContext = priorContext
        }
    }
}