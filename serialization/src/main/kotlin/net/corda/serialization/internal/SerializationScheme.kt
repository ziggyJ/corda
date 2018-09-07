package net.corda.serialization.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.buildNamed
import net.corda.core.serialization.*
import net.corda.core.utilities.ByteSequence
import org.slf4j.LoggerFactory
import java.io.NotSerializableException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException

const val attachmentsClassLoaderEnabledPropertyName = "attachments.class.loader.enabled"

internal object NullEncodingWhitelist : EncodingWhitelist {
    override fun acceptEncoding(encoding: SerializationEncoding) = false
}

@KeepForDJVM
data class SerializationContextImpl @JvmOverloads constructor(override val preferredSerializationVersion: SerializationMagic,
                                                              override val deserializationClassLoader: ClassLoader,
                                                              override val whitelist: ClassWhitelist,
                                                              override val properties: Map<Any, Any>,
                                                              override val objectReferencesEnabled: Boolean,
                                                              override val useCase: SerializationContext.UseCase,
                                                              override val encoding: SerializationEncoding?,
                                                              override val encodingWhitelist: EncodingWhitelist = NullEncodingWhitelist,
                                                              override val lenientCarpenterEnabled: Boolean = false) : SerializationContext {
    private val builder = AttachmentsClassLoaderBuilder(properties, deserializationClassLoader)

    /**
     * {@inheritDoc}
     *
     * We need to cache the AttachmentClassLoaders to avoid too many contexts, since the class loader is part of cache key for the context.
     */
    override fun withAttachmentsClassLoader(attachmentHashes: List<SecureHash>): SerializationContext {
        properties[attachmentsClassLoaderEnabledPropertyName] as? Boolean == true || return this
        val classLoader = builder.build(attachmentHashes) ?: return this
        return withClassLoader(classLoader)
    }

    override fun withProperty(property: Any, value: Any): SerializationContext {
        return copy(properties = properties + (property to value))
    }

    override fun withoutReferences(): SerializationContext {
        return copy(objectReferencesEnabled = false)
    }

    override fun withLenientCarpenter(): SerializationContext = copy(lenientCarpenterEnabled = true)

    override fun withClassLoader(classLoader: ClassLoader): SerializationContext {
        return copy(deserializationClassLoader = classLoader)
    }

    override fun withWhitelisted(clazz: Class<*>): SerializationContext {
        return copy(whitelist = object : ClassWhitelist {
            override fun hasListed(type: Class<*>): Boolean = whitelist.hasListed(type) || type.name == clazz.name
        })
    }

    override fun withPreferredSerializationVersion(magic: SerializationMagic) = copy(preferredSerializationVersion = magic)
    override fun withEncoding(encoding: SerializationEncoding?) = copy(encoding = encoding)
    override fun withEncodingWhitelist(encodingWhitelist: EncodingWhitelist) = copy(encodingWhitelist = encodingWhitelist)
}

@KeepForDJVM
data class AMQPSerializationContextImpl @JvmOverloads constructor(
                                                              override val deserializationClassLoader: ClassLoader,
                                                              override val whitelist: ClassWhitelist,
                                                              override val properties: Map<Any, Any>,
                                                              override val objectReferencesEnabled: Boolean,
                                                              override val useCase: AMQPSerializationContext.UseCase,
                                                              override val encoding: AMQPSerializationEncoding?,
                                                              override val encodingWhitelist: EncodingWhitelist = NullEncodingWhitelist,
                                                              override val lenientCarpenterEnabled: Boolean = false) : AMQPSerializationContext {

    private val builder = AttachmentsClassLoaderBuilder(properties, deserializationClassLoader)

    /**
     * {@inheritDoc}
     *
     * We need to cache the AttachmentClassLoaders to avoid too many contexts, since the class loader is part of cache key for the context.
     */
    override fun withAttachmentsClassLoader(attachmentHashes: List<SecureHash>): AMQPSerializationContext {
        properties[attachmentsClassLoaderEnabledPropertyName] as? Boolean == true || return this
        val classLoader = builder.build(attachmentHashes) ?: return this
        return withClassLoader(classLoader)
    }

    override fun withProperty(property: Any, value: Any): AMQPSerializationContext {
        return copy(properties = properties + (property to value))
    }

    override fun withoutReferences(): AMQPSerializationContext {
        return copy(objectReferencesEnabled = false)
    }

    override fun withLenientCarpenter(): AMQPSerializationContext = copy(lenientCarpenterEnabled = true)

    override fun withClassLoader(classLoader: ClassLoader): AMQPSerializationContext {
        return copy(deserializationClassLoader = classLoader)
    }

    override fun withWhitelisted(clazz: Class<*>): AMQPSerializationContext {
        return copy(whitelist = object : ClassWhitelist {
            override fun hasListed(type: Class<*>): Boolean = whitelist.hasListed(type) || type.name == clazz.name
        })
    }

    override fun withEncoding(encoding: AMQPSerializationEncoding?) = copy(encoding = encoding)
    override fun withEncodingWhitelist(encodingWhitelist: EncodingWhitelist) = copy(encodingWhitelist = encodingWhitelist)
}

/*
 * This class is internal rather than private so that serialization-deterministic
 * can replace it with an alternative version.
 */
