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

interface HasTypeNotation : HasTypeInformation {
    val typeNotation: TypeNotation
}

interface CanWriteObjectWithPropertyAccessors : CanWriteObject {
    val propertyAccessors: List<PropertyAccessor>

    @JvmDefault
    fun writeData(obj: Any, data: Data, output: SerializationOutput, context: SerializationContext, debugLevel: Int = 0) =
            propertyAccessors.forEach {
                it.serializer.writeProperty(obj, data, output, context, debugLevel)
            }
}

interface ObjectSerializer : AMQPSerializer<Any>, CanWriteObjectWithPropertyAccessors, HasTypeNotation {
    companion object {
        fun forType(type: Type, factory: SerializerFactory): ObjectSerializer {
            val interfaces = interfacesForSerialization(type, factory)

            return if (type.asClass().isConcreteClass) {
                makeConcreteObjectSerializer(type, factory, interfaces)
            } else {
                makeAbstractObjectSerializer(type, factory, interfaces)
            }
        }

        private fun makeAbstractObjectSerializer(type: Type, factory: SerializerFactory, interfaces: List<Type>): AbstractClassObjectSerializer {
            val propertySerializers = propertiesForAbstractTypeSerialization(type, factory)
            val typeInformation = TypeInformation.forType(type, factory, propertySerializers)
            val outputWriter = ObjectWriterUsingPropertyAccessors(typeInformation.typeNotation, interfaces, propertySerializers.serializationOrder)

            return AbstractClassObjectSerializer(typeInformation, outputWriter)
        }

        private fun makeConcreteObjectSerializer(type: Type, factory: SerializerFactory, interfaces: List<Type>): ConcreteClassObjectSerializer {
            val objectConstructor = ObjectConstructor(type, constructorForDeserialization(type))
            val propertySerializers = propertiesForConcreteTypeSerialization(constructorForDeserialization(type), type, factory)
            val typeInformation = TypeInformation.forType(type, factory, propertySerializers)
            val outputWriter = ObjectWriterUsingPropertyAccessors(typeInformation.typeNotation, interfaces, propertySerializers.serializationOrder)

            return ConcreteClassObjectSerializer(typeInformation, outputWriter, objectConstructor)
        }
    }
}

class ObjectWriterUsingPropertyAccessors(
        private val typeNotation: TypeNotation,
        private val interfaces: List<Type>,
        override val propertyAccessors: List<PropertyAccessor>): CanWriteObject, CanWriteObjectWithPropertyAccessors {

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            for (iface in interfaces) {
                output.requireSerializer(iface)
            }

            propertyAccessors.forEach { property ->
                property.serializer.writeClassInfo(output)
            }
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) {
        // Write described
        data.withDescribed(typeNotation.descriptor) {
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
 * Knows how to construct an object of the given type from a list of parameter values.
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
}

private class ConcreteClassObjectSerializer(
        private val typeInformation: TypeInformation,
        private val objectWriter: ObjectWriterUsingPropertyAccessors,
        private val objectConstructor: ObjectConstructor):
        ObjectSerializer,
        HasTypeNotation by typeInformation,
        CanWriteObjectWithPropertyAccessors by objectWriter {

    companion object {
        private val logger = contextLogger()
    }

    override fun writeObject(
            obj: Any,
            data: Data,
            type: Type,
            output: SerializationOutput,
            context: SerializationContext,
            debugIndent: Int) = ifThrowsAppend({ type.typeName }
    ) {
        if (propertyAccessors.size != objectConstructor.parameterCount && objectConstructor.parameterCount > 0) {
            throw AMQPNotSerializableException(type, "Serialization constructor for class $type expects "
                    + "${objectConstructor.parameterCount} parameters but we have ${propertyAccessors.size} "
                    + "properties to serialize.")
        }

        objectWriter.writeObject(obj, data, type, output, context, debugIndent)
    }

    override fun readObject(
            obj: Any,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ type.typeName }) {
        if (obj is List<*>) {
            if (obj.size > propertyAccessors.size) {
                throw AMQPNotSerializableException(type, "Too many properties in described type ${type.typeName}")
            }

            return if (propertyAccessors.any { it is PropertyAccessorConstructor }) {
                readObjectBuildViaConstructor(obj, schemas, input, context)
            } else {
                readObjectBuildViaSetters(obj, schemas, input, context)
            }
        } else {
            throw AMQPNotSerializableException(type, "Body of described type is unexpected $obj")
        }
    }

    private fun readObjectBuildViaConstructor(
            obj: List<*>,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ type.typeName }) {
        logger.trace { "Calling construction based construction for ${type.typeName}" }

        return objectConstructor.construct(propertyAccessors
                .zip(obj)
                .map { Pair(it.first.initialPosition, it.first.serializer.readProperty(it.second, schemas, input, context)) }
                .sortedWith(compareBy({ it.first }))
                .map { it.second })
    }

    private fun readObjectBuildViaSetters(
            obj: List<*>,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ type.typeName }) {
        logger.trace { "Calling setter based construction for ${type.typeName}" }

        val instance: Any = objectConstructor.construct(emptyList())

        // read the properties out of the serialised form, since we're invoking the setters the order we
        // do it in doesn't matter
        val propertiesFromBlob = obj
                .zip(propertyAccessors)
                .map { it.second.serializer.readProperty(it.first, schemas, input, context) }

        // one by one take a property and invoke the setter on the class
        propertyAccessors.zip(propertiesFromBlob).forEach {
            it.first.set(instance, it.second)
        }

        return instance
    }
}
/**
 * Responsible for serializing an abstract object instance via a series of properties.
 */
