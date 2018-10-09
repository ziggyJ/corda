package net.corda.node.services.config

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.type.TypeFactory
import com.uchuhimo.konf.*
import com.uchuhimo.konf.source.DefaultLoaders
import com.uchuhimo.konf.source.Loader
import com.uchuhimo.konf.source.MapLoader
import com.uchuhimo.konf.source.Source
import com.uchuhimo.konf.source.base.KVSource
import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.time.Duration
import java.util.*
import kotlin.reflect.KClass

// TODO sollecitom perhaps move to a common module
internal class Konfiguration(internal val value: Config, override val schema: Configuration.Schema) : Configuration {

    override fun <TYPE> get(property: Configuration.Property<TYPE>): TYPE {

        return property.valueIn(this)
    }

    override fun <TYPE> getRaw(key: String): TYPE {

        return value[key]
    }

    override fun <TYPE> getOptional(property: Configuration.Property<TYPE>): TYPE? {

        return value.getOrNull(property.key)
    }

    override fun toMap(): Map<String, Any> = value.toMap()

    internal class Builder(private var value: Config, private val schema: Configuration.Schema) : Configuration.Builder {

        override val from: Configuration.Builder.SourceSelector get() = Konfiguration.Builder.SourceSelector(value.from, schema)

        override val with: Configuration.Builder.ValueSelector get() = Konfiguration.Builder.ValueSelector(value.from, schema)

        override operator fun <TYPE : Any> set(property: Configuration.Property<TYPE>, value: TYPE) {

            this.value = this.value.from.map.kv(mapOf(property.key to value))
        }

        override fun build(): Configuration = Konfiguration(value, schema)

        internal class Selector(private val schema: Configuration.Schema) : Configuration.Builder.Selector {

            private val spec = schema.toSpec()
            private val config = Config.invoke().also { it.addSpec(spec) }

            override val from get(): Configuration.Builder.SourceSelector = Konfiguration.Builder.SourceSelector(config.from, schema)

            override val with get(): Configuration.Builder.ValueSelector = Konfiguration.Builder.ValueSelector(config.from, schema)

            override val empty get(): Configuration.Builder = Konfiguration.Builder(config, schema)
        }

        private class ValueSelector(private val from: DefaultLoaders, private val schema: Configuration.Schema) : Configuration.Builder.ValueSelector {

            override fun <TYPE : Any> value(property: Configuration.Property<TYPE>, value: TYPE): Configuration.Builder {

                // TODO sollecitom use polymorphism here, if possible
                return if (property is CollectionProperty<*, *> && property.elementType == Configuration::class.java) {
                    @Suppress("UNCHECKED_CAST")
                    Builder(from.map.kv(mapOf(property.key to (value as Collection<Configuration>).map(Configuration::toMap))), schema)
                } else {
                    if (value is Configuration) {
                        from.config[property.key] = value.toMap()
                        Builder(from.config, schema)
                    } else {
                        Builder(from.map.kv(mapOf(property.key to value)), schema)
                    }
                }
            }
        }

        private class SourceSelector(private val from: DefaultLoaders, private val schema: Configuration.Schema) : Configuration.Builder.SourceSelector {

            override fun systemProperties(prefixFilter: String) = Konfiguration.Builder(from.config.withSource(SystemPropertiesProvider.source(prefixFilter)), schema)

            override fun environment(prefixFilter: String) = Konfiguration.Builder(from.config.withSource(EnvProvider.source(prefixFilter)), schema)

            override fun properties(properties: Properties): Configuration.Builder {

                // TODO sollecitom maybe try kv to avoid unchecked cast
                @Suppress("UNCHECKED_CAST")
                return map.hierarchical(properties as Map<String, Any>)
            }

            override val map: Configuration.Builder.SourceSelector.MapSpecific get() = Konfiguration.Builder.SourceSelector.MapSpecific(from.map, schema)

            override val hocon: Configuration.Builder.SourceSelector.FormatAware get() = Konfiguration.Builder.SourceSelector.FormatAware(from.hocon, schema)

            override val yaml: Configuration.Builder.SourceSelector.FormatAware get() = Konfiguration.Builder.SourceSelector.FormatAware(from.yaml, schema)

            override val xml: Configuration.Builder.SourceSelector.FormatAware get() = Konfiguration.Builder.SourceSelector.FormatAware(from.xml, schema)

            override val json: Configuration.Builder.SourceSelector.FormatAware get() = Konfiguration.Builder.SourceSelector.FormatAware(from.json, schema)

            override val toml: Configuration.Builder.SourceSelector.FormatAware get() = Konfiguration.Builder.SourceSelector.FormatAware(from.toml, schema)

