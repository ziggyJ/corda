package net.corda.node.services.config

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.type.TypeFactory
import com.uchuhimo.konf.*
import java.io.InputStream
import java.io.Reader
import java.nio.file.Path
import java.util.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

interface Configuration {

    companion object {

        // TODO sollecitom perhaps try to use JvmStatic here
        val from: Builder.SourceSelector get() = Konfiguration.Builder.SourceSelector(Config.invoke().from)

        fun empty(specification: Configuration.Specification): Configuration = Konfiguration(Config.invoke(), specification)
    }

    fun mutable(): Configuration.Mutable

    operator fun <VALUE> get(key: Configuration.Property<VALUE>): VALUE

    fun <VALUE> getOrNull(key: Configuration.Property<VALUE>): VALUE?

    interface Mutable : Configuration {

        operator fun <VALUE> set(key: Configuration.Property<VALUE>, value: VALUE)

        override fun mutable() = this
    }

    val specification: Configuration.Specification

    // TODO sollecitom refactor - make it private if possible - make delegate internal again
    sealed class Specification constructor(val prefix: String = "", val delegate: ConfigSpec, val properties: Set<Configuration.Property<*>>) {

        // TODO sollecitom make this private if possible
        open class Mutable private constructor(prefix: String = "", delegate: ConfigSpec, properties: MutableSet<Configuration.Property<*>>) : Specification(prefix, delegate, properties) {

            constructor(prefix: String = "") : this(prefix, object : ConfigSpec(prefix) {}, mutableSetOf())

            // TODO sollecitom refactor private
            val addProperty: (Configuration.Property<*>) -> Unit = { property: Configuration.Property<*> -> properties.add(property) }

            // TODO sollecitom remove distinction optional/required nullable/non-nullable -> either optional/nullable or required/non-nullable
            // TODO sollecitom move these into a Builder type and use a receiver function to create the spec - avoids `object : Configuration.Specification("name") { ... }`
            inline fun <reified T> required(name: String? = null, description: String = ""): DelegatedProperty<T> = required(name, description, null is T)

            inline fun <reified T> optional(default: T, name: String? = null, description: String = ""): DelegatedProperty<T> = optional(default, name, description, null is T)

            inline fun <reified TYPE> required(name: String?, description: String, nullable: Boolean): DelegatedProperty<TYPE> {

                return object : RequiredDelegatedProperty<TYPE>(addProperty, delegate, name, description, nullable) {}
            }

            inline fun <reified TYPE> optional(default: TYPE, name: String?, description: String, nullable: Boolean): DelegatedProperty<TYPE> {

                return object : OptionalDelegatedProperty<TYPE>(addProperty, delegate, default, name, description, nullable) {}
            }

            inline fun <reified T : Configuration?> required(specification: Configuration.Specification, name: String? = null, description: String = ""): DelegatedProperty<Configuration> = required(specification, name, description, null is T)

            inline fun <reified T : Configuration?> optional(specification: Configuration.Specification, default: Configuration, name: String? = null, description: String = ""): DelegatedProperty<Configuration> = optional(specification, default, name, description, null is T)

            fun required(specification: Configuration.Specification, name: String?, description: String, nullable: Boolean): DelegatedProperty<Configuration> {

                // TODO sollecitom maybe merge with the other `required` function, by passing a Configuration.Specification? as param
                return object : RequiredNestedDelegatedProperty(specification, addProperty, delegate, name, description, nullable) {}
            }

            fun optional(specification: Configuration.Specification, default: Configuration, name: String?, description: String, nullable: Boolean): DelegatedProperty<Configuration> {

                // TODO sollecitom maybe merge with the other `optional` function, by passing a Configuration.Specification? as param
                return object : OptionalNestedDelegatedProperty(specification, addProperty, delegate, default, name, description, nullable) {}
            }
        }
    }

    // TODO sollecitom refactor
    sealed class Property<TYPE>(protected val name: String, val description: String, val nullable: Boolean, val optional: Boolean, val default: TYPE?) {

