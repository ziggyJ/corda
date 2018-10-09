package net.corda.node.services.config

import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.time.Duration
import java.util.*
import kotlin.reflect.KClass

// TODO sollecitom perhaps move to a common module
interface Configuration {

    @Throws(Exception.Missing::class, Exception.WrongType::class, Exception.BadValue::class)
    operator fun <TYPE> get(property: Property<TYPE>): TYPE

    fun <TYPE> getOptional(property: Property<TYPE>): TYPE?

    @Throws(Exception.Missing::class, Exception.WrongType::class, Exception.BadValue::class)
    fun toMap(): Map<String, Any>

    fun <TYPE> getRaw(key: String): TYPE

    val schema: Schema

    companion object {

        fun withSchema(schema: Schema): Builder.Selector = ConfigurationFactory.builderSelectorFor(schema)
    }

    interface Schema : Validator<Configuration, ConfigValidationError> {

        fun description(): String

        val properties: Set<Property<*>>

        companion object {

            // TODO sollecitom try perhaps to reproduce the delegated properties approach to instantiate a Configuration.Schema, so that a reference to the individual properties stays within the object. `object MySchema: Configuration.SchemaImpl() { val myLegalName by string().optional() }`
            fun withProperties(strict: Boolean = false, properties: Iterable<Property<*>>): Schema = ConfigurationFactory.schemaOf(strict, properties)

            fun withProperties(vararg properties: Property<*>, strict: Boolean = false): Schema = withProperties(strict, properties.toSet())

            fun withProperties(strict: Boolean = false, builder: Property.Builder.() -> Iterable<Property<*>>): Schema = withProperties(strict, builder.invoke(ConfigurationFactory.propertyBuilder()))
        }
    }

    interface Builder {

        interface Selector {

            val from: SourceSelector

            val empty: Builder

            val with: ValueSelector
        }

        val from: SourceSelector

        val with: ValueSelector

        operator fun <TYPE : Any> set(property: Property<TYPE>, value: TYPE)

        fun build(): Configuration

        interface ValueSelector {

            fun <TYPE : Any> value(property: Property<TYPE>, value: TYPE): Builder
        }

        interface SourceSelector {

            fun systemProperties(prefixFilter: String = ""): Builder

            fun environment(prefixFilter: String = ""): Builder

            fun properties(properties: Properties): Builder

            val map: MapSpecific

            fun hierarchicalMap(map: Map<String, Any>): Builder

            val hocon: FormatAware

            val yaml: FormatAware

            val xml: FormatAware

            val json: FormatAware

            val toml: FormatAware

            val properties: FormatAware

            interface MapSpecific {

                fun hierarchical(map: Map<String, Any>): Builder

                fun flat(map: Map<String, String>): Builder

                fun keyValue(map: Map<String, Any>): Builder
            }

            interface FormatAware {

                fun file(path: Path): Builder

                fun resource(resourceName: String): Builder

                fun reader(reader: Reader): Builder

                fun inputStream(stream: InputStream): Builder

                fun string(rawFormat: String): Builder

                fun bytes(bytes: ByteArray): Builder
            }
        }
    }

    interface Property<TYPE> : Validator<Configuration, ConfigValidationError> {

        val key: String
        val description: String
        val type: Class<TYPE>

        interface Optional<TYPE> : Property<TYPE> {

            val defaultValue: TYPE
        }

        interface Multiple<TYPE> : Property<List<TYPE>> {

            val elementType: Class<TYPE>
        }

        @Throws(Exception.Missing::class, Exception.WrongType::class, Exception.BadValue::class)
        fun valueIn(configuration: Configuration): TYPE

        fun isSpecifiedBy(configuration: Configuration): Boolean

        fun optional(defaultValue: TYPE?): Property<TYPE?>

        fun multiple(): Multiple<TYPE>

        // TODO sollecitom see if you need this
        fun contextualize(currentContext: String?): String? = currentContext

        override fun validate(target: Configuration): Set<ConfigValidationError> {

            try {
                valueIn(target)
                return emptySet()
            } catch (exception: kotlin.Exception) {
                if (exception is Exception) {
                    return setOf(exception.toValidationError(key, type))
                }
                throw exception
            }
        }

        // TODO sollecitom check
        private fun Exception.toValidationError(keyName: String, type: Class<*>) = ConfigValidationError(keyName, type, message!!)

        companion object {

            val ofType: Builder get() = ConfigurationFactory.propertyBuilder()
        }

        // TODO sollecitom introduce a function `list()` in property to produce a collection-like property
        // TODO create wrapper properties OptionalProperty and ListProperty, delegating to required single-element ones
        interface Builder {

            fun int(key: String, description: String = ""): Property<Int>
            fun intList(key: String, description: String = ""): Property<List<Int>>

            fun boolean(key: String, description: String = ""): Property<Boolean>
            fun booleanList(key: String, description: String = ""): Property<List<Boolean>>

            fun double(key: String, description: String = ""): Property<Double>
            fun doubleList(key: String, description: String = ""): Property<List<Double>>

            fun string(key: String, description: String = ""): Property<String>
            fun stringList(key: String, description: String = ""): Property<List<String>>

            fun duration(key: String, description: String = ""): Property<Duration>
            fun durationList(key: String, description: String = ""): Property<List<Duration>>

            fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>, description: String = ""): Property<ENUM>
            fun <ENUM : Enum<ENUM>> enumList(key: String, enumClass: KClass<ENUM>, description: String = ""): Property<List<ENUM>>

            fun nested(key: String, schema: Schema, description: String = ""): Property<Configuration>
            fun nestedList(key: String, schema: Schema, description: String = ""): Property<List<Configuration>>
        }
    }

    // TODO sollecitom add message and cause
    sealed class Exception : kotlin.Exception() {

        // TODO sollecitom add fields
        class Missing : Exception()

        // TODO sollecitom add fields
        class WrongType : Exception()

        // TODO sollecitom add fields
        class BadValue : Exception()
    }
}