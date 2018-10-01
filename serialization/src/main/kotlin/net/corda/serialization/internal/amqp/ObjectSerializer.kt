package net.corda.serialization.internal.amqp

import net.corda.core.internal.isConcreteClass
import net.corda.core.serialization.SerializationContext
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.serialization.internal.amqp.SerializerFactory.Companion.nameForType
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import kotlin.reflect.KFunction

/**
 * An [AMQPSerializer] that holds property accessor information for an object type, and uses it to read and write object
 * data.
 */
interface ObjectSerializer : AMQPSerializer<Any> {

    companion object {
        fun forType(type: Type, factory: SerializerFactory): ObjectSerializer =
                if (type.asClass().isConcreteClass) {
                    makeConcreteObjectSerializer(type, factory)
                } else {
                    makeAbstractObjectSerializer(type, factory)
                }

        private fun makeAbstractObjectSerializer(type: Type, factory: SerializerFactory): ObjectSerializer {
            val propertyAccessors = propertiesForAbstractTypeSerialization(type, factory).serializationOrder
            val typeInformation = TypeInformation.forType(type, factory, propertyAccessors.map { it.serializer })

            return StandardObjectSerializer(
                    typeInformation,
                    propertyAccessors,
                    AbstractClassListObjectReader(type))
        }

        private fun makeConcreteObjectSerializer(type: Type, factory: SerializerFactory): ObjectSerializer {
            val objectConstructor = ObjectConstructor(type, constructorForDeserialization(type))
            val propertyAccessors = propertiesForConcreteTypeSerialization(constructorForDeserialization(type), type, factory)
                    .serializationOrder
            val propertySerializers = propertyAccessors.map(PropertyAccessor::serializer)
            val typeInformation = TypeInformation.forType(type, factory, propertySerializers)

            if (propertyAccessors.size != objectConstructor.parameterCount && objectConstructor.parameterCount > 0) {
                throw AMQPNotSerializableException(type, "Serialization constructor for class $type expects "
                        + "${objectConstructor.parameterCount} parameters but we have ${propertyAccessors.size} "
                        + "properties to serialize.")
            }

            val objectReader = ConcreteClassListObjectReader(
                    type,
                    getObjectBuilderProvider(propertyAccessors, objectConstructor),
                    propertySerializers)

            return StandardObjectSerializer(
                    typeInformation,
                    propertyAccessors,
                    objectReader)
        }

        private fun getObjectBuilderProvider(propertyAccessors: List<PropertyAccessor>, objectConstructor: ObjectConstructor): ObjectBuilderProvider =
                if (propertyAccessors.any { it is PropertyAccessorConstructor }) {
                    val slots = objectConstructor.getParameterIndices(propertyAccessors.map { it.serializer.name })
                    ConstructorObjectBuilder.provider(objectConstructor, propertyAccessors.size, slots)
                } else {
                    SetterObjectBuilder.provider(objectConstructor, propertyAccessors.toTypedArray())
                }
    }

    val propertyAccessors: List<PropertyAccessor>
    val typeInformation: TypeInformation
    fun writeData(obj: Any, data: Data, output: SerializationOutput, context: SerializationContext, debugLevel: Int = 0)

    @JvmDefault
    override val type
        get() = typeInformation.type

    @JvmDefault
    override val typeDescriptor
        get() = typeInformation.typeDescriptor
}

/**
 * A reader that knows how to deserialize a list of items into an object.
 */