private class AbstractClassObjectSerializer(typeInformation: TypeInformation, private val objectWriter: ObjectWriterUsingPropertyAccessors)
    : ObjectSerializer,
    HasTypeNotation by typeInformation,
    CanWriteObjectWithPropertyAccessors by objectWriter {

    override fun readObject(
            obj: Any,
            schemas: SerializationSchemas,
            input: DeserializationInput,
            context: SerializationContext): Any = ifThrowsAppend({ type.typeName }) {
        throw AMQPNotSerializableException(type, "Cannot read abstract type ${type.typeName}")
    }
}

/**
 * Contains the type information held by an [ObjectSerializer].
 *
 * @param type The type serialized by the serializer.
 * @param typeDescriptor A symbol representing the type.
 * @param typeNotation [TypeNotation] corresponding to the type.
 */
data class TypeInformation(
        override val type: Type,
        override val typeDescriptor: Symbol,
        override val typeNotation: TypeNotation): HasTypeNotation {

    companion object {
        /**
         * Get type information for a type with no serializers.
         *
         * @param type The type to get information for.
         * @param factory The factory to use to construct the type's fingerprint and [TypeNotation].
         */
        fun forType(type: Type, factory: SerializerFactory) = forType(type, factory, emptyList())

        /**
         * Get type information for a type with a set of [PropertySerializers].
         *
         * @param type The type to get information for.
         * @param factory The factory to use to construct the type's [TypeNotation].
         * @param propertySerializers The property serialisers to use to construct the type's fingerprint and [TypeNotation].
         */
        fun forType(type: Type, factory: SerializerFactory, propertySerializers: PropertySerializers) =
                forType(type, factory, propertySerializers.serializationOrder)

        private fun forType(type: Type, factory: SerializerFactory, propertyAccessors: List<PropertyAccessor>): TypeInformation {
            val typeDescriptor = Symbol.valueOf("$DESCRIPTOR_DOMAIN:${factory.fingerPrinter.fingerprint(type)}")
            val typeNotation = getTypeNotation(type, typeDescriptor, factory, propertyAccessors)
            return TypeInformation(type, typeDescriptor, typeNotation)
        }

        private fun getTypeNotation(
                type: Type,
                typeDescriptor: Symbol,
                factory: SerializerFactory,
                accessors: List<PropertyAccessor>): TypeNotation {
            val interfaceNames = interfacesForSerialization(type, factory).map { nameForType(it) }
            val fields = accessors.map {
                Field(it.serializer.name, it.serializer.type, it.serializer.requires, it.serializer.default, null, it.serializer.mandatory, false)
            }
            return CompositeType(nameForType(type), null, interfaceNames, Descriptor(typeDescriptor), fields)
        }
    }
}