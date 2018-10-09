package net.corda.node.services.config.v2

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.type.TypeFactory
import com.uchuhimo.konf.*
import com.uchuhimo.konf.source.DefaultLoaders
import com.uchuhimo.konf.source.Loader
import com.uchuhimo.konf.source.Source
import com.uchuhimo.konf.source.base.KVSource
import net.corda.node.services.config.ConfigValidationError
import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.time.Duration
import java.util.*
import kotlin.reflect.KClass

// TODO sollecitom add a constructor which doesn't add the schema to the Config
class Konfiguration(internal val value: Config, private val schema: Configuration.Schema) : Configuration {

    override fun <TYPE> get(property: Configuration.Property<TYPE>): TYPE {

        // TODO sollecitom maybe add `prefix` here?
        return property.valueIn(this)
    }

    override fun <TYPE> getRaw(key: String): TYPE {

        return value[key]
    }

    override fun <TYPE> getOptional(property: Configuration.Property<TYPE>): TYPE? {

        return value.getOrNull(property.key)
    }

    override fun toMap(): Map<String, Any> = value.toMap()

    class Builder(private var value: Config, private val schema: Configuration.Schema) : Configuration.Builder {

        // TODO sollecitom make it a `val get() =` perhaps?
        override val from get() = Konfiguration.Builder.SourceSelector(value.from, schema)

        override val with get() = Konfiguration.Builder.ValueSelector(value.from, schema)

        override operator fun <TYPE : Any> set(property: Configuration.Property<TYPE>, value: TYPE) {

            this.value = this.value.from.map.kv(mapOf(property.key to value))
        }

        override fun build(): Configuration {

            return Konfiguration(value, schema)
        }

        class Selector(private val schema: Configuration.Schema) : Configuration.Builder.Selector {

            private val spec = schema.toSpec()
            private val config = Config.invoke().also { it.addSpec(spec) }

            // TODO sollecitom perhaps try to use JvmStatic here
            override val from get(): Configuration.Builder.SourceSelector = Konfiguration.Builder.SourceSelector(config.from, schema)

            override val with get(): Configuration.Builder.ValueSelector = Konfiguration.Builder.ValueSelector(config.from, schema)

            override val empty get(): Configuration.Builder = Konfiguration.Builder(config, schema)
        }

        class ValueSelector(private val from: DefaultLoaders, private val schema: Configuration.Schema) : Configuration.Builder.ValueSelector {

            override fun <TYPE : Any> value(property: Configuration.Property<TYPE>, value: TYPE): Configuration.Builder {

                // TODO sollecitom use polymorphism if possible
                return if (value is Configuration) {
                    from.config[property.key] = value.toMap()
                    Konfiguration.Builder(from.config, schema)
                } else {
                    Konfiguration.Builder(from.map.kv(mapOf(property.key to value)), schema)
                }
            }
        }

        class SourceSelector(private val from: DefaultLoaders, private val schema: Configuration.Schema) : Configuration.Builder.SourceSelector {

            override fun systemProperties(prefixFilter: String) = Konfiguration.Builder(from.config.withSource(SystemPropertiesProvider.source(prefixFilter)), schema)

            override fun environment(prefixFilter: String) = Konfiguration.Builder(from.config.withSource(EnvProvider.source(prefixFilter)), schema)

            // TODO sollecitom perhaps expose a different Selector interface for Map & Properties
            override fun properties(properties: Properties): Configuration.Builder {

                @Suppress("UNCHECKED_CAST")
                return hierarchicalMap(properties as Map<String, Any>)
            }

            // TODO sollecitom look here at the difference between .kv() and .flat()
            override fun map(map: Map<String, Any>) = Konfiguration.Builder(from.map.flat(map.mapValues { (_, value) -> value.toString() }), schema)

            override fun hierarchicalMap(map: Map<String, Any>) = Konfiguration.Builder(from.map.hierarchical(map), schema)