@DeleteForDJVM
internal class AttachmentsClassLoaderBuilder(private val properties: Map<Any, Any>, private val deserializationClassLoader: ClassLoader) {
    private val cache: Cache<List<SecureHash>, AttachmentsClassLoader> = Caffeine.newBuilder().weakValues().maximumSize(1024).buildNamed("SerializationScheme_attachmentClassloader")

    fun build(attachmentHashes: List<SecureHash>): AttachmentsClassLoader? {
        val serializationContext = properties[serializationContextKey] as? SerializeAsTokenContext ?: return null // Some tests don't set one.
        try {
            return cache.get(attachmentHashes) {
                val missing = ArrayList<SecureHash>()
                val attachments = ArrayList<Attachment>()
                attachmentHashes.forEach { id ->
                    serializationContext.serviceHub.attachments.openAttachment(id)?.let { attachments += it }
                        ?: run { missing += id }
                }
                missing.isNotEmpty() && throw MissingAttachmentsException(missing)
                AttachmentsClassLoader(attachments, parent = deserializationClassLoader)
            }!!
        } catch (e: ExecutionException) {
            // Caught from within the cache get, so unwrap.
            throw e.cause!!
        }
    }
}

@KeepForDJVM
open class CheckpointSerializationFactory(val checkpointScheme: SerializationScheme) : SerializationFactory() {

    private val creator: List<StackTraceElement> = Exception().stackTrace.asList()

    @Throws(NotSerializableException::class)
    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T {
        return asCurrent { withCurrentContext(context) { checkpointScheme.deserialize(byteSequence, clazz, context) } }
    }

    @Throws(NotSerializableException::class)
    override fun <T : Any> deserializeWithCompatibleContext(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): ObjectWithCompatibleContext<T> {
        return asCurrent {
            withCurrentContext(context) {
                val deserializedObject = checkpointScheme.deserialize(byteSequence, clazz, context)
                ObjectWithCompatibleContext(deserializedObject, context)
            }
        }
    }

    override fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T> {
        return asCurrent { withCurrentContext(context) { checkpointScheme.serialize(obj, context) } }
    }

    @Suppress("UNUSED_PARAMETER")
    fun registerScheme(scheme: SerializationScheme) {
        throw UnsupportedOperationException("Checkpoint serialization factory must be initialised with serialisation scheme")
    }

    override fun toString(): String {
        return "${this.javaClass.name} registeredSchemes=[$checkpointScheme] ${creator.joinToString("\n")}"
    }

    override fun equals(other: Any?): Boolean {
        return other is CheckpointSerializationFactory && other.checkpointScheme == checkpointScheme
    }

    override fun hashCode(): Int = checkpointScheme.hashCode()
}

@KeepForDJVM
class AMQPSerializationFactoryImpl(
        // TODO: This is read-mostly. Probably a faster implementation to be found.
        private val schemes: MutableMap<AMQPSerializationContext.UseCase, AMQPSerializationScheme>
) : AMQPSerializationFactory() {
    @DeleteForDJVM
    constructor() : this(ConcurrentHashMap())

    private val creator: List<StackTraceElement> = Exception().stackTrace.asList()

    private val registeredSchemes: MutableCollection<AMQPSerializationScheme> = Collections.synchronizedCollection(mutableListOf())

    private val logger = LoggerFactory.getLogger(javaClass)

    @Suppress("UNUSED_PARAMETER") // TODO: eliminate parameter
    private fun schemeFor(lookupKey: AMQPSerializationContext.UseCase): AMQPSerializationScheme {
        return schemes.computeIfAbsent(lookupKey) {
            registeredSchemes.filter { it.canDeserializeVersion(lookupKey) }.forEach { return@computeIfAbsent it }
            logger.warn("Cannot find serialization scheme for: $lookupKey, registeredSchemes are: $registeredSchemes")
            throw UnsupportedOperationException("Serialization scheme $lookupKey not supported.")
        }
    }

    @Throws(NotSerializableException::class)
    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: AMQPSerializationContext): T {
        return asCurrent { withCurrentContext(context) { schemeFor(context.useCase).deserialize(byteSequence, clazz, context) } }
    }

    override fun <T : Any> serialize(obj: T, context: AMQPSerializationContext): SerializedBytes<T> {
        return asCurrent { withCurrentContext(context) { schemeFor(context.useCase).serialize(obj, context) } }
    }

    fun registerScheme(scheme: AMQPSerializationScheme) {
        check(schemes.isEmpty()) { "All serialization schemes must be registered before any scheme is used." }
        registeredSchemes += scheme
    }

    override fun toString(): String {
        return "${this.javaClass.name} registeredSchemes=$registeredSchemes ${creator.joinToString("\n")}"
    }

    override fun equals(other: Any?): Boolean {
        return other is AMQPSerializationFactoryImpl && other.registeredSchemes == this.registeredSchemes
    }

    override fun hashCode(): Int = registeredSchemes.hashCode()
}


@KeepForDJVM
interface SerializationScheme {
    fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: SerializationContext): T

    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T, context: SerializationContext): SerializedBytes<T>
}

@KeepForDJVM
interface AMQPSerializationScheme {
    fun canDeserializeVersion(target: AMQPSerializationContext.UseCase): Boolean

    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, context: AMQPSerializationContext): T

    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T, context: AMQPSerializationContext): SerializedBytes<T>
}