        abstract val path: List<String>

        abstract val fullPath: List<String>

        // TODO sollecitom refactor!
        abstract fun valueIn(configuration: Configuration): TYPE

        // TODO sollecitom refactor!
        abstract fun valueOrNullIn(configuration: Configuration): TYPE?

        internal class Required<TYPE>(name: String, description: String, nullable: Boolean, internal val item: RequiredItem<TYPE>) : Property<TYPE>(name, description, nullable, false, null) {

            override val path: List<String> get() = item.path

            override val fullPath: List<String> get() = item.spec.prefix.toPath() + path

            // TODO sollecitom refactor!
            override fun valueIn(configuration: Configuration): TYPE {

                return (configuration as Konfiguration).value[item]
            }

            // TODO sollecitom refactor!
            override fun valueOrNullIn(configuration: Configuration): TYPE? {

                return (configuration as Konfiguration).value.getOrNull(item)
            }
        }

        internal class Optional<TYPE>(default: TYPE, name: String, description: String, nullable: Boolean, internal val item: OptionalItem<TYPE>) : Property<TYPE>(name, description, nullable, true, default) {

            override val path: List<String> get() = item.path

            override val fullPath: List<String> get() = item.spec.prefix.toPath() + path

            // TODO sollecitom refactor!
            override fun valueIn(configuration: Configuration): TYPE {

                return (configuration as Konfiguration).value[item]
            }

            // TODO sollecitom refactor!
            override fun valueOrNullIn(configuration: Configuration): TYPE? {

                return (configuration as Konfiguration).value.getOrNull(item)
            }
        }

        // TODO sollecitom refactor!
        internal class RequiredNested(private val specification: Specification, private val parentPath: List<String> = emptyList(), name: String, description: String, nullable: Boolean) : Property<Configuration>(name, description, nullable, false, null) {

            override val path: List<String> get() = listOf(name)

            override val fullPath: List<String> get() = parentPath + path

            // TODO sollecitom refactor!
            override fun valueIn(configuration: Configuration): Configuration {

                // TODO sollecitom check name vs path
                return Konfiguration((configuration as Konfiguration).value.at(name), specification)
//                return Konfiguration.Builder((configuration as Konfiguration).value.at(name)).build(specification)
            }

            // TODO sollecitom refactor!
            override fun valueOrNullIn(configuration: Configuration): Configuration? {

                val config = (configuration as Konfiguration).value
                // TODO check here nullable vs required/optional
                // TODO sollecitom check name vs path
                if (config.contains(name)) {
                    // TODO sollecitom check name vs path
                    return Konfiguration(config.at(name), specification)
//                    return Konfiguration.Builder(config.at(name)).build(specification)
                }
                return null
            }
        }

        // TODO sollecitom refactor!
        internal class OptionalNested(private val specification: Specification, private val parentPath: List<String> = emptyList(), default: Configuration, name: String, description: String, nullable: Boolean) : Property<Configuration>(name, description, nullable, true, default) {

            override val path: List<String> get() = listOf(name)

            override val fullPath: List<String> get() = parentPath + path

            // TODO sollecitom refactor!
            override fun valueIn(configuration: Configuration): Configuration {

                // TODO sollecitom check name vs path
                return Konfiguration((configuration as Konfiguration).value.at(name), specification)
//                return Konfiguration.Builder((configuration as Konfiguration).value.at(name)).build(specification)
            }

            // TODO sollecitom refactor!
            override fun valueOrNullIn(configuration: Configuration): Configuration? {

                val config = (configuration as Konfiguration).value
                // TODO check here nullable vs required/optional
                // TODO sollecitom check name vs path
                if (config.contains(name)) {
                    // TODO sollecitom check name vs path
                    return Konfiguration(config.at(name), specification)
//                    return Konfiguration.Builder(config.at(name)).build(specification)
                }
                return null
            }
        }
    }

    interface Builder {