            // TODO sollecitom add `properties` as a supported type
            override val hocon: Configuration.Builder.SourceSelector.FormatAware
                get() = Konfiguration.Builder.SourceSelector.FormatAware(from.hocon, schema)

            override val yaml: Configuration.Builder.SourceSelector.FormatAware
                get() = Konfiguration.Builder.SourceSelector.FormatAware(from.yaml, schema)

            override val xml: Configuration.Builder.SourceSelector.FormatAware
                get() = Konfiguration.Builder.SourceSelector.FormatAware(from.xml, schema)

            override val json: Configuration.Builder.SourceSelector.FormatAware
                get() = Konfiguration.Builder.SourceSelector.FormatAware(from.json, schema)

            class FormatAware(private val loader: Loader, private val schema: Configuration.Schema) : Configuration.Builder.SourceSelector.FormatAware {

                override fun file(path: Path) = Konfiguration.Builder(loader.file(path.toAbsolutePath().toFile()), schema)

                override fun resource(resourceName: String) = Konfiguration.Builder(loader.resource(resourceName), schema)

                override fun reader(reader: Reader) = Konfiguration.Builder(loader.reader(reader), schema)

                override fun inputStream(stream: InputStream) = Konfiguration.Builder(loader.inputStream(stream), schema)

                override fun string(rawFormat: String) = Konfiguration.Builder(loader.string(rawFormat), schema)

                override fun bytes(bytes: ByteArray) = Konfiguration.Builder(loader.bytes(bytes), schema)
            }
        }
    }

    private object SystemPropertiesProvider {

        fun source(prefixFilter: String = ""): Source = KVSource(System.getProperties().toMap().onlyWithPrefix(prefixFilter), type = "system-properties")

        @Suppress("UNCHECKED_CAST")
        private fun Properties.toMap(): Map<String, String> = this as Map<String, String>
    }

    private object EnvProvider {

        fun source(prefixFilter: String = ""): Source = KVSource(System.getenv().mapKeys { (key, _) -> key.toLowerCase().replace('_', '.') }.onlyWithPrefix(prefixFilter), type = "system-environment")
    }

    class Property {

        class Builder : Configuration.Property.Builder {

            override fun int(key: String, description: String): Configuration.Property<Int> = KonfigProperty(key, description, Int::class.javaObjectType)
            override fun intList(key: String, description: String): Configuration.Property<List<Int>> = KonfigProperty(key, description, Int::class.javaObjectType).multiple()

            override fun boolean(key: String, description: String): Configuration.Property<Boolean> = KonfigProperty(key, description, Boolean::class.javaObjectType)
            override fun booleanList(key: String, description: String): Configuration.Property<List<Boolean>> = KonfigProperty(key, description, Boolean::class.javaObjectType).multiple()

            override fun double(key: String, description: String): Configuration.Property<Double> = KonfigProperty(key, description, Double::class.javaObjectType)
            override fun doubleList(key: String, description: String): Configuration.Property<List<Double>> = KonfigProperty(key, description, Double::class.javaObjectType).multiple()

            override fun string(key: String, description: String): Configuration.Property<String> = KonfigProperty(key, description, String::class.java)
            override fun stringList(key: String, description: String): Configuration.Property<List<String>> = KonfigProperty(key, description, String::class.java).multiple()

            override fun duration(key: String, description: String): Configuration.Property<Duration> = KonfigProperty(key, description, Duration::class.java)
            override fun durationList(key: String, description: String): Configuration.Property<List<Duration>> = KonfigProperty(key, description, Duration::class.java).multiple()

            override fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>, description: String): Configuration.Property<ENUM> = KonfigProperty(key, description, enumClass.java)
            override fun <ENUM : Enum<ENUM>> enumList(key: String, enumClass: KClass<ENUM>, description: String): Configuration.Property<List<ENUM>> = KonfigProperty(key, description, enumClass.java).multiple()

