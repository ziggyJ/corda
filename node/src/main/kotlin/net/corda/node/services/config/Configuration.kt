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
        val from: Builder.SourceSelector = Konfiguration.Builder.SourceSelector(Config.invoke().from)
    }

    fun mutable(): Configuration.Mutable

    operator fun <VALUE> get(key: String): VALUE

    operator fun <VALUE> get(key: Configuration.Property<VALUE>): VALUE

    fun <VALUE> getOrNull(key: String): VALUE?

    fun <VALUE> getOrNull(key: Configuration.Property<VALUE>): VALUE?

    interface Mutable : Configuration {

        operator fun set(key: String, value: Any?)

        override fun mutable() = this
    }

    // TODO sollecitom refactor
    open class Specification(val prefix: String = "") {

        internal val delegate = object : ConfigSpec(prefix) {}

        private val propertiesSet = mutableSetOf<Configuration.Property<*>>()

        val properties: Set<Configuration.Property<*>> = propertiesSet

        inline fun <reified T> required(name: String? = null, description: String = ""): DelegatedProperty<T> = required(name, description, null is T)

        inline fun <reified T> optional(default: T, name: String? = null, description: String = ""): DelegatedProperty<T> = optional(default, name, description, null is T)

        fun <TYPE> required(name: String?, description: String, nullable: Boolean): DelegatedProperty<TYPE> {

            return object : RequiredDelegatedProperty<TYPE>({ propertiesSet.add(it) }, delegate, name, description, nullable) {}
        }

        fun <TYPE> optional(default: TYPE, name: String?, description: String, nullable: Boolean): DelegatedProperty<TYPE> {

            return object : OptionalDelegatedProperty<TYPE>({ propertiesSet.add(it) }, delegate, default, name, description, nullable) {}
        }
    }

    // TODO sollecitom refactor
    sealed class Property<TYPE>(val name: String, val description: String, val type: Class<TYPE>, val nullable: Boolean, val optional: Boolean, val default: TYPE?, internal val item: Item<TYPE>) {

        internal class Required<TYPE>(name: String, description: String, type: Class<TYPE>, nullable: Boolean, item: RequiredItem<TYPE>) : Property<TYPE>(name, description, type, nullable, false, null, item)

        internal class Optional<TYPE>(default: TYPE, name: String, description: String, type: Class<TYPE>, nullable: Boolean, item: OptionalItem<TYPE>) : Property<TYPE>(name, description, type, nullable, true, default, item)
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
        private val addProperty: (Configuration.Property<T>) -> Unit,
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
        val prop = Configuration.Property.Required(name, description, type.rawClass as Class<T>, nullable, item).also(addProperty)

        return object : ReadOnlyProperty<Any?, Configuration.Property<T>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): Configuration.Property<T> = prop
        }
    }
}

open class OptionalDelegatedProperty<T>(
        private val addProperty: (Configuration.Property<T>) -> Unit,
        private val spec: Spec,
        private val default: T,
        private val name: String? = null,
        private val description: String = "",
        private val nullable: Boolean = false
) : DelegatedProperty<T> {

    @Suppress("LeakingThis")
    private val type: JavaType = TypeFactory.defaultInstance().constructType(this::class.java).findSuperType(OptionalProperty::class.java).bindings.typeParameters[0]

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property<T>> {

        val name = name ?: property.name
        val item = object : OptionalItem<T>(spec, name, default, description, type, nullable) {}
        val prop = Configuration.Property.Optional(default, name, description, type.rawClass as Class<T>, nullable, item).also(addProperty)

        return object : ReadOnlyProperty<Any?, Configuration.Property<T>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): Configuration.Property<T> = prop
        }
    }
}