            override val properties: Configuration.Builder.SourceSelector.FormatAware get() = Konfiguration.Builder.SourceSelector.FormatAware(from.properties, schema)

            private class MapSpecific(private val loader: MapLoader, private val schema: Configuration.Schema) : Configuration.Builder.SourceSelector.MapSpecific {

                override fun hierarchical(map: Map<String, Any>): Configuration.Builder = Konfiguration.Builder(loader.hierarchical(map), schema)

                override fun flat(map: Map<String, String>): Configuration.Builder = Konfiguration.Builder(loader.flat(map), schema)

                override fun keyValue(map: Map<String, Any>): Configuration.Builder = Konfiguration.Builder(loader.kv(map), schema)
            }

            private class FormatAware(private val loader: Loader, private val schema: Configuration.Schema) : Configuration.Builder.SourceSelector.FormatAware {

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

        fun source(prefixFilter: String = ""): Source = KVSource(systemProperties().onlyWithPrefix(prefixFilter), type = "system-properties")

        @Suppress("UNCHECKED_CAST")
        private fun systemProperties(): Map<String, String> = System.getProperties() as Map<String, String>
    }

    private object EnvProvider {

        fun source(prefixFilter: String = ""): Source = KVSource(environmentVariables().onlyWithPrefix(prefixFilter), type = "system-environment")

        private fun environmentVariables(): Map<String, String> = System.getenv().mapKeys { (key, _) -> key.toLowerCase().replace('_', '.') }
    }

    internal class Property {

        internal class Builder : Configuration.Property.Builder {

            override fun int(key: String, description: String): Configuration.Property.Standard<Int> = TypedProperty(key, description, Int::class.javaObjectType)

            override fun boolean(key: String, description: String): Configuration.Property.Standard<Boolean> = TypedProperty(key, description, Boolean::class.javaObjectType)

            override fun double(key: String, description: String): Configuration.Property.Standard<Double> = TypedProperty(key, description, Double::class.javaObjectType)

            override fun string(key: String, description: String): Configuration.Property.Standard<String> = TypedProperty(key, description, String::class.java)

            override fun duration(key: String, description: String): Configuration.Property.Standard<Duration> = TypedProperty(key, description, Duration::class.java)

            override fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>, description: String): Configuration.Property.Standard<ENUM> = TypedProperty(key, description, enumClass.java)

            override fun nested(key: String, schema: Configuration.Schema, description: String): Configuration.Property.Standard<Configuration> = NestedTypedProperty(key, description, schema)
        }
    }

    internal class Schema(private val strict: Boolean, unorderedProperties: Iterable<Configuration.Property<*>>) : Configuration.Schema {

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

    // TODO sollecitom add equals and hashcode to schema, etc.
    override fun equals(other: Any?): Boolean {

        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Konfiguration

        if (value != other.value) return false
        if (schema != other.schema) return false

        return true
    }