            override fun nested(key: String, schema: Configuration.Schema, description: String): Configuration.Property<Configuration> = NestedKonfigProperty(key, description, schema)
            override fun nestedList(key: String, schema: Configuration.Schema, description: String): Configuration.Property<List<Configuration>> = NestedKonfigProperty(key, description, schema).multiple()
        }
    }

    internal class Schema(private val strict: Boolean, unorderedProperties: Iterable<Configuration.Property<*>>) : Configuration.Schema {

        // TODO sollecitom try and remove
        override val prefix: String = ""

        override val properties = unorderedProperties.sortedBy(Configuration.Property<*>::key).toSet()

        init {
            val invalid = properties.groupBy(Configuration.Property<*>::key).mapValues { entry -> entry.value.size }.filterValues { propertiesForKey -> propertiesForKey > 1 }
            if (invalid.isNotEmpty()) {
                throw IllegalArgumentException("More than one property was found for keys ${invalid.keys}.")
            }
        }

        override fun validate(target: Configuration): Set<ConfigValidationError> {

            TODO("sollecitom")
//        val propertyErrors = properties.flatMap { property -> property.validate(target).map { error -> error.withContainingPath(property.contextualize(error.containingPath)) } }.toSet()
//        if (strict) {
//            val unknownKeys = target.root().keys - properties.map(Configuration.Property<*>::key)
//            return propertyErrors + unknownKeys.map(::unknownPropertyError)
//        }
//        return propertyErrors
        }

        private fun unknownPropertyError(key: String) = ConfigValidationError(key, message = "Unknown configuration key: \"$key\".")

        override fun description(): String {

            TODO("sollecitom")
//        val description = StringBuilder()
//        var rootDescription = configObject()
//        properties.forEach { property ->
//            rootDescription = rootDescription.withValue(property.key, ConfigValueFactory.fromAnyRef(typeRef(property)))
//        }
//        description.append(rootDescription.toConfig().serialize())
//
//        val nestedProperties = (properties + properties.flatMap(::nestedProperties)).filterIsInstance<NestedConfiguration.Property<*>>().distinctBy(NestedConfiguration.Property<*>::schema)
//        nestedProperties.forEach { property ->
//            description.append(System.lineSeparator())
//            description.append("${property.typeName}: ")
//            description.append(property.schema.description())
//            description.append(System.lineSeparator())
//        }
//        return description.toString()
        }

        private fun nestedProperties(property: Configuration.Property<*>): Set<Configuration.Property<*>> {

            TODO("sollecitom")
//        return when (property) {
//            is NestedConfiguration.Property<*> -> (property.schema as ConfigPropertySchema).properties
//            else -> emptySet()
//        }
        }

        private fun typeRef(property: Configuration.Property<*>): String {

            TODO("sollecitom")
//        return if (property is NestedConfiguration.Property<*>) {
//            "#${property.typeName}"
//        } else {
//            property.typeName
//        }
        }

        override fun equals(other: Any?): Boolean {

            if (this === other) {
                return true
            }
            if (javaClass != other?.javaClass) {
                return false
            }

            other as Configuration.Schema

            if (properties != other.properties) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {

            return properties.hashCode()
        }
    }
}

private fun Configuration.Schema.toSpec(): Spec {

    // TODO sollecitom make it not an object
    return object : ConfigSpec(prefix) {}.also { properties.forEach { property -> property.addAsItem(it) } }
}

private fun <TYPE> Configuration.Property<TYPE>.addAsItem(spec: Spec): Item<TYPE> {

    var type: JavaType? = null
    if (this is Configuration.Property.Multiple<*>) {
        type = TypeFactory.defaultInstance().constructCollectionLikeType(List::class.java, this.elementType)
    }
    return if (this is Configuration.Property.Optional<TYPE>) {
        object : OptionalItem<TYPE>(spec, key, defaultValue, description, type, true) {}
    } else {
        object : RequiredItem<TYPE>(spec, key, description, type, false) {}
    }
}

private open class KonfigProperty<TYPE>(override val key: String, override val description: String, override val type: Class<TYPE>) : Configuration.Property<TYPE> {

    override fun valueIn(configuration: Configuration): TYPE {

        return configuration.getRaw(key)
    }

