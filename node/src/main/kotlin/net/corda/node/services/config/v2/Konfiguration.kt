package net.corda.node.services.config.v2

import com.uchuhimo.konf.*
import com.uchuhimo.konf.source.DefaultLoaders
import com.uchuhimo.konf.source.Loader
import com.uchuhimo.konf.source.Source
import com.uchuhimo.konf.source.base.KVSource
import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.time.Duration
import java.util.*
import kotlin.reflect.KClass

// TODO sollecitom add a constructor which doesn't add the schema to the Config
class Konfiguration(internal val value: Config, private val schema: ConfigSchema) : Configuration {

    override fun <TYPE> get(property: Configuration.Property<TYPE>): TYPE {

        // TODO sollecitom maybe add `prefix` here?
        return property.valueIn(this)
    }

    override fun <TYPE> get(key: String): TYPE {

        return value[key]
    }

    override fun <TYPE> getOptional(property: Configuration.Property<TYPE>): TYPE? {

        return value.getOrNull(property.key)
    }

    override fun <TYPE> getOptional(key: String): TYPE? {

        return value.getOrNull(key)
    }

    class Builder(private var value: Config, private val schema: ConfigSchema) : Configuration.Builder {

        // TODO sollecitom make it a `val get() =` perhaps?
        override val from get() = Konfiguration.Builder.SourceSelector(value.from, schema)

        override val with get() = Konfiguration.Builder.ValueSelector(value.from, schema)

        override operator fun <TYPE : Any> set(property: Configuration.Property<TYPE>, value: TYPE) {

            this.value = this.value.from.map.kv(mapOf(property.key to value))
        }

        override fun build(): Configuration {

            return Konfiguration(value, schema)
        }

        class Selector(private val schema: ConfigSchema) : Configuration.Builder.Selector {

            private val spec = schema.toSpec()
            private val config = Config.invoke().also { it.addSpec(spec) }

            // TODO sollecitom perhaps try to use JvmStatic here
            override val from get(): Configuration.Builder.SourceSelector = Konfiguration.Builder.SourceSelector(config.from, schema)

            override val with get(): Configuration.Builder.ValueSelector = Konfiguration.Builder.ValueSelector(config.from, schema)

            override val empty get(): Configuration.Builder = Konfiguration.Builder(config, schema)
        }

        class ValueSelector(private val from: DefaultLoaders, private val schema: ConfigSchema) : Configuration.Builder.ValueSelector {

            override fun <TYPE : Any> value(property: Configuration.Property<TYPE>, value: TYPE): Configuration.Builder {

                // TODO sollecitom check
                return Konfiguration.Builder(from.map.kv(mapOf(property.key to value)), schema)
            }
        }

        class SourceSelector(private val from: DefaultLoaders, private val schema: ConfigSchema) : Configuration.Builder.SourceSelector {

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

            class FormatAware(private val loader: Loader, private val schema: ConfigSchema) : Configuration.Builder.SourceSelector.FormatAware {

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

            override fun intList(key: String, description: String): Configuration.Property<List<Int>> = TODO("sollecitom implement")

            override fun boolean(key: String, description: String): Configuration.Property<Boolean> = TODO("sollecitom implement")
            override fun booleanList(key: String, description: String): Configuration.Property<List<Boolean>> = TODO("sollecitom implement")

            override fun double(key: String, description: String): Configuration.Property<Double> = TODO("sollecitom implement")
            override fun doubleList(key: String, description: String): Configuration.Property<List<Double>> = TODO("sollecitom implement")

            override fun string(key: String, description: String): Configuration.Property<String> = KonfigProperty(key, description, String::class.java)
            override fun stringList(key: String, description: String): Configuration.Property<List<String>> = TODO("sollecitom implement")

            override fun duration(key: String, description: String): Configuration.Property<Duration> = TODO("sollecitom implement")
            override fun durationList(key: String, description: String): Configuration.Property<List<Duration>> = TODO("sollecitom implement")

