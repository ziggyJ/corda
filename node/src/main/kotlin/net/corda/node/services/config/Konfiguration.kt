package net.corda.node.services.config

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.DefaultLoaders
import com.uchuhimo.konf.source.Loader
import com.uchuhimo.konf.source.Source
import com.uchuhimo.konf.source.base.FlatSource
import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.util.*

// TODO sollecitom make value protected again
internal open class Konfiguration(val value: Config, final override val specification: Configuration.Specification) : Configuration {

    override fun <VALUE> get(key: Configuration.Property<VALUE>): VALUE = key.valueIn(this)

    override fun <VALUE> getOrNull(key: Configuration.Property<VALUE>): VALUE? = key.valueOrNullIn(this)

    // TODO sollecitom check why it doesn't work
    override fun mutable(): Configuration.Mutable = Konfiguration.Mutable(Config.invoke { value.items.forEach { this.addItem(it) } }, specification)

    private class Mutable(value: Config, specification: Configuration.Specification) : Konfiguration(value, specification), Configuration.Mutable {

        override fun <VALUE> set(key: Configuration.Property<VALUE>, value: VALUE) {

            // TODO sollecitom refactor
            when{
                key is Configuration.Property.Required<VALUE> -> this.value[key.item] = value
                key is Configuration.Property.Optional<VALUE> -> this.value[key.item] = value
                // TODO sollecitom check, improve and refactor
                key is Configuration.Property.RequiredNested && value is Configuration -> {

//                    this.value.at(key.path.joinToString(separator = ".")).clear()
                    val prefix = key.path.joinToString(separator = ".")
                    (value as Konfiguration).value.withPrefix(prefix).items.forEach { this.value.addItem(it, prefix) }
                }
                // TODO sollecitom check, improve and refactor
                key is Configuration.Property.OptionalNested && value is Configuration -> {

//                    this.value.at(key.path.joinToString(separator = ".")).clear()
                    val prefix = key.path.joinToString(separator = ".")
                    (value as Konfiguration).value.withPrefix(prefix).items.forEach { this.value.addItem(it, prefix) }
                }
            }
        }

        override fun mutable() = this
    }

    class Builder(private val value: Config = Config()) : Configuration.Builder {

        override val from = Konfiguration.Builder.SourceSelector(value.from)

        override fun build(specification: Configuration.Specification): Configuration {

            value.addSpec(specification.delegate)
            return Konfiguration(value, specification)
        }

        class SourceSelector(private val from: DefaultLoaders) : Configuration.Builder.SourceSelector {

            override fun systemProperties(prefixFilter: String) = Konfiguration.Builder(from.config.withSource(SystemPropertiesProvider.source(prefixFilter)))

            override fun environment(prefixFilter: String) = Konfiguration.Builder(from.config.withSource(EnvProvider.source(prefixFilter)))

            // TODO sollecitom perhaps expose a different Selector interface for Map & Properties
            override fun properties(properties: Properties): Configuration.Builder {

                @Suppress("UNCHECKED_CAST")
                return hierarchicalMap(properties as Map<String, Any>)
            }

            // TODO sollecitom look here at the difference between .kv() and .flat()
            override fun map(map: Map<String, Any>) = Konfiguration.Builder(from.map.flat(map.mapValues { (_, value) -> value.toString() }))

            override fun hierarchicalMap(map: Map<String, Any>) = Konfiguration.Builder(from.map.hierarchical(map))

            override val hocon: Configuration.Builder.SourceSelector.FormatAware
                get() = Konfiguration.Builder.SourceSelector.FormatAware(from.hocon)

            override val yaml: Configuration.Builder.SourceSelector.FormatAware
                get() = Konfiguration.Builder.SourceSelector.FormatAware(from.yaml)

            override val xml: Configuration.Builder.SourceSelector.FormatAware
                get() = Konfiguration.Builder.SourceSelector.FormatAware(from.xml)

            override val json: Configuration.Builder.SourceSelector.FormatAware
                get() = Konfiguration.Builder.SourceSelector.FormatAware(from.json)

            class FormatAware(private val loader: Loader) : Configuration.Builder.SourceSelector.FormatAware {

                override fun file(path: Path) = Konfiguration.Builder(loader.file(path.toAbsolutePath().toFile()))

                override fun resource(resourceName: String) = Konfiguration.Builder(loader.resource(resourceName))

                override fun reader(reader: Reader) = Konfiguration.Builder(loader.reader(reader))

                override fun inputStream(stream: InputStream) = Konfiguration.Builder(loader.inputStream(stream))

                override fun string(rawFormat: String) = Konfiguration.Builder(loader.string(rawFormat))

                override fun bytes(bytes: ByteArray) = Konfiguration.Builder(loader.bytes(bytes))
            }
        }
    }
}

private object SystemPropertiesProvider {

    fun source(prefixFilter: String = ""): Source = FlatSource(System.getProperties().toMap().onlyWithPrefix(prefixFilter), type = "system-properties")

    @Suppress("UNCHECKED_CAST")
    private fun Properties.toMap(): Map<String, String> = this as Map<String, String>
}

private object EnvProvider {

    fun source(prefixFilter: String = ""): Source {

        return FlatSource(System.getenv().mapKeys { (key, _) -> key.toLowerCase().replace('_', '.') }.onlyWithPrefix(prefixFilter), type = "system-environment")
    }
}

private fun Map<String, String>.onlyWithPrefix(prefix: String): Map<String, String> {

    val prefixValue = if  (prefix.isNotEmpty()) "$prefix." else prefix
    return filterKeys { key -> key.startsWith(prefixValue) }.mapKeys { (key, _) -> key.removePrefix(prefixValue) }
}