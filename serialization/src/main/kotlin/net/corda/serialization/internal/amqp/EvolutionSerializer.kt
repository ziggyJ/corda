package net.corda.serialization.internal.amqp

import net.corda.core.KeepForDJVM
import net.corda.core.internal.isConcreteClass
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.serialization.SerializationContext
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.core.utilities.loggerFor
import net.corda.serialization.internal.carpenter.getTypeAsClass
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Type
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

/**
 * Serializer for deserializing objects whose definition has changed since they
 * were serialised.
 *
 * @property typeInformation Information about the type supported by this serializer.
 * @property propertySerializers An list of serializers representing the properties of the object as they were serialized.
 * Note this may contain properties that are no longer needed by the class. These *must* be read however to ensure
 * any referenced objects in the object stream are captured properly.
 * @property listObjectReader The [ListObjectReader] to use to deserialise a list of values into an object of the required type.
 */
class EvolutionSerializer private constructor(
        override val typeInformation: TypeInformation,
        private val propertySerializers: List<PropertySerializer>,
        private val listObjectReader: ListObjectReader) :
        ObjectSerializer {

    companion object {
        val logger = contextLogger()

        /**
         * Build a serialization object for deserialization only of objects serialised
         * as different versions of a class.
         *
         * @param old is an object holding the schema that represents the object
         *  as it was serialised and the type descriptor of that type
         * @param new is the Serializer built for the Class as it exists now, not
         * how it was serialised and persisted.
         * @param factory the [SerializerFactory] associated with the serialization
         * context this serializer is being built for
         */
        fun make(old: CompositeType,
                 new: ObjectSerializer,
                 factory: SerializerFactory
        ): AMQPSerializer<Any> {
            // Evolution is triggered by a mismatch in the fingerprint for the entire type,
            // however the actual difference may not be in this type but in the type of one of its properties
            // (or one of its properties' properties, etc), in which case we can safely return the serializer
            // we just generated for this type, and let the serializer generated for the property type look
            // after evolving values of that type.
            //
            // The outcome of doing this is that the non-evolution serializer is associated with the new fingerprint,
            // so we won't go looking for an evolution serializer the next time around.
            if (!mustEvolve(old, new)) return new

            val readers = getReaders(old, factory, new)

            // cope with the situation where a generic interface was serialised as a type, in such cases
            // return the synthesised object which is, given the absence of a constructor, a no op
            val constructor = getEvolverConstructor(new.type, readers) ?: return new

            val type = new.type
            val classProperties = type.asClass().propertyDescriptors()
            val typeInfo = TypeInformation.forType(type, factory)
            val objectConstructor = ObjectConstructor(type, constructor)

            return if (classProperties.isNotEmpty() && constructor.parameters.isEmpty()) {
                makeWithSetters(typeInfo, factory, objectConstructor, readers, classProperties)
            } else {
                makeWithConstructor(typeInfo, objectConstructor, readers)
            }
        }

        /**
         * We must evolve if the number of fields is different, or their names or types do not match up.
         */
        private fun mustEvolve(old: CompositeType, new: ObjectSerializer): Boolean {
            if (old.fields.size != new.propertyAccessors.size) return true
            old.fields.zip(new.propertyAccessors).forEach { (field, accessor) ->
                if (field.name != accessor.serializer.name) return true
                if (field.type != accessor.serializer.type) return true
            }
            return false
        }

        private fun getReaders(old: CompositeType, factory: SerializerFactory, new: ObjectSerializer) = old.fields.map {
            try {
                PropertySerializer.make(it.name, EvolutionPropertyReader(),
                        it.getTypeAsClass(factory.classloader), factory)
            } catch (e: ClassNotFoundException) {
                throw AMQPNotSerializableException(new.type, e.message ?: "")
            }
        }

        /**
         * Unlike the generic deserialization case where we need to locate the primary constructor
         * for the object (or our best guess) in the case of an object whose structure has changed
         * since serialisation we need to attempt to locate a constructor that we can use. For example,
         * its parameters match the serialised members and it will initialise any newly added
         * elements.
         *
         * TODO: Type evolution
         * TODO: rename annotation
         */
        private fun getEvolverConstructor(type: Type, oldArgs: List<PropertySerializer>): KFunction<Any>? {
            val clazz: Class<*> = type.asClass()

            if (!clazz.isConcreteClass) return null

            val oldArguments = oldArgs.associateBy(PropertySerializer::name) { it.resolvedType.asClass() }
            var maxConstructorVersion = Integer.MIN_VALUE
            var constructor: KFunction<Any>? = null

            clazz.kotlin.constructors.forEach {
                val version = it.findAnnotation<DeprecatedConstructorForDeserialization>()?.version ?: Integer.MIN_VALUE

                if (version > maxConstructorVersion &&
                        it.parameters.all { parameter ->
                            oldArguments[parameter.name]?.equals(parameter.type.javaType.asClass()) == true
                        }
                ) {
                    constructor = it
                    maxConstructorVersion = version

                    with(logger) {
                        info("Select annotated constructor version=$version nparams=${it.parameters.size}")
                        debug { "  params=${it.parameters}" }
                    }
                } else if (version != Integer.MIN_VALUE) {
                    with(logger) {
                        info("Ignore annotated constructor version=$version nparams=${it.parameters.size}")
                        debug { "  params=${it.parameters}" }
                    }
                }
            }

            // if we didn't get an exact match revert to existing behaviour, if the new parameters
            // are not mandatory (i.e. nullable) things are fine
            return constructor ?: run {
                logger.info("Failed to find annotated historic constructor")
                constructorForDeserialization(type)
            }
        }

        private fun makeWithConstructor(
                typeInformation: TypeInformation,
                objectConstructor: ObjectConstructor,
                oropertySerializers: List<PropertySerializer>): AMQPSerializer<Any> {
            // We use the set both for lookup, and to map readers to parameter indices, so we need to retain order.
            val serializedPropertyNames = LinkedHashSet(oropertySerializers.map { it.name })

            // Java doesn't care about nullability unless it's a primitive in which
            // case it can't be referenced. Unfortunately whilst Kotlin does apply
            // Nullability annotations we cannot use them here as they aren't
            // retained at runtime so we cannot rely on the absence of
            // any particular NonNullable annotation type to indicate cross
            // compiler nullability
            val isKotlin = typeInformation.isKotlinType

            // Validate that parameters with no corresponding readers are nullable.
            for (parameter in objectConstructor.parameters) {
                if (parameter.name in serializedPropertyNames) continue

                if ((isKotlin && !parameter.type.isMarkedNullable)
                        || (!isKotlin && isJavaPrimitive(parameter.type.jvmErasure.java))
                ) {
                    throw AMQPNotSerializableException(
                            typeInformation.type,
                            "New parameter \"${parameter.name}\" is mandatory, should be nullable for evolution " +
                                    "to work, isKotlinClass=$isKotlin type=${parameter.type}")
                }
            }

            val parameterIndices = objectConstructor.getParameterIndices(serializedPropertyNames)
            val objectReader = ConcreteClassListObjectReader(
                    typeInformation.type,
                    ConstructorObjectBuilder.provider(objectConstructor, objectConstructor.parameterCount, parameterIndices),
                    oropertySerializers)

            return EvolutionSerializer(
                    typeInformation,
                    oropertySerializers,
                    objectReader)
        }

        private fun makeWithSetters(
                typeInformation: TypeInformation,
                factory: SerializerFactory,
                objectConstructor: ObjectConstructor,
                propertySerializers: List<PropertySerializer>,
                classProperties: Map<String, PropertyDescriptor>): AMQPSerializer<Any> {
            val settersByName = propertiesForSerializationFromSetters(classProperties,
                    typeInformation.type,
                    factory).associateBy { it.serializer.name }

            val slots = propertySerializers.map { settersByName[it.name] }.toTypedArray()

            val objectReader = ConcreteClassListObjectReader(
                    typeInformation.type,
                    SetterObjectBuilder.provider(objectConstructor, slots),
                    propertySerializers)

            return EvolutionSerializer(
                    typeInformation,
                    propertySerializers,
                    objectReader)
        }
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext
    ): Any {
        if (obj !is List<*>) throw NotSerializableException("Body of described type is unexpected $obj")

        return listObjectReader.readObject(obj, schemas, input, context)
    }

    override fun writeClassInfo(output: SerializationOutput) =
            throw UnsupportedOperationException("It should be impossible to write an evolution serializer")

    override fun writeData(obj: Any, data: Data, output: SerializationOutput, context: SerializationContext, debugLevel: Int) =
            throw UnsupportedOperationException("It should be impossible to write an evolution serializer")

    override val propertyAccessors get() = emptyList<PropertyAccessor>()

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput,
                             context: SerializationContext, debugIndent: Int) =
            throw UnsupportedOperationException("It should be impossible to write an evolution serializer")
}