    override fun isSpecifiedBy(configuration: Configuration): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    // TODO sollecitom check here
    override fun optional(defaultValue: TYPE?): Configuration.Property<TYPE?> = KonfigProperty.Optional(key, description, type as Class<TYPE?>, defaultValue)

    override fun multiple(): Configuration.Property.Multiple<TYPE> {

        val outer = this@KonfigProperty
        return object : Configuration.Property.Multiple<TYPE> {

            override val key = outer.key

            override val description = outer.description

            override val type: Class<List<TYPE>> = List::class.java as Class<List<TYPE>>

            override val elementType: Class<TYPE> = outer.type

            override fun valueIn(configuration: Configuration): List<TYPE> {

                return configuration.getRaw(key)
            }

            override fun isSpecifiedBy(configuration: Configuration) = outer.isSpecifiedBy(configuration)

            override fun optional(defaultValue: List<TYPE>?): Configuration.Property<List<TYPE>?> {
                TODO("not implemented")
            }

            override fun multiple(): Configuration.Property.Multiple<List<TYPE>> {
                TODO("not implemented")
            }
        }
    }

    private class Optional<TYPE>(key: String, description: String, type: Class<TYPE>, override val defaultValue: TYPE) : KonfigProperty<TYPE>(key, description, type), Configuration.Property.Optional<TYPE>
}

private open class NestedKonfigProperty(override val key: String, override val description: String, val schema: Configuration.Schema) : Configuration.Property<Configuration> {

    override val type = Configuration::class.java

    override fun valueIn(configuration: Configuration): Configuration {

        val rawValue: Map<String, Any> = configuration.getRaw(key)
        val rawConfig = Config.invoke { addSpec(schema.toSpec()) }.from.map.kv(rawValue)
        return Konfiguration(rawConfig, schema)
    }

    override fun isSpecifiedBy(configuration: Configuration): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun multiple(): Configuration.Property.Multiple<Configuration> {

        val outer = this@NestedKonfigProperty
        return object : Configuration.Property.Multiple<Configuration> {

            override val key = outer.key

            override val description = outer.description

            override val type: Class<List<Configuration>> = List::class.java as Class<List<Configuration>>

            override val elementType: Class<Configuration> = outer.type

            override fun valueIn(configuration: Configuration): List<Configuration> {

                return configuration.getRaw(key)
            }

            override fun isSpecifiedBy(configuration: Configuration) = outer.isSpecifiedBy(configuration)

            override fun optional(defaultValue: List<Configuration>?): Configuration.Property<List<Configuration>?> {
                TODO("not implemented")
            }

            override fun multiple(): Configuration.Property.Multiple<List<Configuration>> {
                TODO("not implemented")
            }
        }
    }

    // TODO sollecitom check here
    override fun optional(defaultValue: Configuration?): Configuration.Property<Configuration?> = NestedKonfigProperty.Optional(key, description, schema, defaultValue)

    private class Optional(override val key: String, override val description: String, val schema: Configuration.Schema, override val defaultValue: Configuration?) : Configuration.Property.Optional<Configuration?> {

        override val type: Class<Configuration?> = Configuration::class.java as Class<Configuration?>

        override fun valueIn(configuration: Configuration): Configuration {

            return configuration.getRaw(key)
        }

        override fun isSpecifiedBy(configuration: Configuration): Boolean {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        // TODO sollecitom check here
        override fun optional(defaultValue: Configuration?): Configuration.Property<Configuration?> = NestedKonfigProperty.Optional(key, description, schema, defaultValue)

        override fun multiple(): Configuration.Property.Multiple<Configuration?> {
            TODO("not implemented")
        }
    }
}

private fun Map<String, String>.onlyWithPrefix(prefix: String): Map<String, String> {

    val prefixValue = if (prefix.isNotEmpty()) "$prefix." else prefix
    return filterKeys { key -> key.startsWith(prefixValue) }.mapKeys { (key, _) -> key.removePrefix(prefixValue) }
}