    override fun hashCode(): Int {

        var result = value.hashCode()
        result = 31 * result + schema.hashCode()
        return result
    }
}

private fun Configuration.Schema.toSpec(): Spec {

    // TODO sollecitom make it not an object
    return object : ConfigSpec() {}.also { properties.forEach { property -> property.addAsItem(it) } }
//    return object : ConfigSpec(prefix) {}.also { properties.forEach { property -> property.addAsItem(it) } }
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

private open class TypedProperty<TYPE>(override val key: String, override val description: String, override val type: Class<TYPE>) : Configuration.Property.Standard<TYPE> {

    override fun valueIn(configuration: Configuration): TYPE {

        return configuration.getRaw(key)
    }

    override fun isSpecifiedBy(configuration: Configuration): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun optional(defaultValue: TYPE?): Configuration.Property.Single<TYPE?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun list(): Configuration.Property.Required<List<TYPE>> {

        // TODO sollecitom check
        @Suppress("UNCHECKED_CAST")
        return CollectionProperty(key, description, List::class.java as Class<List<TYPE>>, type)
    }

//    override fun optional(defaultValue: TYPE?): Configuration.Property<TYPE?> = Optional(key, description, type as Class<TYPE?>, defaultValue)
//
//    override fun multiple(): Configuration.Property.Multiple<TYPE> {
//
//        val outer = this@TypedProperty
//        return object : Configuration.Property.Multiple<TYPE> {
//
//            override val key = outer.key
//
//            override val description = outer.description
//
//            override val type: Class<List<TYPE>> = List::class.java as Class<List<TYPE>>
//
//            override val elementType: Class<TYPE> = outer.type
//
//            override fun valueIn(configuration: Configuration): List<TYPE> {
//
//                return configuration.getRaw(key)
//            }
//
//            override fun isSpecifiedBy(configuration: Configuration) = outer.isSpecifiedBy(configuration)
//
//            override fun optional(defaultValue: List<TYPE>?): Configuration.Property<List<TYPE>?> {
//                TODO("not implemented")
//            }
//
//            override fun multiple(): Configuration.Property.Multiple<List<TYPE>> {
//                TODO("not implemented")
//            }
//        }
//    }

    private class Optional<TYPE>(key: String, description: String, type: Class<TYPE>, override val defaultValue: TYPE) : TypedProperty<TYPE>(key, description, type), Configuration.Property.Optional<TYPE>
}

private class CollectionProperty<COLLECTION : Collection<ELEMENT>, ELEMENT>(override val key: String, override val description: String, override val type: Class<COLLECTION>, val elementType: Class<ELEMENT>, val elementSchema: Configuration.Schema? = null) : Configuration.Property.Required<COLLECTION> {

    override fun valueIn(configuration: Configuration): COLLECTION {

        return if (elementType == Configuration::class.java) {
            val rawValues: Collection<Map<String, Any>> = configuration.getRaw(key)
            @Suppress("UNCHECKED_CAST")
            rawValues.map { rawValue -> rawValue.toConfiguration(elementSchema!!) } as COLLECTION
        } else {
            configuration.getRaw(key)
        }
    }

    private fun Map<String, Any>.toConfiguration(schema: Configuration.Schema): Configuration {

        return Configuration.withSchema(schema).from.map.keyValue(this).build()
    }

    override fun isSpecifiedBy(configuration: Configuration): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun optional(defaultValue: COLLECTION?): Configuration.Property<COLLECTION?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

private open class NestedTypedProperty(override val key: String, override val description: String, val schema: Configuration.Schema) : Configuration.Property.Standard<Configuration> {

    override val type = Configuration::class.java

    override fun valueIn(configuration: Configuration): Configuration {

        val rawValue: Map<String, Any> = configuration.getRaw(key)
        val rawConfig = Config.invoke { addSpec(schema.toSpec()) }.from.map.kv(rawValue)
        return Konfiguration(rawConfig, schema)
    }

    override fun isSpecifiedBy(configuration: Configuration): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun optional(defaultValue: Configuration?): Configuration.Property.Single<Configuration?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    // TODO sollecitom this is the same implementation, join NestedTypedProperty and TypedProperty in one hierarchy
    override fun list(): Configuration.Property.Required<List<Configuration>> {

        @Suppress("UNCHECKED_CAST")
        return CollectionProperty(key, description, List::class.java as Class<List<Configuration>>, Configuration::class.java, schema)
    }
//
//    override fun multiple(): Configuration.Property.Multiple<Configuration> {
//
//        val outer = this@NestedTypedProperty
//        return object : Configuration.Property.Multiple<Configuration> {
//
//            override val key = outer.key
//
//            override val description = outer.description
//
//            override val type: Class<List<Configuration>> = List::class.java as Class<List<Configuration>>
//
//            override val elementType: Class<Configuration> = outer.type
//
//            override fun valueIn(configuration: Configuration): List<Configuration> {
//
//                return configuration.getRaw(key)
//            }
//
//            override fun isSpecifiedBy(configuration: Configuration) = outer.isSpecifiedBy(configuration)
//
//            override fun optional(defaultValue: List<Configuration>?): Configuration.Property<List<Configuration>?> {
//                TODO("not implemented")
//            }
//
//            override fun multiple(): Configuration.Property.Multiple<List<Configuration>> {
//                TODO("not implemented")
//            }
//        }
}

//    override fun optional(defaultValue: Configuration?): Configuration.Property<Configuration?> = Optional(key, description, schema, defaultValue)

private class Optional(override val key: String, override val description: String, val schema: Configuration.Schema, override val defaultValue: Configuration?) : Configuration.Property.Optional<Configuration?> {

    override val type: Class<Configuration?> = Configuration::class.java as Class<Configuration?>

    override fun valueIn(configuration: Configuration): Configuration {

        return configuration.getRaw(key)
    }

    override fun isSpecifiedBy(configuration: Configuration): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

private fun Map<String, String>.onlyWithPrefix(prefix: String): Map<String, String> {

    val prefixValue = if (prefix.isNotEmpty()) "$prefix." else prefix
    return filterKeys { key -> key.startsWith(prefixValue) }.mapKeys { (key, _) -> key.removePrefix(prefixValue) }
}