        val from: SourceSelector

        fun build(specification: Specification): Configuration

        interface SourceSelector {

            fun systemProperties(prefixFilter: String = ""): Configuration.Builder

            fun environment(prefixFilter: String = ""): Configuration.Builder

            fun properties(properties: Properties): Configuration.Builder

            fun map(map: Map<String, Any>): Configuration.Builder

            fun hierarchicalMap(map: Map<String, Any>): Configuration.Builder

            val hocon: SourceSelector.FormatAware

            val yaml: SourceSelector.FormatAware

            val xml: SourceSelector.FormatAware

            val json: SourceSelector.FormatAware

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
}

interface DelegatedProperty<T> {

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property<T>>
}

open class RequiredDelegatedProperty<T>(
        private val addProperty: (Configuration.Property<*>) -> Unit,
        private val spec: Spec,
        private val name: String? = null,
        private val description: String = "",
        private val nullable: Boolean = false
) : DelegatedProperty<T> {

    @Suppress("LeakingThis")
    private val type: JavaType = TypeFactory.defaultInstance().constructType(this::class.java).findSuperType(RequiredDelegatedProperty::class.java).bindings.typeParameters[0]

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property<T>> {

        val name = name ?: property.name
        val item = object : RequiredItem<T>(spec, name, description, type, nullable) {}
        val prop = Configuration.Property.Required(name, description, nullable, item).also(addProperty)

        return object : ReadOnlyProperty<Any?, Configuration.Property<T>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): Configuration.Property<T> = prop
        }
    }
}

open class OptionalDelegatedProperty<T>(
        private val addProperty: (Configuration.Property<*>) -> Unit,
        private val spec: Spec,
        private val default: T,
        private val name: String? = null,
        private val description: String = "",
        private val nullable: Boolean = false
) : DelegatedProperty<T> {

    @Suppress("LeakingThis")
    private val type: JavaType = TypeFactory.defaultInstance().constructType(this::class.java).findSuperType(OptionalDelegatedProperty::class.java).bindings.typeParameters[0]

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property<T>> {

        val name = name ?: property.name
        val item = object : OptionalItem<T>(spec, name, default, description, type, nullable) {}
        val prop = Configuration.Property.Optional(default, name, description, nullable, item).also(addProperty)

        return object : ReadOnlyProperty<Any?, Configuration.Property<T>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): Configuration.Property<T> = prop
        }
    }
}

// TODO sollecitom perhaps join with `RequiredDelegatedProperty` by adding a field of type Configuration.Specification?
open class RequiredNestedDelegatedProperty(
        private val specification: Configuration.Specification,
        private val addProperty: (Configuration.Property<*>) -> Unit,
        private val spec: Spec,
        private val name: String? = null,
        private val description: String = "",
        private val nullable: Boolean = false
) : DelegatedProperty<Configuration> {

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property<Configuration>> {

        val name = name ?: property.name
        val prop = Configuration.Property.RequiredNested(specification, spec.prefix.toPath(), name, description, nullable).also(addProperty)

        return object : ReadOnlyProperty<Any?, Configuration.Property<Configuration>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): Configuration.Property<Configuration> = prop
        }
    }
}

// TODO sollecitom perhaps join with `OptionalDelegatedProperty` by adding a field of type Configuration.Specification?
open class OptionalNestedDelegatedProperty(
        private val specification: Configuration.Specification,
        private val addProperty: (Configuration.Property<*>) -> Unit,
        private val spec: Spec,
        private val default: Configuration,
        private val name: String? = null,
        private val description: String = "",
        private val nullable: Boolean = false
) : DelegatedProperty<Configuration> {

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property<Configuration>> {

        val name = name ?: property.name
        val prop = Configuration.Property.OptionalNested(specification, spec.prefix.toPath(), default, name, description, nullable).also(addProperty)

        return object : ReadOnlyProperty<Any?, Configuration.Property<Configuration>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): Configuration.Property<Configuration> = prop
        }
    }
}