            override fun value(key: String, description: String): Configuration.Property<Configuration> = TODO("sollecitom implement")
            override fun valueList(key: String, description: String): Configuration.Property<List<Configuration>> = TODO("sollecitom implement")

            override fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>, description: String): Configuration.Property<ENUM> = TODO("sollecitom implement")
            override fun <ENUM : Enum<ENUM>> enumList(key: String, enumClass: KClass<ENUM>, description: String): Configuration.Property<List<ENUM>> = TODO("sollecitom implement")

            override fun nested(key: String, schema: ConfigSchema, description: String): Configuration.Property<Configuration> = NestedKonfigProperty(key, description, schema)
            override fun nestedList(key: String, schema: ConfigSchema, description: String): Configuration.Property<List<Configuration>> = TODO("sollecitom implement")
        }
    }
}

private fun ConfigSchema.toSpec(): Spec {

    // TODO sollecitom make it not an object
    return object : ConfigSpec(prefix) {}.also { properties.forEach { property -> property.addAsItem(it) } }
}

private fun <TYPE> Configuration.Property<TYPE>.addAsItem(spec: Spec): Item<TYPE> {

    // TODO sollecitom check
    //        val type: JavaType? = null
    return if (this is Configuration.Property.Optional<TYPE>) {
        object : OptionalItem<TYPE>(spec, key, defaultValue, description, null, true) {}
    } else {
        object : RequiredItem<TYPE>(spec, key, description, null, false) {}
    }
}

private open class KonfigProperty<TYPE>(override val key: String, override val description: String, override val type: Class<TYPE>) : Configuration.Property<TYPE> {

    override fun valueIn(configuration: Configuration): TYPE {

        return (configuration as Konfiguration).value[key]
    }

    override fun isSpecifiedBy(configuration: Configuration): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    // TODO sollecitom check here
    override fun optional(defaultValue: TYPE?): Configuration.Property<TYPE?> = KonfigProperty.Optional(key, description, type as Class<TYPE?>, defaultValue)

    private class Optional<TYPE>(key: String, description: String, type: Class<TYPE>, override val defaultValue: TYPE) : KonfigProperty<TYPE>(key, description, type), Configuration.Property.Optional<TYPE>
}

private open class NestedKonfigProperty(override val key: String, override val description: String, val schema: ConfigSchema) : Configuration.Property<Configuration> {

    override val type = Configuration::class.java

    override fun valueIn(configuration: Configuration): Configuration {

        val konf = (configuration as Konfiguration).value.at(key)
        // TODO sollecitom here
        return Konfiguration(konf, schema)
    }

    override fun isSpecifiedBy(configuration: Configuration): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    // TODO sollecitom check here
    override fun optional(defaultValue: Configuration?): Configuration.Property<Configuration?> = NestedKonfigProperty.Optional(key, description, schema, defaultValue)

    private class Optional(override val key: String, override val description: String, val schema: ConfigSchema, override val defaultValue: Configuration?) : Configuration.Property.Optional<Configuration?> {

        override val type: Class<Configuration?> = Configuration::class.java as Class<Configuration?>

        override fun valueIn(configuration: Configuration): Configuration {

            return (configuration as Konfiguration).value[key]
        }

        override fun isSpecifiedBy(configuration: Configuration): Boolean {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        // TODO sollecitom check here
        override fun optional(defaultValue: Configuration?): Configuration.Property<Configuration?> = NestedKonfigProperty.Optional(key, description, schema, defaultValue)
    }
}

private fun Map<String, String>.onlyWithPrefix(prefix: String): Map<String, String> {

    val prefixValue = if (prefix.isNotEmpty()) "$prefix." else prefix
    return filterKeys { key -> key.startsWith(prefixValue) }.mapKeys { (key, _) -> key.removePrefix(prefixValue) }
}