internal interface ListObjectReader {
    fun readObject(obj: List<*>, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any
}

/**
 * If we're not evolving, this is the [ObjectSerializer] to use.
 */
private class StandardObjectSerializer(
        override val typeInformation: TypeInformation,
        override val propertyAccessors: List<PropertyAccessor>,
        private val listObjectReader: ListObjectReader) :
        ObjectSerializer {

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        if (obj !is List<*>) {
            throw AMQPNotSerializableException(type, "Body of described type is unexpected $obj")
        }

        return listObjectReader.readObject(obj, schemas, input, context)
    }

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeInformation.typeNotation)) {
            for (iface in typeInformation.interfaces) {
                output.requireSerializer(iface)
            }

            propertyAccessors.forEach { property ->
                property.serializer.writeClassInfo(output)
            }
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type,
                             output: SerializationOutput, context: SerializationContext, debugIndent: Int) = ifThrowsAppend({ type.typeName }) {
        // Write described
        data.withDescribed(typeInformation.typeNotation.descriptor) {
            // Write list
            withList {
                writeData(obj, this, output, context, debugIndent + 1)
            }
        }
    }

    override fun writeData(obj: Any, data: Data, output: SerializationOutput, context: SerializationContext, debugLevel: Int) =
            propertyAccessors.forEach {
                it.serializer.writeProperty(obj, data, output, context, debugLevel)
            }
}

/**
 * Uses an [ObjectBuilder] and list of [PropertySerializer]s to deserialize a list of items into an object.
 */
internal class ConcreteClassListObjectReader(
        private val type: Type,
        private val objectBuilderProvider: ObjectBuilderProvider,
        private val propertySerializers: List<PropertySerializer>
) : ListObjectReader {

    override fun readObject(
            obj: List<*>,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ type.typeName }) {
        val builder = objectBuilderProvider.invoke()

        // Make sure we have enough information to build the object.
        if (propertySerializers.size != builder.size) {
            throw AMQPNotSerializableException(type,
                    "Type ${type.typeName} needs ${builder.size} property values to be created, " +
                            "but we have ${propertySerializers.size} property serializers")
        }

        // Populate the builder using the serializers.
        propertySerializers.asSequence().zip(obj.asSequence()).forEachIndexed { index, (serializer, item) ->
            builder.write(index, serializer.readProperty(item, schemas, input, context))
        }

        // Return the configured object.
        return builder.build()
    }
}

/**
 * No-op implementation for abstract classes, which can't be read in this way.
 */
private class AbstractClassListObjectReader(private val type: Type) : ListObjectReader {
    override fun readObject(
            obj: List<*>,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ type.typeName }) {
        throw AMQPNotSerializableException(type, "Cannot read abstract type ${type.typeName}")
    }
}

/**
 * Thin wrapper around Kotlin's [KFunction] constructor.
 *
 * @param type The type of object to be constructed.
 * @param kotlinConstructor The constructor to be invoked.
 */
internal class ObjectConstructor(
        private val type: Type,
        private val kotlinConstructor: KFunction<Any>) {

    companion object {
        val logger = contextLogger()
    }

    val parameters get() = kotlinConstructor.parameters
    val parameterCount get() = parameters.size

    fun construct(): Any = construct(emptyArray())

    fun construct(properties: List<Any?>): Any = construct(properties.toTypedArray())

    fun construct(properties: Array<Any?>): Any {
        logger.trace { "Calling constructor: '$kotlinConstructor' with properties '$properties'" }

        if (properties.size != parameterCount) {
            throw AMQPNotSerializableException(type, "Serialization constructor for class $type expects "
                    + "${parameterCount} parameters but we have ${properties.size} "
                    + "serialized properties.")
        }

        return kotlinConstructor.call(*properties)
    }

    /**
     * Given a list of parameter names, return the indices of the corresponding constructor
     * parameter names. This is useful when parameters are removed, added or shuffled around.
     */
    fun getParameterIndices(parameterNames: Iterable<String>): Array<Int?> {
        // Build a lookup of parameter indices by name.
        val parameterIndices = parameters.asSequence().mapIndexed { index, parameter ->
            parameter.name!! to index
        }.toMap()

        return parameterNames.map(parameterIndices::get).toTypedArray()
    }
}

/**
 * Instantiates a new ObjectBuilder that can be populated with values to create an object.
 */
internal typealias ObjectBuilderProvider = () -> ObjectBuilder

/**
 * An object builder holds a set of integer-indexed slots into which values are written.
 * When all of the slots are populated, call [ObjectBuilder.build] to build the object.
 */
internal interface ObjectBuilder {
    /**
     * The number of slots that must be filled before building the object.
     */
    val size: Int

