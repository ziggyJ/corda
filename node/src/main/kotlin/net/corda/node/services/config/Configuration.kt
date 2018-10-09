package net.corda.node.services.config

import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.time.Duration
import java.util.*
import kotlin.reflect.KClass

// TODO sollecitom perhaps move to a common module
interface Configuration {

    @Throws(Configuration.Exception::class)
    operator fun <TYPE> get(property: Property<TYPE>): TYPE

    @Throws(Configuration.Exception.WrongType::class)
    fun <TYPE> getOptional(property: Property<TYPE>): TYPE?

    @Throws(Configuration.Exception::class)
    fun toMap(): Map<String, Any>

    @Throws(Configuration.Exception::class)
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

            val from: Configuration.Builder.SourceSelector

            val empty: Configuration.Builder

            val with: Configuration.Builder.ValueSelector
        }

        val from: Configuration.Builder.SourceSelector

        val with: Configuration.Builder.ValueSelector

        operator fun <TYPE : Any> set(property: Property<TYPE>, value: TYPE)

        fun build(): Configuration

        interface ValueSelector {

            fun <TYPE : Any> value(property: Property<TYPE>, value: TYPE): Configuration.Builder
        }

        interface SourceSelector {

            fun systemProperties(prefixFilter: String = ""): Configuration.Builder

            fun environment(prefixFilter: String = ""): Configuration.Builder

            fun properties(properties: Properties): Configuration.Builder

            val map: Configuration.Builder.SourceSelector.MapSpecific

            val hocon: Configuration.Builder.SourceSelector.FormatAware

            val yaml: Configuration.Builder.SourceSelector.FormatAware

            val xml: Configuration.Builder.SourceSelector.FormatAware

            val json: Configuration.Builder.SourceSelector.FormatAware

            val toml: Configuration.Builder.SourceSelector.FormatAware

            val properties: Configuration.Builder.SourceSelector.FormatAware

            interface MapSpecific {

                fun hierarchical(map: Map<String, Any>): Configuration.Builder

                fun flat(map: Map<String, String>): Configuration.Builder

                fun keyValue(map: Map<String, Any>): Configuration.Builder
            }

            interface FormatAware {

                fun file(path: Path): Configuration.Builder

                fun resource(resourceName: String): Configuration.Builder

                fun reader(reader: Reader): Configuration.Builder

                fun inputStream(stream: InputStream): Configuration.Builder

                fun string(rawFormat: String): Configuration.Builder

                fun bytes(bytes: ByteArray): Configuration.Builder
            }
        }
    }

    interface Property<TYPE> : Validator<Configuration, ConfigValidationError> {

        val key: String
        val description: String
        val type: Class<TYPE>

        @Throws(Configuration.Exception::class)
        fun valueIn(configuration: Configuration): TYPE

        fun isSpecifiedBy(configuration: Configuration): Boolean

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

        interface Builder {

            fun int(key: String, description: String = ""): Property.Standard<Int>

            fun boolean(key: String, description: String = ""): Property.Standard<Boolean>

            fun double(key: String, description: String = ""): Property.Standard<Double>

            fun string(key: String, description: String = ""): Property.Standard<String>

            fun duration(key: String, description: String = ""): Property.Standard<Duration>

            fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>, description: String = ""): Property.Standard<ENUM>

            fun nested(key: String, schema: Schema, description: String = ""): Property.Standard<Configuration>
        }

        interface Required<TYPE> : Configuration.Property<TYPE> {

            fun optional(defaultValue: TYPE?): Configuration.Property<TYPE?>
        }

        interface Single<TYPE> : Configuration.Property<TYPE> {

            // TODO sollecitom expand with other collection types
            fun list(): Configuration.Property.Required<List<TYPE>>
        }

        interface Standard<TYPE> : Configuration.Property.Required<TYPE>, Configuration.Property.Single<TYPE>

        interface Optional<TYPE> : Configuration.Property<TYPE> {

            val defaultValue: TYPE
        }

        interface Multiple<TYPE> : Configuration.Property<List<TYPE>> {

            val elementType: Class<TYPE>
        }
    }

    // TODO sollecitom add message and cause
    sealed class Exception : kotlin.Exception() {

        // TODO sollecitom add fields
        class Missing : Configuration.Exception()

        // TODO sollecitom add fields
        class WrongType : Configuration.Exception()

        // TODO sollecitom add fields
        class BadValue : Configuration.Exception()
    }
}