/**
 * Instances of this type are injected into a [SerializerFactory] at creation time to dictate the
 * behaviour of evolution within that factory. Under normal circumstances this will simply
 * be an object that returns an [EvolutionSerializer]. Of course, any implementation that
 * extends this class can be written to invoke whatever behaviour is desired.
 */
abstract class EvolutionSerializerGetterBase {
    abstract fun getEvolutionSerializer(
            factory: SerializerFactory,
            typeNotation: TypeNotation,
            newSerializer: AMQPSerializer<Any>,
            schemas: SerializationSchemas): AMQPSerializer<Any>
}

/**
 * The normal use case for generating an [EvolutionSerializer]'s based on the differences
 * between the received schema and the class as it exists now on the class path,
 */
@KeepForDJVM
class EvolutionSerializerGetter : EvolutionSerializerGetterBase() {
    override fun getEvolutionSerializer(factory: SerializerFactory,
                                        typeNotation: TypeNotation,
                                        newSerializer: AMQPSerializer<Any>,
                                        schemas: SerializationSchemas): AMQPSerializer<Any> =
            factory.serializersByDescriptor.computeIfAbsent(typeNotation.descriptor.name!!) {
                getEvolutionSerializerUncached(typeNotation, newSerializer, factory, schemas)
            }

    private fun getEvolutionSerializerUncached(typeNotation: TypeNotation, newSerializer: AMQPSerializer<Any>, factory: SerializerFactory, schemas: SerializationSchemas): AMQPSerializer<Any> =
            when (typeNotation) {
                is CompositeType ->
                    EvolutionSerializer.make(typeNotation, newSerializer as ObjectSerializer, factory)
                is RestrictedType -> {
                    // The fingerprint of a generic collection can be changed through bug fixes to the
                    // fingerprinting function making it appear as if the class has altered whereas it hasn't.
                    // Given we don't support the evolution of these generic containers, if it appears
                    // one has been changed, simply return the original serializer and associate it with
                    // both the new and old fingerprint
                    if (newSerializer is CollectionSerializer || newSerializer is MapSerializer) {
                        newSerializer
                    } else if (newSerializer is EnumSerializer) {
                        EnumEvolutionSerializer.make(typeNotation, newSerializer, factory, schemas)
                    } else {
                        loggerFor<SerializerFactory>().error("typeNotation=${typeNotation.name} Need to evolve unsupported type")
                        throw NotSerializableException("${typeNotation.name} cannot be evolved")
                    }
                }
            }
}