    /**
     * Write a value into one of the builder's slots.
     *
     * @param slot The index of the slot to write the value into.
     * @param value The value to write into the slot.
     */
    fun write(slot: Int, value: Any?)

    /**
     * Obtain the fully-configured object.
     */
    fun build(): Any
}

/**
 * Maps slot indices to indices in an array of constructor parameters,
 * and passes the array of parameters to a constructor to obtain the object.
 */

internal class ConstructorObjectBuilder(
        private val params: Array<Any?>,
        private val parameterIndices: Array<Int?>,
        private val constructor: ObjectConstructor) : ObjectBuilder {

    companion object {
        fun provider(objectConstructor: ObjectConstructor, argCount: Int, parameterIndices: Array<Int?>): ObjectBuilderProvider {
            return {
                // Note that we want to initialise a new array of nulls each time, otherwise concurrent attempts to
                // construct instances of the same type may collide nastily.
                ConstructorObjectBuilder(arrayOfNulls(argCount), parameterIndices, objectConstructor)
            }
        }
    }

    override val size get() = parameterIndices.size

    override fun write(slot: Int, value: Any?) {
        parameterIndices[slot]?.let { params[it] = value }
    }

    override fun build() = constructor.construct(params)
}

/**
 * Maps slot indices to property accessors,
 * and uses these to set values on an already-initialized object.
 */
internal class SetterObjectBuilder(
        private val target: Any,
        private val setters: Array<PropertyAccessor?>) : ObjectBuilder {

    companion object {
        fun provider(objectConstructor: ObjectConstructor, setters: Array<PropertyAccessor?>): ObjectBuilderProvider {
            return {
                SetterObjectBuilder(objectConstructor.construct(), setters)
            }
        }
    }

    override val size get() = setters.size

    override fun write(slot: Int, value: Any?) {
        setters[slot]?.set(target, value)
    }

    override fun build(): Any = target
}

/**
 * Contains the type information held by an [ObjectSerializer].
 *
 * @param type The type serialized by the serializer.
 * @param typeDescriptor A symbol representing the type.
 * @param typeNotation [TypeNotation] corresponding to the type.
 * @param interfaces Interfaces belonging to the type.
 */
data class TypeInformation(
        val type: Type,
        val typeDescriptor: Symbol,
        val typeNotation: TypeNotation,
        val interfaces: List<Type>) {

    internal companion object {
        /**
         * Get type information for a type with no [PropertySerializer]s.
         *
         * @param type The type to get information for.
         * @param factory The factory to use to construct the type's fingerprint and [TypeNotation].
         */
        fun forType(type: Type, factory: SerializerFactory) = forType(type, factory, emptyList())

        /**
         * Get type information for a type with a set of [PropertySerializer].
         *
         * @param type The type to get information for.
         * @param factory The factory to use to construct the type's [TypeNotation].
         * @param propertyAccessors The property accessors to use to construct the type's fingerprint and [TypeNotation].
         */
        fun forType(type: Type, factory: SerializerFactory, propertyAccessors: List<PropertySerializer>): TypeInformation {
            val typeDescriptor = Symbol.valueOf("$DESCRIPTOR_DOMAIN:${factory.fingerPrinter.fingerprint(type)}")
            val interfaces = interfacesForSerialization(type, factory)
            val typeNotation = getTypeNotation(type, typeDescriptor, propertyAccessors, interfaces)
            return TypeInformation(type, typeDescriptor, typeNotation, interfaces)
        }

        private fun getTypeNotation(
                type: Type,
                typeDescriptor: Symbol,
                accessors: List<PropertySerializer>,
                interfaces: List<Type>): TypeNotation {
            val interfaceNames = interfaces.map { nameForType(it) }
            val fields = accessors.map {
                Field(it.name, it.type, it.requires, it.default, null, it.mandatory, false)
            }
            return CompositeType(nameForType(type), null, interfaceNames, Descriptor(typeDescriptor), fields)
        }
    }

    val isKotlinType
        get() = type.javaClass.declaredAnnotations.any {
            it.annotationClass.qualifiedName == "kotlin.Metadata"
        }
}